"""Native yt-dlp fragments only; Android owns final container export/validation."""
import hashlib
import json
import os
import re
import threading
import time
from contextlib import contextmanager
from urllib.parse import urljoin

from yt_dlp.downloader.hls import HlsFD
from yt_dlp.downloader.dash import DashSegmentsFD
from yt_dlp.networking import Request
from yt_dlp.networking.exceptions import HTTPError, TransportError

from .web_media import validate_url
from .pornhub_http import transfer_youtube_dl_configuration
from .ts_timeline import normalize_ts_discontinuities


class TransferCancelled(Exception):
    pass


_target_guard = threading.Lock()
_target_owners = {}


@contextmanager
def _own_target(destination, observer):
    key = os.path.abspath(destination)
    with _target_guard:
        owner = _target_owners.setdefault(key, [threading.Lock(), 0])
        owner[1] += 1
    acquired = False
    try:
        while not acquired:
            if observer.cancelled():
                raise TransferCancelled("下载已暂停或取消")
            acquired = owner[0].acquire(timeout=0.1)
        yield
    finally:
        if acquired:
            owner[0].release()
        with _target_guard:
            owner[1] -= 1
            if owner[1] == 0:
                del _target_owners[key]


class _QuietLogger:
    def debug(self, message):
        pass

    def warning(self, message):
        pass

    def error(self, message):
        # Do not print signed CDN addresses or server response bodies.
        pass


class _AssemblerOwnedStream:
    """A fragment worker must not close the ordered assembler's shared stream.

    yt-dlp shallow-copies its context for parallel workers and closes
    dest_stream when a fatal fragment exhausts retries. The assembler still
    receives that failure, and our existing finally closes the real stream.
    """
    def __init__(self, stream):
        self._stream = stream
        self._owner = threading.get_ident()

    def __getattr__(self, name):
        return getattr(self._stream, name)

    def close(self):
        if threading.get_ident() == self._owner:
            self._stream.close()


def _hls_variant(manifest, base_url, max_height):
    """Pick one self-contained rendition; never silently discard external audio."""
    lines = [line.strip() for line in manifest.splitlines()]
    variants = []
    for index, line in enumerate(lines[:-1]):
        if not line.startswith("#EXT-X-STREAM-INF:"):
            continue
        details = line.split(":", 1)[1]
        target = lines[index + 1]
        if not target or target.startswith("#"):
            continue
        if re.search(r"(?:^|,)AUDIO=", details):
            raise ValueError("该 HLS 主清单使用独立音轨，请通过作品页重新解析，未保存无声视频")
        resolution = re.search(r"(?:^|,)RESOLUTION=\d+x(\d+)", details)
        bandwidth = re.search(r"(?:^|,)BANDWIDTH=(\d+)", details)
        height = int(resolution.group(1)) if resolution else 0
        rate = int(bandwidth.group(1)) if bandwidth else 0
        url = urljoin(base_url, target)
        validate_url(url)
        variants.append((height, rate, url))
    if not variants:
        return None
    allowed = [item for item in variants if item[0] <= max_height]
    if allowed:
        return max(allowed, key=lambda item: (item[0], item[1]))[2]
    return min(variants, key=lambda item: (item[0], -item[1]))[2]


def _clear_stale_plans(destination, current_fingerprint):
    """Remove obsolete yt-dlp fragments for this one cache destination only."""
    directory = os.path.dirname(destination) or "."
    if not os.path.isdir(directory):
        return
    prefix = os.path.basename(destination) + "."
    pattern = re.compile(re.escape(prefix) +
                         r"([a-f0-9]{24})\.segments(?:\.part(?:-Frag\d+(?:\.part)?)?|\.ytdl|\.timeline\.ts(?:\.part)?)?")
    with os.scandir(directory) as entries:
        for entry in entries:
            match = pattern.fullmatch(entry.name)
            if match and match.group(1) != current_fingerprint and entry.is_file(follow_symlinks=False):
                os.unlink(entry.path)


def _load_hls_plan(path, request_fingerprint):
    try:
        with open(path, encoding="utf-8") as stream:
            raw = stream.read(5 * 1024 * 1024 + 1)
        if len(raw) > 5 * 1024 * 1024:
            return None
        saved = json.loads(raw)
        if not isinstance(saved, dict):
            return None
        info = saved.get("info")
        if saved.get("request_fingerprint") != request_fingerprint or not isinstance(info, dict):
            return None
        validate_url(info["url"])
        manifest = info.get("hls_media_playlist_data")
        if info.get("protocol") != "m3u8_native" or not isinstance(manifest, str):
            return None
        return info
    except (OSError, ValueError, KeyError, TypeError):
        return None


def _save_hls_plan(path, request_fingerprint, info):
    temporary = path + ".tmp"
    try:
        with open(temporary, "w", encoding="utf-8") as stream:
            json.dump({"request_fingerprint": request_fingerprint, "info": info}, stream)
        os.replace(temporary, path)
    finally:
        if os.path.isfile(temporary):
            os.unlink(temporary)


def download_segments(spec_json, destination, observer):
    """observer exposes cancelled() and progress(bytes, total); works from JVM or tests."""
    # WorkManager replacement may start before a cancelled native read unwinds.
    # The successor must not share fragment streams or checkpoint files with it.
    with _own_target(destination, observer):
        return _download_owned_segments(spec_json, destination, observer)


def _download_owned_segments(spec_json, destination, observer):
    info = json.loads(spec_json)
    validate_url(info["url"])
    protocol = info.get("protocol")
    if protocol not in {"m3u8_native", "http_dash_segments"}:
        raise ValueError("不支持的分段协议")
    headers = {k: v for k, v in info.get("http_headers", {}).items()
               if k.lower() not in {"cookie", "authorization", "proxy-authorization", "host"}}
    info["http_headers"] = headers
    info["is_live"] = False
    is_cancelled = observer.cancelled
    on_progress = observer.progress
    on_network = getattr(observer, "network", None)
    on_http_failure = getattr(observer, "httpFailure", None)

    def phase(name, started):
        if hasattr(observer, "phase"):
            observer.phase(name, int((time.monotonic() - started) * 1000))

    def check_cancel():
        if is_cancelled():
            raise TransferCancelled("下载已暂停或取消")

    def progress(event):
        check_cancel()
        on_progress(int(event.get("downloaded_bytes") or 0), int(event.get("total_bytes") or 0))
        check_cancel()

    check_cancel()
    connection_budget = int(observer.connections()) if hasattr(observer, "connections") else 1
    connection_budget = max(1, min(connection_budget, 6))
    options = {"quiet": True, "no_warnings": True, "logger": _QuietLogger(),
               "socket_timeout": 15, "retries": 2, "fragment_retries": 2,
               "skip_unavailable_fragments": False, "concurrent_fragment_downloads": connection_budget,
               "continuedl": True, "noprogress": True, "fixup": "never",
               "buffersize": 256 * 1024, "noresizebuffer": True,
               "http_headers": headers}
    ydl_base, options = transfer_youtube_dl_configuration(info, options)
    headers = options["http_headers"]
    info["http_headers"] = headers
    network_bytes = 0
    last_network_update = 0.0
    slow_recovery_used = False
    meter_lock = threading.Lock()
    response_lock = threading.Lock()
    expired_plan = threading.Event()
    open_responses = []
    plan_path = destination + ".hls-plan.json"
    request_fingerprint = hashlib.sha256(json.dumps(info, sort_keys=True).encode()).hexdigest()

    def notify_network(force=False):
        nonlocal last_network_update
        now = time.monotonic()
        if on_network is not None and (force or now - last_network_update >= .25):
            on_network(network_bytes)
            last_network_update = now

    class MeteredYoutubeDL(ydl_base):
        def close(self):
            try:
                for response in open_responses:
                    response.close()
            finally:
                super().close()

        def urlopen(self, request):
            check_cancel()
            try:
                response = super().urlopen(request)
            except HTTPError as error:
                if error.status in {401, 403, 404, 410}:
                    expired_plan.set()
                if on_http_failure is not None:
                    on_http_failure(error.status)
                error.response.close()
                raise
            # A healthy parallel sibling must not erase a failed fragment's
            # HTTP status before the caller can refresh an expired source.
            with response_lock:
                open_responses[:] = [item for item in open_responses if not item.closed]
                open_responses.append(response)
            read = response.read
            try:
                response_bytes = int(response.headers.get("Content-Length") or 0)
            except (TypeError, ValueError):
                response_bytes = 0
            window_started = time.monotonic()
            window_bytes = 0
            received_bytes = 0

            def metered_read(*args):
                nonlocal network_bytes, slow_recovery_used, window_started, window_bytes, received_bytes
                check_cancel()
                data = read(*args)
                reconnect = False
                with meter_lock:
                    network_bytes += len(data)
                    received_bytes += len(data)
                    window_bytes += len(data)
                    notify_network()
                    now = time.monotonic()
                    elapsed = now - window_started
                    # One recovery across this fragment plan. Small manifests and nearly
                    # complete fragments stay on their existing connection; healthy siblings
                    # and their committed files are never discarded by this intervention.
                    if (data and not slow_recovery_used and response_bytes >= 512 * 1024
                            and response_bytes - received_bytes >= 128 * 1024 and elapsed >= 5):
                        if window_bytes / elapsed < 128 * 1024:
                            slow_recovery_used = True
                            reconnect = True
                        window_started, window_bytes = now, 0
                if reconnect:
                    response.close()
                    raise TransportError("媒体连接持续低速，保留分片断点后重新连接")
                return data

            response.read = metered_read
            return response

    with MeteredYoutubeDL(options) as ydl:
        manifest_started = time.monotonic()
        if protocol == "m3u8_native":
            saved = _load_hls_plan(plan_path, request_fingerprint)
            if saved is not None:
                info = saved
                manifest = info["hls_media_playlist_data"]
            else:
                for _ in range(3):
                    with ydl.urlopen(Request(info["url"], headers=headers)) as response:
                        info["url"] = response.url
                        manifest = response.read(4 * 1024 * 1024 + 1)
                    if len(manifest) > 4 * 1024 * 1024:
                        raise ValueError("HLS 清单过大")
                    manifest = manifest.decode("utf-8-sig")
                    variant = _hls_variant(manifest, info["url"], int(info.get("max_height") or 100000))
                    if variant is None:
                        break
                    info["url"] = variant
                else:
                    raise ValueError("HLS 主清单嵌套过深")
            if "#EXT-X-ENDLIST" not in manifest:
                raise ValueError("该 HLS 不是已结束的点播清单，请使用具体点播作品链接")
            if not HlsFD.can_download(manifest, info):
                raise ValueError("该 HLS 使用 DRM 或当前不支持的封装，未生成不完整视频")
            info["hls_media_playlist_data"] = manifest
            _save_hls_plan(plan_path, request_fingerprint, info)
            downloader_class = HlsFD
        else:
            fragments = info.get("fragments")
            if not isinstance(fragments, list) or not fragments:
                raise ValueError("DASH 缺少完整分片清单")
            for fragment in fragments:
                validate_url(fragment.get("url") or urljoin(info.get("fragment_base_url", ""), fragment.get("path", "")))
            downloader_class = DashSegmentsFD
        phase("fragment_manifest", manifest_started)
        check_cancel()
        # Include the manifest/fragment plan, not just a filename, in resume identity.
        # A changed signed URL/plan restarts safely instead of mixing two versions.
        fingerprint = hashlib.sha256(json.dumps(info, sort_keys=True).encode()).hexdigest()[:24]
        raw_path = destination + "." + fingerprint + ".segments"
        _clear_stale_plans(destination, fingerprint)
        contexts = []

        class OwnedFragmentDownloader(downloader_class):
            def _write_ytdl_file(self, context):
                # Parallel native progress mutates fragment_index on the shared
                # context. Persist the fragment actually flushed by the ordered
                # assembler, not the number of independently finished requests.
                checkpoint = dict(context)
                filename = context.get("fragment_filename_sanitized") or ""
                appended = re.search(r"-Frag(\d+)$", filename)
                if appended:
                    checkpoint["fragment_index"] = int(appended.group(1))
                return super()._write_ytdl_file(checkpoint)

            def _prepare_and_start_frag_download(self, context, media_info):
                contexts.append(context)
                result = super()._prepare_and_start_frag_download(context, media_info)
                if connection_budget > 1 and context.get("dest_stream") is not None:
                    context["dest_stream"] = _AssemblerOwnedStream(context["dest_stream"])
                return result

        downloader = OwnedFragmentDownloader(ydl, options)
        downloader.add_progress_hook(progress)
        transfer_started = time.monotonic()
        try:
            succeeded, _ = downloader.download(raw_path, info)
        except BaseException:
            if protocol == "m3u8_native" and expired_plan.is_set():
                if os.path.isfile(plan_path):
                    os.unlink(plan_path)
            raise
        finally:
            # yt-dlp's fragment stream is otherwise left open when a user
            # cancellation escapes its progress hook. Keep partials for resume.
            for context in contexts:
                stream = context.get("dest_stream")
                if stream is not None and not stream.closed:
                    stream.close()
            with meter_lock:
                notify_network(force=True)
            phase("fragment_transfer", transfer_started)
        check_cancel()
        if not succeeded or not os.path.isfile(raw_path) or not os.path.getsize(raw_path):
            raise ValueError("分段下载未完成，没有可发布的文件")
        if protocol == "m3u8_native":
            discontinuities = sum(line.strip() == "#EXT-X-DISCONTINUITY" for line in manifest.splitlines())
            duration = 0.0
            boundaries = []
            for line in manifest.splitlines():
                if line.startswith("#EXTINF:"):
                    duration += float(line.split(":", 1)[1].split(",", 1)[0])
                elif line.strip() == "#EXT-X-DISCONTINUITY":
                    boundaries.append(duration)
            normalization_started = time.monotonic()
            try:
                return normalize_ts_discontinuities(raw_path, discontinuities, is_cancelled,
                                                    expected_duration=duration, expected_boundaries=boundaries)
            except InterruptedError as error:
                raise TransferCancelled("下载已暂停或取消") from error
            finally:
                phase("fragment_timeline", normalization_started)
        return raw_path
