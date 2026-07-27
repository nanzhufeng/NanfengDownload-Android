package com.nanzhufeng.videodownloader.domain.download.video

data class VideoSegmentRange(
    val startUs: Long,
    val endUs: Long,
)

object VideoSegmentPlanner {
    fun plan(
        durationUs: Long,
        segmentCount: Int,
        syncPointsUs: List<Long>,
    ): List<VideoSegmentRange> {
        require(segmentCount in 1..MAX_SEGMENTS) {
            "视频分段数量必须在 1 到 $MAX_SEGMENTS 之间"
        }
        require(durationUs > 0L) { "无法读取视频时长，不能安全分段" }
        if (segmentCount == 1) return listOf(VideoSegmentRange(0L, durationUs))
        require(durationUs >= segmentCount * MIN_SEGMENT_DURATION_US) {
            "视频太短，无法安全分成 $segmentCount 段；请减少分段数量"
        }

        val usableSyncPoints = syncPointsUs
            .asSequence()
            .filter { it in 1 until durationUs }
            .distinct()
            .sorted()
            .toList()
        val boundaries = mutableListOf(0L)
        repeat(segmentCount - 1) { zeroBasedIndex ->
            val targetUs = durationUs * (zeroBasedIndex + 1L) / segmentCount
            val previousBoundary = boundaries.last()
            val latestAllowed = durationUs - (segmentCount - zeroBasedIndex - 1L) * MIN_SEGMENT_DURATION_US
            val nextBoundary = usableSyncPoints
                .filter { point ->
                    point > previousBoundary &&
                        point - previousBoundary >= MIN_SEGMENT_DURATION_US &&
                        point <= latestAllowed
                }
                .minByOrNull { point -> kotlin.math.abs(point - targetUs) }
                ?: usableSyncPoints.lastOrNull { point ->
                point > previousBoundary &&
                    point - previousBoundary >= MIN_SEGMENT_DURATION_US &&
                    point <= latestAllowed
            }
            requireNotNull(nextBoundary) {
                "视频关键帧过少，无法安全分成 $segmentCount 段；请减少分段数量"
            }
            boundaries += nextBoundary
        }
        boundaries += durationUs

        return boundaries.zipWithNext { start, end ->
            require(end > start) { "视频分段边界无效，请减少分段数量后重试" }
            VideoSegmentRange(startUs = start, endUs = end)
        }
    }

    private const val MAX_SEGMENTS = 20
    private const val MIN_SEGMENT_DURATION_US = 500_000L
}
