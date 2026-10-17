package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.domain.history.HistoryCleanupResult

internal fun historyCleanupFeedback(result: HistoryCleanupResult): String = buildString {
    append("已检查 ${result.checked} 条，清理 ${result.removed} 条失效记录")
    if (result.uncertain > 0) append("；${result.uncertain} 条暂无法确认，已保留")
    if (result.changed > 0) append("；${result.changed} 条记录已变化，已跳过")
    if (result.restored > 0) append("；恢复 ${result.restored} 条可用记录")
}
