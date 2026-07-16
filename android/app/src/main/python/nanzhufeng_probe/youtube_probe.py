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


def _select_streams(formats):
    progressive = _best(
        formats,
        lambda item: item.get("url")
        and item.get("ext") == "mp4"
        and item.get("vcodec") not in {None, "none"}
        and item.get("acodec") not in {None, "none"}
        and (item.get("height") or 0) <= 720,
        lambda item: ((item.get("height") or 0), (item.get("tbr") or 0)),
    )
    video = _best(
        formats,
        lambda item: item.get("url")
        and item.get("ext") == "mp4"
        and item.get("vcodec") not in {None, "none"}
        and item.get("acodec") in {None, "none"}
        and (item.get("height") or 0) <= 720,
        lambda item: ((item.get("height") or 0), (item.get("tbr") or 0)),
    )
    audio = _select_audio(formats)
    progressive_height = (progressive or {}).get("height") or 0
    if video and audio and (video.get("height") or 0) > progressive_height:
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


def _media_result(info):
    if info.get("_type") in {"playlist", "multi_video"} or info.get("entries"):
        raise ValueError("单视频探测返回了列表，已中止")

    chosen_video, audio = _select_streams(info.get("formats") or [])
    if not chosen_video:
        raise ValueError("没有找到可下载且具备音频的 MP4 视频流")

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
        "headers": info.get("http_headers") or {},
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
        info.get("uploader") or info.get("channel") or expected_handle or "未知作者"
    )
    root_id = _normalized_handle(
        info.get("uploader_id") or info.get("channel_id") or expected_handle
    )
    accepted_identities = {value for value in {root_id, expected_handle} if value}
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

        item_creator = _normalized_handle(
            item.get("uploader_id") or item.get("channel_id")
        )
        item_url = str(item.get("webpage_url") or item.get("url") or "")
        creator_matches = item_creator and item_creator in accepted_identities
        url_matches = expected_path and expected_path in item_url.lower()
        if item_creator and not creator_matches:
            foreign_count += 1
            continue
        if not item_creator and not url_matches:
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
                "creator_id": item_creator or root_id or expected_handle,
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


def extract_creator(url: str) -> str:
    match = re.search(r"/@([^/?#]+)", url)
    if not match:
        raise ValueError("TikTok 作者主页缺少 @作者 标识")
    expected_handle = match.group(1)
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist",
        "lazy_playlist": False,
        "socket_timeout": 20,
        "retries": 1,
    }
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)
    if not info.get("entries"):
        raise ValueError("TikTok 作者主页没有返回公开作品")
    return json.dumps(_creator_result(info, expected_handle), ensure_ascii=False)


def extract_single(url: str) -> str:
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

    return json.dumps(_media_result(info), ensure_ascii=False)
