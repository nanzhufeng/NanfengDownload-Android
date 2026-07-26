import json
import re
import sys
from datetime import datetime, timezone
from urllib.parse import parse_qs, urlencode, urlsplit, urlunsplit
from urllib.request import Request, urlopen

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


def runtime_info() -> str:
    return json.dumps(
        {"python": sys.version.split()[0], "yt_dlp": yt_dlp.version.__version__},
        ensure_ascii=False,
    )


def _with_session_access(options, cookie_header="", cookie_file=""):
    scoped = dict(options)
    if cookie_header:
        headers = dict(scoped.get("http_headers") or {})
        headers["Cookie"] = cookie_header
        scoped["http_headers"] = headers
    if cookie_file:
        scoped["cookiefile"] = cookie_file
    return scoped


def _best(items, predicate, score):
    candidates = [item for item in items if predicate(item)]
    return max(candidates, key=score) if candidates else None


def _select_audio(formats):
    def is_audio(item):
        return (
            item.get("url")
            and item.get("acodec") not in {None, "none"}
            and item.get("vcodec") in {None, "none"}
        )

    score = lambda item: (item.get("abr") or item.get("tbr") or 0)
    compatible = _best(
        formats,
        lambda item: is_audio(item) and item.get("ext") in {"m4a", "mp4"},
        score,
    )
    return compatible or _best(formats, is_audio, score)


def _short_edge(item):
    dimensions = [
        value
        for value in (item.get("width"), item.get("height"))
        if isinstance(value, (int, float)) and value > 0
    ]
    return min(dimensions) if dimensions else 0


def _select_streams(formats, resolution="UP_TO_720P"):
    audio = _select_audio(formats)
    if resolution == "AUDIO_MP3":
        if audio:
            return audio, None
        progressive_audio_source = _best(
            formats,
            lambda item: item.get("url")
            and item.get("vcodec") not in {None, "none"}
            and item.get("acodec") not in {None, "none"},
            lambda item: (item.get("abr") or item.get("tbr") or 0),
        )
        return progressive_audio_source, None

    max_short_edge = {
        "UP_TO_360P": 360,
        "UP_TO_720P": 720,
        "UP_TO_1080P": 1080,
        "BEST": float("inf"),
    }.get(resolution)
    if max_short_edge is None:
        raise ValueError(f"不支持的分辨率：{resolution}")

    progressive = _best(
        formats,
        lambda item: item.get("url")
        and item.get("ext") == "mp4"
        and item.get("vcodec") not in {None, "none"}
        and item.get("acodec") not in {None, "none"}
        and _short_edge(item) <= max_short_edge,
        lambda item: (_short_edge(item), (item.get("tbr") or 0)),
    )
    video = _best(
        formats,
        lambda item: item.get("url")
        and item.get("ext") == "mp4"
        and item.get("vcodec") not in {None, "none"}
        and item.get("acodec") in {None, "none"}
        and _short_edge(item) <= max_short_edge,
        lambda item: (_short_edge(item), (item.get("tbr") or 0)),
    )
    progressive_edge = _short_edge(progressive or {})
    if video and audio and _short_edge(video) > progressive_edge:
        return video, audio
    if progressive:
        return progressive, None
    if video and audio:
        return video, audio
    return None, None


def _host_matches(host, domain):
    host = str(host or "").lower().split(":", 1)[0]
    return host == domain or host.endswith(f".{domain}")


def _request_headers(url, cookie_header=""):
    host = urlsplit(url).netloc.lower()
    headers = {
        "User-Agent": _MOBILE_USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.5",
    }
    if _host_matches(host, "bilibili.com") or _host_matches(host, "b23.tv"):
        headers["Referer"] = "https://www.bilibili.com/"
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
    request = Request(url, headers=_request_headers(url, cookie_header))
    with urlopen(request, timeout=20) as response:
        payload = response.read(max_bytes + 1)
        if len(payload) > max_bytes:
            raise ValueError("平台页面数据过大，已中止读取")
        charset = response.headers.get_content_charset() or "utf-8"
        return payload.decode(charset, errors="replace"), response.geturl()


def _resolve_known_short_link(url, cookie_header=""):
    host = urlsplit(url).netloc.lower().split(":", 1)[0]
    if host not in {
        "b23.tv",
        "xhslink.com",
        "www.xhslink.com",
        "xhslink.cn",
        "www.xhslink.cn",
    }:
        return url
    _, final_url = _fetch(url, cookie_header, max_bytes=2 * 1024 * 1024)
    final_host = urlsplit(final_url).netloc.lower().split(":", 1)[0]
    if host == "b23.tv" and not _host_matches(final_host, "bilibili.com"):
        raise ValueError("哔哩哔哩短链接跳转到了非官方域名，已拒绝读取")
    if host != "b23.tv" and not (
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


def _xiaohongshu_info(url, cookie_header=""):
    html, final_url = _fetch(url, cookie_header)
    note = _xiaohongshu_note_state(html)
    stream_root = (((note.get("video") or {}).get("media") or {}).get("stream") or {})
    streams = list(stream_root.get("h264") or [])
    if not streams:
        streams = list(stream_root.get("h265") or [])
    if str(note.get("type") or "").lower() not in {"video", "normal"} or not streams:
        raise ValueError("Xiaohongshu image-only note: no video formats")

    headers = _request_headers(final_url, cookie_header)
    formats = []
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

    user = note.get("user") or {}
    image_list = note.get("imageList") or note.get("image_list") or []
    thumbnail = str((image_list[0] if image_list else {}).get("url") or "")
    return {
        "extractor_key": "Xiaohongshu",
        "id": str(note.get("noteId") or note.get("note_id") or ""),
        "title": str(note.get("title") or note.get("desc") or "未知标题"),
        "uploader": str(user.get("nickName") or user.get("nickname") or "未知作者"),
        "uploader_id": str(user.get("userId") or user.get("user_id") or ""),
        "webpage_url": final_url,
        "upload_date": _upload_date(note.get("time")),
        "thumbnail": thumbnail,
        "http_headers": headers,
        "formats": formats,
    }


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


def _media_result(info, cookie_header="", resolution="UP_TO_720P"):
    if info.get("_type") in {"playlist", "multi_video"} or info.get("entries"):
        raise ValueError("单视频探测返回了列表，已中止")

    chosen_video, audio = _select_streams(info.get("formats") or [], resolution)
    if not chosen_video:
        raise ValueError("没有找到可下载且具备音频的 MP4 视频流")

    # yt-dlp may attach the working player User-Agent and fetch headers to the
    # selected formats rather than to the top-level result. Dropping them can
    # make googlevideo reject or severely throttle otherwise valid Range URLs.
    headers = dict(info.get("http_headers") or {})
    headers.update(chosen_video.get("http_headers") or {})
    if audio:
        headers.update(audio.get("http_headers") or {})
    if cookie_header:
        headers["Cookie"] = cookie_header

    return {
        "platform": _platform_name(info),
        "id": str(info.get("id") or ""),
        "title": str(info.get("title") or "未知标题"),
        "creator": str(info.get("channel") or info.get("uploader") or "未知作者"),
        "creator_id": str(info.get("channel_id") or info.get("uploader_id") or ""),
        "webpage_url": str(info.get("webpage_url") or info.get("original_url") or ""),
        "upload_date": str(info.get("upload_date") or ""),
        "thumbnail": str(info.get("thumbnail") or ""),
        "video_url": chosen_video["url"],
        "audio_url": (audio or {}).get("url", ""),
        "video_ext": chosen_video.get("ext") or "mp4",
        "audio_ext": (audio or {}).get("ext", ""),
        "headers": headers,
    }


def _source_result(info):
    is_creator = info.get("_type") in {"playlist", "multi_video"} or bool(
        info.get("entries")
    )
    return {
        "kind": "creator" if is_creator else "single",
        "url": str(info.get("webpage_url") or info.get("original_url") or ""),
    }


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
    if _host_matches(host, "xiaohongshu.com") or _host_matches(host, "rednote.com"):
        info = _xiaohongshu_info(url, cookie_header)
        return json.dumps(
            _media_result(info, cookie_header=cookie_header, resolution=resolution),
            ensure_ascii=False,
        )
    if _host_matches(host, "bilibili.com") and urlsplit(url).path.lower().startswith("/video/"):
        info = _bilibili_fallback_info(url, cookie_header)
        return json.dumps(
            _media_result(info, cookie_header=cookie_header, resolution=resolution),
            ensure_ascii=False,
        )

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
            chosen_video, _ = _select_streams(info.get("formats") or [], resolution)
            extracted_cookie_header = (
                ydl.cookiejar.get_cookie_header(chosen_video["url"])
                if chosen_video and hasattr(ydl.cookiejar, "get_cookie_header")
                else ""
            )
        result = _media_result(
            info,
            cookie_header=extracted_cookie_header or cookie_header,
            resolution=resolution,
        )
    except Exception:
        if not _host_matches(host, "bilibili.com"):
            raise
        info = _bilibili_fallback_info(url, cookie_header)
        result = _media_result(
            info,
            cookie_header=cookie_header,
            resolution=resolution,
        )

    return json.dumps(result, ensure_ascii=False)
