import json
import re
import sys
from datetime import datetime, timezone
from urllib.error import HTTPError
from urllib.parse import parse_qs, quote, urlencode, urljoin, urlsplit, urlunsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

import yt_dlp
from yt_dlp import YoutubeDL


_MOBILE_USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)
_DESKTOP_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_NO_REDIRECT_OPENER = build_opener(_NoRedirect())
_REDIRECT_STATUS_CODES = {301, 302, 303, 307, 308}


def runtime_info() -> str:
    return json.dumps(
        {"python": sys.version.split()[0], "yt_dlp": yt_dlp.version.__version__},
        ensure_ascii=False,
    )


def _with_session_access(options, cookie_header="", cookie_file=""):
    scoped = dict(options)
    # Cookie jars apply host rules on each request. A raw Cookie header does
    # not, so only legacy callers without a cookie file may use it.
    if cookie_header and not cookie_file:
        headers = dict(scoped.get("http_headers") or {})
        headers["Cookie"] = cookie_header
        scoped["http_headers"] = headers
    if cookie_file:
        scoped["cookiefile"] = cookie_file
    return scoped


def _without_credential_headers(headers):
    """Keep extractor fetch hints, but never treat credentials as generic headers."""
    return {
        name: value
        for name, value in dict(headers or {}).items()
        if str(name).lower() not in {"cookie", "authorization", "proxy-authorization", "host"}
    }


def _best(items, predicate, score):
    candidates = [item for item in items if predicate(item)]
    return max(candidates, key=score) if candidates else None


def _is_direct_media_format(item):
    protocol = str(item.get("protocol") or "").lower()
    return protocol not in {"m3u8", "m3u8_native", "http_dash_segments"}


def _select_audio(formats):
    def is_audio(item):
        return (
            item.get("url")
            and _is_direct_media_format(item)
            and item.get("acodec") not in {None, "none"}
            and item.get("vcodec") in {None, "none"}
        )

    def is_mp3_compatible(item):
        channels = item.get("audio_channels")
        return channels is None or channels in {1, 2}

    score = lambda item: (item.get("abr") or item.get("tbr") or 0)
    compatible = _best(
        formats,
        lambda item: (
            is_audio(item)
            and is_mp3_compatible(item)
            and item.get("ext") in {"m4a", "mp4"}
        ),
        score,
    )
    return compatible or _best(
        formats,
        lambda item: is_audio(item) and is_mp3_compatible(item),
        score,
    )


def _short_edge(item):
    dimensions = [
        value
        for value in (item.get("width"), item.get("height"))
        if isinstance(value, (int, float)) and value > 0
    ]
    return min(dimensions) if dimensions else 0


def _select_audio_conversion_video(formats, max_short_edge=720):
    def is_progressive(item):
        return (
            item.get("url")
            and _is_direct_media_format(item)
            and item.get("vcodec") not in {None, "none"}
            and item.get("acodec") not in {None, "none"}
            and item.get("audio_channels") in {None, 1, 2}
        )

    progressive = [item for item in formats if is_progressive(item)]
    bounded = [
        item
        for item in progressive
        if 0 < _short_edge(item) <= max_short_edge
    ]
    if bounded:
        return max(
            bounded,
            key=lambda item: (
                _short_edge(item),
                item.get("abr") or 0,
                item.get("tbr") or 0,
            ),
        )

    unknown_dimensions = [item for item in progressive if _short_edge(item) == 0]
    if unknown_dimensions:
        return max(
            unknown_dimensions,
            key=lambda item: (item.get("abr") or 0, item.get("tbr") or 0),
        )

    # Some platforms expose only one progressive rendition above 720p. It is
    # still a valid last-resort audio source, but choose the smallest one to
    # avoid downloading unnecessary video data merely to extract its audio.
    return min(
        progressive,
        key=lambda item: (
            _short_edge(item),
            -(item.get("abr") or 0),
            item.get("tbr") or 0,
        ),
    ) if progressive else None


def _select_streams(formats, resolution="UP_TO_720P"):
    audio = _select_audio(formats)
    if resolution == "AUDIO_MP3":
        if audio:
            return audio, None
        return _select_audio_conversion_video(formats), None

    max_short_edge = {
        "UP_TO_360P": 360,
        "UP_TO_720P": 720,
        "UP_TO_1080P": 1080,
        "BEST": float("inf"),
    }.get(resolution)
    if max_short_edge is None:
        raise ValueError(f"不支持的分辨率：{resolution}")

    def select_video(predicate):
        candidates = [item for item in formats if predicate(item)]
        bounded = [item for item in candidates if _short_edge(item) <= max_short_edge]
        if bounded:
            return (
                max(
                    bounded,
                    key=lambda item: (
                        item.get("source_preference") or 0,
                        _short_edge(item),
                        item.get("tbr") or 0,
                    ),
                ),
                True,
            )
        # Some platforms expose only 540p or 720p for a work. A quality
        # preset is a preference, not a reason to reject an otherwise valid
        # download: use the smallest available rendition as the fallback.
        return (
            min(
                candidates,
                key=lambda item: (_short_edge(item), -(item.get("tbr") or 0)),
            ) if candidates else None,
            False,
        )

    progressive, progressive_fits = select_video(
        lambda item: item.get("url")
        and _is_direct_media_format(item)
        and item.get("ext") == "mp4"
        and item.get("vcodec") not in {None, "none"}
        and item.get("acodec") not in {None, "none"}
    )
    video, video_fits = select_video(
        lambda item: item.get("url")
        and _is_direct_media_format(item)
        and item.get("ext") == "mp4"
        and item.get("vcodec") not in {None, "none"}
        and item.get("acodec") in {None, "none"}
    )

    # Never let an above-cap fallback displace a stream that actually meets
    # the selected preset. This matters for platforms that offer 360p video
    # plus a separate audio track, while their only progressive file is 540p.
    if progressive_fits or (video_fits and audio):
        if video_fits and audio and (
            not progressive_fits or _short_edge(video) > _short_edge(progressive or {})
        ):
            return video, audio
        if progressive_fits:
            return progressive, None
        return video, audio

    # Neither form matches the requested cap. Pick the smallest valid
    # rendition, rather than failing the task solely because the source has
    # no 360p/720p ladder.
    if video and audio and (
        not progressive or _short_edge(video) < _short_edge(progressive)
    ):
        return video, audio
    if progressive:
        return progressive, None
    return None, None


def _prefer_xiaohongshu_original_formats(info):
    formats = [dict(item) for item in info.get("formats") or []]
    if _platform_name(info) != "xiaohongshu":
        return formats
    for item in formats:
        # yt-dlp labels the verified originVideoKey endpoint as `direct`, but
        # deliberately leaves codecs unknown. It is a progressive MP4; without
        # these fields our generic selector would discard it and choose the
        # platform's watermarked rendition instead.
        if str(item.get("format_id") or "").lower() == "direct" and item.get("url"):
            if not item.get("ext"):
                item["ext"] = "mp4"
            if str(item.get("vcodec") or "").lower() in {"", "none"}:
                item["vcodec"] = "avc1"
            if str(item.get("acodec") or "").lower() in {"", "none"}:
                item["acodec"] = "mp4a"
            item["source_preference"] = max(int(item.get("source_preference") or 0), 100)
    return formats


def _host_matches(host, domain):
    host = str(host or "").lower().split(":", 1)[0]
    return host == domain or host.endswith(f".{domain}")


def _extractor_args_for(host):
    # The default web client now requires a PO Token for Google Video Server
    # requests on many public videos. web_embedded is an official supported
    # client that provides direct MP4 URLs without that token.
    if _host_matches(host, "youtube.com") or host == "youtu.be":
        return {"youtube": {"player_client": ["web_embedded"]}}
    return None


def _request_headers(url, cookie_header=""):
    host = urlsplit(url).netloc.lower()
    headers = {
        "User-Agent": _MOBILE_USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.5",
    }
    if _host_matches(host, "bilibili.com") or _host_matches(host, "b23.tv"):
        headers["Referer"] = "https://www.bilibili.com/"
    elif _host_matches(host, "douyin.com") or _host_matches(host, "iesdouyin.com"):
        headers["Referer"] = "https://www.douyin.com/"
    elif _host_matches(host, "tiktok.com"):
        headers["Referer"] = "https://www.tiktok.com/"
    elif (
        _host_matches(host, "xiaohongshu.com")
        or _host_matches(host, "rednote.com")
        or _host_matches(host, "xhslink.com")
        or _host_matches(host, "xhslink.cn")
    ):
        headers["Referer"] = "https://www.xiaohongshu.com/"
    if cookie_header:
        headers["Cookie"] = cookie_header
    return headers


def _fetch(url, cookie_header="", max_bytes=12 * 1024 * 1024):
    original_host = urlsplit(url).hostname.lower() if urlsplit(url).hostname else ""
    current_url = url
    for _ in range(12):
        parsed = urlsplit(current_url)
        if parsed.scheme.lower() != "https" or not parsed.hostname:
            raise ValueError("平台跳转到了非 HTTPS 地址，已拒绝读取")
        scoped_cookie = cookie_header if parsed.hostname.lower() == original_host else ""
        request = Request(current_url, headers=_request_headers(current_url, scoped_cookie))
        try:
            with _NO_REDIRECT_OPENER.open(request, timeout=20) as response:
                payload = response.read(max_bytes + 1)
                if len(payload) > max_bytes:
                    raise ValueError("平台页面数据过大，已中止读取")
                charset = response.headers.get_content_charset() or "utf-8"
                return payload.decode(charset, errors="replace"), current_url
        except HTTPError as error:
            if error.code not in _REDIRECT_STATUS_CODES:
                raise
            location = error.headers.get("Location")
            error.close()
            if not location:
                raise ValueError("平台跳转缺少目标地址")
            current_url = urljoin(current_url, location)
    raise ValueError("平台链接重定向次数过多，已停止读取")


def _resolve_known_short_link(url, cookie_header=""):
    host = urlsplit(url).netloc.lower().split(":", 1)[0]
    path = urlsplit(url).path.lower()
    is_tiktok_share = (
        (_host_matches(host, "tiktok.com") and path.startswith("/t/"))
        or host in {"vm.tiktok.com", "vt.tiktok.com"}
    )
    if host not in {
        "b23.tv",
        "v.douyin.com",
        "xhslink.com",
        "www.xhslink.com",
        "xhslink.cn",
        "www.xhslink.cn",
    } and not is_tiktok_share:
        return url
    _, final_url = _fetch(url, cookie_header, max_bytes=2 * 1024 * 1024)
    final_host = urlsplit(final_url).netloc.lower().split(":", 1)[0]
    if host == "b23.tv" and not _host_matches(final_host, "bilibili.com"):
        raise ValueError("哔哩哔哩短链接跳转到了非官方域名，已拒绝读取")
    if host == "v.douyin.com":
        if not (
            _host_matches(final_host, "douyin.com")
            or _host_matches(final_host, "iesdouyin.com")
        ):
            raise ValueError("抖音短链接跳转到了非官方域名，已拒绝读取")
        work_id = re.search(r"/(?:share/)?(video|note)/(\d+)", urlsplit(final_url).path)
        if work_id:
            return f"https://www.douyin.com/{work_id.group(1)}/{work_id.group(2)}"
        # 分享页的路由会随平台变动。只要仍是官方地址，就交给维护中的
        # extractor 继续判定，不能因本地的路径正则过窄而提前拒绝。
        return final_url
    if is_tiktok_share:
        if not _host_matches(final_host, "tiktok.com"):
            raise ValueError("TikTok 短链接跳转到了非官方域名，已拒绝读取")
        work = re.search(r"/@([^/?]+)/video/(\d+)", urlsplit(final_url).path)
        if work:
            return f"https://www.tiktok.com/@{work.group(1)}/video/{work.group(2)}"
        # 允许新的官方分享路由继续由 yt-dlp 处理；安全边界只是不能离开
        # TikTok 官方域，并非把未知但合法的新路由视为不支持。
        return final_url
    if host not in {"b23.tv", "v.douyin.com"} and not (
        _host_matches(final_host, "xiaohongshu.com")
        or _host_matches(final_host, "rednote.com")
    ):
        raise ValueError("小红书短链接跳转到了非官方域名，已拒绝读取")
    return final_url


def _initial_state(html, marker, replace_undefined=False):
    marker_index = html.find(marker)
    if marker_index < 0:
        raise ValueError("平台页面没有返回可识别的初始数据")
    payload = html[marker_index + len(marker):]
    if replace_undefined:
        payload = re.sub(r"\bundefined\b", "null", payload)
    try:
        return json.JSONDecoder().raw_decode(payload)[0]
    except (json.JSONDecodeError, TypeError) as error:
        raise ValueError("平台页面初始数据格式已更新") from error


def _upload_date(epoch_value):
    try:
        numeric = float(epoch_value or 0)
    except (TypeError, ValueError):
        return ""
    if numeric > 10_000_000_000:
        numeric /= 1000
    if numeric <= 0:
        return ""
    return datetime.fromtimestamp(numeric, tz=timezone.utc).strftime("%Y%m%d")


def _bilibili_page_state(html, page_number=1):
    state = _initial_state(html, "__INITIAL_STATE__=")
    video_root = state.get("video") or {}
    view = state.get("videoData") or video_root.get("viewInfo") or {}
    pages = list(view.get("pages") or [])
    if not view or not pages:
        raise ValueError("Bilibili page state missing video metadata")
    page_number = max(1, int(page_number or 1))
    page = next(
        (item for item in pages if int(item.get("page") or 0) == page_number),
        pages[0],
    )
    owner = view.get("owner") or {}
    return {
        "bvid": str(view.get("bvid") or video_root.get("bvid") or ""),
        "aid": str(view.get("aid") or video_root.get("avid") or ""),
        "cid": str(page.get("cid") or ""),
        "page": int(page.get("page") or 1),
        "page_count": len(pages),
        "part": str(page.get("part") or ""),
        "title": str(view.get("title") or "未知标题"),
        "thumbnail": str(view.get("pic") or ""),
        "creator": str(owner.get("name") or "未知作者"),
        "creator_id": str(owner.get("mid") or ""),
        "upload_date": _upload_date(view.get("pubdate")),
    }


def _bilibili_fallback_info(url, cookie_header=""):
    html, final_url = _fetch(url, cookie_header)
    page_number = int((parse_qs(urlsplit(final_url).query).get("p") or ["1"])[0])
    page = _bilibili_page_state(html, page_number)
    if not page["bvid"] or not page["cid"]:
        raise ValueError("Bilibili page state missing bvid or cid")

    query = urlencode(
        {
            "bvid": page["bvid"],
            "cid": page["cid"],
            "qn": 80,
            "fnval": 16,
            "fourk": 1,
        }
    )
    api_url = f"https://api.bilibili.com/x/player/playurl?{query}"
    body, _ = _fetch(api_url, cookie_header, max_bytes=4 * 1024 * 1024)
    response = json.loads(body)
    if response.get("code") not in {0, None}:
        raise ValueError(
            f"Bilibili API rejected request: {response.get('code')} {response.get('message', '')}"
        )
    data = response.get("data") or {}
    dash = data.get("dash") or {}
    # Bilibili's current public CDN URLs reject the Android mobile UA used to
    # fetch the page, even when the URL itself is valid. A normal desktop
    # browser identity is accepted for the media Range requests.
    headers = {
        "User-Agent": _DESKTOP_USER_AGENT,
        "Referer": final_url,
        "Accept": "*/*",
    }
    if cookie_header:
        headers["Cookie"] = cookie_header
    formats = []
    for item in dash.get("video") or []:
        media_url = item.get("baseUrl") or item.get("base_url")
        if not media_url:
            continue
        formats.append(
            {
                "format_id": f"bili-video-{item.get('id', '')}",
                "url": media_url,
                "ext": "mp4",
                "width": item.get("width"),
                "height": item.get("height"),
                "tbr": (item.get("bandwidth") or 0) / 1000,
                "vcodec": item.get("codecs") or "avc1",
                "acodec": "none",
                "http_headers": headers,
            }
        )
    for item in dash.get("audio") or []:
        media_url = item.get("baseUrl") or item.get("base_url")
        if not media_url:
            continue
        formats.append(
            {
                "format_id": f"bili-audio-{item.get('id', '')}",
                "url": media_url,
                "ext": "m4a",
                "abr": (item.get("bandwidth") or 0) / 1000,
                "vcodec": "none",
                "acodec": item.get("codecs") or "mp4a",
                "http_headers": headers,
            }
        )
    for item in data.get("durl") or []:
        media_url = item.get("url")
        if not media_url:
            continue
        formats.append(
            {
                "format_id": f"bili-progressive-{data.get('quality', '')}",
                "url": media_url,
                "ext": "mp4",
                "height": data.get("quality"),
                "vcodec": "avc1",
                "acodec": "mp4a",
                "http_headers": headers,
            }
        )
    if not formats:
        raise ValueError("Bilibili API returned no downloadable media formats")

    title = page["title"]
    if page["page_count"] > 1 and page["part"]:
        title = f"{title} - P{page['page']} {page['part']}"
    content_id = page["bvid"]
    if page["page_count"] > 1:
        content_id = f"{content_id}_p{page['page']}"
    return {
        "extractor_key": "Bilibili",
        "id": content_id,
        "title": title,
        "channel": page["creator"],
        "channel_id": page["creator_id"],
        "webpage_url": final_url,
        "upload_date": page["upload_date"],
        "thumbnail": page["thumbnail"],
        "http_headers": headers,
        "formats": formats,
    }


def _xiaohongshu_note_state(html):
    state = _initial_state(
        html,
        "window.__INITIAL_STATE__=",
        replace_undefined=True,
    )
    current = ((state.get("noteData") or {}).get("data") or {}).get("noteData")
    if current:
        return current
    detail_map = (state.get("note") or {}).get("noteDetailMap") or {}
    for value in detail_map.values():
        note = (value or {}).get("note") or value
        if isinstance(note, dict) and note:
            return note
    raise ValueError("Xiaohongshu initial state missing note data")


def _xiaohongshu_scene_is_watermarked(scene):
    scene = str(scene or "").upper()
    return (
        scene == "WM"
        or "WATERMARK" in scene
        or "_WM" in scene
        or scene.startswith("WM_")
    )


def _xiaohongshu_original_image_url(value):
    """Turn Xiaohongshu's share-image rendition into its original PNG endpoint.

    `H5_DTL` names the page presentation scene, not an image provenance level;
    it can already have the platform mark baked in. The public web page's
    `sns-webpic-qc` URL keeps the underlying image key before its `!` rendition
    suffix. The official image service accepts that key without the share
    rendition suffix and serves the original PNG.
    """
    parsed = urlsplit(str(value or "").strip())
    if parsed.netloc.lower() != "sns-webpic-qc.xhscdn.com":
        return ""
    parts = parsed.path.strip("/").split("/", 2)
    if len(parts) != 3 or not parts[0].isdigit() or not parts[1]:
        return ""
    image_key = parts[2].split("!", 1)[0].strip()
    if not image_key:
        return ""
    # `notes_uhdr` is Xiaohongshu's Ultra HDR / Live Photo still-image key.
    # That endpoint rejects a PNG conversion with HTTP 400, while its JPEG
    # rendition is the supported original still. Keep the regular original
    # images as PNG, which avoids the page's watermarked share rendition.
    image_format = "jpg" if image_key.startswith("notes_uhdr/") else "png"
    return f"https://ci.xiaohongshu.com/{image_key}?imageView2/2/w/format/{image_format}"


def _xiaohongshu_image_urls(note):
    return [item["url"] for item in _xiaohongshu_image_items(note)]


def _xiaohongshu_live_photo_url(image):
    """Return the motion MP4 paired with a Xiaohongshu Live Photo, if present.

    Live Photos are not ordinary videos: the note remains a swipeable image
    collection, while each animated image has a short H.264/H.265 companion
    stream.  Keep that association instead of flattening the collection into
    either still images or unrelated video segments.
    """
    if not bool(image.get("livePhoto") or image.get("live_photo")):
        return ""
    stream_root = image.get("stream") or {}
    for codec in ("h264", "h265"):
        for stream in stream_root.get(codec) or []:
            if not isinstance(stream, dict):
                continue
            media_url = str(stream.get("masterUrl") or stream.get("master_url") or "").strip()
            if media_url:
                return _secure_media_url(media_url)
    return ""


def _xiaohongshu_image_items(note):
    """Return original still images with their optional Live Photo motion URL."""
    items = []
    for image in note.get("imageList") or note.get("image_list") or []:
        if not isinstance(image, dict):
            continue
        # Do not infer image provenance from H5/WB scene names: those describe
        # the presentation surface and can still be watermarked. Build the
        # only verified original endpoint from the share rendition's image key.
        candidates = [image.get("urlDefault"), image.get("url"), image.get("urlPre")]
        for item in image.get("infoList") or image.get("info_list") or []:
            if not isinstance(item, dict):
                continue
            scene = str(
                item.get("imageScene") or item.get("image_scene") or item.get("scene") or ""
            ).upper()
            if _xiaohongshu_scene_is_watermarked(scene):
                continue
            candidates.extend((item.get("url"), item.get("urlDefault"), item.get("urlPre")))
        url = next(
            (
                original
                for value in candidates
                for original in (_xiaohongshu_original_image_url(value),)
                if original
            ),
            "",
        )
        if url and url not in {item["url"] for item in items}:
            items.append(
                {
                    "url": url,
                    "motion_url": _xiaohongshu_live_photo_url(image),
                }
            )
    return items


def _xiaohongshu_origin_video_url(note):
    consumer = (note.get("video") or {}).get("consumer") or {}
    origin_key = str(consumer.get("originVideoKey") or consumer.get("origin_video_key") or "").strip()
    return f"https://sns-video-bd.xhscdn.com/{origin_key}" if origin_key else ""


def _has_xiaohongshu_original_format(info):
    return any(
        str(item.get("format_id") or "").lower() == "direct" and item.get("url")
        for item in info.get("formats") or []
        if isinstance(item, dict)
    )


def _xiaohongshu_info(url, cookie_header=""):
    html, final_url = _fetch(url, cookie_header)
    note = _xiaohongshu_note_state(html)
    stream_root = (((note.get("video") or {}).get("media") or {}).get("stream") or {})
    streams = list(stream_root.get("h264") or [])
    if not streams:
        streams = list(stream_root.get("h265") or [])
    headers = _request_headers(final_url, cookie_header)
    user = note.get("user") or {}
    image_items = _xiaohongshu_image_items(note)
    image_urls = [item["url"] for item in image_items]
    common = {
        "extractor_key": "Xiaohongshu",
        "id": str(note.get("noteId") or note.get("note_id") or ""),
        "title": str(note.get("title") or note.get("desc") or "未知标题"),
        "uploader": str(user.get("nickName") or user.get("nickname") or "未知作者"),
        "uploader_id": str(user.get("userId") or user.get("user_id") or ""),
        "webpage_url": final_url,
        "upload_date": _upload_date(note.get("time")),
        "thumbnail": str(image_urls[0] if image_urls else ""),
        "http_headers": headers,
    }
    if str(note.get("type") or "").lower() not in {"video", "normal"} or not streams:
        if image_urls:
            return {
                **common,
                "image_urls": image_urls,
                "image_items": image_items,
                "formats": [],
            }
        raise ValueError("小红书当前未提供可验证的无水印原图，未生成下载任务")

    formats = []
    origin_video_url = _xiaohongshu_origin_video_url(note)
    if not origin_video_url:
        # Do not silently swap to the share rendition: it is the endpoint
        # where the platform applies the Xiaohongshu watermark.
        raise ValueError("小红书当前未提供可验证的原始视频，未下载带水印版本")
    formats.append(
        {
            "format_id": "direct",
            "url": origin_video_url,
            "ext": "mp4",
            # The original endpoint is a progressive MP4. Mark it so the
            # generic selector does not discard it for missing metadata.
            "vcodec": "avc1",
            "acodec": "mp4a",
            "source_preference": 100,
            "http_headers": headers,
        }
    )
    for index, item in enumerate(streams):
        media_url = item.get("masterUrl") or item.get("master_url")
        if not media_url:
            continue
        if media_url.startswith("http://"):
            media_url = "https://" + media_url[len("http://"):]
        average_bitrate = item.get("avgBitrate") or item.get("videoBitrate") or 0
        formats.append(
            {
                "format_id": f"xhs-{item.get('qualityType') or index}",
                "url": media_url,
                "ext": "mp4",
                "width": item.get("width"),
                "height": item.get("height"),
                "tbr": average_bitrate / 1000 if average_bitrate > 10_000 else average_bitrate,
                "vcodec": item.get("videoCodec") or "avc1",
                "acodec": item.get("audioCodec") or "mp4a",
                "filesize": item.get("size"),
                "http_headers": headers,
            }
        )
    if not formats:
        raise ValueError("Xiaohongshu note data contains no downloadable video URL")

    return {**common, "formats": formats}


def _platform_name(info):
    extractor = str(info.get("extractor_key") or info.get("extractor") or "").lower()
    if "tiktok" in extractor:
        return "tiktok"
    if "youtube" in extractor:
        return "youtube"
    if "bilibili" in extractor:
        return "bilibili"
    if "xiaohongshu" in extractor or "rednote" in extractor:
        return "xiaohongshu"
    return extractor or "unknown"


def _secure_media_url(url):
    """Upgrade only the official Xiaohongshu CDN's HTTP media URLs to HTTPS.

    Android intentionally blocks cleartext traffic. Some current Xiaohongshu
    extractor results still advertise an HTTP CDN URL even though the same
    signed resource is available over HTTPS. Do not rewrite URLs from other
    hosts: their transport semantics are owned by the platform.
    """
    parsed = urlsplit(str(url or ""))
    if parsed.scheme.lower() == "http" and _host_matches(parsed.netloc, "xhscdn.com"):
        return urlunsplit(parsed._replace(scheme="https"))
    return str(url or "")


def _media_result(info, cookie_header="", resolution="UP_TO_720P"):
    if info.get("_type") in {"playlist", "multi_video"} or info.get("entries"):
        raise ValueError("单视频探测返回了列表，已中止")

    image_urls = [str(url) for url in info.get("image_urls") or [] if str(url or "").strip()]
    if image_urls:
        if resolution == "AUDIO_MP3":
            raise ValueError("图文作品没有音轨，不能转换为 MP3")
        headers = _without_credential_headers(info.get("http_headers") or {})
        return {
            "platform": _platform_name(info),
            "id": str(info.get("id") or ""),
            "title": str(info.get("title") or "未知标题"),
            "creator": str(info.get("channel") or info.get("uploader") or "未知作者"),
            "creator_id": str(info.get("channel_id") or info.get("uploader_id") or ""),
            "webpage_url": str(info.get("webpage_url") or info.get("original_url") or ""),
            "upload_date": str(info.get("upload_date") or ""),
            "thumbnail": str(info.get("thumbnail") or image_urls[0]),
            "video_url": "",
            "audio_url": "",
            "video_ext": "jpg",
            "video_size_bytes": 0,
            "audio_ext": "",
            "audio_from_video_source": False,
            "image_urls": [_secure_media_url(url) for url in image_urls],
            "image_items": [
                {
                    "url": _secure_media_url(item.get("url")),
                    "motion_url": _secure_media_url(item.get("motion_url")),
                }
                for item in info.get("image_items") or []
                if isinstance(item, dict) and str(item.get("url") or "").strip()
            ],
            "headers": headers,
            "video_cookie_header": "",
            "audio_cookie_header": "",
        }

    chosen_video, audio = _select_streams(
        _prefer_xiaohongshu_original_formats(info),
        resolution,
    )
    if not chosen_video:
        if resolution == "AUDIO_MP3":
            raise ValueError(
                "没有找到可提取的音轨。该资源可能只有纯视频画面，无法转换为音频。"
                "请改为下载视频，或选择包含声音的其他资源。"
            )
        raise ValueError("没有找到可下载且具备音频的 MP4 视频流")
    audio_from_video_source = (
        resolution == "AUDIO_MP3"
        and chosen_video.get("vcodec") not in {None, "none"}
        and chosen_video.get("acodec") not in {None, "none"}
    )

    # yt-dlp may attach the working player User-Agent and fetch headers to the
    # selected formats rather than to the top-level result. Dropping them can
    # make googlevideo reject or severely throttle otherwise valid Range URLs.
    headers = dict(info.get("http_headers") or {})
    headers.update(chosen_video.get("http_headers") or {})
    if audio:
        headers.update(audio.get("http_headers") or {})
    headers = _without_credential_headers(headers)

    return {
        "platform": _platform_name(info),
        "id": str(info.get("id") or ""),
        "title": str(info.get("title") or "未知标题"),
        "creator": str(info.get("channel") or info.get("uploader") or "未知作者"),
        "creator_id": str(info.get("channel_id") or info.get("uploader_id") or ""),
        "webpage_url": str(info.get("webpage_url") or info.get("original_url") or ""),
        "upload_date": str(info.get("upload_date") or ""),
        "thumbnail": str(info.get("thumbnail") or ""),
        "video_url": _secure_media_url(chosen_video["url"]),
        "audio_url": _secure_media_url((audio or {}).get("url", "")),
        "video_ext": chosen_video.get("ext") or "mp4",
        "video_size_bytes": int(
            chosen_video.get("filesize") or chosen_video.get("filesize_approx") or 0
        ),
        "audio_ext": (audio or {}).get("ext", ""),
        "audio_from_video_source": audio_from_video_source,
        "image_urls": [],
        "headers": headers,
        "video_cookie_header": str(info.get("video_cookie_header") or ""),
        "audio_cookie_header": str(info.get("audio_cookie_header") or ""),
    }


def _source_result(info):
    is_creator = info.get("_type") in {"playlist", "multi_video"} or bool(
        info.get("entries")
    )
    return {
        "kind": "creator" if is_creator else "single",
        "url": str(info.get("webpage_url") or info.get("original_url") or ""),
    }


def _douyin_gallery_result(detail, page_url):
    images = detail.get("images") or (detail.get("image_post_info") or {}).get("images") or []
    if not images:
        return None
    clean_urls = []
    for image in images:
        url_list = image.get("url_list") or image.get("urlList") or []
        if not url_list and isinstance(image.get("display_image"), dict):
            url_list = image["display_image"].get("url_list") or []
        source = next((
            str(url) for url in url_list
            if str(url).startswith("https://")
            and "tplv-dy-aweme-images" in str(url).lower()
            and "tplv-dy-water" not in str(url).lower()
        ), "")
        if source:
            clean_urls.append(source)
    expected_count = len(images)
    if expected_count <= 0 or len(clean_urls) != expected_count or len(set(clean_urls)) != expected_count:
        raise ValueError("抖音图文没有返回完整的无水印原图列表")
    author = detail.get("author") or {}
    return {
        "work_id": str(detail.get("aweme_id") or ""),
        "page_url": page_url,
        "title": str(detail.get("desc") or "抖音图文"),
        "creator": str(author.get("nickname") or author.get("unique_id") or "抖音用户"),
        "creator_id": str(author.get("sec_uid") or author.get("uid") or ""),
        "thumbnail": clean_urls[0],
        "image_urls": clean_urls,
        "expected_count": expected_count,
    }


def extract_douyin_gallery(url: str, cookie_header: str = "") -> str:
    work = re.search(r"/(?:note|video)/(\d+)", urlsplit(url).path)
    if not work:
        raise ValueError("抖音图文地址缺少作品 ID")
    work_id = work.group(1)
    api_url = (
        "https://www.douyin.com/aweme/v1/web/aweme/detail/"
        f"?aweme_id={quote(work_id)}"
    )
    payload, _ = _fetch(api_url, cookie_header, max_bytes=4 * 1024 * 1024)
    if not payload.strip():
        raise ValueError("抖音网页会话未返回作品详情，请到设置中重新登录抖音后重试")
    data = json.loads(payload)
    detail = data.get("aweme_detail") or {}
    if str(detail.get("aweme_id") or "") != work_id:
        raise ValueError("抖音返回的作品与目标不一致，已停止读取")
    gallery = _douyin_gallery_result(detail, url)
    return json.dumps(gallery or {}, ensure_ascii=False)


def resolve_source(url: str, cookie_header: str = "", cookie_file: str = "") -> str:
    url = _resolve_known_short_link(url, cookie_header)
    host = urlsplit(url).netloc.lower().split(":", 1)[0]
    if (
        _host_matches(host, "bilibili.com")
        or _host_matches(host, "xiaohongshu.com")
        or _host_matches(host, "rednote.com")
    ):
        if host == "space.bilibili.com":
            raise ValueError("bilibili creator batch is not supported")
        return json.dumps({"kind": "single", "url": url}, ensure_ascii=False)
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist",
        "playlistend": 1,
        "socket_timeout": 20,
        "retries": 1,
    }
    extractor_args = _extractor_args_for(host)
    if extractor_args:
        options["extractor_args"] = extractor_args
    options = _with_session_access(options, cookie_header, cookie_file)
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)
    return json.dumps(_source_result(info), ensure_ascii=False)


def _normalized_handle(value):
    return str(value or "").strip().lstrip("@").lower()


def _expected_creator_hint(url):
    parsed = urlsplit(str(url or ""))
    query = parse_qs(parsed.query)
    playlist_id = (query.get("list") or [""])[0]
    if playlist_id:
        return _normalized_handle(playlist_id)

    parts = [part for part in parsed.path.split("/") if part]
    if parsed.netloc.lower().endswith("space.bilibili.com") and parts and parts[0].isdigit():
        return _normalized_handle(parts[0])
    for marker in ("channel", "user", "c"):
        if marker in parts:
            index = parts.index(marker)
            if index + 1 < len(parts):
                return _normalized_handle(parts[index + 1])
    for part in parts:
        if part.startswith("@"):
            return _normalized_handle(part)
    return ""


def _normalize_collection_url(url):
    parsed = urlsplit(str(url or "").strip())
    host = parsed.netloc.lower().split(":", 1)[0]
    if host not in {"youtube.com", "www.youtube.com", "m.youtube.com"}:
        return urlunsplit(parsed)
    if (parse_qs(parsed.query).get("list") or [""])[0]:
        return urlunsplit(parsed)

    parts = [part for part in parsed.path.split("/") if part]
    is_channel = bool(parts) and (
        parts[0].startswith("@") or parts[0] in {"channel", "c", "user"}
    )
    if is_channel and parts[-1] not in {"videos", "shorts", "streams"}:
        normalized_path = parsed.path.rstrip("/") + "/videos"
        return urlunsplit(parsed._replace(path=normalized_path))
    return urlunsplit(parsed)


def _is_youtube_collection(info):
    extractor = str(info.get("extractor_key") or info.get("extractor") or "").lower()
    source_url = str(
        info.get("webpage_url")
        or info.get("original_url")
        or info.get("_collection_url")
        or ""
    ).lower()
    return "youtube" in extractor or "youtu.be" in source_url or "youtube.com" in source_url


def _entry_webpage_url(info, item, video_id):
    item_url = str(item.get("webpage_url") or item.get("url") or "").strip()
    if _is_youtube_collection(info) and not re.match(r"^https?://", item_url):
        return f"https://www.youtube.com/watch?v={video_id}"
    return item_url


def _creator_result(
    info,
    expected_handle,
    strict_owner=True,
    allow_inherited_owner=False,
):
    expected_handle = _normalized_handle(expected_handle)
    root_creator = str(
        info.get("channel")
        or info.get("uploader")
        or info.get("title")
        or expected_handle
        or "未知作者"
    )
    root_channel_id = _normalized_handle(info.get("channel_id") or info.get("id"))
    root_uploader_id = _normalized_handle(info.get("uploader_id"))
    root_id = (
        root_channel_id or root_uploader_id or expected_handle
    )
    expected_path = f"/@{expected_handle}/" if expected_handle else ""
    entries = []
    seen = set()
    duplicate_count = 0
    foreign_count = 0

    for item in info.get("entries") or []:
        video_id = str(item.get("id") or "")
        if not video_id:
            continue
        if video_id in seen:
            duplicate_count += 1
            continue

        item_handle = _normalized_handle(item.get("uploader"))
        item_uploader_id = _normalized_handle(item.get("uploader_id"))
        item_channel_id = _normalized_handle(item.get("channel_id"))
        item_url = _entry_webpage_url(info, item, video_id)
        if strict_owner:
            if root_channel_id and item_channel_id and item_channel_id != root_channel_id:
                foreign_count += 1
                continue
            if root_uploader_id and item_uploader_id and item_uploader_id != root_uploader_id:
                foreign_count += 1
                continue

            channel_matches = root_channel_id and item_channel_id == root_channel_id
            uploader_id_matches = root_uploader_id and item_uploader_id == root_uploader_id
            handle_matches = expected_handle and item_handle == expected_handle
            url_matches = expected_path and expected_path in item_url.lower()
            has_item_identity = bool(item_channel_id or item_uploader_id or item_handle)
            inherited_owner = allow_inherited_owner and not has_item_identity and bool(root_id)
            if not (
                channel_matches
                or uploader_id_matches
                or handle_matches
                or url_matches
                or inherited_owner
            ):
                foreign_count += 1
                continue

        seen.add(video_id)
        entries.append(
            {
                "id": video_id,
                "title": str(item.get("title") or "未知标题"),
                "creator": str(
                    item.get("uploader") or item.get("channel") or root_creator
                ),
                "creator_id": root_id if not strict_owner else item_channel_id or root_id,
                "webpage_url": item_url,
                "upload_date": str(item.get("upload_date") or ""),
                "thumbnail": str(item.get("thumbnail") or ""),
            }
        )

    return {
        "creator": root_creator,
        "creator_id": root_id or expected_handle,
        "entries": entries,
        "duplicate_count": duplicate_count,
        "foreign_count": foreign_count,
    }


def _creator_page_result(
    info,
    expected_handle,
    start,
    page_size,
    strict_owner=True,
    allow_inherited_owner=False,
):
    raw_entries = list(info.get("entries") or [])
    page_info = dict(info)
    page_info["entries"] = raw_entries[:page_size]
    result = _creator_result(
        page_info,
        expected_handle,
        strict_owner=strict_owner,
        allow_inherited_owner=allow_inherited_owner,
    )
    result["has_more"] = len(raw_entries) > page_size
    result["next_start"] = start + page_size if result["has_more"] else 0
    return result


def extract_creator(
    url: str,
    start: int = 1,
    page_size: int = 50,
    cookie_header: str = "",
    cookie_file: str = "",
) -> str:
    if start < 1 or page_size < 1:
        raise ValueError("作品列表分页参数无效")
    normalized_url = _normalize_collection_url(url)
    expected_handle = _expected_creator_hint(normalized_url)
    parsed = urlsplit(normalized_url)
    is_youtube = "youtube" in parsed.netloc.lower()
    is_youtube_playlist = is_youtube and bool(
        (parse_qs(parsed.query).get("list") or [""])[0]
    )
    strict_owner = not is_youtube_playlist
    allow_inherited_owner = is_youtube and not is_youtube_playlist
    if strict_owner and not expected_handle:
        raise ValueError("作者或频道链接缺少可验证的身份标识")
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist",
        "lazy_playlist": False,
        "playliststart": start,
        "playlistend": start + page_size,
        "socket_timeout": 20,
        "retries": 1,
    }
    options = _with_session_access(options, cookie_header, cookie_file)
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(normalized_url, download=False)
    if start == 1 and not info.get("entries"):
        raise ValueError("作者、频道或播放列表没有返回可读取作品")
    info = dict(info)
    info["_collection_url"] = normalized_url
    result = _creator_page_result(
        info,
        expected_handle,
        start,
        page_size,
        strict_owner=strict_owner,
        allow_inherited_owner=allow_inherited_owner,
    )
    if start == 1 and not result["entries"]:
        raise ValueError("作者、频道或播放列表没有返回可验证的作品")
    return json.dumps(result, ensure_ascii=False)


def extract_single(
    url: str,
    resolution: str = "UP_TO_720P",
    cookie_header: str = "",
    cookie_file: str = "",
) -> str:
    url = _resolve_known_short_link(url, cookie_header)
    host = urlsplit(url).netloc.lower().split(":", 1)[0]
    if _host_matches(host, "bilibili.com") and urlsplit(url).path.lower().startswith("/video/"):
        info = _bilibili_fallback_info(url, cookie_header)
        return json.dumps(
            _media_result(info, cookie_header=cookie_header, resolution=resolution),
            ensure_ascii=False,
        )

    xiaohongshu_static_error = None
    if _host_matches(host, "xiaohongshu.com") or _host_matches(host, "rednote.com"):
        try:
            # Prefer the public note state when it is available: unlike the
            # maintained extractor's normal rendition, it exposes
            # originVideoKey and image scene labels needed to avoid platform
            # watermark variants. Fall back to yt-dlp only when this state is
            # unavailable because the web page shape changes.
            info = _xiaohongshu_info(url, cookie_header)
            if not info.get("image_urls") and not _has_xiaohongshu_original_format(info):
                raise ValueError("小红书当前未提供可验证的原始媒体，未下载带水印版本")
            return json.dumps(
                _media_result(info, cookie_header=cookie_header, resolution=resolution),
                ensure_ascii=False,
            )
        except Exception as error:
            xiaohongshu_static_error = error

    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 1,
    }
    options = _with_session_access(options, cookie_header, cookie_file)
    try:
        with YoutubeDL(options) as ydl:
            info = ydl.extract_info(url, download=False)
            if (
                (_host_matches(host, "xiaohongshu.com") or _host_matches(host, "rednote.com"))
                and not info.get("image_urls")
                and not _has_xiaohongshu_original_format(info)
            ):
                raise ValueError("小红书当前未提供可验证的原始媒体，未下载带水印版本")
            chosen_video, chosen_audio = _select_streams(info.get("formats") or [], resolution)
            video_cookie_header = (
                ydl.cookiejar.get_cookie_header(chosen_video["url"])
                if chosen_video and hasattr(ydl.cookiejar, "get_cookie_header")
                else ""
            )
            audio_cookie_header = (
                ydl.cookiejar.get_cookie_header(chosen_audio["url"])
                if chosen_audio and hasattr(ydl.cookiejar, "get_cookie_header")
                else ""
            )
        result = _media_result(
            {
                **info,
                "video_cookie_header": video_cookie_header,
                "audio_cookie_header": audio_cookie_header,
            },
            cookie_header=cookie_header,
            resolution=resolution,
        )
    except Exception:
        if _host_matches(host, "xiaohongshu.com") or _host_matches(host, "rednote.com"):
            if xiaohongshu_static_error is not None:
                raise xiaohongshu_static_error
            info = _xiaohongshu_info(url, cookie_header)
        elif _host_matches(host, "bilibili.com"):
            info = _bilibili_fallback_info(url, cookie_header)
        else:
            raise
        result = _media_result(
            info,
            cookie_header=cookie_header,
            resolution=resolution,
        )

    return json.dumps(result, ensure_ascii=False)
