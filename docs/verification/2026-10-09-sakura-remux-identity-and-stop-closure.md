# 樱花封装、网址身份与停止下载闭环

日期：2026-10-09（Asia/Shanghai）。仅操作 `emulator-5580 / NanfengWebParityTmp`。保留接手时 94 个改动/未跟踪路径；未提交、推送、发布或操作 OPPO。没有执行任何 connected*AndroidTest、数据库注入、卸载或清数据。

## 已确认根因和证据保全

原任务 `6e808840-ec08-4e6d-bcc1-91953dfc078f` 在传输状态 169,610,956 B 后报 `writeSampleData returned an error`。先只读复制任务缓存、数据库及 WAL、报告和 logcat，再做重试。原 `.segments` 实际 169,753,472 B，SHA-256 `f93f7466a83fe71182c4286e70064665e80f54c91bc2ffddfeccdf5d2f2ba12a`；清单 32,789 B，SHA-256 `58c051cb2b958419092a9ca454853c54076bf13ef426f09322e09d654b5a0d32`。私有证据目录 `<private-evidence>/nanfeng-sakura-evidence-20261009/` 权限 700；原文件仍保留，媒体及临时 CDN 清单不进入仓库。

- 原错误日志：AAC `215422 < 232199`，MPEG4Writer 拒绝乱序音频。Android TS 提取器将起始负音频 PTS 钳到 0；首个 PES 有 11 个 44.1kHz AAC 帧，应覆盖约 255.419ms，下一采样却在 215.422ms 开始。只剥 ADTS 头不能解决时间重叠。
- 清单声明 3 次 discontinuity，原媒体 PCR 对应 3 次前后跳变；TS 连续性计数在同样的边界重置。初版时间修复虽能发布，仍丢 36 个 AAC 帧及少量视频，不能计作完整通过。
- 修复计数后保留全部 AAC；其中一处切换重叠 45.491ms，超过一帧但小于两帧。现在只在两帧以内保持连续采样并保留每个编码帧；更大倒退明确失败，不把长回跳压成几个微秒。
- AVC 尾部还缓冲 1 个真实视频帧。空起始码、单独 AUD 均经正常 UI 证明无效，未采用。完整 AUD 加后继起始码边界才能释放最后真实访问单元；不包含新的 slice/picture。辅助定位参照 [AOSP ESQueue](https://android.googlesource.com/platform/frameworks/av/+/35022b1/media/libstagefright/mpeg2ts/ESQueue.cpp)，是否有效最终由本 AVD 的真实输出采样数证明。

## 续传追补：不能把重复字节当作完整媒体

为保留原中间成品且避免完成去重复用它，正常 UI 对同一公开第 1 集使用带验证查询的页面，播放目标视频并确认源，生成任务 `ac701443-dfc4-4abd-a4c8-ee9318b3a462`。没有改旧记录或插入数据库。实际传输后，在暂停/继续交接处发现完整分片被重复追加：170,168,012 B、30,807 个视频包、53,016 个 AAC 包；PCR 额外回退 2.36 秒，时间轴多出 2.4 秒。414,540 B 的前后相邻块逐字节相等。门禁先拒绝发布，未报完成。

- Python public downloader 并发回归先红：替代调用能在旧写入仍阻塞时结束，抢用分片/清单检查点。现在按精确目标持有独占锁直到 Python 处理退出，进度回调返回后再次检查取消。
- Kotlin public downloader 的封装并发回归也先红。现在整个“分片 → 时间轴处理 → Android 封装 → 缓存收束”生命周期独占同一目标；替代尝试等待旧尝试释放，等待本身可取消，不影响不同目标并行。
- 已有坏缓存恢复只在额外时钟切换、清单明确的切换位置与总时长、相邻完整字节重复共同成立时跳过被重复追加的一份。清单声明的重复片段必须保留；非相等字节、未知切换、时长/位置不符均失败并保留原缓存。原文件不被改写，只生成本任务私有的新处理文件。
- 本实站原缓存已再次保全。清单 410 段、1229.88 秒，声明位置 1111.2、1128.84、1212.24 秒。最终正常“重试下载”使用这些实站缓存完成恢复、封装和发布；最终尝试没有重新传输整片。修复后成功收束 App 私有缓存，主机保全副本仍在。

## 当前包和自动验证

- `1.2.97 / 10297`，Room 10，无 entity/schema/migration 修改。
- APK：`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.97.apk`；SHA-256 `09b8748da41472b5804af75e7eb566bc1c48f528fb9130763a1fde7c1b690a09`。AVD `base.apk` 回拉哈希相同。
- 证书 SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`；Android 与验签均使用 Android Studio JBR。这是隔离验收包，不是递增版本的主设备交付。
- Python 92 项通过；包括真实 yt-dlp/FFmpeg 分片、同目标交接、重复追加恢复、不可证明的重复拒绝、声明重复保留、原文件保全、解码像素不变和取消清理。
- Debug/Release JVM 各 311 项，0 失败；包含 AAC 起始时间、两帧以内重叠、更大回退拒绝、失败保全/成功收束及封装目标独占。
- lintDebug 0 errors、35 warnings、3 information；Release/R8 Parcelable CREATOR 门禁、assembleRelease 通过。既有 Gradle 依赖校验问题沿用临时离线 `--dependency-verification=off`，未修改校验元数据。
- 命令：`PYTHONPATH=android/app/src/main/python <既有隔离Python> -W always::ResourceWarning -m unittest discover -s android/app/src/test/python`；`JAVA_HOME='<Android Studio JBR>' ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleRelease --offline --dependency-verification=off --console=plain`。临时采样日志已移除；只保留无 URL 的轨道/时间失败诊断。

## 正常 UI 和成品验收

| 声明 | 实际证据 |
| --- | --- |
| 旧 MP3 不重复入队 | 最新身份修复包正常输入 `8765/audio.mp3` 并智能读取；任务/历史总数维持接手的 15/12，没有另造该音频记录。原 URI 保持 `content://media/external/audio/media/1000000114`。最终包再次回查仍为同一 URI；DASH 旧 URI `1000000115` 同样保留。 |
| 同 basename 不同端口 | 8766 原任务 `806041b3-f1e4-4087-bb04-7f028581ce67` 与 8767 新任务 `0cb5a3c5-c638-4303-ae7e-aff5046e2c1e` 分别入队，后者不再被前者误去重。 |
| 同端口不同路径 | 8767 的 `/slow/release-hls.m3u8` 与 `/release-hls.m3u8` 各有独立身份/任务；后者正常输出 URI `1000000117`，没有覆盖原 8766 URI `1000000116`。 |
| 正常停止 | 身份修复包在 DOWNLOADING 正常点击“停止下载”后为 CANCELLED、1024 B、无输出 URI、fileSize=0，完成记录数不增加。最终包早期取消任务 `5ffd5ea8-4304-427e-bf23-0d28f95b4c47` 也是 CANCELLED；系统视频条目保持 27→27。传输中追补见下文。 |
| 樱花最终实站成品 | 新任务 `ac701443-dfc4-4abd-a4c8-ee9318b3a462` 最终 COMPLETED；URI `content://media/external/video/media/1000000125`，162,297,814 B，fileExists=1。 |
| 媒体完整性 | H.264，主体 1920×810，AAC 44.1kHz 双声道；视频 30,747 个采样，AAC 52,912 个采样，音视频时长约 1229.88 秒。AAC 编码负载逐一与恢复后的实站源相等；无重复追加造成的额外 2.4 秒。成品 SHA-256 `442b3e78ba24e1227edd525ae3724d44939cc7f1eabb99e25dc41922d21dcbb9`。 |
| 全片解码 | 对上述实际 MediaStore 回拉文件执行 `ffmpeg -v error -xerror -i <成品> -map 0:v:0 -map 0:a:0 -f null -`，exit 0，stderr 0 B；不是只探测容器或开头。 |
| 历史播放 | 正常历史首张第 1 集缩略图打开内置播放器；实际显示动画画面。SurfaceView 有 127 条有效帧采样，两次末帧时间戳从 20900550855257 推进到 20942499087652。 |
| 冷进程重开 | force-stop App 后由桌面图标正常重开，进入历史再播放同一最新条目。有效帧 127，末帧从 21031830919861 推进到 21113797188359。历史和 URI 保留。这是进程重开，未重启 AVD。 |

原任务 `6e808840-ec08-4e6d-bcc1-91953dfc078f` 的中间 URI `1000000120` 虽曾 COMPLETED，但存在边界缺帧，保留作证据，不能作为最终完整验收。最终结论只引用上表新实站任务和 URI；没有用受控回放冒充新实站传输。

最终包传输中取消追补：使用同端口的 5 轮合成慢样本，经正常输入、读取、开始，先只读确认任务 `f3513193-0517-470b-b707-1b8867e5bd8d` 为 DOWNLOADING、8,704 B，再用最新 UI XML 的“停止下载”正常点击。取消收束后为 CANCELLED、23,040 B，取消历史无 URI、fileSize=0；没有新增该任务成品或待发布输出。8,704 B 是点击前的读数，不能当作点击瞬间或收束后的精确字节。较短对照任务 `df15474f-e71f-4dd2-b41f-fa486c09e481` 已在操作前完成为 URI `1000000126`，明确不作为取消通过证据；早期 0 B 取消也不代替本项传输中验证。收束时活动任务为 0，樱花 URI `1000000125` 和旧 MP3/DASH URI 保留。

## 边界

本轮要求的身份兼容、停止、樱花实站缓存恢复/完整成品/历史/冷进程链均有证据。其余樱花剧集、更多站点/Pornhub 作品、cn 站点独立 Android 下载和 OPPO 用户路径仍未逐个验证。TS 修复限制单 PCR 时钟及可证明的清单切换；超过恢复边界或其他不支持变体明确失败。没有宣称所有电脑版网站都已实测通过，也未新增常驻入口、升级引擎或引入媒体后端。
