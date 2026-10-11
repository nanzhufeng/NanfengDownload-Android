package com.nanzhufeng.videodownloader.core.ui

internal enum class IslandTone { SUCCESS, ERROR, INFO }

internal data class IslandNotice(
    val title: String,
    val detail: String = "",
    val tone: IslandTone = IslandTone.INFO,
) {
    val spokenText: String get() = listOf(title, detail).filter(String::isNotBlank).joinToString("。")
    val readingMillis: Long get() = maxOf(
        if (tone == IslandTone.ERROR) 6_500L else 3_200L,
        spokenText.codePointCount(0, spokenText.length) * 40L + 1_500L,
    ).coerceAtMost(12_000L)
}

/** A timer belonging to an older message must never close its replacement. */
internal class IslandNoticeState {
    var generation = 0L
        private set
    var current: IslandNotice? = null
        private set

    fun present(notice: IslandNotice): Long {
        current = notice
        return ++generation
    }

    fun dismiss(expectedGeneration: Long): Boolean {
        if (generation != expectedGeneration || current == null) return false
        current = null
        return true
    }
}
