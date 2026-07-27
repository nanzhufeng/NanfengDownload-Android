package com.nanzhufeng.videodownloader.core.diagnostics

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform

object UserFacingErrorPresenter {
    fun message(
        rawError: String?,
        platform: DownloadPlatform? = null,
        fallbackProblem: String = "下载处理失败，底层组件返回了无法识别的错误",
        fallbackAction: String = "请重新智能读取链接后重试；仍失败可在下载详情中查看原始错误",
    ): String {
        val evidence = rawError.orEmpty().replace(Regex("\\s+"), " ").trim()
        val lower = evidence.lowercase()
        val resolvedPlatform = platform ?: inferPlatform(lower)
        return when {
            lower.contains("fresh cookies") && resolvedPlatform == DownloadPlatform.DOUYIN ->
                "抖音需要新的网页会话。请到“设置 → 账号与权限 → 抖音”重新登录，返回后重试。"

            resolvedPlatform == DownloadPlatform.BILIBILI && hasHttpStatus(lower, 412) ->
                "哔哩哔哩拒绝了当前解析请求（412），分享链接、网页会话或网络出口可能已失效。" +
                    "解决办法：请重新复制官方分享链接；仍失败请到“设置 → 账号与权限 → 哔哩哔哩”登录后重试。"

            resolvedPlatform == DownloadPlatform.BILIBILI &&
                hasAny(lower, "creator batch is not supported", "space.bilibili.com") ->
                "哔哩哔哩当前只支持单个视频，暂不支持从 UP 主页批量读取。" +
                    "解决办法：请打开具体视频后复制分享链接，再使用“智能读取”。"

            resolvedPlatform == DownloadPlatform.XIAOHONGSHU &&
                hasAny(lower, "image-only note", "image only note") ->
                "这是小红书图文笔记，没有可下载的视频。" +
                    "解决办法：请选择带视频播放内容的笔记，再复制最新分享链接。"

            resolvedPlatform == DownloadPlatform.XIAOHONGSHU &&
                hasAny(lower, "xsec_token", "initial state", "note data") ->
                "小红书分享链接的访问凭证已失效或页面未返回视频数据。" +
                    "解决办法：请在小红书 App 中重新复制该视频的最新分享链接；仍失败请登录后重试。"

            hasAny(lower, "login required", "sign in to confirm", "confirm you're not a bot", "cookies are no longer valid") ->
                "平台要求验证登录信息。解决办法：${loginAction(resolvedPlatform)}"

            hasHttpStatus(lower, 401) || hasHttpStatus(lower, 403) || lower.contains("forbidden") -> {
                val status = if (hasHttpStatus(lower, 401)) 401 else 403
                "平台拒绝了下载请求（$status），登录 Cookie 或临时下载地址可能已失效。" +
                    "解决办法：${loginAction(resolvedPlatform)}"
            }

            hasHttpStatus(lower, 404) || hasHttpStatus(lower, 410) ||
                hasAny(lower, "video unavailable", "this video is unavailable", "private video", "content is no longer available") ->
                "视频不可用，可能已删除、设为私密或原链接已失效。" +
                    "解决办法：请在平台 App 中确认视频仍可播放，再复制最新分享链接。"

            hasHttpStatus(lower, 416) || lower.contains("range not satisfiable") ->
                "已下载的分片与服务器当前文件不一致（416）。" +
                    "解决办法：请点击重试，App 会重新探测下载地址并安全续传。"

            hasHttpStatus(lower, 429) || hasAny(lower, "too many requests", "rate limit") ->
                "平台限制了过于频繁的请求（429）。" +
                    "解决办法：请暂停 10–30 分钟后重试，不要连续反复点击。"

            hasAny(lower, "requested format is not available", "no video formats", "no formats found") ||
                evidence.contains("没有可下载视频流") || evidence.contains("没有找到可下载") ->
                "当前画质没有可用的视频流。" +
                    "解决办法：请降低分辨率或改为“最佳画质”，重新智能读取后再下载。"

            lower.contains("ffmpeg") && hasAny(lower, "not found", "no such file", "missing") ->
                "音视频处理组件缺失。" +
                    "解决办法：请安装最新完整版 App；若仍失败，请保留原始错误日志用于排查。"

            lower.contains("ffmpeg") || hasAny(lower, "merging formats", "postprocessing failed") ->
                "音视频合并失败。" +
                    "解决办法：请降低分辨率后重试；若仍失败，请在下载详情中查看原始错误。"

            hasAny(lower, "mp3 校验失败", "lame 输出未通过", "音频转码结果不是有效 mp3") ->
                "音频转换完成，但生成的 MP3 没有通过完整性校验。" +
                    "解决办法：请直接点击重试，App 会复用已下载的转换源重新生成，不会重复下载整段视频；" +
                    "若仍失败，请保留下载详情中的高级信息。"

            hasAny(lower, "源文件中没有可解码的音频轨道", "没有可解码音轨") ->
                "已下载的转换源没有可解码音轨。" +
                    "解决办法：请重新智能读取后选择其他画质；若平台没有独立音频，App 会改用带声音的视频转换。"

            hasAny(lower, "unexpected end of stream", "premature eof", "connection reset", "connection closed", "stream was reset") ->
                "网络传输中断，服务器提前关闭了连接。" +
                    "解决办法：请保持 App 运行并点击重试，App 会重新探测地址并续传。"

            hasAny(
                lower,
                "certificate_verify_failed",
                "hostname mismatch",
                "certificate is not valid for",
                "sslpeerunverifiedexception",
            ) ->
                "安全连接证书与平台域名不匹配，DNS 或代理可能把请求导向了错误节点。" +
                    "解决办法：请切换代理或 DNS 后重试；哔哩哔哩也可复制完整的 bilibili.com 视频地址。"

            hasAny(lower, "timed out", "timeout", "network is unreachable", "temporary failure", "urlopen error") ->
                "网络连接超时或当前网络不可达。" +
                    "解决办法：请检查 Wi-Fi、移动网络和代理，恢复后 App 会继续任务。"

            lower.contains("ssl") || lower.contains("certificate") ->
                "安全连接建立失败，可能受证书、代理或网络拦截影响。" +
                    "解决办法：请更换网络或检查代理设置后重试。"

            hasAny(lower, "no space left", "enospc", "disk full") ->
                "手机可用存储空间不足。解决办法：请清理至少大于成品体积的空间后重试。"

            lower.contains("permission denied") || evidence.contains("权限") ->
                "当前保存位置没有写入权限。" +
                    "解决办法：请到“设置 → 保存位置”重新选择文件夹，然后重试。"

            hasAny(lower, "unsupported url", "extractor", "yt-dlp") || evidence.contains("链接未能解析") ->
                "平台解析失败，链接格式可能不受支持，或平台规则已更新。" +
                    "解决办法：请重新复制官方分享链接并智能读取；仍失败可在详情中查看原始错误。"

            evidence.isNotBlank() && containsChinese(evidence) && hasAction(evidence) -> evidence
            evidence.isNotBlank() && containsChinese(evidence) ->
                "${evidence.trimEnd('。')}。解决办法：$genericRetryAction"

            else -> "${fallbackProblem.trimEnd('。')}。解决办法：${fallbackAction.trimEnd('。')}。"
        }
    }

    private fun loginAction(platform: DownloadPlatform?): String = when (platform) {
        DownloadPlatform.YOUTUBE ->
            "请到“设置 → 账号与权限 → YouTube”重新导入最新 cookies.txt，再重新智能读取并重试。"
        DownloadPlatform.DOUYIN ->
            "请到“设置 → 账号与权限 → 抖音”重新登录，再重新智能读取并重试。"
        DownloadPlatform.TIKTOK ->
            "请到“设置 → 账号与权限 → TikTok”重新登录，再重新智能读取并重试。"
        DownloadPlatform.BILIBILI ->
            "请到“设置 → 账号与权限 → 哔哩哔哩”重新登录，再重新智能读取并重试。"
        DownloadPlatform.XIAOHONGSHU ->
            "请到“设置 → 账号与权限 → 小红书”重新登录，再重新智能读取并重试。"
        null ->
            "请到“设置 → 账号与权限”更新对应平台的登录信息，再重新智能读取并重试。"
    }

    private fun inferPlatform(lower: String): DownloadPlatform? = when {
        lower.contains("youtube") -> DownloadPlatform.YOUTUBE
        lower.contains("douyin") -> DownloadPlatform.DOUYIN
        lower.contains("tiktok") -> DownloadPlatform.TIKTOK
        lower.contains("bilibili") -> DownloadPlatform.BILIBILI
        lower.contains("xiaohongshu") || lower.contains("rednote") -> DownloadPlatform.XIAOHONGSHU
        else -> null
    }

    private fun hasAny(value: String, vararg parts: String): Boolean = parts.any(value::contains)
    private fun hasHttpStatus(value: String, status: Int): Boolean = Regex("(^|\\D)$status(\\D|$)").containsMatchIn(value)
    private fun containsChinese(value: String): Boolean = value.any { it in '\u4e00'..'\u9fff' }
    private fun hasAction(value: String): Boolean = listOf("请", "建议", "重试", "检查", "更换").any(value::contains)

    private const val genericRetryAction = "请重新智能读取链接后重试；仍失败可在下载详情中查看原始错误"
}
