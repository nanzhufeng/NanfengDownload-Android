# 南烛枫视频下载器 Android 真正 MP3 编码设计

日期：2026-07-16  
状态：用户持续目标已确认采用“Android 解码 PCM + LAME 原生编码”路线

## 1. 结论

`AUDIO_MP3` 必须生成内容真实为 MPEG Audio Layer III 的 `.mp3` 文件，不能把 m4a/mp4 改扩展名，也不能要求平台必须提供 MP3 直链。

唯一正式链路固定为：

```text
yt-dlp 选择最佳音频源
  -> 下载原始音频到任务缓存
  -> 已是有效 MP3：验证后直接使用
  -> 其他可解码音频：MediaExtractor + MediaCodec 流式解码为 PCM16
  -> libmp3lame 流式编码
  -> 临时 MP3 完整关闭与校验
  -> MediaStore 发布
  -> Repository 标记完成并归档历史
```

输出文件只有在真实 MP3 校验通过后才能进入 `MediaStoreOutputStore.publish`。失败、取消或进程中断只能留下任务缓存中的临时文件，不能生成“完成”历史。

## 2. 方案比较

### 采用：MediaCodec 解码 + LAME 动态库编码

- Android 平台负责解码平台返回的 m4a/mp4/WebM 等音频源。
- LAME 4.0 只负责 PCM16 到 MP3 的编码，通过 JNI 形成独立动态库边界。
- 优点：真正 MP3、离线、包体和职责可控，保留现有下载与持久化架构。
- 成本：需要 NDK、CMake、JNI、LGPL 许可与原生真机测试。

### 不采用：整合完整 FFmpeg

- 能覆盖解码与编码，但会重复 Android/Media3 已有能力。
- 包体、ABI、许可、漏洞升级和长期维护面明显更大。
- 当前任务只缺 MP3 编码，不值得引入完整媒体工具链。

### 不采用：保存 m4a 或云端转换

- 保存 m4a 不满足“仅音频 MP3”的产品承诺。
- 云端转换引入隐私、服务成本、网络依赖和长期不可控性，不符合本地稳定工具定位。

## 3. 官方能力与版本选择

- Android 官方支持表对 MP3 标注为解码支持，没有通用 MP3 编码保证，因此不能依赖设备自带 MP3 encoder：<https://developer.android.com/media/platform/supported-formats>。
- Android 官方推荐通过 `externalNativeBuild` 使用 NDK CMake 工具链：<https://developer.android.com/ndk/guides/cmake>。
- 当前项目 AGP 8.5.2 的官方默认 NDK 是 `26.1.10909125`，本项目固定该版本，避免随 SDK 最新版本漂移：<https://developer.android.com/build/releases/agp-8-5-0-release-notes>。
- CMake 固定 `3.22.1`，由 Android SDK side-by-side 安装。
- LAME 固定官方源码版本 `4.0`。LAME 官方说明其为 LGPL MP3 encoder 且只分发源码：<https://lame.sourceforge.io/>、<https://lame.sourceforge.io/download.php>。

## 4. 唯一所有者与文件边界

| 概念 | 唯一所有者 | 公开入口 | 禁止项 |
| --- | --- | --- | --- |
| 音频源选择 | `YtDlpTaskMediaResolver` | `TaskMediaResolver.resolve` | UI 自行决定直链或格式 |
| 下载原始媒体 | `DirectMediaTransfer` | `MediaTransfer.download` | 页面直接下载或改文件名 |
| PCM 解码 | `AndroidPcmDecoder` | `PcmDecoder.decode` | JNI 或 UI 自行解析容器 |
| MP3 编码 | `LameMp3Encoder` | `Mp3Encoder.encode` | 把 m4a 内容伪装为 MP3 |
| 音频转码编排 | `Mp3AudioTranscoder` | `AudioTranscoder.transcode` | Repository 直接调用 JNI |
| MP3 有效性 | `Mp3FileValidator` | `validate` | 仅凭扩展名、大小或 ID3 判断成功 |
| 状态与历史 | `DownloadTaskRunner` + `DownloadRepository` | 现有任务状态机 | 转码器直接写 Room 或历史 |

计划新增文件：

```text
android/app/src/main/java/.../domain/download/audio/AudioTranscoder.kt
android/app/src/main/java/.../domain/download/audio/AndroidPcmDecoder.kt
android/app/src/main/java/.../domain/download/audio/LameMp3Encoder.kt
android/app/src/main/java/.../domain/download/audio/Mp3AudioTranscoder.kt
android/app/src/main/java/.../domain/download/audio/Mp3FileValidator.kt
android/app/src/main/cpp/CMakeLists.txt
android/app/src/main/cpp/nanzhufeng_mp3_jni.cpp
android/app/src/main/cpp/third_party/lame-4.0/
android/app/src/main/assets/open_source_licenses/LAME-LGPL.txt
android/app/src/main/assets/open_source_licenses/LAME-SOURCE.txt
```

## 5. PCM 与编码合同

`PcmDecoder` 以块为单位输出：

```kotlin
data class PcmFormat(
    val sampleRateHz: Int,
    val channelCount: Int,
    val totalDurationUs: Long,
)

interface PcmDecoder {
    suspend fun decode(
        input: File,
        onFormat: (PcmFormat) -> Unit,
        onPcm16: (ShortArray, frames: Int, presentationTimeUs: Long) -> Unit,
    )
}
```

约束：

- 只接受 1 或 2 声道；其他声道明确失败，不静默丢声道。
- 解码输出统一为有符号 PCM16、交错声道。
- 保留源采样率；LAME 负责必要的内部重采样。
- 每个 `MediaCodec` output buffer 及时释放，不把整段 PCM 放入内存或落地为完整 WAV。
- 协程取消必须停止 codec、关闭 extractor，并删除未发布的 `.part.mp3`。

`Mp3Encoder` 以 session 方式工作：

```kotlin
interface Mp3Encoder : AutoCloseable {
    fun open(format: PcmFormat, output: File)
    fun encodeInterleaved(samples: ShortArray, frames: Int)
    fun finish()
}
```

编码参数：

- 双声道：192 kbps CBR，joint stereo。
- 单声道：128 kbps CBR，mono。
- LAME quality：2。
- 写入 ID3v2 头，输出 MIME 固定为 `audio/mpeg`。
- `finish` 必须调用 LAME flush；flush 或文件关闭失败即任务失败。

## 6. 下载与转码数据流

`DirectMediaTransfer` 的 `AUDIO_MP3` 分支改为：

1. 下载 `ResolvedMedia.videoUrl` 到 `source.<真实扩展名>`。
2. 若来源扩展名为 mp3，仍必须通过 `Mp3FileValidator`；通过才直接返回。
3. 其他格式调用 `AudioTranscoder.transcode(source, target.part.mp3)`。
4. 转码成功后校验临时文件。
5. 通过原子 rename/copy 发布为任务缓存中的 `audio.mp3`，删除原始源文件。
6. 返回 `PreparedMedia(audio.mp3, "audio/mpeg")`。

视频下载、独立音视频合并与现有 `Media3MuxProbe` 不受影响。

## 7. 有效性验证

`Mp3FileValidator` 必须同时满足：

- 文件存在且大于 1 KiB；不能用 64 KiB 作为 MP3 下限，因为合法的短音频可能更小。
- 首部存在 ID3v2 或合法 MPEG audio frame sync。
- `MediaExtractor` 能找到 `audio/mpeg` 轨道。
- 轨道时长大于 0，采样率与声道数合法。

通用 `MediaFileValidator` 增加 MPEG frame sync 识别，并按内容类型区分最小尺寸：MP3 可从 1 KiB 起做结构验证，`ftyp`/WebM 仍保留原有 64 KiB 门槛。这样已发布 MP3 的后续“已有文件跳过”能够通过；但完成前必须使用更严格的 `Mp3FileValidator`。

## 8. 错误、取消与恢复

- 下载阶段网络异常仍按现有规则进入 `WAITING_NETWORK`。
- 解码器不支持、LAME 初始化失败、编码失败或输出校验失败属于不可伪装的媒体处理失败，进入 `FAILED` 并保留具体摘要。
- 用户取消时抛出 `CancellationException`，由现有任务生命周期处理；临时编码文件删除。
- 应用进程重启后不续写半个 MP3；重新运行任务时删除不完整 `.part.mp3` 并从已下载源文件重新编码。
- 已存在文件只有通过当前 MediaStore 与媒体校验后才跳过。

## 9. 构建、ABI 与许可

- `android/app/build.gradle.kts` 固定 `ndkVersion = "26.1.10909125"`、CMake `3.22.1`。
- ABI 继续只构建 `arm64-v8a` 与 `x86_64`；OPPO 使用 arm64，模拟器保留 x86_64 兼容面。
- LAME 编译为 `libmp3lame.so`，JNI 包装编译为 `libnanzhufeng_mp3.so`，不静态揉进 Kotlin/业务库。
- 不修改 LAME 算法源代码；Android 构建胶水和 JNI wrapper 单独维护。
- App 内提供 LGPL 文本、LAME 版本、官方源码 URL、源码校验值和替换说明。
- 许可合规记录是发布门，不以“能编译”替代。

## 10. 验收等级

### JVM 合同

- 非 MP3 音频必须调用转码器；有效 MP3 可直接通过。
- m4a/mp4 不能以 `.mp3` MIME/扩展名直接返回。
- 编码失败、校验失败、取消均不产生完成结果。
- 临时文件发布与清理规则可重复验证。

### 原生与模拟器

- JNI 能加载 LAME，编码确定性 PCM fixture，生成可解析 MP3。
- MediaExtractor 读取输出为 `audio/mpeg`、时长大于 0。
- arm64 模拟器完成一次 m4a -> MP3 集成测试。

### OPPO 真机

- 覆盖安装后，用公开短音频完成一次真实 m4a/mp4 -> MP3。
- 输出路径、扩展名、MIME、时长、声道和播放均正确。
- 取消或失败不会出现伪完成文件或历史。

## 11. 停止条件

只有 JVM、原生构建、模拟器真实转码和 OPPO 真机真实转码均通过，且许可文件随 APK 提供时，才能称“真正 MP3 已完成”。这仍不等于整个 App 已在 OPPO 完整落地；随后还必须完成三平台、断网、通知、历史、折叠屏和发布验收。
