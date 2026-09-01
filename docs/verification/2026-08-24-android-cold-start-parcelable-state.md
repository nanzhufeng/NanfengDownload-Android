# Android 冷启动 Compose 状态恢复崩溃修复（2026-08-24）

## 结论

OPPO 上“冷启动立即退出、再次打开正常”的直接原因已经定位并在 Release 混淆边界修复：`androidx.compose.runtime.ParcelableSnapshotMutableState` 的 `CREATOR` 被 R8 删除或改名，Android 在恢复 Compose 保存状态时无法通过反射反序列化该对象。

本记录证明代码、R8 产物、自动回归与同签名覆盖的数据保留边界。覆盖安装不等于冷启动用户路径已验收。

## 现场证据与根因

| 证据 | 结果 | 结论边界 |
| --- | --- | --- |
| OPPO `dumpsys dropbox --print` 只读记录 | v1.2.57、v1.2.70、v1.2.81、v1.2.82 均在进程运行约 246–386 ms 后出现 `BadParcelableException` | 真实设备已复现崩溃，不代表新构建已在设备验证 |
| 异常链 | `Parcelable protocol requires a Parcelable.Creator called CREATOR on class L.f0`，根因为 `NoSuchFieldException: CREATOR` | 崩溃发生在 Android 恢复 Activity/Compose 状态，不在下载业务链 |
| v1.2.82 Release 映射 | `L.f0` 反查为 `androidx.compose.runtime.ParcelableSnapshotMutableState`，未列出 Creator 初始化 | 锁定被混淆删除的具体类 |
| Compose 运行时 AAR | 该类声明 `public static final Parcelable.Creator CREATOR` | 说明它是系统反射恢复的必需字段 |

## 修复

1. 在 Release 构建中显式加载 Android 默认优化规则和 `app/proguard-rules.pro`。
2. 在项目 R8 规则中只保留 `ParcelableSnapshotMutableState.CREATOR`；不关闭 R8、不禁用 Compose 状态保存、不清除用户数据。
3. 增加 `verifyReleaseParcelableCreatorRetention` 构建门禁。它依赖 `minifyReleaseWithR8`，并验证映射中保留 Creator 的初始化路径；`assembleRelease` 依赖该门禁。

## 自动验证

执行于 `android/`：

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease \
  --max-workers=1 --console=plain
```

结果：`BUILD SUCCESSFUL`（115 actionable tasks；20 executed，95 up-to-date）。其中包含 Debug/Release JVM 单测、Debug/Release lint、Release R8、签名配置验证和 `verifyReleaseParcelableCreatorRetention`。没有执行任何 `connected*AndroidTest`。

对本次 Release APK 的 DEX 直接检查确认：混淆后的 `ParcelableSnapshotMutableState` 类实现 `android.os.Parcelable`，并含有 `public static final android.os.Parcelable$Creator CREATOR`。

## OPPO 正式覆盖安装

安装前，设备为 v1.2.82 / 10282，证书为 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`，首次安装时间为 `2026-08-21 02:04:16`，CE/DE 数据 inode 分别为 `1788131` / `1745695`。

在用户明确授权下，仅执行正式 APK 推送与 `pm install -r --user 0` 同签名覆盖。安装后只读核验结果：

- 包版本：v1.2.83 / 10283，`DEBUGGABLE` 未出现。
- 本地正式 APK 与设备回拉 `base.apk` 均为 46,038,888 B，SHA-256 均为 `bcb77dde446fdb066399f6204a111740a4e3d9cac80213f20d4e666e274236a9`。
- 证书 SHA-256 保持为 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。
- 首次安装时间与 CE/DE 数据 inode 均保持安装前值；临时 APK 已从设备临时目录清理。

未自动启动 App、未创建、停止、重试或删除任何用户下载任务。

## 未覆盖的验证与后续

- 本轮未自动启动 App，故本修复尚未取得 OPPO 冷启动用户路径结论。
- v1.2.83 已在 OPPO 覆盖安装，但尚未发布到 GitHub；不得以本地构建或安装成功代替正式 Release。
- 下一步由用户手动冷启动两次，确认不再退出；如仍异常，只读回收新的系统崩溃记录后继续排查。
