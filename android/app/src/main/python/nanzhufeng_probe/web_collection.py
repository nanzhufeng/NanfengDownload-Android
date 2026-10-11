"""Bounded public web collections. Only extractor-owned entries become tasks."""

import hashlib
import json
import re
from urllib.parse import urljoin, urlsplit


def _identity(*parts):
    return hashlib.sha256("\n".join(str(part) for part in parts).encode()).hexdigest()


def collection_media_id(url):
    """URL-owned identity, stable before an individual extractor is run."""
    from .web_media import validate_url
    parsed = validate_url(url)
    return "collection:" + _identity(parsed.hostname.lower(), url)


def episode_links(source, anchors):
    """Return only episodes of the same Yinghua work, in numerical order."""
    from .web_media import validate_url

    parsed = validate_url(source)
    if parsed.hostname not in {"yhdm.one", "www.yhdm.one"}:
        return []
    work = re.search(r"/vod(?:-detail|-play)?/(\d+)", parsed.path)
    seen = {}
    for href, _ in anchors:
        target = urljoin(source, str(href))
        target_uri = urlsplit(target)
        match = re.fullmatch(r"/vod-play/(\d+)/ep(\d+)\.html", target_uri.path)
        if target_uri.hostname != parsed.hostname or not match or target_uri.query:
            continue
        if work and work.group(1) != match.group(1):
            continue
        seen.setdefault(target, (match.group(1), int(match.group(2))))
    work_ids = {item[0] for item in seen.values()}
    if len(work_ids) > 1:
        raise ValueError("页面包含多个作品，请打开具体作品的合集页")
    return [(url, f"第{item[1]}集") for url, item in
            sorted(seen.items(), key=lambda pair: pair[1][1])]


def catalog_from_info(info, source, start, page_size):
    from .web_media import validate_url

    source_uri = validate_url(source)
    if info.get("_type") not in {"playlist", "multi_video"}:
        raise ValueError("该页面不是可确认的作品合集")
    collection_id = _identity(source_uri.hostname.lower(),
                              info.get("extractor_key") or info.get("extractor") or "generic",
                              info.get("id") or source)
    raw_entries = list(info.get("entries") or [])
    entries = []
    seen = set()
    for item in raw_entries[:page_size]:
        if not isinstance(item, dict) or item.get("_type") in {"playlist", "multi_video"}:
            continue
        # GenericIE uses the collection page as webpage_url for every
        # embedded <video>. In that case the direct media URL, not the shared
        # page, is the only member-owned source.
        url = ""
        for candidate in (item.get("webpage_url"), item.get("original_url"), item.get("url")):
            if not candidate or candidate == source:
                continue
            try:
                validate_url(candidate)
            except (ValueError, TypeError):
                continue
            url = candidate
            break
        if not url:
            continue
        media_id = collection_media_id(url)
        if media_id in seen:
            continue
        seen.add(media_id)
        entries.append(dict(id=media_id, title=str(item.get("title") or "未知标题"),
                            creator=str(item.get("uploader") or item.get("channel") or info.get("uploader") or source_uri.hostname),
                            creator_id=collection_id, webpage_url=url,
                            upload_date=str(item.get("upload_date") or ""),
                            thumbnail=str(item.get("thumbnail") or "")))
    return dict(creator=str(info.get("title") or info.get("uploader") or source_uri.hostname),
                creator_id=collection_id, entries=entries,
                duplicate_count=len(raw_entries[:page_size]) - len(entries), foreign_count=0,
                has_more=len(raw_entries) > page_size, next_start=start + page_size if len(raw_entries) > page_size else 0)


def extract_web_collection(url, start=1, page_size=50):
    from yt_dlp import YoutubeDL
    from .web_media import validate_url

    validate_url(url)
    if start < 1 or page_size < 1 or page_size > 100:
        raise ValueError("合集分页参数无效")
    with YoutubeDL(dict(quiet=True, no_warnings=True, skip_download=True,
                        extract_flat="in_playlist", playliststart=start,
                        playlistend=start + page_size, socket_timeout=20, retries=1)) as ydl:
        info = ydl.extract_info(url, download=False)
    if not isinstance(info, dict):
        raise ValueError("合集没有返回作品信息")
    result = catalog_from_info(info, url, start, page_size)
    if start == 1 and not result["entries"]:
        raise ValueError("合集没有可确认的独立作品链接")
    return json.dumps(result, ensure_ascii=False)
