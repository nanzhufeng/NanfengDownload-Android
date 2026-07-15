# 智能读取隐藏浏览器与性能优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让抖音智能读取在保留完整作品信息的前提下不显示后台浏览器窗口，并在结果齐全后立即结束等待。

**Architecture:** 在 `app/douyin.py` 中抽出两个纯函数：后台浏览器启动参数和媒体捕获完成判定。单作品嗅探继续监听抖音页面响应，但用 250ms 轮询替换固定 12 秒等待；当视频或图文媒体、标题、博主和发布日期同时存在时立即关闭浏览器。作者列表保持 API 优先和作者过滤，只同步采用隐藏浏览器启动参数。

**Tech Stack:** Python 3.13、unittest、Playwright、PySide6、现有 BAT 启动脚本。

## Global Constraints

- 只支持公开内容和登录后账号有权限访问的内容；不新增会员、付费、DRM 或私密内容绕过。
- 单条作品链接仍只读取当前作品；只有作者主页、频道或播放列表才读取批量列表。
- 登录抖音和登录 YouTube 的可见浏览器行为不改。
- 先验证 BAT/源码版；本计划不生成 EXE。
- 不改变现有工作台布局、下载命名、队列状态或作者过滤规则。

---

### Task 1: 为后台浏览器决策建立回归测试

**Files:**

- Create: `tests/__init__.py`
- Create: `tests/test_douyin_browser_capture.py`
- Modify: `app/douyin.py:492-589,757-896`

**Interfaces:**

- Consumes: `os.name` 与抖音浏览器嗅探中已有的 `video_urls`、`image_urls`、`page_title`、`publish_date`、`author_name`。
- Produces: `_background_browser_args(platform_name: str | None = None) -> list[str]` 和 `_capture_is_ready(video_urls: list[str], image_urls: list[str], title: str, publish_date: str | None, author_name: str | None, fallback_title: str) -> bool`。

- [ ] **Step 1: 写入失败测试**

```python
import unittest

from app.douyin import _background_browser_args, _capture_is_ready


class BackgroundBrowserArgsTests(unittest.TestCase):
    def test_windows_args_hide_background_browser_window(self) -> None:
        args = _background_browser_args("nt")
        self.assertIn("--headless=new", args)
        self.assertIn("--no-startup-window", args)
        self.assertIn("--window-position=-32000,-32000", args)

    def test_non_windows_args_keep_only_cross_platform_browser_flags(self) -> None:
        args = _background_browser_args("posix")
        self.assertIn("--disable-blink-features=AutomationControlled", args)
        self.assertNotIn("--window-position=-32000,-32000", args)


class CaptureReadyTests(unittest.TestCase):
    def test_ready_when_video_and_metadata_are_available(self) -> None:
        self.assertTrue(
            _capture_is_ready(
                ["https://cdn.example/video.mp4"],
                [],
                "作品标题",
                "2026-07-15",
                "博主",
                "抖音视频 1",
            )
        )

    def test_not_ready_when_metadata_is_incomplete(self) -> None:
        self.assertFalse(
            _capture_is_ready(
                ["https://cdn.example/video.mp4"],
                [],
                "作品标题",
                None,
                "博主",
                "抖音视频 1",
            )
        )

    def test_ready_for_image_post_when_metadata_is_available(self) -> None:
        self.assertTrue(
            _capture_is_ready(
                [],
                ["https://cdn.example/image.jpg"],
                "图文标题",
                "2026-07-15",
                "博主",
                "抖音视频 1",
            )
        )


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: 运行测试，确认因接口尚不存在而失败**

Run: `python -m unittest tests.test_douyin_browser_capture -v`

Expected: FAIL，提示无法从 `app.douyin` 导入 `_background_browser_args` 和 `_capture_is_ready`。

- [ ] **Step 3: 写入最小实现**

在 `app/douyin.py` 的浏览器辅助函数区域加入：

```python
def _background_browser_args(platform_name: str | None = None) -> list[str]:
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
    has_media = bool(video_urls or image_urls)
    has_title = bool(title.strip()) and title.strip() != fallback_title
    return has_media and has_title and bool(publish_date) and bool(author_name)
```

并把 `_discover_douyin_with_browser()` 和 `_sniff_douyin_with_browser()` 的 `args=["--disable-blink-features=AutomationControlled"]` 替换为 `args=_background_browser_args()`。

- [ ] **Step 4: 运行测试，确认通过**

Run: `python -m unittest tests.test_douyin_browser_capture -v`

Expected: PASS，5 个测试全部通过。

- [ ] **Step 5: 记录版本控制状态**

Run: `git status --short`

Expected: 当前目录不是 Git 仓库时记录该事实；不初始化仓库、不创建提交。

### Task 2: 以动态轮询替换单作品固定等待

**Files:**

- Modify: `app/douyin.py:837-868`
- Test: `tests/test_douyin_browser_capture.py`

**Interfaces:**

- Consumes: Task 1 的 `_capture_is_ready()`；`on_response()` 已填充的媒体地址、标题、博主和发布日期。
- Produces: `_sniff_douyin_with_browser()` 在信息齐全后提前返回 `DouyinInfo`，仍保留 12 秒最大等待上限。

- [ ] **Step 1: 补充失败测试，锁定最大等待与提前完成条件**

在 `tests/test_douyin_browser_capture.py` 中追加：

```python
from app.douyin import _capture_wait_rounds


class CapturePollingTests(unittest.TestCase):
    def test_wait_rounds_use_250ms_polling_with_a_12_second_upper_bound(self) -> None:
        self.assertEqual(_capture_wait_rounds(timeout_ms=12_000, poll_ms=250), 48)

    def test_wait_rounds_never_returns_zero(self) -> None:
        self.assertEqual(_capture_wait_rounds(timeout_ms=100, poll_ms=250), 1)
```

- [ ] **Step 2: 运行新增测试，确认因辅助函数不存在而失败**

Run: `python -m unittest tests.test_douyin_browser_capture.CapturePollingTests -v`

Expected: FAIL，提示无法导入 `_capture_wait_rounds`。

- [ ] **Step 3: 写入最小实现并替换固定等待**

在 `app/douyin.py` 加入：

```python
def _capture_wait_rounds(timeout_ms: int, poll_ms: int) -> int:
    return max(1, (timeout_ms + poll_ms - 1) // poll_ms)
```

将 `_sniff_douyin_with_browser()` 中：

```python
page.wait_for_timeout(12_000)
```

替换为：

```python
for _ in range(_capture_wait_rounds(timeout_ms=12_000, poll_ms=250)):
    if _capture_is_ready(
        video_urls,
        image_urls,
        page_title,
        publish_date,
        author_name,
        f"抖音视频 {aweme_id}",
    ):
        break
    page.wait_for_timeout(250)
```

- [ ] **Step 4: 运行完整回归测试**

Run: `python -m unittest tests.test_douyin_browser_capture -v`

Expected: PASS，7 个测试全部通过。

- [ ] **Step 5: 进行真实链接计时验证**

Run:

```powershell
@'
import time
from pathlib import Path
from app.auth_profile import AUTH_COOKIE_MODE
from app.downloader import DownloadOptions
from app.douyin import _fetch_douyin_info

started = time.perf_counter()
info = _fetch_douyin_info(
    "https://v.douyin.com/uS14j-Frr6g/",
    DownloadOptions(Path("."), "720p 及以下", AUTH_COOKIE_MODE, None, None),
)
print(round(time.perf_counter() - started, 2), info.title, info.author_name, info.publish_date)
'@ | python -
```

Expected: 返回非空标题、博主和发布日期；总耗时显著低于修改前约 15 秒基线，网络波动时允许略有差异。

### Task 3: 让作者列表按新增作品动态等待

**Files:**

- Modify: `app/douyin.py:556-584`
- Test: `tests/test_douyin_browser_capture.py`

**Interfaces:**

- Consumes: Playwright 页面对象和作者列表 `catalog`。
- Produces: `_wait_for_catalog_growth(page: Any, catalog: list[dict[str, str | None]], previous_count: int, timeout_ms: int, poll_ms: int = 250) -> bool`，在收到新增作品后立即返回。

- [ ] **Step 1: 补充失败测试，锁定新增作品时提前结束**

在 `tests/test_douyin_browser_capture.py` 中追加：

```python
from app.douyin import _wait_for_catalog_growth


class _FakePage:
    def __init__(self, callback) -> None:
        self.callback = callback
        self.wait_calls = 0

    def wait_for_timeout(self, milliseconds: int) -> None:
        self.wait_calls += 1
        self.callback(self.wait_calls)


class CatalogGrowthWaitTests(unittest.TestCase):
    def test_returns_as_soon_as_catalog_grows(self) -> None:
        catalog = []
        page = _FakePage(lambda calls: catalog.append({"url": "https://example.com/1"}) if calls == 2 else None)

        changed = _wait_for_catalog_growth(page, catalog, previous_count=0, timeout_ms=2_000)

        self.assertTrue(changed)
        self.assertEqual(page.wait_calls, 2)

    def test_returns_false_after_timeout_without_catalog_growth(self) -> None:
        catalog = []
        page = _FakePage(lambda calls: None)

        changed = _wait_for_catalog_growth(page, catalog, previous_count=0, timeout_ms=500)

        self.assertFalse(changed)
        self.assertEqual(page.wait_calls, 2)
```

- [ ] **Step 2: 运行新增测试，确认因辅助函数不存在而失败**

Run: `python -m unittest tests.test_douyin_browser_capture.CatalogGrowthWaitTests -v`

Expected: FAIL，提示无法导入 `_wait_for_catalog_growth`。

- [ ] **Step 3: 写入最小实现并替换作者列表固定等待**

在 `app/douyin.py` 加入：

```python
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
```

将 `_discover_douyin_with_browser()` 中：

```python
page.wait_for_timeout(3_000)
```

替换为：

```python
_wait_for_catalog_growth(page, catalog, previous_count=0, timeout_ms=3_000)
```

将每次滚动后的：

```python
page.wait_for_timeout(1_200)
if len(catalog) == last_count:
```

替换为：

```python
grew = _wait_for_catalog_growth(page, catalog, last_count, timeout_ms=1_500)
if not grew:
```

并把连续空转上限从 `6` 轮改为 `4` 轮。

- [ ] **Step 4: 运行完整回归测试**

Run: `python -m unittest tests.test_douyin_browser_capture -v`

Expected: PASS，9 个测试全部通过。

### Task 4: 验证 BAT/源码版实际交互

**Files:**

- Modify: 无
- Test: `启动南烛枫视频下载器.bat`

**Interfaces:**

- Consumes: Task 1 和 Task 2 的后台浏览器参数及动态等待。
- Produces: 可供用户确认的 BAT/源码版体验，不生成 EXE。

- [ ] **Step 1: 启动 BAT/源码版**

Run: `启动南烛枫视频下载器.bat`

Expected: 主工作台正常打开，无黑色控制台窗口影响使用。

- [ ] **Step 2: 手动验证智能读取**

在链接框粘贴：

```text
https://v.douyin.com/uS14j-Frr6g/
```

点击“智能读取”。

Expected: 不出现白框或可见后台浏览器；队列只加入一条抖音作品；标题、博主和发布日期完整。

- [ ] **Step 3: 记录验证边界**

Expected: 记录真实链接结果、计时和是否观察到白框；若平台临时风控或网络异常，标为待验证风险，不修改下载范围规则。
