from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

from .auth_profile import AUTH_COOKIE_MODE, export_auth_cookies_txt


ProgressCallback = Callable[[dict[str, Any]], None]
CancelCallback = Callable[[], bool]


class DownloadStopped(RuntimeError):
    """用户主动停止当前下载。"""


def raise_if_cancelled(cancel_callback: CancelCallback | None) -> None:
    if cancel_callback and cancel_callback():
        raise DownloadStopped("用户已停止下载。")


@dataclass(frozen=True)
class DownloadOptions:
    output_dir: Path
    quality: str
    cookie_mode: str
    cookie_file: Path | None
    ffmpeg_dir: Path | None
    creator_name: str | None = None


@dataclass(frozen=True)
class DownloadResult:
    files: list[Path]
    skipped: bool = False
    message: str = ""


def split_urls(text: str) -> list[str]:
    """从多行文本里提取链接，保留输入顺序并去重。"""
    stripped = text.strip()
    candidates = re.findall(r"https?://[^\s，。；;]+", stripped)
    for url in re.findall(r"(?<![\w./:-])(?:www\.)?(?:youtube\.com|youtu\.be)/[^\s，。；;]+", stripped, flags=re.IGNORECASE):
        clean = url
        if clean.lower().startswith("youtube.com/"):
            clean = f"www.{clean}"
        candidates.append(f"https://{clean}")
    candidates.extend(f"https://www.youtube.com/@{handle}" for handle in re.findall(r"(?<![\w./-])@([A-Za-z0-9._-]{3,60})", stripped))
    for line in stripped.splitlines():
        handle = line.strip().strip(" @")
        if re.fullmatch(r"[A-Za-z0-9._-]{3,60}", handle) and not re.fullmatch(r"\d{16,22}", handle):
            candidates.append(f"https://www.youtube.com/@{handle}")
    candidates.extend(f"https://www.douyin.com/video/{item_id}" for item_id in re.findall(r"(?<!\d)(\d{16,22})(?!\d)", text))
    seen: set[str] = set()
    urls: list[str] = []
    for url in candidates:
        clean_url = url.strip().rstrip(".,)")
        if clean_url and clean_url not in seen:
            seen.add(clean_url)
            urls.append(clean_url)
    return urls


def detect_platform(url: str) -> str:
    lower = url.lower()
    if "douyin.com" in lower:
        return "抖音"
    if "youtube.com" in lower or "youtu.be" in lower:
        return "YouTube"
    return "未知"


def find_ffmpeg_dir(project_root: Path) -> Path | None:
    """优先复用当前工具包里的 FFmpeg，找不到时交给系统 PATH。"""
    candidates = [
        project_root / "tools" / "ffmpeg",
        project_root.parent / "JHlib" / "ffmpeg",
        project_root / "JHlib" / "ffmpeg",
        Path("/opt/homebrew/bin"),
        Path("/usr/local/bin"),
        Path("/usr/bin"),
    ]
    ffmpeg_name = "ffmpeg.exe" if os.name == "nt" else "ffmpeg"
    ffprobe_name = "ffprobe.exe" if os.name == "nt" else "ffprobe"
    for candidate in candidates:
        if (candidate / ffmpeg_name).exists() and (candidate / ffprobe_name).exists():
            return candidate
    return None


def build_format_selector(quality: str) -> str:
    if quality == "仅音频 MP3":
        return "bestaudio/best"
    if quality == "1080p 及以下":
        return "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/bv*[height<=1080]+ba/b[height<=1080]/best"
    if quality == "720p 及以下":
        return "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]/best"
    return "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/best"


def safe_path_name(text: str | None, fallback: str = "未知作者") -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\r\n\t]+', " ", text or "").strip()
    cleaned = re.sub(r"\s+", " ", cleaned)
    if not cleaned:
        cleaned = fallback
    return cleaned[:80]


def build_ydl_options(
    options: DownloadOptions,
    progress_callback: ProgressCallback,
    cancel_callback: CancelCallback | None = None,
) -> dict[str, Any]:
    def checked_progress(info: dict[str, Any]) -> None:
        raise_if_cancelled(cancel_callback)
        progress_callback(info)
        raise_if_cancelled(cancel_callback)

    creator_dir = safe_path_name(options.creator_name) if options.creator_name else "%(uploader|未知作者).80B"
    output_template = str(options.output_dir / "%(extractor_key)s" / creator_dir / "%(upload_date)s %(title).120B.%(ext)s")
    ydl_options: dict[str, Any] = {
        "outtmpl": output_template,
        "format": build_format_selector(options.quality),
        "merge_output_format": "mp4",
        "noplaylist": False,
        "ignoreerrors": False,
        "continuedl": True,
        "retries": 3,
        "fragment_retries": 3,
        "windowsfilenames": True,
        "progress_hooks": [checked_progress],
        "quiet": True,
        "no_warnings": False,
    }

    if options.ffmpeg_dir:
        ydl_options["ffmpeg_location"] = str(options.ffmpeg_dir)

    if options.quality == "仅音频 MP3":
        ydl_options["postprocessors"] = [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            }
        ]
    else:
        ydl_options["postprocessors"] = [
            {
                "key": "FFmpegVideoConvertor",
                "preferedformat": "mp4",
            }
        ]

    if options.cookie_mode == AUTH_COOKIE_MODE:
        ydl_options["cookiefile"] = str(export_auth_cookies_txt())
    elif options.cookie_mode in {"Chrome", "Edge", "Firefox"}:
        ydl_options["cookiesfrombrowser"] = (options.cookie_mode.lower(),)
    elif options.cookie_mode == "cookies.txt" and options.cookie_file:
        ydl_options["cookiefile"] = str(options.cookie_file)

    return ydl_options


def _friendly_cookie_error(exc: Exception) -> RuntimeError | None:
    detail = str(exc)
    if "Could not copy Chrome cookie database" not in detail:
        return None
    return RuntimeError(
        "无法读取 Chrome 登录态：Chrome 正在占用 Cookie 文件。\n\n"
        "处理方式：\n"
        "1. 完全关闭 Chrome，包括右下角托盘里的后台 Chrome。\n"
        "2. 回到本软件，保持登录态为 Chrome，再重新操作。\n"
        "3. 如果仍失败，请用浏览器扩展导出 Netscape 格式 cookies.txt，"
        "然后在本软件里把登录态改成 cookies.txt。"
    )


def _clean_title_stem(stem: str) -> str:
    """统一清理下载后的文件名，去掉平台状态词和多余标点。"""
    cleaned = stem
    cleaned = re.sub(r"[“”\"'‘’]", "", cleaned)
    cleaned = re.sub(r"\s*(正在直播|直播中|正在首播|Premiere|LIVE)\s*[！!]*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*\[[A-Za-z0-9_-]{6,24}\]\s*$", "", cleaned)
    cleaned = re.sub(r"^(?P<date>\d{4})(?P<month>\d{2})(?P<day>\d{2})\s+", r"\g<date>-\g<month>-\g<day> ", cleaned)
    cleaned = re.sub(r"^(NA|N/A|None|null)\s+", "未知日期 ", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" .-_")
    return cleaned or stem


def _unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    for index in range(1, 1000):
        candidate = path.with_name(f"{path.stem} ({index}){path.suffix}")
        if not candidate.exists():
            return candidate
    raise RuntimeError(f"无法生成不重名文件名：{path}")


def _normalize_downloaded_files(files: list[Path]) -> list[Path]:
    normalized: list[Path] = []
    for file_path in files:
        if not file_path.exists():
            continue
        new_stem = _clean_title_stem(file_path.stem)
        new_path = file_path.with_name(f"{new_stem}{file_path.suffix.lower()}")
        if new_path != file_path:
            new_path = _unique_path(new_path)
            file_path.rename(new_path)
            file_path = new_path
        normalized.append(file_path)
    return normalized


def _snapshot_files(directory: Path) -> dict[Path, tuple[int, int]]:
    if not directory.exists():
        return {}
    snapshot: dict[Path, tuple[int, int]] = {}
    for file_path in directory.rglob("*"):
        if not file_path.is_file():
            continue
        if file_path.suffix.lower() in {".part", ".ytdl", ".tmp", ".temp"}:
            continue
        stat = file_path.stat()
        snapshot[file_path] = (stat.st_size, stat.st_mtime_ns)
    return snapshot


def _changed_files(before: dict[Path, tuple[int, int]], after: dict[Path, tuple[int, int]]) -> list[Path]:
    changed: list[Path] = []
    for file_path, signature in after.items():
        if before.get(file_path) != signature:
            changed.append(file_path)
    return sorted(changed, key=lambda item: item.stat().st_mtime_ns, reverse=True)


def download_url(
    url: str,
    options: DownloadOptions,
    progress_callback: ProgressCallback,
    cancel_callback: CancelCallback | None = None,
) -> DownloadResult:
    """执行单个链接下载。

    这里延迟导入 yt_dlp，方便界面启动时给出清晰的依赖缺失提示。
    """
    from yt_dlp import YoutubeDL

    from .douyin import download_douyin_url, is_douyin_url

    raise_if_cancelled(cancel_callback)

    if is_douyin_url(url):
        return download_douyin_url(url, options, progress_callback, cancel_callback)

    options.output_dir.mkdir(parents=True, exist_ok=True)
    before = _snapshot_files(options.output_dir)

    ydl_options = build_ydl_options(options, progress_callback, cancel_callback)
    with YoutubeDL(ydl_options) as ydl:
        try:
            raise_if_cancelled(cancel_callback)
            result_code = ydl.download([url])
            raise_if_cancelled(cancel_callback)
        except Exception as exc:
            if isinstance(exc, DownloadStopped):
                raise
            friendly_error = _friendly_cookie_error(exc)
            if friendly_error:
                raise friendly_error from exc
            raise

    after = _snapshot_files(options.output_dir)
    changed = _changed_files(before, after)
    if result_code not in (None, 0):
        raise RuntimeError(f"yt-dlp 返回失败状态：{result_code}")
    if not changed:
        if before:
            return DownloadResult(files=[], skipped=True, message="保存目录中已存在对应文件，已跳过下载。")
        raise RuntimeError("下载流程结束，但保存目录里没有新增文件。可能是链接解析失败、平台限制，或需要选择浏览器登录态。")
    return DownloadResult(files=_normalize_downloaded_files(changed))
