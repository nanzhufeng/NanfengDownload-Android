from __future__ import annotations

import http.cookiejar
import json
import math
import os
import re
import tempfile
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Callable

from yt_dlp.cookies import extract_cookies_from_browser

from .auth_profile import AUTH_COOKIE_MODE, cookie_jar_from_auth_profile
from .downloader import CancelCallback, DownloadOptions, DownloadResult, DownloadStopped, raise_if_cancelled, safe_path_name


ProgressCallback = Callable[[dict[str, Any]], None]

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)

VIDEO_URL_HINTS = (
    "douyinvod.com",
    "amemv.com",
    "ixigua.com",
    "mime_type=video",
    "media-video",
    "video_id=",
)
AUDIO_URL_HINTS = (
    "ies-music",
    "mime_type=audio",
    "media-audio",
    "cs=4",
    ".mp3",
)
IMAGE_URL_HINTS = (
    "image",
    "cover",
    "avatar",
    ".jpg",
    ".jpeg",
    ".png",
    ".webp",
    ".gif",
)
NON_WORK_MEDIA_HINTS = (
    "douyin_pc_client",
    "bytednsdoc.com",
    "/obj/eden-",
    "/obj/eden/",
    "download/douyin",
    "douyin-pc-web",
    "effectcdn",
    "byteeffect",
    "ies.fe.effect",
)


@dataclass(frozen=True)
class DouyinInfo:
    aweme_id: str
    title: str
    video_url: str
    audio_url: str | None = None
    image_urls: tuple[str, ...] = ()
    publish_date: str | None = None
    author_name: str | None = None


def is_douyin_url(url: str) -> bool:
    return "douyin.com" in url.lower()


def _build_cookie_jar(options: DownloadOptions) -> http.cookiejar.CookieJar | None:
    if options.cookie_mode == AUTH_COOKIE_MODE:
        return cookie_jar_from_auth_profile()
    if options.cookie_mode in {"Chrome", "Edge", "Firefox"}:
        try:
            return extract_cookies_from_browser(options.cookie_mode.lower())
        except Exception as exc:
            detail = str(exc)
            if options.cookie_mode == "Chrome" and "Could not copy Chrome cookie database" in detail:
                raise RuntimeError(
                    "无法读取 Chrome 登录态：Chrome 正在占用 Cookie 文件。\n\n"
                    "处理方式：\n"
                    "1. 完全关闭 Chrome，包括右下角托盘里的后台 Chrome。\n"
                    "2. 回到本软件，保持登录态为 Chrome，再重新读取作品列表。\n"
                    "3. 如果仍失败，请用浏览器扩展导出 Netscape 格式 cookies.txt，"
                    "然后在本软件里把登录态改成 cookies.txt。"
                ) from exc
            raise RuntimeError(
                f"无法读取 {options.cookie_mode} 登录态。请关闭浏览器后重试，"
                "或导出 Netscape 格式 cookies.txt 后在软件里选择。"
            ) from exc
    if options.cookie_mode == "cookies.txt" and options.cookie_file:
        jar = http.cookiejar.MozillaCookieJar(str(options.cookie_file))
        jar.load(ignore_discard=True, ignore_expires=True)
        return jar
    return None


def _build_opener(options: DownloadOptions) -> urllib.request.OpenerDirector:
    handlers: list[Any] = [urllib.request.HTTPRedirectHandler()]
    cookie_jar = _build_cookie_jar(options)
    if cookie_jar:
        handlers.append(urllib.request.HTTPCookieProcessor(cookie_jar))
    return urllib.request.build_opener(*handlers)


def _request_text(opener: urllib.request.OpenerDirector, url: str, referer: str | None = None) -> tuple[str, str]:
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    }
    if referer:
        headers["Referer"] = referer
    request = urllib.request.Request(url, headers=headers)
    with opener.open(request, timeout=25) as response:
        raw = response.read()
        encoding = response.headers.get_content_charset() or "utf-8"
        return response.geturl(), raw.decode(encoding, errors="ignore")


def _request_json(opener: urllib.request.OpenerDirector, url: str, referer: str) -> dict[str, Any]:
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Referer": referer,
    }
    request = urllib.request.Request(url, headers=headers)
    with opener.open(request, timeout=25) as response:
        raw = response.read()
        return json.loads(raw.decode("utf-8", errors="ignore"))


def _extract_aweme_id(url: str, html: str = "") -> str | None:
    candidates = [
        r"/video/(\d+)",
        r"/note/(\d+)",
        r"[?&](?:modal_id|aweme_id|item_id)=(\d+)",
        r'"aweme_id"\s*:\s*"(\d+)"',
        r'"awemeId"\s*:\s*"(\d+)"',
        r'"itemId"\s*:\s*"(\d+)"',
    ]
    haystack = f"{url}\n{html}"
    for pattern in candidates:
        match = re.search(pattern, haystack)
        if match:
            return match.group(1)
    return None


def _extract_sec_uid(url: str, html: str = "") -> str | None:
    candidates = [
        r"/share/user/([^/?&#]+)",
        r"/user/([^/?&#]+)",
        r"[?&]sec_uid=([^&]+)",
        r'"sec_uid"\s*:\s*"([^"]+)"',
        r'"secUid"\s*:\s*"([^"]+)"',
    ]
    haystack = f"{url}\n{html}"
    for pattern in candidates:
        match = re.search(pattern, haystack)
        if match:
            return urllib.parse.unquote(match.group(1))
    return None


def _extract_author_from_html(html: str) -> str | None:
    title_match = re.search(r"<title>\s*(.*?)\s*的抖音", html, flags=re.S)
    if title_match:
        author = re.sub(r"\s+", " ", title_match.group(1)).strip()
        if author:
            return author
    return None


def _iter_values(value: Any) -> Any:
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from _iter_values(child)
    elif isinstance(value, list):
        for child in value:
            yield from _iter_values(child)


def _iter_string_values(value: Any) -> Any:
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for child in value.values():
            yield from _iter_string_values(child)
    elif isinstance(value, list):
        for child in value:
            yield from _iter_string_values(child)


def _clean_candidate_url(value: str) -> str:
    cleaned = value.replace("\\/", "/").replace("\\u0026", "&")
    cleaned = urllib.parse.unquote(cleaned)
    return cleaned.strip().strip('"\',;)]}')


def _is_probable_media_url(url: str, media_type: str = "video") -> bool:
    lower = url.lower()
    if not lower.startswith("http"):
        return False
    if any(hint in lower for hint in NON_WORK_MEDIA_HINTS):
        return False
    if any(hint in lower for hint in IMAGE_URL_HINTS):
        return False
    if media_type == "audio":
        return any(hint in lower for hint in AUDIO_URL_HINTS)
    if any(hint in lower for hint in AUDIO_URL_HINTS):
        return False
    return any(hint in lower for hint in VIDEO_URL_HINTS)


def _candidate_media_urls(value: Any, media_type: str = "video") -> list[str]:
    candidates: list[str] = []
    seen: set[str] = set()
    for text in _iter_string_values(value):
        normalized = text.replace("\\/", "/").replace("\\u0026", "&")
        for match in re.finditer(r"https?://[^\s\"'<>]+", normalized):
            url = _clean_candidate_url(match.group(0))
            if url in seen or not _is_probable_media_url(url, media_type):
                continue
            seen.add(url)
            candidates.append(url)
    return candidates


def _is_aweme_image_url(url: str) -> bool:
    lower = url.lower()
    if not lower.startswith("http"):
        return False
    if "avatar" in lower or "emblem" in lower or "twemoji" in lower:
        return False
    return (
        "biz_tag=aweme_images" in lower
        or "aweme_images" in lower
        or "packsourceenum_aweme_detail" in lower
    )


def _find_video_url(data: dict[str, Any]) -> str | None:
    def url_from_addr(value: Any) -> str | None:
        if not isinstance(value, dict):
            return None
        url_list = value.get("url_list") or value.get("urlList")
        if isinstance(url_list, list):
            for item in url_list:
                if isinstance(item, str) and item.startswith("http"):
                    candidate = item.replace("playwm", "play")
                    if _is_probable_media_url(candidate, "video"):
                        return candidate
        for key in ("url", "uri", "main_url", "backup_url"):
            item = value.get(key)
            if isinstance(item, str) and item.startswith("http"):
                candidate = item.replace("playwm", "play")
                if _is_probable_media_url(candidate, "video"):
                    return candidate
        return None

    for node in _iter_values(data):
        if not isinstance(node, dict):
            continue
        for key in ("play_addr", "playAddr", "play_addr_h264", "download_addr", "downloadAddr"):
            url = url_from_addr(node.get(key))
            if url:
                return url

        bit_rates = node.get("bit_rate") or node.get("bitRate")
        if isinstance(bit_rates, list):
            for bit_rate in bit_rates:
                if not isinstance(bit_rate, dict):
                    continue
                url = url_from_addr(bit_rate.get("play_addr") or bit_rate.get("playAddr"))
                if url:
                    return url
    candidates = _candidate_media_urls(data, "video")
    if candidates:
        return candidates[0]
    return None


def _find_title(data: dict[str, Any], fallback: str) -> str:
    for node in _iter_values(data):
        if not isinstance(node, dict):
            continue
        if node.get("aweme_id") or node.get("awemeId") or node.get("item_id"):
            value = node.get("desc") or node.get("title")
            if isinstance(value, str) and value.strip():
                return value.strip()
    for node in _iter_values(data):
        if not isinstance(node, dict):
            continue
        for key in ("desc", "title", "text"):
            value = node.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    return fallback


def _find_author_name(data: dict[str, Any]) -> str | None:
    for node in _iter_values(data):
        if not isinstance(node, dict):
            continue
        for key in ("nickname", "author_name", "authorName"):
            value = node.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    return None


def _normalize_author_name(value: str | None) -> str:
    return re.sub(r"\s+", "", value or "").strip()


def _find_sec_uids(data: dict[str, Any]) -> set[str]:
    sec_uids: set[str] = set()
    for node in _iter_values(data):
        if not isinstance(node, dict):
            continue
        for key in ("sec_uid", "secUid", "sec_user_id", "secUserId"):
            value = node.get(key)
            if isinstance(value, str) and value.strip():
                sec_uids.add(value.strip())
    return sec_uids


def _is_target_author_node(
    node: dict[str, Any],
    target_sec_uid: str | None = None,
    target_author: str | None = None,
) -> bool:
    if target_sec_uid:
        sec_uids = _find_sec_uids(node)
        if sec_uids:
            return target_sec_uid in sec_uids

    if target_author:
        author_name = _find_author_name(node)
        if author_name:
            return _normalize_author_name(author_name) == _normalize_author_name(target_author)

    return not target_sec_uid and not target_author


def _find_publish_date(data: dict[str, Any]) -> str | None:
    for node in _iter_values(data):
        if not isinstance(node, dict):
            continue
        for key in ("create_time", "createTime", "publish_time", "publishTime"):
            value = node.get(key)
            if isinstance(value, str) and value.isdigit():
                value = int(value)
            if isinstance(value, int) and value > 0:
                timestamp = value / 1000 if value > 10_000_000_000 else value
                try:
                    return datetime.fromtimestamp(timestamp).strftime("%Y-%m-%d")
                except (OSError, ValueError):
                    continue
    return None


def _extract_json_blobs(html: str) -> list[dict[str, Any]]:
    blobs: list[dict[str, Any]] = []
    patterns = [
        r'<script id="RENDER_DATA" type="application/json">(.*?)</script>',
        r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">(.*?)</script>',
    ]
    for pattern in patterns:
        for match in re.finditer(pattern, html, flags=re.S):
            raw = urllib.parse.unquote(match.group(1))
            try:
                blobs.append(json.loads(raw))
            except json.JSONDecodeError:
                continue
    return blobs


def _collect_aweme_catalog_items(
    data: dict[str, Any],
    fallback_author: str | None = None,
    target_sec_uid: str | None = None,
    target_author: str | None = None,
) -> list[dict[str, str | None]]:
    items: list[dict[str, str | None]] = []
    seen: set[str] = set()
    for node in _iter_values(data):
        if not isinstance(node, dict):
            continue
        aweme_id = node.get("aweme_id") or node.get("awemeId") or node.get("item_id")
        if not isinstance(aweme_id, str) or not aweme_id.isdigit() or aweme_id in seen:
            continue
        if not _is_target_author_node(node, target_sec_uid=target_sec_uid, target_author=target_author):
            continue
        seen.add(aweme_id)
        items.append(
            {
                "url": f"https://www.douyin.com/video/{aweme_id}",
                "title": _find_title(node, f"抖音视频 {aweme_id}"),
                "publish_date": _find_publish_date(node),
                "author_name": _find_author_name(node) or fallback_author,
            }
        )
    return items


def _request_douyin_author_api(
    opener: urllib.request.OpenerDirector,
    sec_uid: str,
    cursor: int,
    referer: str,
) -> dict[str, Any] | None:
    params = {
        "device_platform": "webapp",
        "aid": "6383",
        "channel": "channel_pc_web",
        "sec_user_id": sec_uid,
        "max_cursor": str(cursor),
        "count": "20",
        "publish_video_strategy_type": "2",
        "pc_client_type": "1",
        "version_code": "170400",
        "version_name": "17.4.0",
        "cookie_enabled": "true",
        "screen_width": "1920",
        "screen_height": "1080",
        "browser_language": "zh-CN",
        "browser_platform": "Win32",
        "browser_name": "Chrome",
        "browser_version": "126.0.0.0",
        "browser_online": "true",
        "engine_name": "Blink",
        "engine_version": "126.0.0.0",
        "os_name": "Windows",
        "os_version": "10",
        "cpu_core_num": "8",
        "device_memory": "8",
        "platform": "PC",
    }
    api_url = "https://www.douyin.com/aweme/v1/web/aweme/post/?" + urllib.parse.urlencode(params)
    try:
        return _request_json(opener, api_url, referer)
    except Exception:
        return None


def _playwright_cookies_from_options(options: DownloadOptions) -> list[dict[str, Any]]:
    jar = _build_cookie_jar(options)
    if not jar:
        return []

    cookies: list[dict[str, Any]] = []
    for cookie in jar:
        domain = cookie.domain or ".douyin.com"
        if "douyin" not in domain and "iesdouyin" not in domain:
            continue
        item: dict[str, Any] = {
            "name": cookie.name,
            "value": cookie.value,
            "domain": domain,
            "path": cookie.path or "/",
            "httpOnly": bool(cookie.has_nonstandard_attr("HttpOnly")),
            "secure": bool(cookie.secure),
        }
        if cookie.expires:
            item["expires"] = float(cookie.expires)
        same_site = cookie.get_nonstandard_attr("SameSite")
        if same_site in {"Strict", "Lax", "None"}:
            item["sameSite"] = same_site
        cookies.append(item)
    return cookies


def _background_browser_args(platform_name: str | None = None) -> list[str]:
    """返回仅供后台解析使用的浏览器参数，不影响可见登录窗口。"""
    platform_name = platform_name or os.name
    args = ["--disable-blink-features=AutomationControlled"]
    if platform_name == "nt":
        args.extend(
            [
                "--headless=new",
                "--no-startup-window",
                "--window-position=-32000,-32000",
            ]
        )
    return args


def _capture_is_ready(
    video_urls: list[str],
    image_urls: list[str],
    title: str,
    publish_date: str | None,
    author_name: str | None,
    fallback_title: str,
) -> bool:
    """媒体与命名所需元数据均已到位时，可提前结束后台嗅探。"""
    has_media = bool(video_urls or image_urls)
    has_title = bool(title.strip()) and title.strip() != fallback_title
    return has_media and has_title and bool(publish_date) and bool(author_name)


def _capture_wait_rounds(timeout_ms: int, poll_ms: int) -> int:
    return max(1, (timeout_ms + poll_ms - 1) // poll_ms)


def _wait_for_catalog_growth(
    page: Any,
    catalog: list[dict[str, str | None]],
    previous_count: int,
    timeout_ms: int,
    poll_ms: int = 250,
) -> bool:
    for _ in range(_capture_wait_rounds(timeout_ms, poll_ms)):
        if len(catalog) > previous_count:
            return True
        page.wait_for_timeout(poll_ms)
    return len(catalog) > previous_count


def _discover_douyin_with_browser(
    sec_uid: str,
    options: DownloadOptions,
    max_items: int,
    author_name: str | None = None,
) -> list[dict[str, str | None]]:
    from playwright.sync_api import sync_playwright

    browser_path = _find_chrome_path()
    if not browser_path:
        raise RuntimeError("没有找到 Chrome 或 Edge，无法读取抖音作者页作品列表。")

    page_url = f"https://www.douyin.com/user/{sec_uid}"
    catalog: list[dict[str, str | None]] = []
    seen: set[str] = set()
    current_author = author_name

    def add_items(items: list[dict[str, str | None]]) -> None:
        nonlocal current_author
        for item in items:
            item_url = item.get("url")
            if not item_url or item_url in seen:
                continue
            if item.get("author_name"):
                current_author = item.get("author_name")
            elif current_author:
                item["author_name"] = current_author
            seen.add(item_url)
            catalog.append(item)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            executable_path=browser_path,
            headless=True,
            args=_background_browser_args(),
        )
        context = browser.new_context(
            user_agent=USER_AGENT,
            viewport={"width": 1280, "height": 900},
            locale="zh-CN",
        )
        playwright_cookies = _playwright_cookies_from_options(options)
        if playwright_cookies:
            context.add_cookies(playwright_cookies)
        page = context.new_page()

        def on_response(response: Any) -> None:
            lower = response.url.lower()
            if "aweme" not in lower and "post" not in lower:
                return
            try:
                data = response.json()
            except Exception:
                return
            if isinstance(data, dict):
                add_items(
                    _collect_aweme_catalog_items(
                        data,
                        current_author,
                        target_sec_uid=sec_uid,
                        target_author=author_name,
                    )
                )

        page.on("response", on_response)
        page.goto(page_url, wait_until="domcontentloaded", timeout=30_000)
        _wait_for_catalog_growth(page, catalog, previous_count=0, timeout_ms=3_000)
        max_scrolls = min(120, max(12, max_items // 6))
        idle_rounds = 0
        last_count = len(catalog)
        for _ in range(max_scrolls):
            if len(catalog) >= max_items:
                break
            page.evaluate(
                """
                () => {
                    const containers = Array.from(document.querySelectorAll("*"))
                        .filter(el => el.scrollHeight > el.clientHeight + 50)
                        .sort((a, b) => (b.scrollHeight - b.clientHeight) - (a.scrollHeight - a.clientHeight));
                    const target = containers[0] || document.scrollingElement || document.documentElement;
                    target.scrollTop = target.scrollHeight;
                    window.dispatchEvent(new Event("scroll"));
                }
                """
            )
            grew = _wait_for_catalog_growth(page, catalog, last_count, timeout_ms=1_500)
            if not grew:
                idle_rounds += 1
                if idle_rounds >= 4:
                    break
            else:
                idle_rounds = 0
                last_count = len(catalog)

        context.close()
        browser.close()

    return catalog[:max_items]


def discover_douyin_author_items(url: str, options: DownloadOptions, max_items: int = 500) -> list[Any]:
    """把抖音作者页短链解析为作品列表，供界面勾选下载。"""
    from .catalog import CatalogItem

    opener = _build_opener(options)
    final_url, html = _request_text(opener, url)
    sec_uid = _extract_sec_uid(final_url, html)
    source_aweme_id = _extract_aweme_id(final_url) or _extract_aweme_id(url)
    aweme_id = source_aweme_id or _extract_aweme_id(final_url, html)
    author_name = _extract_author_from_html(html)

    if source_aweme_id or (not sec_uid and aweme_id):
        item_aweme_id = source_aweme_id or aweme_id
        try:
            info = _fetch_douyin_info(final_url, options)
        except Exception:
            info = None
        if info:
            return [
                CatalogItem(
                    platform="抖音",
                    title=info.title,
                    url=f"https://www.douyin.com/video/{info.aweme_id}",
                    publish_date=info.publish_date,
                    creator_name=info.author_name,
                )
            ]
        return [
            CatalogItem(
                platform="抖音",
                title=f"抖音视频 {item_aweme_id}",
                url=f"https://www.douyin.com/video/{item_aweme_id}",
                publish_date=None,
                creator_name=author_name,
            )
        ]

    if not sec_uid:
        raise RuntimeError("没有从抖音链接解析到作者页。请复制作者主页分享链接，或作品页链接。")

    referer = f"https://www.douyin.com/user/{sec_uid}"
    raw_items: list[dict[str, str | None]] = []
    cursor = 0
    for _ in range(50):
        data = _request_douyin_author_api(opener, sec_uid, cursor, referer)
        if not data:
            break
        author_name = _find_author_name(data) or author_name
        raw_items.extend(
            _collect_aweme_catalog_items(
                data,
                author_name,
                target_sec_uid=sec_uid,
                target_author=author_name,
            )
        )
        if len(raw_items) >= max_items or not data.get("has_more"):
            break
        next_cursor = data.get("max_cursor")
        if not isinstance(next_cursor, int) or next_cursor == cursor:
            break
        cursor = next_cursor

    if len(raw_items) < max_items:
        try:
            raw_items.extend(_discover_douyin_with_browser(sec_uid, options, max_items=max_items, author_name=author_name))
        except Exception:
            if not raw_items:
                raise

    items: list[CatalogItem] = []
    seen: set[str] = set()
    for item in raw_items:
        item_url = item.get("url")
        if not item_url or item_url in seen:
            continue
        seen.add(item_url)
        items.append(
            CatalogItem(
                platform="抖音",
                title=str(item.get("title") or "抖音视频"),
                url=item_url,
                publish_date=item.get("publish_date"),
                creator_name=item.get("author_name") or author_name,
            )
        )
        if len(items) >= max_items:
            break
    return items


def _fetch_douyin_info(url: str, options: DownloadOptions) -> DouyinInfo:
    opener = _build_opener(options)
    final_url, html = _request_text(opener, url)
    aweme_id = _extract_aweme_id(final_url, html)

    if not aweme_id:
        raise RuntimeError(
            "没有从抖音链接解析到作品 ID。这个短链可能已失效、被重定向到首页，"
            "请重新从抖音复制分享链接，或打开作品后复制浏览器地址栏里的 /video/ 链接。"
        )

    for blob in _extract_json_blobs(html):
        video_url = _find_video_url(blob)
        if video_url:
            return DouyinInfo(
                aweme_id=aweme_id,
                title=_find_title(blob, f"抖音视频 {aweme_id}"),
                video_url=video_url,
                publish_date=_find_publish_date(blob),
                author_name=_find_author_name(blob),
            )

    referer = final_url if "douyin.com" in final_url else f"https://www.douyin.com/video/{aweme_id}"
    api_urls = [
        (
            "https://www.douyin.com/aweme/v1/web/aweme/detail/"
            f"?aweme_id={aweme_id}&aid=1128&device_platform=webapp"
        ),
        f"https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids={aweme_id}",
    ]
    last_error: str | None = None
    for api_url in api_urls:
        try:
            data = _request_json(opener, api_url, referer)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            last_error = str(exc)
            continue
        video_url = _find_video_url(data)
        if video_url:
            return DouyinInfo(
                aweme_id=aweme_id,
                title=_find_title(data, f"抖音视频 {aweme_id}"),
                video_url=video_url,
                publish_date=_find_publish_date(data),
                author_name=_find_author_name(data),
            )

    try:
        return _sniff_douyin_with_browser(aweme_id, options)
    except Exception as exc:
        detail = f"最后错误：{last_error}" if last_error else "接口没有返回可下载视频地址。"
        raise RuntimeError(f"已解析到抖音作品 ID {aweme_id}，但没有找到视频地址。{detail} 浏览器嗅探也失败：{exc}") from exc


def _find_chrome_path() -> str | None:
    candidates = [
        Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
        Path(r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"),
        Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
        Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
        Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
        Path.home() / "Applications" / "Google Chrome.app" / "Contents" / "MacOS" / "Google Chrome",
        Path("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"),
        Path.home() / "Applications" / "Microsoft Edge.app" / "Contents" / "MacOS" / "Microsoft Edge",
        Path("/usr/bin/google-chrome"),
        Path("/usr/bin/chromium"),
        Path("/usr/bin/microsoft-edge"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return str(candidate)
    return None


def _sniff_douyin_with_browser(aweme_id: str, options: DownloadOptions) -> DouyinInfo:
    from playwright.sync_api import sync_playwright

    browser_path = _find_chrome_path()
    if not browser_path:
        raise RuntimeError("没有找到 Chrome 或 Edge，无法使用浏览器嗅探。")

    page_url = f"https://www.douyin.com/video/{aweme_id}"
    video_urls: list[str] = []
    audio_urls: list[str] = []
    image_urls: list[str] = []
    candidate_urls: list[str] = []
    page_title = f"抖音视频 {aweme_id}"
    publish_date: str | None = None
    author_name: str | None = None

    def add_candidate(url: str, media_type: str = "video") -> None:
        target = audio_urls if media_type == "audio" else video_urls
        if url and url not in target:
            target.append(url)

    def add_loose_candidate(url: str) -> None:
        if url and url not in candidate_urls:
            candidate_urls.append(url)

    def add_image(url: str) -> None:
        if _is_aweme_image_url(url) and url not in image_urls:
            image_urls.append(url)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            executable_path=browser_path,
            headless=True,
            args=_background_browser_args(),
        )
        context = browser.new_context(
            user_agent=USER_AGENT,
            viewport={"width": 1280, "height": 720},
            locale="zh-CN",
        )
        playwright_cookies = _playwright_cookies_from_options(options)
        if playwright_cookies:
            context.add_cookies(playwright_cookies)
        page = context.new_page()

        def on_response(response: Any) -> None:
            nonlocal page_title, publish_date, author_name
            response_url = response.url
            content_type = response.headers.get("content-type", "")
            lower = response_url.lower()
            if any(hint in lower for hint in NON_WORK_MEDIA_HINTS):
                return
            if f"aweme_id={aweme_id}" in lower and "aweme/detail" in lower:
                try:
                    data = response.json()
                    page_title = _find_title(data, page_title)
                    publish_date = _find_publish_date(data) or publish_date
                    author_name = _find_author_name(data) or author_name
                    for url in _candidate_media_urls(data, "video"):
                        add_candidate(url)
                except Exception:
                    pass
            if "audio" in content_type or _is_probable_media_url(response_url, "audio"):
                add_candidate(response_url, "audio")
                return
            if "video" in content_type or _is_probable_media_url(response_url, "video"):
                add_candidate(response_url, "video")
                return
            if _is_aweme_image_url(response_url):
                add_image(response_url)
                return
            if any(hint in lower for hint in ("douyinvod", "video_id=", "mime_type=video")):
                add_loose_candidate(response_url)

        def on_request(request: Any) -> None:
            request_url = request.url
            if any(hint in request_url.lower() for hint in NON_WORK_MEDIA_HINTS):
                return
            if _is_probable_media_url(request_url, "video"):
                add_candidate(request_url, "video")
            elif _is_probable_media_url(request_url, "audio"):
                add_candidate(request_url, "audio")

        page.on("response", on_response)
        page.on("request", on_request)
        page.goto(page_url, wait_until="domcontentloaded", timeout=30_000)
        fallback_title = f"抖音视频 {aweme_id}"
        for _ in range(_capture_wait_rounds(timeout_ms=12_000, poll_ms=250)):
            if _capture_is_ready(
                video_urls,
                image_urls,
                page_title,
                publish_date,
                author_name,
                fallback_title,
            ):
                break
            page.wait_for_timeout(250)
        try:
            dom_urls = page.evaluate(
                """
                ({
                    media: Array.from(document.querySelectorAll('video, source'))
                        .map((el) => el.currentSrc || el.src)
                        .filter(Boolean),
                    images: Array.from(document.querySelectorAll('img'))
                        .map((el) => el.currentSrc || el.src)
                        .filter(Boolean)
                })
                """
            )
            if isinstance(dom_urls, dict):
                for item in dom_urls.get("media", []):
                    if isinstance(item, str):
                        if _is_probable_media_url(item, "audio"):
                            add_candidate(item, "audio")
                        elif _is_probable_media_url(item, "video"):
                            add_candidate(item, "video")
                for item in dom_urls.get("images", []):
                    if isinstance(item, str):
                        add_image(item)
        except Exception:
            pass
        page_title = page.title() or page_title
        context.close()
        browser.close()

    if not video_urls and candidate_urls:
        video_urls.extend(candidate_urls)

    if not video_urls and image_urls:
        title = page_title.replace(" - 抖音", "").strip() or f"抖音视频 {aweme_id}"
        return DouyinInfo(
            aweme_id=aweme_id,
            title=title,
            video_url="",
            audio_url=audio_urls[0] if audio_urls else None,
            image_urls=tuple(image_urls),
            publish_date=publish_date,
            author_name=author_name,
        )

    if not video_urls:
        raise RuntimeError("浏览器没有捕获到目标视频流。")

    title = page_title.replace(" - 抖音", "").strip() or f"抖音视频 {aweme_id}"
    return DouyinInfo(
        aweme_id=aweme_id,
        title=title,
        video_url=video_urls[0],
        audio_url=audio_urls[0] if audio_urls else None,
        publish_date=publish_date,
        author_name=author_name,
    )


def _safe_file_name(text: str, fallback: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\r\n\t]+', " ", text).strip()
    cleaned = re.sub(r"\s+", " ", cleaned)
    if not cleaned:
        cleaned = fallback
    return cleaned[:120]


def _unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    for index in range(1, 1000):
        candidate = path.with_name(f"{path.stem} ({index}){path.suffix}")
        if not candidate.exists():
            return candidate
    raise RuntimeError(f"无法生成不重名文件名：{path}")


def _format_speed(bytes_per_second: float) -> str:
    if bytes_per_second <= 0:
        return "-"
    units = ["B/s", "KB/s", "MB/s", "GB/s"]
    value = bytes_per_second
    unit = units[0]
    for unit in units:
        if value < 1024 or unit == units[-1]:
            break
        value /= 1024
    return f"{value:.1f} {unit}"


def _format_eta(seconds: float) -> str:
    if not math.isfinite(seconds) or seconds <= 0:
        return "-"
    minutes, sec = divmod(int(seconds), 60)
    if minutes:
        return f"{minutes}m{sec:02d}s"
    return f"{sec}s"


def _looks_like_media(data: bytes) -> bool:
    sample = data[:64]
    return (
        b"ftyp" in sample
        or sample.startswith(b"\xff\xfb")
        or sample.startswith(b"ID3")
        or sample.startswith(b"\x1aE\xdf\xa3")
    )


def _download_binary(
    url: str,
    target: Path,
    options: DownloadOptions,
    progress_callback: ProgressCallback,
    cancel_callback: CancelCallback | None = None,
) -> None:
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "*/*",
        "Referer": "https://www.douyin.com/",
    }
    request = urllib.request.Request(url, headers=headers)
    opener = _build_opener(options)
    target.parent.mkdir(parents=True, exist_ok=True)
    start_time = time.monotonic()
    downloaded = 0
    try:
        raise_if_cancelled(cancel_callback)
        with opener.open(request, timeout=40) as response:
            total = int(response.headers.get("Content-Length") or 0)
            content_type = (response.headers.get("Content-Type") or "").lower()
            if "text/html" in content_type or "application/json" in content_type:
                raise RuntimeError(f"平台没有返回视频流，而是返回了 {content_type or '网页内容'}。请确认已登录后重新读取作品列表。")
            first_chunk = response.read(1024 * 256)
            raise_if_cancelled(cancel_callback)
            if not first_chunk:
                raise RuntimeError("平台返回了空内容，没有可保存的视频流。")
            downloaded += len(first_chunk)
            first_chunk_is_media = _looks_like_media(first_chunk)

            with target.open("wb") as file_obj:
                file_obj.write(first_chunk)
                while True:
                    raise_if_cancelled(cancel_callback)
                    chunk = response.read(1024 * 256)
                    if not chunk:
                        break
                    raise_if_cancelled(cancel_callback)
                    file_obj.write(chunk)
                    downloaded += len(chunk)
                    elapsed = max(time.monotonic() - start_time, 0.001)
                    speed = downloaded / elapsed
                    eta = (total - downloaded) / speed if total and speed else 0
                    progress_callback(
                        {
                            "status": "downloading",
                            "filename": str(target),
                            "downloaded_bytes": downloaded,
                            "total_bytes": total,
                            "_speed_str": _format_speed(speed),
                            "_eta_str": _format_eta(eta),
                        }
                    )
    except DownloadStopped:
        target.unlink(missing_ok=True)
        raise
    if downloaded < 64 * 1024 and not first_chunk_is_media:
        target.unlink(missing_ok=True)
        raise RuntimeError("平台返回内容不像有效视频，已阻止保存假 mp4。请先登录抖音后重新读取作品列表。")


def _asset_suffix(content_type: str, fallback: str) -> str:
    lowered = content_type.lower()
    if "webp" in lowered:
        return ".webp"
    if "png" in lowered:
        return ".png"
    if "jpeg" in lowered or "jpg" in lowered:
        return ".jpg"
    if "mpeg" in lowered or "mp3" in lowered:
        return ".mp3"
    if "mp4" in lowered:
        return ".mp4"
    return fallback


def _download_asset(
    url: str,
    target_without_suffix: Path,
    options: DownloadOptions,
    cancel_callback: CancelCallback | None = None,
    fallback_suffix: str = ".bin",
) -> Path:
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "*/*",
        "Referer": "https://www.douyin.com/",
    }
    request = urllib.request.Request(url, headers=headers)
    opener = _build_opener(options)
    raise_if_cancelled(cancel_callback)
    with opener.open(request, timeout=40) as response:
        content_type = response.headers.get("Content-Type") or ""
        suffix = _asset_suffix(content_type, fallback_suffix)
        target = target_without_suffix.with_suffix(suffix)
        data = response.read()
    raise_if_cancelled(cancel_callback)
    if not data:
        raise RuntimeError("平台返回了空素材，无法生成图文视频。")
    target.write_bytes(data)
    return target


def _ffmpeg_executable(options: DownloadOptions) -> str:
    if not options.ffmpeg_dir:
        return "ffmpeg"
    name = "ffmpeg.exe" if os.name == "nt" else "ffmpeg"
    return str(options.ffmpeg_dir / name)


def _concat_file_entry(path: Path) -> str:
    normalized = str(path).replace("\\", "/").replace("'", "'\\''")
    return f"file '{normalized}'"


def _make_image_post_video(
    info: DouyinInfo,
    target: Path,
    options: DownloadOptions,
    progress_callback: ProgressCallback,
    cancel_callback: CancelCallback | None = None,
) -> Path:
    if not info.image_urls:
        raise RuntimeError("这个抖音作品没有视频流，也没有可合成的图文图片。")

    ffmpeg = _ffmpeg_executable(options)

    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="douyin_image_post_", dir=str(target.parent)) as temp_dir:
        temp_path = Path(temp_dir)
        image_paths: list[Path] = []
        for index, image_url in enumerate(info.image_urls, start=1):
            image_path = _download_asset(
                image_url,
                temp_path / f"image_{index:03d}",
                options,
                cancel_callback,
                fallback_suffix=".jpg",
            )
            image_paths.append(image_path)
            progress_callback(
                {
                    "status": "downloading",
                    "filename": str(target),
                    "downloaded_bytes": index,
                    "total_bytes": len(info.image_urls) + (1 if info.audio_url else 0),
                    "_speed_str": "读取图文",
                    "_eta_str": "-",
                }
            )

        audio_path: Path | None = None
        if info.audio_url:
            audio_path = _download_asset(
                info.audio_url,
                temp_path / "audio",
                options,
                cancel_callback,
                fallback_suffix=".mp3",
            )

        list_path = temp_path / "images.txt"
        lines: list[str] = []
        duration_per_image = 3
        for image_path in image_paths:
            lines.append(_concat_file_entry(image_path))
            lines.append(f"duration {duration_per_image}")
        lines.append(_concat_file_entry(image_paths[-1]))
        list_path.write_text("\n".join(lines), encoding="utf-8")

        command = [
            ffmpeg,
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(list_path),
        ]
        if audio_path:
            command.extend(["-i", str(audio_path), "-shortest"])
        command.extend(
            [
                "-vf",
                "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p",
                "-r",
                "30",
                "-c:v",
                "libx264",
            ]
        )
        if audio_path:
            command.extend(["-c:a", "aac"])
        command.extend(["-movflags", "+faststart", str(target)])
        _run_ffmpeg(command, cancel_callback)

    if not target.exists() or target.stat().st_size == 0:
        raise RuntimeError("图文作品合成流程结束，但没有生成有效视频。")
    progress_callback({"status": "finished", "filename": str(target)})
    return target


def _run_ffmpeg(command: list[str], cancel_callback: CancelCallback | None = None) -> None:
    process = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    while True:
        return_code = process.poll()
        if return_code is not None:
            if return_code != 0:
                raise subprocess.CalledProcessError(return_code, command)
            return
        if cancel_callback and cancel_callback():
            process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=3)
            raise DownloadStopped("用户已停止下载。")
        time.sleep(0.2)


def _extract_audio(source: Path, options: DownloadOptions, cancel_callback: CancelCallback | None = None) -> Path:
    ffmpeg = _ffmpeg_executable(options)
    target = source.with_suffix(".mp3")
    command = [
        ffmpeg,
        "-y",
        "-i",
        str(source),
        "-vn",
        "-codec:a",
        "libmp3lame",
        "-q:a",
        "2",
        str(target),
    ]
    _run_ffmpeg(command, cancel_callback)
    source.unlink(missing_ok=True)
    return target


def _merge_video_audio(
    video_path: Path,
    audio_path: Path,
    target: Path,
    options: DownloadOptions,
    cancel_callback: CancelCallback | None = None,
) -> Path:
    ffmpeg = _ffmpeg_executable(options)
    command = [
        ffmpeg,
        "-y",
        "-i",
        str(video_path),
        "-i",
        str(audio_path),
        "-c",
        "copy",
        str(target),
    ]
    _run_ffmpeg(command, cancel_callback)
    video_path.unlink(missing_ok=True)
    audio_path.unlink(missing_ok=True)
    return target


def download_douyin_url(
    url: str,
    options: DownloadOptions,
    progress_callback: ProgressCallback,
    cancel_callback: CancelCallback | None = None,
) -> DownloadResult:
    info = _fetch_douyin_info(url, options)
    base_name = _safe_file_name(info.title, f"抖音视频 {info.aweme_id}")
    publish_date = info.publish_date or "未知日期"
    creator_dir = safe_path_name(options.creator_name or info.author_name)
    target = options.output_dir / "Douyin" / creator_dir / f"{publish_date} {base_name}.mp4"
    if options.quality == "仅音频 MP3":
        target = target.with_suffix(".mp3")
    if target.exists() and target.stat().st_size > 0:
        progress_callback({"status": "skipped", "filename": str(target)})
        return DownloadResult(files=[target], skipped=True, message="保存目录中已存在对应文件，已跳过下载。")
    target = _unique_path(target)

    if info.image_urls:
        if options.quality == "仅音频 MP3":
            if not info.audio_url:
                raise RuntimeError("这个抖音图文作品没有可下载的背景音乐。")
            _download_binary(info.audio_url, target, options, progress_callback, cancel_callback)
            progress_callback({"status": "finished", "filename": str(target)})
            return DownloadResult(files=[target])
        generated = _make_image_post_video(info, target, options, progress_callback, cancel_callback)
        return DownloadResult(files=[generated])

    if not info.video_url:
        raise RuntimeError("这个抖音作品没有可下载的视频流。")

    temp_files: list[Path] = []

    try:
        raise_if_cancelled(cancel_callback)
        audio_only_done = False
        if info.audio_url and options.quality != "仅音频 MP3":
            temp_video = target.with_suffix(".video.mp4")
            temp_audio = target.with_suffix(".audio.mp4")
            temp_files.extend([temp_video, temp_audio])
            _download_binary(info.video_url, temp_video, options, progress_callback, cancel_callback)
            _download_binary(info.audio_url, temp_audio, options, progress_callback, cancel_callback)
            target = _merge_video_audio(temp_video, temp_audio, target, options, cancel_callback)
        elif info.audio_url and options.quality == "仅音频 MP3":
            temp_audio = target.with_suffix(".audio.mp4")
            temp_files.append(temp_audio)
            _download_binary(info.audio_url, temp_audio, options, progress_callback, cancel_callback)
            temp_video = temp_audio
            target = _extract_audio(temp_video, options, cancel_callback)
            audio_only_done = True
        else:
            _download_binary(info.video_url, target, options, progress_callback, cancel_callback)

        if options.quality == "仅音频 MP3" and not audio_only_done:
            target = _extract_audio(target, options, cancel_callback)
    except DownloadStopped:
        for file_path in [target, *temp_files]:
            file_path.unlink(missing_ok=True)
        raise

    if not target.exists() or target.stat().st_size == 0:
        raise RuntimeError("抖音下载流程结束，但没有生成有效文件。")

    progress_callback({"status": "finished", "filename": str(target)})
    return DownloadResult(files=[target])
