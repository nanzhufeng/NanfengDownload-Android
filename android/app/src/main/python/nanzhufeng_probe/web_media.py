"""Public website adapter. Existing platform extractors keep their own contracts."""
import hashlib
import json
from urllib.parse import parse_qs, urlsplit

KNOWN_HOSTS = ("youtube.com", "youtu.be", "douyin.com", "iesdouyin.com", "tiktok.com",
               "bilibili.com", "b23.tv", "xiaohongshu.com", "rednote.com", "xhslink.com", "xhslink.cn")
SEGMENT_PROTOCOLS = {"m3u8", "m3u8_native", "http_dash_segments"}


def is_web_source(url):
    host = urlsplit(url).hostname or ""
    return not any(host == domain or host.endswith("." + domain) for domain in KNOWN_HOSTS)


def validate_url(url):
    parsed = urlsplit(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("请输入不含账号密码的 HTTP/HTTPS 网站链接")
    return parsed


def web_media_result(info, url, resolution):
    from .youtube_probe import _media_result
    parsed = validate_url(url)
    if info.get("_type") in {"playlist", "multi_video"} or info.get("entries") is not None:
        raise ValueError("这是作品合集，请选择具体作品后下载")
    if info.get("is_live") or info.get("has_drm"):
        raise ValueError("该资源是直播或受 DRM 保护，当前不能保存为普通视频")
    raw_formats = info.get("formats") or ([info] if info.get("url") else [])
    normalized = []
    for index, original in enumerate(raw_formats):
        if original.get("has_drm") or not original.get("url"):
            continue
        protocol = original.get("protocol") or urlsplit(original["url"]).scheme
        if protocol not in SEGMENT_PROTOCOLS | {"http", "https"}:
            continue
        validate_url(original["url"])
        fmt = dict(original)
        # The shared selector ranks quality; only this adapter enables segment
        # sources. The actual protocol survives in the separate download spec.
        fmt["url"] = f"https://selection.invalid/{index}"
        fmt["protocol"] = "https"
        if fmt.get("ext") in {"mp3", "m4a", "aac", "ogg", "opus", "wav", "flac"}:
            fmt["vcodec"] = "none"
            fmt["acodec"] = fmt.get("acodec") or "unknown"
        else:
            # yt-dlp's direct HTTP extractor emits codec keys with None, so
            # setdefault alone still drops a valid progressive MP4.
            fmt["vcodec"] = fmt.get("vcodec") or "unknown"
            fmt["acodec"] = fmt.get("acodec") or "unknown"
            # Ranking is container-independent; real extension is restored below.
            fmt["ext"] = "mp4"
        normalized.append(fmt)
    audio_only = bool(normalized) and all(item.get("vcodec") == "none" for item in normalized)
    result = _media_result({**info, "formats": normalized},
                           resolution="AUDIO_MP3" if audio_only else resolution)
    host = parsed.hostname.lower()
    is_pornhub = host == "pornhub.com" or host.endswith(".pornhub.com")
    extractor = str(info.get("extractor_key") or info.get("extractor") or "generic").lower()
    content_id = str(info.get("id") or "")
    if is_pornhub:
        expected = (parse_qs(parsed.query).get("viewkey") or [""])[0]
        if expected and content_id != expected:
            raise ValueError("解析结果不是该 Pornhub 链接的目标作品")
    else:
        legacy_identity = f"{host}\n{extractor}\n{content_id or url}"
        # GenericIE often reports only a basename (e.g. index.m3u8), which is
        # shared by unrelated works. Keep the task's full input URL as its identity.
        identity = f"{host}\n{extractor}\n{url}" if extractor == "generic" else legacy_identity
        if extractor == "generic":
            result["legacy_id"] = hashlib.sha256(legacy_identity.encode()).hexdigest()
        content_id = hashlib.sha256(identity.encode()).hexdigest()
    if not content_id:
        raise ValueError("网站未返回可确认的作品标识")
    result.update(platform="pornhub" if is_pornhub else "generic", id=content_id, webpage_url=url)
    result["audio_only"] = audio_only
    for role in ("video", "audio"):
        selected = result[f"{role}_url"]
        result[f"{role}_download_spec"] = ""
        result[f"legacy_{role}_download_spec"] = ""
        if not selected:
            continue
        original = raw_formats[int(urlsplit(selected).path.strip("/"))]
        result[f"{role}_url"] = original["url"]
        result[f"{role}_ext"] = original.get("ext") or "mp4"
        headers = dict(info.get("http_headers") or {})
        headers.update(original.get("http_headers") or {})
        headers = {k: v for k, v in headers.items()
                   if k.lower() not in {"cookie", "authorization", "proxy-authorization", "host"}}
        headers.setdefault("Referer", url)
        result[f"{role}_headers"] = headers
        protocol = original.get("protocol") or urlsplit(original["url"]).scheme
        if protocol in SEGMENT_PROTOCOLS:
            fields = ("url", "ext", "format_id", "fragments", "fragment_base_url",
                      "extra_param_to_segment_url", "extra_param_to_key_url")
            spec = {key: original[key] for key in fields if key in original}
            spec.update(protocol="m3u8_native" if protocol == "m3u8" else protocol,
                        id=content_id, http_headers=headers)
            if is_pornhub:
                spec["source_page_url"] = url
            result[f"{role}_download_spec"] = json.dumps(spec, ensure_ascii=False)
            if result.get("legacy_id"):
                result[f"legacy_{role}_download_spec"] = json.dumps(
                    {**spec, "id": result["legacy_id"]}, ensure_ascii=False)
    result["headers"] = result.get("video_headers", {})
    return result
