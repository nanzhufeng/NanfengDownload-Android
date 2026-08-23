package com.nanzhufeng.videodownloader.domain.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.model.TransferReportOutcome
import com.nanzhufeng.videodownloader.domain.download.audio.AudioTranscoder
import com.nanzhufeng.videodownloader.domain.download.audio.AudioSourceFileValidator
import com.nanzhufeng.videodownloader.domain.download.audio.Mp3AudioTranscoder
import com.nanzhufeng.videodownloader.domain.download.video.AndroidMp4TrackMuxer
import com.nanzhufeng.videodownloader.domain.download.video.AndroidMedia3VideoResolutionTranscoder
import com.nanzhufeng.videodownloader.domain.download.video.AndroidMp4VideoSegmenter
import com.nanzhufeng.videodownloader.domain.download.video.MediaTrackMuxer
import com.nanzhufeng.videodownloader.domain.download.video.VideoResolutionTranscoder
import com.nanzhufeng.videodownloader.domain.download.video.VideoSegmenter
import com.nanzhufeng.videodownloader.probe.DirectDownloadRequest
import com.nanzhufeng.videodownloader.probe.FileDownloader
import com.nanzhufeng.videodownloader.probe.HttpFileDownloader
import com.nanzhufeng.videodownloader.probe.MediaFileValidator
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

fun interface DownloadTransferModeSink {
    suspend fun update(taskId: String, mode: DownloadConnectionMode, connectionCount: Int)

    companion object {
        val NONE = DownloadTransferModeSink { _, _, _ -> }
    }
}

fun interface DownloadThroughputReportSink {
    suspend fun record(report: DownloadThroughputReport)

    companion object {
        val NONE = DownloadThroughputReportSink { }
    }
}

fun interface DownloadProcessingSink {
    suspend fun update(taskId: String, stage: DownloadProcessingStage, progressPercent: Int)

    companion object {
        val NONE = DownloadProcessingSink { _, _, _ -> }
    }
}

@UnstableApi
class DirectMediaTransfer(
    context: Context,
    downloader: FileDownloader = HttpFileDownloader(),
    audioTranscoder: AudioTranscoder = Mp3AudioTranscoder(),
    private val mediaTrackMuxer: MediaTrackMuxer = AndroidMp4TrackMuxer(),
    private val videoSegmenter: VideoSegmenter = AndroidMp4VideoSegmenter(),
    private val videoResolutionTranscoder: VideoResolutionTranscoder =
        AndroidMedia3VideoResolutionTranscoder(context.applicationContext),
    private val performanceReporter: DownloadPerformanceReporter = DownloadPerformanceReporter.NONE,
    private val transferModeSink: DownloadTransferModeSink = DownloadTransferModeSink.NONE,
    private val throughputReportSink: DownloadThroughputReportSink = DownloadThroughputReportSink.NONE,
    private val processingSink: DownloadProcessingSink = DownloadProcessingSink.NONE,
    private val isReusableAudioSource: (File) -> Boolean = {
        AudioSourceFileValidator.inspect(it).valid
    },
    private val transferConcurrencyBudget: TransferConcurrencyBudget = TransferConcurrencyBudget(),
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) : MediaTransfer {
    private val applicationContext = context.applicationContext
    private val cacheRoot = File(applicationContext.cacheDir, "downloads")
    private val audioSourcePreparer = AudioSourcePreparer(audioTranscoder)
    private val streamDownloadCoordinator = StreamDownloadCoordinator(downloader)

    override suspend fun download(
        task: QueuedDownload,
        source: ResolvedMedia,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ): PreparedMedia = withContext(Dispatchers.IO) {
        val budgetLease = transferConcurrencyBudget.enter(task.task.taskId)
        val transferScope = CoroutineScope(currentCoroutineContext())
        val streamModes = ConcurrentHashMap<String, Pair<DownloadConnectionMode, Int>>()
        fun modeObserver(streamLabel: String): (DownloadConnectionMode, Int) -> Unit = { mode, count ->
            streamModes[streamLabel] = mode to count
            val snapshots = streamModes.values.toList()
            val totalConnections = snapshots.sumOf { it.second }.coerceAtLeast(1)
            val aggregateMode = if (
                snapshots.any { it.first == DownloadConnectionMode.MULTI } || totalConnections > 1
            ) {
                DownloadConnectionMode.MULTI
            } else {
                DownloadConnectionMode.SINGLE
            }
            transferScope.launch {
                transferModeSink.update(task.task.taskId, aggregateMode, totalConnections)
            }
        }
        val directory = File(cacheRoot, task.task.taskId).apply { mkdirs() }
        val cancelled = AtomicBoolean(false)
        val completionHandle = currentCoroutineContext()[Job]
            ?.invokeOnCompletion { cancelled.set(true) }
        try {
            if (source.imageUrls.isNotEmpty()) {
                processingSink.update(
                    task.task.taskId,
                    DownloadProcessingStage.NETWORK_MEDIA,
                    0,
                )
                val galleryRequests = source.imageUrls.flatMapIndexed { index, image ->
                    buildList {
                        val itemNumber = (index + 1).toString().padStart(2, '0')
                        image.motionUrl?.takeIf(String::isNotBlank)?.let { motionUrl ->
                            add(
                                DirectDownloadRequest(
                                    url = motionUrl,
                                    headers = source.headers,
                                    target = File(
                                        directory,
                                        "motion-$itemNumber.${image.motionExtension.safeExtension("mp4")}",
                                    ),
                                    taskId = task.task.taskId,
                                    platform = task.media.platform,
                                    streamLabel = "动态图片 ${index + 1}/${source.imageUrls.size}",
                                    transferPolicy = PlatformTransferPolicy.forPlatform(task.media.platform),
                                    reprobeCount = source.reprobeCount,
                                    finalUrlValidator = douyinImageFinalUrlValidator(task),
                                    onModeResolved = modeObserver("动态图片 ${index + 1}/${source.imageUrls.size}"),
                                ),
                            )
                        } ?: add(
                            DirectDownloadRequest(
                                url = image.url,
                                headers = source.headers,
                                target = File(directory, "image-$itemNumber.${image.extension.safeExtension("jpg")}"),
                                taskId = task.task.taskId,
                                platform = task.media.platform,
                                streamLabel = "图片 ${index + 1}/${source.imageUrls.size}",
                                transferPolicy = PlatformTransferPolicy.forImage(task.media.platform),
                                reprobeCount = source.reprobeCount,
                                finalUrlValidator = douyinImageFinalUrlValidator(task),
                                onModeResolved = modeObserver("图片 ${index + 1}/${source.imageUrls.size}"),
                            ),
                        )
                    }
                }
                // The downloader already learns the byte length while opening a stream.
                // A separate serial 1-byte probe for every image made a 14-image gallery
                // wait for 14 additional round trips before downloading anything.
                val downloadedFiles = downloadStreams(
                    task = task,
                    requests = galleryRequests,
                    cancelled = cancelled,
                    onProgress = onProgress,
                    // Douyin signed image URLs can reply with an empty or
                    // truncated body when several gallery requests open at
                    // once.  Preserve every original image by reading that
                    // gallery serially; other platforms keep parallel image
                    // transfers.
                    maxConcurrentStreams = if (task.media.platform == com.nanzhufeng.videodownloader.core.model.DownloadPlatform.DOUYIN) {
                        1
                    } else {
                        MAX_PARALLEL_GALLERY_STREAMS
                    },
                )
                require(downloadedFiles.size == source.imageUrls.size) {
                    "图文作品下载数量不完整：${downloadedFiles.size}/${source.imageUrls.size}"
                }
                val galleryFiles = downloadedFiles.mapIndexed { index, downloaded ->
                    val image = source.imageUrls[index]
                    val isMotion = !image.motionUrl.isNullOrBlank()
                    require(MediaFileValidator.isLikelyMedia(downloaded)) {
                        "下载结果不是有效${if (isMotion) "动态图片" else "图片"}文件：${downloaded.name}"
                    }
                    if (isMotion) downloaded else downloaded.withDetectedImageExtension()
                }
                require(galleryFiles.isNotEmpty()) { "图文作品未返回可下载图片" }
                return@withContext PreparedMedia(
                    file = galleryFiles.first(),
                    // A gallery can start with a Live Photo MP4. Keep the
                    // gallery publication policy even when it has no still
                    // frame, while MediaStore still selects each real MIME.
                    mimeType = "image/jpeg",
                    additionalFiles = galleryFiles.drop(1),
                    galleryItemCount = source.imageUrls.size,
                )
            }
            val isAudioOnly = task.task.resolution == ResolutionPreset.AUDIO_MP3
            val audioStreamLabel = if (source.audioFromVideoSource) {
                "视频转音频源"
            } else {
                "音频源"
            }
            val primaryRequest = DirectDownloadRequest(
                url = source.videoUrl,
                headers = source.headers,
                target = File(
                    directory,
                    if (isAudioOnly) {
                        "audio-source.${source.videoExtension.safeExtension("m4a")}"
                    } else {
                        "video.${source.videoExtension.safeExtension("mp4")}"
                    },
                ),
                taskId = task.task.taskId,
                platform = task.media.platform,
                streamLabel = if (isAudioOnly) audioStreamLabel else "视频流",
                transferPolicy = if (isAudioOnly) {
                    PlatformTransferPolicy.forAudioSource(
                        task.media.platform,
                        source.audioFromVideoSource,
                    )
                } else {
                    PlatformTransferPolicy.forPlatform(task.media.platform)
                },
                cookieHeader = source.videoCookieHeader,
                cookieHost = source.videoUrl.requestHost(),
                reprobeCount = source.reprobeCount,
                onModeResolved = modeObserver(if (isAudioOnly) audioStreamLabel else "视频流"),
            )
            if (isAudioOnly) {
                processingSink.update(
                    task.task.taskId,
                    if (source.audioFromVideoSource) {
                        DownloadProcessingStage.NETWORK_VIDEO_TO_AUDIO
                    } else {
                        DownloadProcessingStage.NETWORK_AUDIO
                    },
                    0,
                )
                val cachedSource = primaryRequest.target.takeIf { file ->
                    isReusableAudioSource(file) &&
                        isAudioSourceLengthCompatible(file.length(), source.videoSizeBytes)
                }
                if (cachedSource == null && primaryRequest.target.exists() && !primaryRequest.target.delete()) {
                    throw IOException("无法清理损坏的音频转换源缓存")
                }
                val reusableSource = cachedSource ?: adoptReusableAudioSource(
                    mediaKey = task.media.mediaKey,
                    target = primaryRequest.target,
                    expectedBytes = source.videoSizeBytes,
                )
                val primary = reusableSource ?: downloadStreams(
                    task,
                    listOf(primaryRequest),
                    cancelled,
                    onProgress,
                ).single().also {
                    writeAudioSourceIdentity(it.parentFile, task.media.mediaKey)
                }
                onProgress(primary.length(), primary.length(), 0L, null)
                writeAudioSourceIdentity(primary.parentFile, task.media.mediaKey)
                processingSink.update(
                    task.task.taskId,
                    DownloadProcessingStage.TRANSCODING,
                    0,
                )
                val lastProgress = java.util.concurrent.atomic.AtomicInteger(-1)
                val progressUpdates = Channel<Int>(Channel.CONFLATED)
                val progressJob = transferScope.launch {
                    for (progress in progressUpdates) {
                        processingSink.update(
                            task.task.taskId,
                            DownloadProcessingStage.TRANSCODING,
                            progress,
                        )
                    }
                }
                val segmentCount = task.task.audioSegmentCount.coerceIn(1, MAX_AUDIO_SEGMENTS)
                val destinations = if (segmentCount == 1) {
                    listOf(File(directory, "audio.mp3"))
                } else {
                    (1..segmentCount).map { index ->
                        File(
                            directory,
                            "audio-segment-${index.toString().padStart(2, '0')}-of-$segmentCount.mp3",
                        )
                    }
                }
                val transcodeStartedAt = wallClockMillis()
                val transcodeStartedNanos = monotonicNanos()
                var transcodeOutcome = TransferReportOutcome.FAILED
                var transcodeError: Throwable? = null
                var transcodeOutputBytes = 0L
                val prepared = try {
                    measureDownloadStage(
                        taskId = task.task.taskId,
                        stage = "audio_transcode",
                        reporter = performanceReporter,
                        nowNanos = monotonicNanos,
                    ) {
                        audioSourcePreparer.prepareSegments(
                            source = primary,
                            destinations = destinations,
                            cancelled = cancelled,
                            onProgress = { progressPercent ->
                                val bounded = progressPercent.coerceIn(0, 100)
                                while (true) {
                                    val previous = lastProgress.get()
                                    if (bounded <= previous) break
                                    if (lastProgress.compareAndSet(previous, bounded)) {
                                        progressUpdates.trySend(bounded)
                                        break
                                    }
                                }
                            },
                        )
                    }.also {
                        transcodeOutputBytes = it.files.sumOf(File::length)
                        transcodeOutcome = TransferReportOutcome.COMPLETED
                    }
                } catch (error: Throwable) {
                    transcodeError = error
                    transcodeOutcome = if (error is java.util.concurrent.CancellationException) {
                        TransferReportOutcome.CANCELLED
                    } else {
                        TransferReportOutcome.FAILED
                    }
                    throw error
                } finally {
                    progressUpdates.close()
                    progressJob.join()
                    val elapsedMillis =
                        ((monotonicNanos() - transcodeStartedNanos).coerceAtLeast(0L) / 1_000_000L)
                    withContext(NonCancellable) {
                        throughputReportSink.record(
                            DownloadThroughputReport(
                                reportId = UUID.randomUUID().toString(),
                                taskId = task.task.taskId,
                                platform = task.media.platform,
                                streamLabel = MP3_TRANSCODE_STREAM_LABEL,
                                outcome = transcodeOutcome,
                                connectionMode = DownloadConnectionMode.UNKNOWN,
                                connectionCount = 0,
                                rangeSupported = false,
                                expectedBytes = primary.length(),
                                committedBytes = transcodeOutputBytes,
                                networkBytes = 0L,
                                startedAt = transcodeStartedAt,
                                finishedAt = transcodeStartedAt + elapsedMillis,
                                elapsedMillis = elapsedMillis,
                                averageBytesPerSecond = 0L,
                                peakBytesPerSecond = 0L,
                                retryCount = 0,
                                reprobeCount = source.reprobeCount,
                                fallbackReason = if (segmentCount == 1) {
                                    "本机解码并编码为 MP3"
                                } else {
                                    "本机解码并均分编码为 $segmentCount 段 MP3"
                                },
                                errorSummary = transcodeError?.message?.take(400),
                            ),
                        )
                    }
                }
                processingSink.update(
                    task.task.taskId,
                    DownloadProcessingStage.TRANSCODING,
                    100,
                )
                if (prepared.file != primary) {
                    primary.delete()
                }
                return@withContext prepared
            }

            processingSink.update(
                task.task.taskId,
                DownloadProcessingStage.NETWORK_MEDIA,
                0,
            )
            val audioUrl = source.audioUrl
            if (audioUrl.isNullOrBlank()) {
                val primary = downloadStreams(task, listOf(primaryRequest), cancelled, onProgress).single()
                require(MediaFileValidator.isLikelyMedia(primary)) { "下载结果不是有效媒体文件" }
                return@withContext prepareVideoSegments(task, source, primary, directory, cancelled)
            }

            val audioRequest = DirectDownloadRequest(
                url = audioUrl,
                headers = source.headers,
                target = File(directory, "audio.${source.audioExtension.safeExtension("m4a")}"),
                taskId = task.task.taskId,
                platform = task.media.platform,
                streamLabel = "音频流",
                transferPolicy = PlatformTransferPolicy.forAudio(task.media.platform),
                cookieHeader = source.audioCookieHeader,
                cookieHost = audioUrl.requestHost(),
                reprobeCount = source.reprobeCount,
                onModeResolved = modeObserver("音频流"),
            )
            val (primary, audio) = downloadStreams(
                task,
                listOf(primaryRequest, audioRequest),
                cancelled,
                onProgress,
            )
            val merged = File(directory, "merged.mp4")
            processingSink.update(task.task.taskId, DownloadProcessingStage.MERGING, 0)
            val mergeProgressUpdates = Channel<Int>(Channel.CONFLATED)
            val mergeProgressJob = CoroutineScope(currentCoroutineContext()).launch {
                for (progress in mergeProgressUpdates) {
                    processingSink.update(
                        task.task.taskId,
                        DownloadProcessingStage.MERGING,
                        progress.coerceIn(0, 100),
                    )
                }
            }
            try {
                measureDownloadStage(
                    taskId = task.task.taskId,
                    stage = "mux",
                    reporter = performanceReporter,
                    nowNanos = monotonicNanos,
                ) {
                    mediaTrackMuxer.merge(
                        video = primary,
                        audio = audio,
                        output = merged,
                        cancelled = cancelled,
                        onProgress = mergeProgressUpdates::trySend,
                    )
                }
            } finally {
                mergeProgressUpdates.close()
                mergeProgressJob.join()
            }
            require(MediaFileValidator.isLikelyMedia(merged)) { "合并结果不是有效媒体文件" }
            primary.delete()
            audio.delete()
            prepareVideoSegments(task, source, merged, directory, cancelled)
        } finally {
            completionHandle?.dispose()
            budgetLease.close()
        }
    }

    private suspend fun prepareVideoSegments(
        task: QueuedDownload,
        source: ResolvedMedia,
        video: File,
        directory: File,
        cancelled: AtomicBoolean,
    ): PreparedMedia {
        val preparedVideo = if (task.task.resolution == ResolutionPreset.UP_TO_360P) {
            processingSink.update(task.task.taskId, DownloadProcessingStage.TRANSCODING, 0)
            val progressUpdates = Channel<Int>(Channel.CONFLATED)
            val progressJob = CoroutineScope(currentCoroutineContext()).launch {
                for (progress in progressUpdates) {
                    processingSink.update(
                        task.task.taskId,
                        DownloadProcessingStage.TRANSCODING,
                        progress.coerceIn(0, 100),
                    )
                }
            }
            try {
                videoResolutionTranscoder.transcodeTo360p(
                    source = video,
                    destination = File(directory, "video-360.mp4"),
                    cancelled = cancelled,
                    onProgress = { progress ->
                        progressUpdates.trySend(progress)
                        Unit
                    },
                )
            } finally {
                progressUpdates.close()
                progressJob.join()
            }.also { output ->
                if (output != video) video.delete()
            }
        } else {
            video
        }
        val segmentCount = task.task.audioSegmentCount.coerceIn(1, MAX_MEDIA_SEGMENTS)
        if (segmentCount == 1) return PreparedMedia(preparedVideo, "video/mp4")

        processingSink.update(
            task.task.taskId,
            DownloadProcessingStage.VIDEO_SEGMENTING,
            0,
        )
        val destinations = (1..segmentCount).map { index ->
            File(
                directory,
                "video-segment-${index.toString().padStart(2, '0')}-of-$segmentCount.mp4",
            )
        }
        val inputBytes = preparedVideo.length()
        val startedAt = wallClockMillis()
        val startedNanos = monotonicNanos()
        var outcome = TransferReportOutcome.FAILED
        var errorSummary: String? = null
        var committedBytes = 0L
        val progressUpdates = Channel<Int>(Channel.CONFLATED)
        val progressScope = CoroutineScope(currentCoroutineContext())
        val progressJob = progressScope.launch {
            for (progress in progressUpdates) {
                processingSink.update(
                    task.task.taskId,
                    DownloadProcessingStage.VIDEO_SEGMENTING,
                    progress.coerceIn(0, 100),
                )
            }
        }
        try {
            val files = measureDownloadStage(
                taskId = task.task.taskId,
                stage = "video_segment",
                reporter = performanceReporter,
                nowNanos = monotonicNanos,
            ) {
                videoSegmenter.split(
                    source = preparedVideo,
                    destinations = destinations,
                    cancelled = cancelled,
                    onProgress = progressUpdates::trySend,
                )
            }
            require(files.size == segmentCount && files.all(MediaFileValidator::isLikelyMedia)) {
                "视频分段结果不完整，请减少分段数量后重试"
            }
            committedBytes = files.sumOf(File::length)
            outcome = TransferReportOutcome.COMPLETED
            progressUpdates.trySend(100)
            preparedVideo.delete()
            return PreparedMedia(
                file = files.first(),
                mimeType = "video/mp4",
                additionalFiles = files.drop(1),
            )
        } catch (error: Throwable) {
            errorSummary = error.message?.take(400)
            outcome = if (error is java.util.concurrent.CancellationException) {
                TransferReportOutcome.CANCELLED
            } else {
                TransferReportOutcome.FAILED
            }
            throw error
        } finally {
            progressUpdates.close()
            progressJob.join()
            val elapsedMillis =
                ((monotonicNanos() - startedNanos).coerceAtLeast(0L) / 1_000_000L)
            withContext(NonCancellable) {
                throughputReportSink.record(
                    DownloadThroughputReport(
                        reportId = UUID.randomUUID().toString(),
                        taskId = task.task.taskId,
                        platform = task.media.platform,
                        streamLabel = VIDEO_SEGMENT_STREAM_LABEL,
                        outcome = outcome,
                        connectionMode = DownloadConnectionMode.UNKNOWN,
                        connectionCount = 0,
                        rangeSupported = false,
                        expectedBytes = inputBytes,
                        committedBytes = committedBytes,
                        networkBytes = 0L,
                        startedAt = startedAt,
                        finishedAt = startedAt + elapsedMillis,
                        elapsedMillis = elapsedMillis,
                        averageBytesPerSecond = 0L,
                        peakBytesPerSecond = 0L,
                        retryCount = 0,
                        reprobeCount = source.reprobeCount,
                        fallbackReason = "按关键帧无损转封装为 $segmentCount 段 MP4",
                        errorSummary = errorSummary,
                    ),
                )
            }
        }
    }

    private suspend fun downloadStreams(
        task: QueuedDownload,
        requests: List<DirectDownloadRequest>,
        cancelled: AtomicBoolean,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
        maxConcurrentStreams: Int = requests.size,
    ): List<File> = measureDownloadStage(
        taskId = task.task.taskId,
        stage = "network_transfer",
        reporter = performanceReporter,
        nowNanos = monotonicNanos,
    ) {
        val activeStreamCount = minOf(maxConcurrentStreams, requests.count { request ->
            !MediaFileValidator.isLikelyMedia(request.target)
        }.coerceAtLeast(1))
        val reports = ConcurrentLinkedQueue<DownloadThroughputReport>()
        try {
            streamDownloadCoordinator.download(
                requests = requests.map { request ->
                    val budgetedPolicy = request.transferPolicy?.let { policy ->
                        policy.copy(
                            maxConnections = transferConcurrencyBudget.connectionsPerStream(
                                taskId = task.task.taskId,
                                activeStreamCount = activeStreamCount,
                                requestedConnections = policy.maxConnections,
                            ),
                        )
                    }
                    request.copy(
                        transferPolicy = budgetedPolicy,
                        onReport = reports::add,
                    )
                },
                cancelled = cancelled,
                maxConcurrentStreams = maxConcurrentStreams,
                onProgress = { progress -> progress.forwardTo(onProgress) },
            )
        } finally {
            reports.forEach { throughputReportSink.record(it) }
        }
    }

    private suspend fun TransferProgress.forwardTo(
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ) = onProgress(downloadedBytes, totalBytes, speedBytesPerSecond, remainingSeconds)

    private fun String?.safeExtension(fallback: String): String = this
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
        ?: fallback

    private fun String.requestHost(): String = runCatching {
        java.net.URI(this).host.orEmpty()
    }.getOrDefault("")

    private fun douyinImageFinalUrlValidator(task: QueuedDownload): (String) -> Unit =
        if (task.media.platform == com.nanzhufeng.videodownloader.core.model.DownloadPlatform.DOUYIN) {
            { finalUrl ->
                require(!finalUrl.contains("tplv-dy-water", ignoreCase = true)) {
                    "抖音原图请求被重定向为带水印变体，已停止保存；请重新读取作品后重试"
                }
            }
        } else {
            {}
        }

    private fun File.imageMimeType(): String =
        detectImageMediaFormat()?.mimeType ?: "image/jpeg"

    private fun adoptReusableAudioSource(
        mediaKey: String,
        target: File,
        expectedBytes: Long,
    ): File? {
        val candidates = cacheRoot.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.filterNot { it.absolutePath == target.parentFile?.absolutePath }
            ?.flatMap { directory ->
                directory.listFiles()
                    ?.asSequence()
                    ?.filter { file ->
                        file.isFile &&
                            file.name.startsWith(AUDIO_SOURCE_PREFIX) &&
                            !file.name.endsWith(".part")
                    }
                    ?: emptySequence()
            }
            ?.mapNotNull { file ->
                val identity = File(file.parentFile, AUDIO_SOURCE_IDENTITY_FILE)
                    .takeIf(File::isFile)
                    ?.runCatching(File::readText)
                    ?.getOrNull()
                    ?.trim()
                val identityMatch = identity == mediaKey
                val legacySizeMatch = identity == null &&
                    expectedBytes > 0L &&
                    file.length() == expectedBytes
                val sourceLengthMatch = isAudioSourceLengthCompatible(file.length(), expectedBytes)
                if (
                    (identityMatch || legacySizeMatch) &&
                    sourceLengthMatch &&
                    isReusableAudioSource(file)
                ) {
                    Triple(file, identityMatch, file.lastModified())
                } else {
                    null
                }
            }
            ?.sortedWith(
                compareByDescending<Triple<File, Boolean, Long>> { it.second }
                    .thenByDescending { it.third },
            )
            ?.map { it.first }
            ?.toList()
            .orEmpty()
        val source = candidates.firstOrNull() ?: return null

        target.parentFile?.mkdirs()
        if (target.exists() && !target.delete()) return null
        val adopted = runCatching {
            Files.createLink(target.toPath(), source.toPath())
            target
        }.recoverCatching {
            source.copyTo(target, overwrite = false)
        }.getOrNull()
        if (adopted == null || !isReusableAudioSource(adopted)) {
            adopted?.delete()
            return null
        }
        writeAudioSourceIdentity(target.parentFile, mediaKey)
        return adopted
    }

    private fun writeAudioSourceIdentity(directory: File?, mediaKey: String) {
        if (directory == null || mediaKey.isBlank()) return
        runCatching {
            directory.mkdirs()
            File(directory, AUDIO_SOURCE_IDENTITY_FILE).writeText(mediaKey)
        }
    }

    private companion object {
        const val AUDIO_SOURCE_PREFIX = "audio-source."
        const val AUDIO_SOURCE_IDENTITY_FILE = "audio-source.media-key"
        const val MP3_TRANSCODE_STREAM_LABEL = "MP3 转码"
        const val MAX_AUDIO_SEGMENTS = 20
        const val MAX_MEDIA_SEGMENTS = 20
        const val VIDEO_SEGMENT_STREAM_LABEL = "视频本机分段"
        const val MAX_PARALLEL_GALLERY_STREAMS = 3
    }
}

internal fun isAudioSourceLengthCompatible(actualBytes: Long, expectedBytes: Long): Boolean {
    if (expectedBytes <= 0L) return true
    val allowedDifference = maxOf(256L * 1_024L, expectedBytes / 100L)
    return kotlin.math.abs(actualBytes - expectedBytes) <= allowedDifference
}
