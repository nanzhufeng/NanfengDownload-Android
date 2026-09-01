# 抖音图文多张图片与水印问题：完整技术交接与求解说明

> 目的：将本文件完整交给 Claude，请它基于代码和事实提出**可落地的根治方案**。这不是“帮忙解释为什么失败”的请求；目标是指出现有链路中仍可让水印或少图穿透的地方，设计可迁移、可验证的重构，并给出需要修改的具体位置、数据结构、迁移与测试方案。
>
> 代码库：`NanzhufengVideoDownloader-Android`（Android/Kotlin/Room/Compose）。
>
> 当前源码版本：`main`，提交 `8149cac`，应用版本 `1.2.81 (versionCode 10281)`。
>
> 本文记录截至 2026-08-23。抖音页面结构、CDN 参数和样本链接均属于外部不稳定信息，Claude 必须现场复核，不能把本文中历史页面取证当作当前平台事实。

---

## 1. 要解决的用户问题（不可降低标准）

用户反馈的不是“有时解析失败”，而是两项必须同时满足的最终结果：

1. 抖音图文下载后，系统媒体库（MediaStore）内的每张图片都没有“抖音号”等平台水印。
2. 下载张数必须等于作品实际图集张数，不能只下载当前轮播的一张，也不能漏下载第 N 张。

以下都**不构成完成**：

- WebView、yt-dlp 或单元测试解析到一批 URL；
- 下载任务入队；
- 第一张文件存在；
- `outputUris.size == expectedCount`；
- URL 不含 `tplv-dy-water`；
- 正式 APK 构建、安装或应用页面显示“完成”。

唯一有效验收为：一次新的、真实的图文下载完成后，实际写入 MediaStore 的 URI 数量严格等于该作品声明的图数；每个 URI 可读取、可解码，并逐张以像素/人工视觉确认没有水印。必须同时保存可追溯的来源与验收证据。

用户明确拒绝以下做法：

- 重新启用“页面当前轮播图/首张图”作为兜底；
- 把长时间“正在校验”直接当作失败而停止任务；
- 删除已有历史、MediaStore 文件或 App 数据库来强行制造重下；
- 用截图、任务状态或单测代替真实成品验收；
- 为了测试而运行 `connected*AndroidTest` 或清理用户 OPPO 主设备数据。

---

## 2. 已确认的事实与证据等级

### 2.1 历史上确实下载到过带水印的最终图片（已确认）

在早期版本 `1.2.68`，曾从 OPPO 实际写入的图片中读取并人工查看：两个图文样本首张底部均有“抖音号”水印。它们不是下载器后处理时添加水印，而是下载器选到了平台提供的带水印变体。

随后对同一作品的候选字节进行过对比：

- 页面结构的 `urlList` 返回较大尺寸的无水印图；
- `downloadUrlList` 或 yt-dlp 图集结果返回带“抖音号”水印图；
- 两者是不同资源，不是客户端保存时压缩或 MediaStore 改写造成的。

这个结论解释了“URL 字段名看起来正确，但最终有水印”为什么会发生：当时任务执行阶段又被 yt-dlp 的结果覆盖。

### 2.2 两个历史公开样本（仅作回归线索，须重新验证）

| 样本 | 作品 ID | 当时从目标页结构得到的声明 | 当时结构化候选 |
|---|---:|---:|---:|
| 果果大王 | `7676041925425736777` | 2 张 | 2 个 `urlList` 非水印候选 |
| 一锅汤 | `7670887343922973155` | 14 张 | 14 个 `urlList` 非水印候选 |

这些样本已存在于用户历史/去重范围内。因此在不删除用户数据的前提下，后续版本对它们的“重新读取”可能不产生一次全新的端到端下载，不能再用它们的读取结果宣称最终问题已解决。

### 2.3 当前“解析与传输”链路已做过的正确改动（已确认）

当前代码已经不是早期“拿首张 DOM 图就下载”的状态：

- 图文 `/note/<id>` 读入时绕过 yt-dlp 图集“成功”分支，优先目标页面采集。
- WebView 脚本从目标作品的 React Flight `window.__pace_f` 中查 `aweme.detail.images`；只取 `urlList`/展示图变体，拒绝 `downloadUrlList` 和 `tplv-dy-water`。
- 只有图集声明数大于 0、候选去重后数量等于声明数、全部通过 URL 规则时，才认为图集结构完整。
- 若 Flight 数据到达前只有当前轮播图，或出现 `1/14`、`13/14`，当前代码等待/失败，不会降级创建首图任务。
- 任务级持久化的 `capturedImageUrls` 已优先于后续 yt-dlp 重解析，避免新任务在执行时被 yt-dlp 的水印图覆盖。
- 图集传输从 1.2.81 起改为最多三路并行，减少每张先做 1-byte Range 探测再串行下载的等待；这只改善速度，不改变内容正确性。

这些改动能说明“新读取的结构化候选更接近正确来源”，但不等于证明 Android 最终文件无水印。

---

## 3. 系统结构与当前数据流

```text
用户输入官方抖音链接
  -> PlatformSourceDiscoveryEngine
  -> 对 /note/ 强制目标页图集读取
  -> DouyinProbeActivity 后台 WebView / React Flight
  -> DiscoveredMedia(capturedImageUrls, expectedCount, sourceVersion)
  -> RoomDownloadRepository 入队/去重/必要时刷新旧元数据
  -> MediaItem + DownloadTask 持久化
  -> YtDlpTaskMediaResolver
  -> ResolvedMedia(imageUrls)
  -> DirectMediaTransfer / DownloadTaskRunner
  -> 输出校验、复制/发布到 MediaStore
  -> DownloadHistory(outputUris, expectedCount, sourceVersion)
```

当前代码中的关键文件：

| 责任 | 文件 | 当前要点 |
|---|---|---|
| 目标页图集解析 | `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/DouyinProbeActivity.kt` | `IMAGE_SOURCE_SCRIPT` 约 411–545 行；`inspectImageGallery()` 约 72–125 行。 |
| 可信 URL 规则 | `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/DouyinCaptureStore.kt` | `isVerifiedImageGallery()` 约 116 行；源版本常量目前为 `1`。 |
| 读取分流 | `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery/PlatformSourceDiscoveryEngine.kt` | 对 `/note/` 调 `readDouyinGalleryOrCapture()`，不让 generic yt-dlp 图集捷径越过采集。 |
| 任务解析优先级 | `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/YtDlpTaskMediaResolver.kt` | 已持久化的 verified `capturedImageUrls` 最优先；无完整捕获的 `/note/` 直接报错，不允许 yt-dlp 退回。 |
| 任务/历史去重与刷新 | `android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepository.kt` | 对结构化图集比较 URL 稳定 path、声明数、源版本、历史输出数量，决定刷新或安全补任务。 |
| 任务完成后历史 | `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DownloadTaskRunner.kt` | `DownloadHistory` 仅保存 expectedCount/sourceVersion/outputUris。 |
| 数据模型/迁移 | `core/model/DownloadModels.kt`、`data/database/entity/Entities.kt`、`data/database/NanzhufengDatabase.kt` | Room 当前版本 `9`；媒体项已持久化 captured URL/张数/源版本，历史仅持久化后两项与 URI。 |

---

## 4. 开发过程、每次修复与为什么仍没有闭环

### 4.1 初始阶段：DOM/网络候选与 yt-dlp 混用

早期后台 WebView 只能稳定看到当前轮播页，网络拦截也会混入多个候选；它可能拿到第一张，或拿到带水印的下载变体。与此同时，yt-dlp 会把某些 `/note/` 当图集“成功解析”，但该解析结果实际可能是水印图。

结果：水印和少图交替出现。修“无水印”时选到单张展示图；修“多图”时让 generic 图集结果重新进入，又把水印带回。

### 4.2 v1.2.65 / v1.2.66：改为等待 React Flight 的完整目标图集

将解析改为从目标作品 ID 的结构化 `images` 中读完整数组，而不是相信当前 DOM 轮播图。

解决了什么：`window.__pace_f` 已可用但目标图集 chunk 尚未到达时，不再早退为首张；声明 14 张但只发现 1 张或 13 张时被拒绝。

没有解决什么：读取正确的 URL 不等于任务执行最终使用这些 URL。当时下游仍有重解析覆盖点。

### 4.3 v1.2.67：修复 yt-dlp 在执行阶段覆盖页面捕获源

发现并证实：目标页 `urlList` 可无水印，而任务执行先调用 yt-dlp 后，yt-dlp 又返回带水印的图集 URL。于是 `YtDlpTaskMediaResolver` 调整为：对于持久化的 verified 抖音图集，直接返回 `capturedImageUrls`；无此捕获的 `/note/` 不再允许 yt-dlp 图集后备。

解决了什么：同一新任务内，“解析阶段无水印 URL 被下载阶段 yt-dlp 水印 URL 覆盖”的明确分流错误。

为什么仍不能结案：当时已在用户设备上完成的历史文件不会因为这次代码修复自动改变；更重要的是，尚没有在这条新链路上取得“最终 MediaStore 的每张像素无水印”证据。

### 4.4 v1.2.69 / v1.2.70：完整张数与无水印 URL 绑定为 fail-closed 门禁

`DouyinProbeActivity` 当前逻辑要求：

```kotlin
if (imageSources.awaitingStructuredImages ||
    (requiresCompleteGallery && !imageSources.structuredComplete)) {
    // 等待，不从当前轮播首图完成
    return
}
```

脚本中 `structuredComplete` 的条件是目标作品已找到、`expectedCount > 0`，且 `uniqueStructuredImages.length == expectedCount`。选 URL 时要求 HTTPS、`tplv-dy-aweme-images`，并排除 `tplv-dy-water`。

解决了什么：`1/14`、`13/14`、明确水印 URL 不创建下载任务，不再用“能下载一张”冒充图集成功。

为什么仍不能结案：fail-closed 只保证“不确定时不要错误下载”，不是“用户一定能拿到正确成品”。此外，这一层仍以 URL 分类而非最终字节/像素证明为依据。

### 4.5 v1.2.71：为旧任务、旧历史做非破坏性图源刷新

发现另一个问题：同作品已经存在于任务/历史时，新的完整干净 `capturedImageUrls` 会被普通去重整个丢弃，旧版留下的空列表、单张列表或水印列表会继续被沿用。

因此仓库层增加了：

- 新读到 verified 图集时，刷新已有 `MediaItem` 元数据；
- 旧源不完整、来源版本/数量不同、输出数不一致时，保留历史与旧文件，额外创建可下载任务；
- 单纯 CDN 域名或签名参数变化仍去重，避免无限重下；活动任务只更新来源，不重复建任务。

这是向正确方向迈进，但其“是否需要重下”的判定仍只看元数据数量与版本，无法知道旧 `outputUris` 指向的真实像素是否有水印。

### 4.6 v1.2.80 / v1.2.81：保存进度与图集速度（非内容修复）

用户另行反馈“正在校验时间长”和图集加载慢。已将 MediaStore 发布阶段与快速校验分开显示、将复制缓冲升级为约 1 MiB，并将图集下载改为至多 3 路并行。

这避免把“保存到系统媒体库”伪装为“正在校验”，也没有通过超时/停止掩盖速度问题。但这两版**没有**补上水印像素溯源或最终验收，不应被算作图文内容问题的修复完成。

---

## 5. 当前核心断裂：为什么“URL/数量都对”仍可能给用户水印或旧文件

### 5.1 历史完成态的认证条件不含真实输出来源或内容证明（最关键）

`DownloadTaskRunner.kt` 当前的完成历史认证近似为：

```kotlin
private fun DownloadHistory.certifies(media: MediaItem): Boolean =
    capturedImageExpectedCount == media.capturedImageExpectedCount &&
        capturedImageSourceVersion == media.capturedImageSourceVersion &&
        outputUris.size == media.capturedImageExpectedCount
```

它没有以下任何字段：

- 每个输出 URI 当时实际下载的源 URL（或其稳定身份）；
- 每个输出文件的 digest、MIME、尺寸、解码结果；
- 输出文件与任务中第几个图之间的稳定映射；
- “该输出由已验证干净源下载”的原子完成证明；
- 基于像素/视觉的无水印验收状态或人工验收记录。

因此存在一个完全合理的风险路径：历史 14 张水印成品在后来被刷新了 `expectedCount/sourceVersion`，或者历史元数据本来就恰好是 14/版本 1；只要 `outputUris.size == 14`，当前逻辑就可把它当成“已认证”，虽然 14 张实际图片可能仍有水印。这是从源码可直接得出的结论，不是已经证明必然发生的单一现场事件。

### 5.2 历史元数据升级与历史物理文件是两个不同对象

v1.2.71 能刷新 `media_items` 中的 `capturedImageUrlsJson`，但不会重写用户已有 MediaStore 文件（这是正确的数据保护行为）。如果历史模型把“新元数据”误当作“旧文件已符合新来源”，用户仍会在历史页看到老的水印图片，而应用看起来又像“已经是完成图集”。

需要的数据语义是：**解析来源版本**、**任务执行来源版本**、**输出文件实际来源版本**、**内容验收版本**彼此独立；不能只用一个 `capturedImageSourceVersion` 同时代表四件事。

### 5.3 URL 标签不是内容证明

当前规则排除 `tplv-dy-water` 是必要的安全过滤，但不充分：CDN 可能改变参数、域名、处理链或字节内容。反之一个看似 display URL 也不应自动被等同于“像素绝对无水印”。

真正可机械化验证的最低层应是：

- 请求实际响应、重定向后最终 URL、关键响应头、MIME、字节摘要；
- 每张写入前/后的可解码性、尺寸和唯一性；
- 将这些数据与 `imageIndex`、任务 ID、输出 URI 原子绑定。

“无水印”本身通常难以只靠一般算法可靠断言；应将其作为显式人工/视觉验收状态，或引入明确、受控且经样本验证的检测模型。绝不能用 URL 字符串继续伪装为像素证明。

### 5.4 按路径比较 URL 可能产生错误的“相同”判断

`RoomDownloadRepository.kt` 的 `stableCapturedImageIdentity()` 目前主要取 URI path，忽略 query。这样做是为避免 CDN 签名参数轮换造成无休止重下；但如果平台把不同渲染变体编码在 query 或同 path 的服务器协商中，path 一致不保证字节相同。这个比较应只用于“候选来源是否值得重下”的启发式，不能作为历史输出已经合格的认证。

### 5.5 解析脚本与运行环境仍有外部脆弱性

`IMAGE_SOURCE_SCRIPT` 依赖未公开的 `window.__pace_f`、Flight 行文本形式、`awemeId`/`images` JSON 字段及 URL 的路径标签。它比 DOM 首图强很多，但依然不是稳定官方 API。页面改版、分块时序、风控、登录状态、UA 差异或编码变化，都可能让它卡在“等待完整结构”或错误地找不到目标图集。

这不是恢复首图兜底的理由。正确处理是：记录结构化诊断、可重试、明确告诉用户“目标页未返回完整无水印图集”，同时为解析器建立可回放 fixture 和版本化选择器。

---

## 6. 建议 Claude 设计的根治架构（请审计、修正并细化）

这是一份有意强调边界的候选设计，不要求逐字照抄。重点是消除“来源元数据”和“历史物理成品”混淆。

### 6.1 设计一个不可变的 `VerifiedGalleryManifest`

在读取成功、**任务创建之前**生成并持久化 manifest；任务执行只能消费它，不能回退或重新解释为别的图集。

建议字段：

```kotlin
data class VerifiedGalleryManifest(
    val manifestId: String,                 // UUID
    val platform: DownloadPlatform,
    val contentId: String,
    val parserVersion: Int,                 // 解析规则版本，不是模糊 sourceVersion
    val capturedAtMillis: Long,
    val expectedCount: Int,
    val items: List<VerifiedGalleryItem>,   // 顺序不可变
    val pageUrl: String,
    val evidenceJson: String,               // 受控诊断摘要，不能泄露 Cookie
)

data class VerifiedGalleryItem(
    val index: Int,                         // 0..expectedCount-1
    val requestedUrl: String,
    val sourceIdentity: String,             // 规范化、版本化的候选身份
    val selectionRule: String,              // 例如 target-flight-urlList-v2
)
```

关键约束：

- `expectedCount == items.size`、索引连续、无重复才可进入 `READY`；
- manifest 创建后不可被“同一个 mediaKey 的最新 URL”静默覆盖；要升级来源，创建新 manifest 和新任务代际；
- `MediaItem` 可以指向“最新可读 manifest”，但 `DownloadTask` 必须固定引用它自己的 `manifestId`；
- 任何 `/note/` 图文没有 verified manifest 就不能下载；禁止 yt-dlp 或 DOM 首图成为任务级后备。

### 6.2 为每张输出建立来源到文件的原子 `GalleryOutputProof`

下载/发布成功时，不只更新一个 `outputUris` 列表。应逐项持久化：

```kotlin
data class GalleryOutputProof(
    val taskId: String,
    val manifestId: String,
    val imageIndex: Int,
    val sourceIdentity: String,
    val requestedUrlRedacted: String,
    val finalUrlRedacted: String?,
    val responseMime: String,
    val responseLength: Long,
    val downloadedSha256: String,
    val decodedWidth: Int,
    val decodedHeight: Int,
    val outputUri: String,
    val publishedSha256: String?,           // 可选：发布后读取核对
    val transferCompletedAtMillis: Long,
    val outputValidationState: OutputValidationState,
)
```

注意：URL 可能有签名/隐私参数，不应把完整可复用 URL 明文长期暴露到 UI、日志或 Release。可保存必要的受控信息，或脱敏/哈希。

原子性要求：只有全部 `expectedCount` 张的 proof 都成功且索引集合完整，任务才能转为 `COMPLETED`。若第 N 张失败，状态必须是部分失败/可恢复，不得生成“完成历史”。

### 6.3 重新定义“历史已认证”

建议不要用“输出 URI 个数 + 最新 sourceVersion”认证历史。新条件至少应为：

```text
history is certified iff
  it references a manifest;
  that manifest is structurally complete;
  it has exactly one valid GalleryOutputProof per manifest item;
  each proof maps to the correct item index and output URI;
  output files remain readable; and
  the proof schema/parser/output validation versions meet current policy.
```

旧历史没有 proof 时，状态应是 `LEGACY_UNVERIFIED`，不是 `CERTIFIED`。它不意味着“文件被删除”或“用户不能查看”；它只意味着不能用它阻止一次安全的重下/重验。

### 6.4 迁移与数据保护

用户数据保护是硬约束：

- 不删除、不覆盖旧历史记录或旧 MediaStore 图片；
- Room 新建 manifest/proof 表并做显式迁移；不得 `fallbackToDestructiveMigration`；
- 现有历史一律初始为 `LEGACY_UNVERIFIED`（除非能从旧版本证据严格重建 proof，当前显然做不到）；
- 当用户重新读取到同一图文的完整 manifest 时：保留旧历史，额外创建一个标识清楚的“已验证来源重下”任务；
- 避免增加一个永久常驻按钮。可将重下作为同一作品重新智能读取后的安全结果，或以现有任务/历史交互中最小且明确的方式呈现；需先与产品决定对齐。

### 6.5 水印验收的诚实模型

不要实现一个不可靠的“程序自动认定无水印”然后再次误报。建议把状态分层：

1. `SOURCE_STRUCTURALLY_VERIFIED`：来自目标作品的完整结构化 source，且符合已知无水印选择规则；
2. `TRANSFER_PROVEN`：每张输出具备来源到文件的 proof，数量/可读性/映射完整；
3. `VISUALLY_ACCEPTED`：对本次样本逐张人工或经验证模型进行像素检查，记录接受者、时间、样本、结果；
4. `REJECTED_WATERMARK`：一旦发现带水印，保留证据，不能让它被后续元数据刷新伪装成合格。

产品对用户的文案必须区分前两项与第三项。即使工程层链路已证实来自 `urlList`，也只能说“已按受控原图来源保存”；没有实际像素检查时不能说“已证实无水印”。

---

## 7. 必须覆盖的测试与真实验收

### 7.1 JVM/Room 回归测试

已有相关测试文件：

- `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/DouyinProbeActivityTest.kt`
- `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/DouyinCaptureStoreTest.kt`
- `android/app/src/test/java/com/nanzhufeng/videodownloader/domain/download/YtDlpTaskMediaResolverTest.kt`
- `android/app/src/test/java/com/nanzhufeng/videodownloader/domain/download/DownloadTaskRunnerTest.kt`
- `android/app/src/test/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepositoryTest.kt`

请至少新增/调整下列契约：

1. 2 张和 14 张完整 Flight fixture 能按原顺序创建 manifest；1/14、13/14、重复 URL、跨作品图、明确 water URL 均拒绝。
2. Flight 未完整时绝不能退回 DOM 首图；超时后产生结构化失败，而不是卡死、静默成功或入队。
3. 对抖音 `/note/`，verified manifest 的任务解析不能调用 yt-dlp；缺 manifest 的图文不能退回 generic image gallery。
4. manifest 生成后，不同 manifest 的 URL/版本不能修改旧任务的执行输入。
5. 完成条件需要每个 `imageIndex` 恰好一个 proof；数量相同但缺 index、重复 index、URI 不可读或哈希不匹配均不能完成。
6. legacy history 即使 URI 数量和 expectedCount 相同，也不能 `certify()`；新 manifest 可以非破坏性创建重下任务。
7. 新来源只变签名参数时应避免无限生成任务；但这个去重优化不得影响“输出 proof 是否合格”的判断。
8. Room 从当前 schema version 9 的升级保留所有旧任务、历史和 URI，并将旧图集标记为 legacy-unverified。
9. 中途停止、进程重启、单张下载/发布失败、MediaStore 写入失败和重新开始时 proof 状态正确恢复，不把半成品写成完成。

### 7.2 真实端到端验收（不可由单测替代）

应选择新的、可公开访问的 2 张和 14 张抖音图文样本，避免同一历史去重。对每个样本记录：

| 阶段 | 必须记录的事实 |
|---|---|
| 读取 | 作品 ID、声明张数、manifest ID、解析器版本、每个 item 的顺序与脱敏来源身份。 |
| 下载 | 每张实际响应 MIME/长度/摘要、是否重定向、失败原因、最终顺序。 |
| 发布 | MediaStore URI、文件可读性、MIME、尺寸、输出摘要与 manifest item 对应关系。 |
| 像素验收 | 每张逐一打开并检查水印区域；保存结果与可追溯的验收记录。 |
| 历史重读 | 重启 App 后，历史仍显示同一 manifest/proof，而不是按最新元数据重解释旧文件。 |

设备约束：严禁运行 `connected*AndroidTest`。OPPO Find N5 是有用户数据的主设备，只能在同签名、递增版本的正式 APK 通过安装前安全检查后，使用 `adb -s 3B157F009E800000 install -r --user 0` 覆盖；不得卸载、清数据、注入数据库或删除媒体。若需要自动化验收，请使用隔离模拟器，并把它与 OPPO 人工真实成品验收分开报告。

---

## 8. 给 Claude 的具体交付请求

请基于当前仓库审计以下问题，并给出可实施方案。不要泛泛建议“换 API”“加重试”“再做 URL 过滤”。

1. 以上对根因的判断是否完整？请用当前代码指出任何仍能让“水印/少图/旧文件伪认证”发生的具体路径。
2. `VerifiedGalleryManifest + GalleryOutputProof` 是否是合适的最小架构？如需替代，请说明数据所有权、不可变边界、任务重启语义和 Room 迁移。
3. 如何从 Room schema 9 安全迁移，且不破坏现有用户任务、历史、MediaStore URI？
4. 如何区分 `source structurally verified`、`transfer proven`、`visually accepted`，避免把 URL 规则冒充像素无水印证明？
5. 请为 `RoomDownloadRepository` 的“旧来源刷新/重下”重新定义纯函数判定，避免以 path 相等或 URI 数量作为最终认证。
6. 给出最小改动文件清单、数据模型、关键 Kotlin 伪代码或 patch 结构，以及每个新增测试的名字和断言。
7. 设计两阶段验收：隔离模拟器可自动验证什么；OPPO 必须由真实新样本证明什么。明确哪些结论在没有逐张像素检查前绝不能宣称。
8. 如果抖音页面结构变化导致 Flight 不完整，给出失败恢复/诊断方案；不要回到首图兜底，不要无限“停止读取”。

期望 Claude 的回答格式：

1. 根因审计（确认事实 / 推断 / 需要现场验证分开）；
2. 推荐架构与反对的备选方案；
3. 迁移与兼容策略；
4. 精确改动点；
5. 单测与真实验收矩阵；
6. 风险与不应做的事情；
7. 分阶段实施顺序，先实现可证明的最小闭环，再进行真实样本验收。

---

## 9. 当前状态结论

当前版本已经把“目标页完整图集的 URL 选择”做成比旧实现严格得多的 fail-closed 路径，并且避免 yt-dlp 再次覆盖新任务的捕获 URL；下载性能与发布进度也已单独优化。

但是，**“抖音多张图片最终全部无水印”尚未完成验收，也不能宣称解决**。真正缺少的是：逐张输出文件与不可变来源 manifest 的原子证明、对旧历史成品的 unverified 语义、以及新的真实样本在 MediaStore 中逐张像素验收。任何继续修改都必须以补齐这三件事为中心，而不是在首图、URL 字符串或 yt-dlp fallback 顺序上继续循环打补丁。
