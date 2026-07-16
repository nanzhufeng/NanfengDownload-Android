# Android TikTok 下载支持 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 可行性验证工程中支持 TikTok 公开单视频下载和作者主页作品列表读取，并在 OPPO Find N5 上验证真实媒体下载与保存。

**Architecture:** 扩展现有 URL 分类器识别 TikTok 单视频、作者主页和短链接；把 Chaquopy 中的 `yt-dlp` 桥接改为平台中性的单视频解析器，并增加轻量作者目录接口。作者目录只返回元数据，用户选择后才进入现有 Kotlin 断点续传、Media3 合并、媒体校验和 MediaStore 保存链路。

**Tech Stack:** Kotlin 2.0、Jetpack Compose、Chaquopy Python 3.13、yt-dlp 2026.6.9、Kotlin Coroutines、Media3 1.8.1、JUnit 4、Python unittest、Android MediaStore。

## Implementation Result

- 已完成 TikTok 单视频、作者主页和短链接分类。
- 已完成平台中性单视频解析、作者隔离、作品去重、每批 50 条分页和“加载更多”。
- 已修复 TikTok 媒体直链缺少 yt-dlp 会话 Cookie 导致的 HTTP 403。
- Find N5 已真实通过公开单视频解析、下载、媒体校验和 MediaStore 写入测试。
- Find N5 已真实通过两批 5 条作者目录合并与作者隔离；连续目录请求存在明显限流，50 条真机规模仍列为待验证风险。
- 详细证据见 `docs/verification/android-find-n5-feasibility-result.md`。

## Global Constraints

- 工作目录固定为 `D:\CodexProjects\CleanVideoDownloader-AndroidProbe`，分支固定为 `feature/android-find-n5-probe`。
- 只修改 `android/` 验证工程和对应验证文档，不修改 Windows 桌面版行为与打包链路。
- TikTok 首版支持公开单视频和作者主页公开作品，不支持登录态、私密、好友可见、付费或区域限制绕过。
- 单视频链接不能展开作者主页；作者主页不能直接进入单视频下载。
- 作者列表不设置人为 500 条上限，按作品 ID 去重，并拒绝可识别为其他作者的条目。
- 下载结果存在、非空且通过媒体容器校验后才能显示完成。
- 当前工作树已有未提交的 YouTube、抖音验证改动；每次提交只暂存当前任务列出的文件，不得使用 `git add .`。
- Windows 中文主路径会影响 Android 单元测试类路径；所有 Gradle 验证从当前 ASCII 工作树执行。
- Find N5 设备序列号为 `3B157F009E800000`，真机验证不得清空应用数据。

---

## File Structure

- `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeModels.kt`：平台、来源类型和通用探测结果模型。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/UrlClassifier.kt`：纯本地 URL 提取与意图分类。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YtDlpProbe.kt`：Chaquopy 平台中性桥接、单视频和作者目录模型。
- `android/app/src/main/python/nanzhufeng_probe/youtube_probe.py`：现有 yt-dlp 模块，扩展为平台中性的单视频与作者目录解析。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeViewModel.kt`：诊断操作编排、选择状态和下载入口约束。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeScreen.kt`：TikTok 诊断按钮和目录摘要展示。
- `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/UrlClassifierTest.kt`：TikTok URL 分类回归。
- `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/CreatorCatalogTest.kt`：作者隔离、去重和选择过滤回归。
- `android/app/src/test/python/test_youtube_probe.py`：流选择与平台中性单视频解析辅助测试。
- `android/app/src/test/python/test_tiktok_catalog.py`：TikTok 作者目录纯函数测试。
- `docs/verification/android-find-n5-feasibility-result.md`：自动测试、模拟器和 Find N5 真实验证证据。

---

### Task 1: TikTok URL 分类

**Files:**
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeModels.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/UrlClassifier.kt`
- Modify: `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/UrlClassifierTest.kt`

**Interfaces:**
- Consumes: `UrlClassifier.extractAndClassify(text: String): ClassifiedSource`
- Produces: `Platform.TIKTOK`、`SourceKind.UNKNOWN_TIKTOK_SHARE` 和稳定的 TikTok 单视频/作者主页分类。

- [ ] **Step 1: 添加失败测试**

在 `UrlClassifierTest.kt` 增加：

```kotlin
@Test
fun tiktokVideoIsSingleVideo() {
    val source = UrlClassifier.extractAndClassify(
        "https://www.tiktok.com/@creator/video/7512345678901234567",
    )
    assertEquals(Platform.TIKTOK, source.platform)
    assertEquals(SourceKind.SINGLE_VIDEO, source.kind)
}

@Test
fun tiktokCreatorIsCatalog() {
    val source = UrlClassifier.extractAndClassify("https://www.tiktok.com/@creator")
    assertEquals(Platform.TIKTOK, source.platform)
    assertEquals(SourceKind.CHANNEL_OR_PLAYLIST, source.kind)
}

@Test
fun tiktokShortLinkDefersNetworkClassification() {
    val source = UrlClassifier.extractAndClassify("https://vt.tiktok.com/ZSMock123/")
    assertEquals(Platform.TIKTOK, source.platform)
    assertEquals(SourceKind.UNKNOWN_TIKTOK_SHARE, source.kind)
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.UrlClassifierTest"
```

Expected: FAIL，原因是 `Platform.TIKTOK` 和 `UNKNOWN_TIKTOK_SHARE` 尚不存在。

- [ ] **Step 3: 最小实现分类规则**

将模型扩展为：

```kotlin
enum class Platform {
    YOUTUBE,
    DOUYIN,
    TIKTOK,
}

enum class SourceKind {
    SINGLE_VIDEO,
    CHANNEL_OR_PLAYLIST,
    UNKNOWN_DOUYIN_SHARE,
    UNKNOWN_TIKTOK_SHARE,
}
```

在 `UrlClassifier` 中把 TikTok 规则放在兜底错误之前：

```kotlin
"tiktok.com/@" in lower && "/video/" in lower ->
    ClassifiedSource(Platform.TIKTOK, SourceKind.SINGLE_VIDEO, raw)

Regex("https?://(?:www\\.)?tiktok\\.com/@[^/?#]+/?(?:[?#].*)?$").matches(lower) ->
    ClassifiedSource(Platform.TIKTOK, SourceKind.CHANNEL_OR_PLAYLIST, raw)

"vm.tiktok.com/" in lower || "vt.tiktok.com/" in lower ->
    ClassifiedSource(Platform.TIKTOK, SourceKind.UNKNOWN_TIKTOK_SHARE, raw)
```

同时把错误文案改成“只支持抖音、YouTube 和 TikTok 链接”，保留现有 YouTube、抖音规则顺序。

- [ ] **Step 4: 运行分类回归**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.UrlClassifierTest"
```

Expected: PASS，现有 YouTube、抖音和新增 TikTok 用例全部通过。

- [ ] **Step 5: 单独提交**

```powershell
git add -- android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeModels.kt android/app/src/main/java/com/nanzhufeng/videodownloader/probe/UrlClassifier.kt android/app/src/test/java/com/nanzhufeng/videodownloader/probe/UrlClassifierTest.kt
git commit -m "feat(android): 识别 TikTok 单视频与作者链接"
```

---

### Task 2: 平台中性 yt-dlp 单视频解析

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YtDlpProbe.kt`
- Delete: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YoutubeProbe.kt`
- Modify: `android/app/src/main/python/nanzhufeng_probe/youtube_probe.py`
- Modify: `android/app/src/test/python/test_youtube_probe.py`

**Interfaces:**
- Consumes: `extract_single(url: str) -> str` JSON。
- Produces: `YtDlpProbe.extractSingle(url: String): YtDlpMediaInfo` 和 `resolveSource(url: String): ResolvedSource`，供 YouTube 与 TikTok 共用。

- [ ] **Step 1: 添加平台中性媒体结果测试**

在 Python 模块中先提取纯函数 `_media_result(info)`，把测试导入改为：

```python
from nanzhufeng_probe.youtube_probe import (
    _media_result,
    _select_audio,
    _select_streams,
    _source_result,
)
```

并在测试类中增加：

```python
def test_progressive_tiktok_mp4_becomes_single_media_result(self):
    info = {
        "id": "7512345678901234567",
        "title": "TikTok sample",
        "uploader": "creator",
        "uploader_id": "creator",
        "webpage_url": "https://www.tiktok.com/@creator/video/7512345678901234567",
        "extractor_key": "TikTok",
        "formats": [{
            "url": "https://example.com/video.mp4",
            "ext": "mp4",
            "vcodec": "h264",
            "acodec": "aac",
            "height": 720,
        }],
        "http_headers": {"Referer": "https://www.tiktok.com/"},
    }

    result = _media_result(info)

    self.assertEqual("tiktok", result["platform"])
    self.assertEqual("creator", result["creator_id"])
    self.assertEqual(info["webpage_url"], result["webpage_url"])
    self.assertEqual("https://example.com/video.mp4", result["video_url"])
    self.assertEqual("", result["audio_url"])

def test_tiktok_playlist_is_resolved_as_creator(self):
    result = _source_result({
        "_type": "playlist",
        "webpage_url": "https://www.tiktok.com/@creator",
        "entries": [{"id": "1"}],
    })

    self.assertEqual("creator", result["kind"])
    self.assertEqual("https://www.tiktok.com/@creator", result["url"])
```

- [ ] **Step 2: 运行 Python 测试并确认失败**

Run:

```powershell
$env:PYTHONPATH='android/app/src/main/python'
& 'C:\Users\Administrator\AppData\Local\Programs\Python\Python313\python.exe' android/app/src/test/python/test_youtube_probe.py -v
```

Expected: FAIL，原因是 `_media_result` 尚不存在。

- [ ] **Step 3: 实现平台中性结果**

在 Python 模块增加：

```python
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
    is_creator = info.get("_type") in {"playlist", "multi_video"} or bool(info.get("entries"))
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
```

让 `extract_single` 只负责调用 yt-dlp，并返回 `json.dumps(_media_result(info), ensure_ascii=False)`。

- [ ] **Step 4: 建立 Kotlin 平台中性桥接**

`YtDlpProbe.kt` 定义以下导入和模型：

```kotlin
package com.nanzhufeng.videodownloader.probe

import com.chaquo.python.Python
import org.json.JSONObject

data class RuntimeInfo(
    val python: String,
    val ytDlp: String,
)

data class YtDlpMediaInfo(
    val platform: String,
    val id: String,
    val title: String,
    val creator: String,
    val creatorId: String,
    val webpageUrl: String,
    val uploadDate: String,
    val thumbnail: String,
    val videoUrl: String,
    val audioUrl: String?,
    val videoExt: String,
    val audioExt: String?,
    val headers: Map<String, String>,
)

data class ResolvedSource(
    val kind: SourceKind,
    val url: String,
)

class YtDlpProbe {
    private val module by lazy {
        Python.getInstance().getModule("nanzhufeng_probe.youtube_probe")
    }

    fun runtimeInfo(): RuntimeInfo {
        val json = JSONObject(module.callAttr("runtime_info").toString())
        return RuntimeInfo(
            python = json.getString("python"),
            ytDlp = json.getString("yt_dlp"),
        )
    }

    fun resolveSource(url: String): ResolvedSource {
        val json = JSONObject(module.callAttr("resolve_source", url).toString())
        val kind = when (json.getString("kind")) {
            "single" -> SourceKind.SINGLE_VIDEO
            "creator" -> SourceKind.CHANNEL_OR_PLAYLIST
            else -> error("yt-dlp 返回了未知来源类型")
        }
        return ResolvedSource(kind = kind, url = json.getString("url"))
    }

    fun extractSingle(url: String): YtDlpMediaInfo {
        val json = JSONObject(module.callAttr("extract_single", url).toString())
        val headersJson = json.getJSONObject("headers")
        return YtDlpMediaInfo(
            platform = json.getString("platform"),
            id = json.getString("id"),
            title = json.getString("title"),
            creator = json.getString("creator"),
            creatorId = json.getString("creator_id"),
            webpageUrl = json.getString("webpage_url"),
            uploadDate = json.getString("upload_date"),
            thumbnail = json.getString("thumbnail"),
            videoUrl = json.getString("video_url"),
            audioUrl = json.getString("audio_url").ifBlank { null },
            videoExt = json.getString("video_ext"),
            audioExt = json.getString("audio_ext").ifBlank { null },
            headers = headersJson.keys().asSequence().associateWith(headersJson::getString),
        )
    }
}
```

删除旧类后修正所有引用，确保工程中只存在一个 yt-dlp 运行时桥接所有者。

- [ ] **Step 5: 运行 Python 与 Android 编译验证**

Run:

```powershell
$env:PYTHONPATH='android/app/src/main/python'
& 'C:\Users\Administrator\AppData\Local\Programs\Python\Python313\python.exe' -m unittest discover -s android/app/src/test/python -v
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\android\gradlew.bat -p .\android :app:testDebugUnitTest :app:assembleDebug
```

Expected: Python 测试 PASS，Kotlin 单元测试 PASS，APK 编译成功。

- [ ] **Step 6: 单独提交**

```powershell
git add -- android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YtDlpProbe.kt android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YoutubeProbe.kt android/app/src/main/python/nanzhufeng_probe/youtube_probe.py android/app/src/test/python/test_youtube_probe.py
git commit -m "refactor(android): 统一 YouTube 与 TikTok 单视频解析"
```

---

### Task 3: TikTok 作者目录与作者隔离

**Files:**
- Modify: `android/app/src/main/python/nanzhufeng_probe/youtube_probe.py`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YtDlpProbe.kt`
- Create: `android/app/src/test/python/test_tiktok_catalog.py`
- Create: `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/CreatorCatalogTest.kt`

**Interfaces:**
- Consumes: `extract_creator(url: str) -> str` JSON。
- Produces: `YtDlpProbe.extractCreator(url: String): CreatorCatalog`、`CreatorCatalog.selectedEntries(): List<CreatorVideoEntry>`。

- [ ] **Step 1: 添加 Python 作者隔离失败测试**

`test_tiktok_catalog.py` 写入：

```python
import unittest

from nanzhufeng_probe.youtube_probe import _creator_result


class CreatorResultTest(unittest.TestCase):
    def test_deduplicates_and_rejects_foreign_creator(self):
        info = {
            "uploader": "target",
            "uploader_id": "target",
            "webpage_url": "https://www.tiktok.com/@target",
            "entries": [
                {"id": "1", "title": "first", "uploader_id": "target", "url": "https://www.tiktok.com/@target/video/1"},
                {"id": "1", "title": "duplicate", "uploader_id": "target", "url": "https://www.tiktok.com/@target/video/1"},
                {"id": "2", "title": "foreign", "uploader_id": "other", "url": "https://www.tiktok.com/@other/video/2"},
                {"id": "3", "title": "inherited", "url": "https://www.tiktok.com/@target/video/3"},
            ],
        }

        result = _creator_result(info, "target")

        self.assertEqual(["1", "3"], [entry["id"] for entry in result["entries"]])
        self.assertEqual(1, result["duplicate_count"])
        self.assertEqual(1, result["foreign_count"])
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:PYTHONPATH='android/app/src/main/python'
& 'C:\Users\Administrator\AppData\Local\Programs\Python\Python313\python.exe' android/app/src/test/python/test_tiktok_catalog.py -v
```

Expected: FAIL，原因是 `_creator_result` 尚不存在。

- [ ] **Step 3: 实现目录纯函数与网络入口**

在 Python 模块增加：

```python
def _normalized_handle(value):
    return str(value or "").strip().lstrip("@").lower()


def _creator_result(info, expected_handle):
    root_creator = str(info.get("uploader") or info.get("channel") or expected_handle or "未知作者")
    root_id = _normalized_handle(
        info.get("uploader_id") or info.get("channel_id") or expected_handle
    )
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
        item_creator = _normalized_handle(item.get("uploader_id") or item.get("channel_id"))
        item_url = str(item.get("webpage_url") or item.get("url") or "")
        if item_creator and root_id and item_creator != root_id:
            foreign_count += 1
            continue
        if root_id and item_creator == "" and f"/@{root_id}/" not in item_url.lower():
            foreign_count += 1
            continue
        seen.add(video_id)
        entries.append({
            "id": video_id,
            "title": str(item.get("title") or "未知标题"),
            "creator": str(item.get("uploader") or item.get("channel") or root_creator),
            "creator_id": item_creator or root_id,
            "webpage_url": item_url,
            "upload_date": str(item.get("upload_date") or ""),
            "thumbnail": str(item.get("thumbnail") or ""),
        })
    return {
        "creator": root_creator,
        "creator_id": root_id,
        "entries": entries,
        "duplicate_count": duplicate_count,
        "foreign_count": foreign_count,
    }


def extract_creator(url: str) -> str:
    handle = url.split("/@", 1)[1].split("/", 1)[0].split("?", 1)[0]
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
    return json.dumps(_creator_result(info, handle), ensure_ascii=False)
```

实现时不得设置 `playlistend=500` 或同类人为上限。

- [ ] **Step 4: 添加 Kotlin 目录模型和选择过滤**

在 `YtDlpProbe.kt` 增加：

```kotlin
data class CreatorVideoEntry(
    val id: String,
    val title: String,
    val creator: String,
    val creatorId: String,
    val webpageUrl: String,
    val uploadDate: String,
    val thumbnail: String,
    val selected: Boolean = true,
)

data class CreatorCatalog(
    val creator: String,
    val creatorId: String,
    val entries: List<CreatorVideoEntry>,
    val duplicateCount: Int,
    val foreignCount: Int,
) {
    fun selectedEntries(): List<CreatorVideoEntry> = entries.filter(CreatorVideoEntry::selected)
}
```

`extractCreator` 使用 `JSONArray` 逐项转换，并拒绝空 `webpageUrl`：

```kotlin
fun extractCreator(url: String): CreatorCatalog {
    val json = JSONObject(module.callAttr("extract_creator", url).toString())
    val items = json.getJSONArray("entries")
    val entries = buildList {
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val webpageUrl = item.getString("webpage_url")
            if (webpageUrl.isBlank()) continue
            add(
                CreatorVideoEntry(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    creator = item.getString("creator"),
                    creatorId = item.getString("creator_id"),
                    webpageUrl = webpageUrl,
                    uploadDate = item.getString("upload_date"),
                    thumbnail = item.getString("thumbnail"),
                ),
            )
        }
    }
    return CreatorCatalog(
        creator = json.getString("creator"),
        creatorId = json.getString("creator_id"),
        entries = entries,
        duplicateCount = json.getInt("duplicate_count"),
        foreignCount = json.getInt("foreign_count"),
    )
}
```

- [ ] **Step 5: 添加 Kotlin 选择状态测试**

`CreatorCatalogTest.kt` 验证取消选择后不会进入下载集合：

```kotlin
@Test
fun selectedEntriesExcludeUncheckedItems() {
    val selected = CreatorVideoEntry("1", "one", "target", "target", "https://www.tiktok.com/@target/video/1", "", "")
    val unchecked = CreatorVideoEntry("2", "two", "target", "target", "https://www.tiktok.com/@target/video/2", "", "", selected = false)
    val catalog = CreatorCatalog("target", "target", listOf(selected, unchecked), 0, 0)

    assertEquals(listOf("1"), catalog.selectedEntries().map(CreatorVideoEntry::id))
}
```

- [ ] **Step 6: 运行目录测试**

Run:

```powershell
$env:PYTHONPATH='android/app/src/main/python'
& 'C:\Users\Administrator\AppData\Local\Programs\Python\Python313\python.exe' -m unittest discover -s android/app/src/test/python -v
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.CreatorCatalogTest"
```

Expected: Python 与 Kotlin 目录测试全部 PASS。

- [ ] **Step 7: 单独提交**

```powershell
git add -- android/app/src/main/python/nanzhufeng_probe/youtube_probe.py android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YtDlpProbe.kt android/app/src/test/python/test_tiktok_catalog.py android/app/src/test/java/com/nanzhufeng/videodownloader/probe/CreatorCatalogTest.kt
git commit -m "feat(android): 读取 TikTok 作者公开作品"
```

---

### Task 4: 诊断页接入 TikTok 单视频与作者目录

**Files:**
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeViewModel.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeScreen.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/MainActivity.kt`

**Interfaces:**
- Consumes: `YtDlpProbe.extractSingle`、`YtDlpProbe.extractCreator`、`HttpFileDownloader.download`、`Media3MuxProbe.merge`。
- Produces: TikTok 单视频解析/下载操作和作者目录摘要；现有 YouTube、抖音按钮继续可用。

- [ ] **Step 1: 把 YouTube 专用状态改为平台中性状态**

在 `ProbeViewModel` 中：

```kotlin
private val ytDlpProbe by lazy { YtDlpProbe() }
private var parsedSourceUrl: String? = null
private var mediaInfo: YtDlpMediaInfo? = null
private var creatorCatalog: CreatorCatalog? = null
```

把 `parseYoutube`/`downloadYoutube` 改为 `parseSingle`/`downloadSingle`，通过 `requireSingle(input)` 同时接受 YouTube 和 TikTok：

```kotlin
private fun requireSingle(input: String): ClassifiedSource {
    val source = UrlClassifier.extractAndClassify(input)
    require(source.platform in setOf(Platform.YOUTUBE, Platform.TIKTOK)) {
        "这里只接受 YouTube 或 TikTok 单视频链接，不会展开频道或作者主页"
    }
    val resolved = if (source.kind == SourceKind.UNKNOWN_TIKTOK_SHARE) {
        ytDlpProbe.resolveSource(source.url)
    } else {
        ResolvedSource(source.kind, source.url)
    }
    require(resolved.kind == SourceKind.SINGLE_VIDEO) {
        "该链接是 TikTok 作者主页，请使用读取作者作品"
    }
    return source.copy(kind = resolved.kind, url = resolved.url.ifBlank { source.url })
}
```

短链接先由 `resolveSource` 判断最终类型；作者主页不得自动把整页加入下载。

- [ ] **Step 2: 增加 TikTok 作者目录入口**

```kotlin
fun parseTiktokCreator(input: String) = runStage("读取 TikTok 作者作品") {
    val classified = UrlClassifier.extractAndClassify(input)
    require(classified.platform == Platform.TIKTOK) {
        "这里只接受 TikTok 作者主页链接"
    }
    val source = if (classified.kind == SourceKind.UNKNOWN_TIKTOK_SHARE) {
        val resolved = ytDlpProbe.resolveSource(classified.url)
        classified.copy(kind = resolved.kind, url = resolved.url.ifBlank { classified.url })
    } else {
        classified
    }
    require(source.kind == SourceKind.CHANNEL_OR_PLAYLIST) { "该链接是 TikTok 单视频" }
    val catalog = ytDlpProbe.extractCreator(source.url)
    creatorCatalog = catalog
    val preview = catalog.entries.take(8).joinToString("\n") { "${it.id}  ${it.title}" }
    _report.value = ProbeReport(
        title = "${catalog.creator}：${catalog.entries.size} 个公开作品",
        detail = "去重 ${catalog.duplicateCount}，剔除其他作者 ${catalog.foreignCount}\n$preview",
    )
    "TikTok 作者作品读取完成"
}
```

可行性诊断页不实现完整批量队列表格；它只验证目录数量、作者隔离和单条下载。完整应用阶段再把 `CreatorCatalog.entries` 接入选择列表。

- [ ] **Step 3: 复用单视频下载函数**

提取平台中性 `downloadMedia(info)`，保持现有视频/音频下载、Media3 合并和媒体校验逻辑不变。缓存目录使用 `${info.platform}-${info.id}`，完成提示使用平台名，不再写死 YouTube。

```kotlin
private suspend fun downloadMedia(info: YtDlpMediaInfo): File {
    val directory = freshProbeDirectory("${info.platform}-${info.id}")
    val video = downloader.download(
        DirectDownloadRequest(
            url = info.videoUrl,
            headers = info.headers,
            target = File(directory, "video.${safeExtension(info.videoExt, "mp4")}"),
        ),
        cancelled,
    ) { downloaded, total -> updateTransferReport("下载视频流", downloaded, total) }
    val output = if (info.audioUrl == null) video else {
        val audio = downloader.download(
            DirectDownloadRequest(
                url = info.audioUrl,
                headers = info.headers,
                target = File(directory, "audio.${safeExtension(info.audioExt, "m4a")}"),
            ),
            cancelled,
        ) { downloaded, total -> updateTransferReport("下载音频流", downloaded, total) }
        Media3MuxProbe.merge(getApplication(), video, audio, File(directory, "merged.mp4"))
    }
    check(MediaFileValidator.isLikelyMedia(output)) { "下载结果不是有效媒体文件" }
    return output
}
```

- [ ] **Step 4: 调整诊断页文案与按钮**

输入标签改成“抖音、YouTube 或 TikTok 分享文本”，操作区保留：

```kotlin
ProbeButton("解析 YouTube / TikTok 单视频", running) { viewModel.parseSingle(input) }
ProbeButton("下载 YouTube / TikTok 单视频", running) { viewModel.downloadSingle(input) }
ProbeButton("读取 TikTok 作者作品", running) { viewModel.parseTiktokCreator(input) }
```

抖音 WebView 捕获和公共目录写入按钮保持现状。按钮只在任务运行期间禁用，完成后恢复。

- [ ] **Step 5: 编译与回归验证**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\android\gradlew.bat -p .\android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Expected: Kotlin 单元测试全部 PASS，debug APK 与测试 APK 编译成功。

- [ ] **Step 6: 单独提交**

```powershell
git add -- android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeViewModel.kt android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeScreen.kt android/app/src/main/java/com/nanzhufeng/videodownloader/MainActivity.kt
git commit -m "feat(android): 接入 TikTok 下载诊断入口"
```

---

### Task 5: 自动验证、Find N5 真机验证与结果文档

**Files:**
- Create: `docs/verification/android-find-n5-feasibility-result.md`

**Interfaces:**
- Consumes: Tasks 1-4 的分类、目录、单视频下载、媒体校验和 MediaStore 写入能力。
- Produces: 可复核的自动测试结果、Find N5 真实下载证据和是否继续完整 Android 应用的结论。

- [ ] **Step 1: 运行完整自动测试**

Run:

```powershell
$env:PYTHONPATH='android/app/src/main/python'
& 'C:\Users\Administrator\AppData\Local\Programs\Python\Python313\python.exe' -m unittest discover -s android/app/src/test/python -v
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\android\gradlew.bat -p .\android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Expected: 所有测试 PASS，两个 APK 均生成；记录测试数量和 APK 路径。

- [ ] **Step 2: 在 Find N5 覆盖安装**

Run:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 3B157F009E800000 install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Expected: `Success`。不得运行卸载、清除数据或重置 WebView 数据命令。

- [ ] **Step 3: 验证 TikTok 公开单视频**

在诊断页粘贴一个当前可访问的 TikTok 公开单视频链接，依次执行“解析 YouTube / TikTok 单视频”“下载 YouTube / TikTok 单视频”“写入 Movies 公共目录”。验收必须同时满足：

- 解析结果只有 1 个作品。
- 平台显示 TikTok，标题和作者非空。
- 下载文件大于 0 字节并通过 `MediaFileValidator`。
- 写入返回 `content://` URI。
- 使用系统播放器打开后画面和声音正常。

- [ ] **Step 4: 验证 TikTok 作者主页**

在诊断页粘贴同一作者主页链接并执行“读取 TikTok 作者作品”。验收必须同时满足：

- 返回多个公开作品，或在作者实际只有一个作品时返回准确数量。
- 所有保留条目的 `creatorId` 与目标作者一致；没有标识的条目必须通过作品 URL 中的 `@作者` 关联。
- `foreignCount` 正确记录剔除条目，队列中不出现其他作者。
- 结果不因达到 500 条而人为停止。
- 从目录挑选一个作品 URL 再走单视频下载，结果可播放。

- [ ] **Step 5: 更新验证结果文档**

在 `docs/verification/android-find-n5-feasibility-result.md` 增加 TikTok 小节，使用以下固定状态词：

```markdown
## TikTok 补充验证

- 已实现：链接分类、单视频解析、作者目录、作者隔离、选择过滤。
- 仅测试通过：记录自动测试名称和数量。
- 已在 Find N5 真实验证：记录样本类型、作品数量、输出字节、content URI、媒体轨道和播放器结果。
- 待验证风险：仅列本轮没有真实完成的项目，不用“应该可以”代替证据。
```

若 TikTok 因网络、区域或平台策略无法访问，必须写为“真机待验证”，保留错误摘要，不把自动测试通过写成真实下载成功。

- [ ] **Step 6: 最终回归检查**

Run:

```powershell
git diff --check
git status --short
```

Expected: 没有空白错误；`build/`、APK、Cookie、下载媒体和 `android/captures/` 不得进入暂存区。

- [ ] **Step 7: 提交验证结果**

```powershell
git add -- docs/verification/android-find-n5-feasibility-result.md
git commit -m "test(android): 验证 TikTok 单视频与作者目录"
```

---

## Stop Conditions

出现以下任一情况，停止完整 Android 应用开发，仅保留诊断 APK、错误证据和结果文档：

1. Find N5 上 TikTok 单视频无法解析出真实媒体地址。
2. 下载结果为空、无法识别为媒体文件或系统播放器无法打开。
3. TikTok 作者主页持续混入其他作者作品，且无法用作者标识或作品 URL 稳定隔离。
4. Chaquopy 中 yt-dlp 的 TikTok 提取器无法在目标 ABI 上运行。
5. 实现需要引入来源不明的第三方解析 API、绕过付费/私密限制或把账号 Cookie 上传到外部服务。
