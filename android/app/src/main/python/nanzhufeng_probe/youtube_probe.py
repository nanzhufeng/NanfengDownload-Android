import json
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

    if info.get("_type") in {"playlist", "multi_video"} or info.get("entries"):
        raise ValueError("单视频探测返回了列表，已中止")

    formats = info.get("formats") or []
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
        and item.get("acodec") in {None, "none"},
        lambda item: ((item.get("height") or 0), (item.get("tbr") or 0)),
    )
    audio = _best(
        formats,
        lambda item: item.get("url")
        and item.get("acodec") not in {None, "none"}
        and item.get("vcodec") in {None, "none"},
        lambda item: (item.get("abr") or item.get("tbr") or 0),
    )

    chosen_video = progressive or video
    if not chosen_video:
        raise ValueError("没有找到可下载的 MP4 视频流")

    result = {
        "id": str(info.get("id") or ""),
        "title": str(info.get("title") or "未知标题"),
        "creator": str(info.get("channel") or info.get("uploader") or "未知作者"),
        "video_url": chosen_video["url"],
        "audio_url": "" if progressive else (audio or {}).get("url", ""),
        "headers": info.get("http_headers") or {},
    }
    return json.dumps(result, ensure_ascii=False)
