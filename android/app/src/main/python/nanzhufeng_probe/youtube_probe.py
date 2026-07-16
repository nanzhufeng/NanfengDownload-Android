import json
import re
import sys

import yt_dlp
from yt_dlp import YoutubeDL


def runtime_info() -> str:
    return json.dumps(
        {"python": sys.version.split()[0], "yt_dlp": yt_dlp.version.__version__},
        ensure_ascii=False,
    )


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
        return audio, None

    max_short_edge = {
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


def _platform_name(info):
    extractor = str(info.get("extractor_key") or info.get("extractor") or "").lower()
    if "tiktok" in extractor:
        return "tiktok"
    if "youtube" in extractor:
        return "youtube"
    return extractor or "unknown"


def _media_result(info, cookie_header="", resolution="UP_TO_720P"):
    if info.get("_type") in {"playlist", "multi_video"} or info.get("entries"):
        raise ValueError("单视频探测返回了列表，已中止")

    chosen_video, audio = _select_streams(info.get("formats") or [], resolution)
    if not chosen_video:
        raise ValueError("没有找到可下载且具备音频的 MP4 视频流")

    headers = dict(info.get("http_headers") or {})
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


def resolve_source(url: str) -> str:
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist",
        "playlistend": 1,
        "socket_timeout": 20,
        "retries": 1,
    }
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)
    return json.dumps(_source_result(info), ensure_ascii=False)


def _normalized_handle(value):
    return str(value or "").strip().lstrip("@").lower()


def _creator_result(info, expected_handle):
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
        item_url = str(item.get("webpage_url") or item.get("url") or "")
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
        if not (channel_matches or uploader_id_matches or handle_matches or url_matches):
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
                "creator_id": item_channel_id or root_id,
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


def _creator_page_result(info, expected_handle, start, page_size):
    raw_entries = list(info.get("entries") or [])
    page_info = dict(info)
    page_info["entries"] = raw_entries[:page_size]
    result = _creator_result(page_info, expected_handle)
    result["has_more"] = len(raw_entries) > page_size
    result["next_start"] = start + page_size if result["has_more"] else 0
    return result


def extract_creator(url: str, start: int = 1, page_size: int = 50) -> str:
    match = re.search(r"/@([^/?#]+)", url)
    if not match:
        raise ValueError("TikTok 作者主页缺少 @作者 标识")
    if start < 1 or page_size < 1:
        raise ValueError("TikTok 作者作品分页参数无效")
    expected_handle = match.group(1)
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
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)
    if start == 1 and not info.get("entries"):
        raise ValueError("TikTok 作者主页没有返回公开作品")
    return json.dumps(
        _creator_page_result(info, expected_handle, start, page_size),
        ensure_ascii=False,
    )


def extract_single(url: str, resolution: str = "UP_TO_720P") -> str:
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 1,
    }
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)
        chosen_video, _ = _select_streams(info.get("formats") or [], resolution)
        cookie_header = (
            ydl.cookiejar.get_cookie_header(chosen_video["url"])
            if chosen_video and hasattr(ydl.cookiejar, "get_cookie_header")
            else ""
        )

    return json.dumps(
        _media_result(info, cookie_header=cookie_header, resolution=resolution),
        ensure_ascii=False,
    )
