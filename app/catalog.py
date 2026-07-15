from __future__ import annotations

import re
from itertools import islice
from dataclasses import dataclass
from typing import Any

from .auth_profile import AUTH_COOKIE_MODE, export_auth_cookies_txt
from .downloader import DownloadOptions, split_urls


@dataclass(frozen=True)
class CatalogItem:
    platform: str
    title: str
    url: str
    publish_date: str | None = None
    creator_name: str | None = None


def _format_youtube_url(entry: dict[str, Any]) -> str | None:
    webpage_url = entry.get("webpage_url")
    if isinstance(webpage_url, str) and _is_youtube_video_url(webpage_url):
        return webpage_url

    url = entry.get("url")
    if isinstance(url, str) and _is_youtube_video_url(url):
        return url

    video_id = entry.get("id")
    if isinstance(video_id, str) and _looks_like_youtube_video_id(video_id):
        return f"https://www.youtube.com/watch?v={video_id}"
    return None


def _is_youtube_video_url(url: str) -> bool:
    lower = url.lower()
    return (
        "youtube.com/watch" in lower
        or "youtu.be/" in lower
        or "youtube.com/shorts/" in lower
    )


def _looks_like_youtube_video_id(value: str) -> bool:
    return re.fullmatch(r"[A-Za-z0-9_-]{11}", value) is not None


def _normalize_youtube_source_url(value: Any) -> str | None:
    if not isinstance(value, str) or not value.strip():
        return None
    url = value.strip()
    if _looks_like_youtube_video_id(url):
        return f"https://www.youtube.com/watch?v={url}"
    if url.startswith("//"):
        return f"https:{url}"
    if url.startswith("/"):
        return f"https://www.youtube.com{url}"
    if url.startswith("http://") or url.startswith("https://"):
        return url
    if url.lower().startswith(("www.youtube.com/", "youtube.com/", "youtu.be/")):
        return f"https://{url}"
    return None


def _youtube_entry_source_url(entry: dict[str, Any]) -> str | None:
    for key in ("webpage_url", "url"):
        source_url = _normalize_youtube_source_url(entry.get(key))
        if source_url:
            return source_url
    return None


def _youtube_publish_date(entry: dict[str, Any]) -> str | None:
    raw_date = entry.get("upload_date") or entry.get("release_date")
    if isinstance(raw_date, str) and len(raw_date) == 8 and raw_date.isdigit():
        return f"{raw_date[:4]}-{raw_date[4:6]}-{raw_date[6:8]}"
    return None


def _youtube_creator_name(entry: dict[str, Any], fallback: str | None = None) -> str | None:
    for key in ("uploader", "channel", "playlist_uploader", "playlist_channel"):
        value = entry.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return fallback


def _friendly_cookie_error(exc: Exception) -> RuntimeError | None:
    detail = str(exc)
    if "Could not copy Chrome cookie database" not in detail:
        return None
    return RuntimeError(
        "无法读取 Chrome 登录态：Chrome 正在占用 Cookie 文件。\n\n"
        "处理方式：\n"
        "1. 完全关闭 Chrome，包括右下角托盘里的后台 Chrome。\n"
        "2. 回到本软件，保持登录态为 Chrome，再重新读取作品列表。\n"
        "3. 如果仍失败，请用浏览器扩展导出 Netscape 格式 cookies.txt，"
        "然后在本软件里把登录态改成 cookies.txt。"
    )


def discover_youtube_items(url: str, options: DownloadOptions, max_items: int = 500) -> list[CatalogItem]:
    """读取 YouTube 视频、频道或播放列表，并返回可下载条目。"""
    from yt_dlp import YoutubeDL

    is_single_video_url = _is_youtube_video_url(url)
    ydl_options: dict[str, Any] = {
        "extract_flat": "in_playlist",
        "skip_download": True,
        "quiet": True,
        "ignoreerrors": True,
        "noplaylist": is_single_video_url,
    }
    if options.cookie_mode == AUTH_COOKIE_MODE:
        ydl_options["cookiefile"] = str(export_auth_cookies_txt())
    elif options.cookie_mode in {"Chrome", "Edge", "Firefox"}:
        ydl_options["cookiesfrombrowser"] = (options.cookie_mode.lower(),)
    elif options.cookie_mode == "cookies.txt" and options.cookie_file:
        ydl_options["cookiefile"] = str(options.cookie_file)

    try:
        with YoutubeDL(ydl_options) as ydl:
            info = ydl.extract_info(url, download=False)
            if not info:
                return []

            fallback_creator = _youtube_creator_name(info)
            items: list[CatalogItem] = []
            seen_items: set[str] = set()
            seen_sources: set[str] = set()

            def collect_entries(source_info: dict[str, Any], depth: int = 0) -> None:
                if len(items) >= max_items:
                    return

                source_entries = source_info.get("entries")
                if not source_entries:
                    source_entries = [source_info]

                for entry in islice(source_entries, max_items):
                    if len(items) >= max_items:
                        return
                    if not isinstance(entry, dict):
                        continue

                    item_url = _format_youtube_url(entry)
                    if item_url:
                        if item_url in seen_items:
                            continue
                        seen_items.add(item_url)
                        title = str(entry.get("title") or "YouTube 视频").strip()
                        items.append(
                            CatalogItem(
                                platform="YouTube",
                                title=title,
                                url=item_url,
                                publish_date=_youtube_publish_date(entry),
                                creator_name=_youtube_creator_name(entry, fallback_creator),
                            )
                        )
                        continue

                    if depth >= 3:
                        continue
                    source_url = _youtube_entry_source_url(entry)
                    if not source_url or source_url in seen_sources:
                        continue
                    seen_sources.add(source_url)
                    try:
                        nested_info = ydl.extract_info(source_url, download=False)
                    except Exception:
                        continue
                    if isinstance(nested_info, dict):
                        collect_entries(nested_info, depth + 1)

            collect_entries(info)
    except Exception as exc:
        friendly_error = _friendly_cookie_error(exc)
        if friendly_error:
            raise friendly_error from exc
        raise

    return items


def discover_links(text: str, options: DownloadOptions, max_items: int = 500) -> list[CatalogItem]:
    """把用户粘贴的作者页、频道、播放列表或单条链接解析成可勾选的视频列表。"""
    from .douyin import discover_douyin_author_items, is_douyin_url

    discovered: list[CatalogItem] = []
    seen: set[str] = set()
    for url in split_urls(text):
        if is_douyin_url(url):
            items = discover_douyin_author_items(url, options, max_items=max_items)
        else:
            items = discover_youtube_items(url, options, max_items=max_items)

        for item in items:
            if item.url in seen:
                continue
            seen.add(item.url)
            discovered.append(item)
    return discovered
