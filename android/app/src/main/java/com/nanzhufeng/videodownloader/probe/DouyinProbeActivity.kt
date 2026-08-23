package com.nanzhufeng.videodownloader.probe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.nanzhufeng.videodownloader.NanzhufengApplication
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONTokener

class DouyinProbeActivity : Activity() {
    private lateinit var webView: WebView
    private val currentPageUrl = AtomicReference("")
    private val handler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var completed = false
    private var canonicalRedirected = false
    private var requiresCompleteGallery = false
    private var strictGalleryReloaded = false
    private var lastGalleryProbeState = ""
    private val captureTimeout = Runnable {
        finishWithError(
            if (requiresCompleteGallery) {
                "抖音页面未返回完整无水印图集，已停止读取，未创建不完整任务"
            } else {
                "抖音页面未在规定时间内返回可下载的视频或图文图片，请确认作品可播放后重试"
            },
        )
    }
    private val inspectVideoSource = object : Runnable {
        override fun run() {
            if (destroyed || !::webView.isInitialized) return
            if (requiresCompleteGallery) {
                inspectImageGallery()
                return
            }
            webView.evaluateJavascript(VIDEO_SOURCE_SCRIPT) { result ->
                if (destroyed) return@evaluateJavascript
                val video = parseVideoSource(result)
                if (video.sourceUrl.isNotBlank()) {
                    val captured = DouyinCaptureStore.capture(currentPageUrl.get(), video.sourceUrl)
                    if (captured != null) {
                        completeCapture(captured)
                        return@evaluateJavascript
                    }
                }
                // 动态图文页会先请求预览封面，稍后才给 video.currentSrc。
                // 一旦页面声明了 video，绝不能把首张封面当作图文成品。
                if (!shouldTryImageFallback(video.hasVideoElement)) {
                    handler.postDelayed(this, VIDEO_SOURCE_POLL_MILLIS)
                    return@evaluateJavascript
                }
                inspectImageGallery()
            }
        }
    }

    private fun inspectImageGallery() {
        webView.evaluateJavascript(IMAGE_SOURCE_SCRIPT) { rawImages ->
            if (destroyed || completed) return@evaluateJavascript
            val imageSources = parseImageSources(rawImages)
            val probeState = buildString {
                append("structured=").append(imageSources.structuredComplete)
                append(", expected=").append(imageSources.expectedCount)
                append(", candidates=").append(imageSources.urls.size)
                append(", awaiting=").append(imageSources.awaitingStructuredImages)
            }
            if (probeState != lastGalleryProbeState) {
                lastGalleryProbeState = probeState
                Log.i(VIDEO_SOURCE_LOG_TAG, "抖音图集读取状态：$probeState")
            }
            if (
                imageSources.awaitingStructuredImages ||
                (requiresCompleteGallery && !imageSources.structuredComplete)
            ) {
                // A Douyin note is fail-closed: never complete from the
                // currently rendered carousel slide.  Wait until the target
                // work declares its full image count and every original URL
                // is present in the same structured payload.
                handler.postDelayed(inspectVideoSource, VIDEO_SOURCE_POLL_MILLIS)
                return@evaluateJavascript
            }
            // `urls` is the exact target work's complete React Flight
            // `images[].urlList` projection.  It is already filtered to the
            // original template by the page script.  Do not round-trip it
            // through WebView request interception: image CDNs may not issue
            // a fetch for these URLs before the task is created, which used
            // to discard a complete structured gallery and end in a timeout.
            val structuredImages = imageSources.urls.distinct().takeIf {
                requiresCompleteGallery &&
                    imageSources.structuredComplete &&
                    DouyinCaptureStore.isVerifiedImageGallery(
                        imageUrls = it,
                        expectedCount = imageSources.expectedCount,
                        sourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
                    )
            }
            val capturedImages = structuredImages ?: run {
                val domImages = imageSources.urls.flatMap { url ->
                    DouyinCaptureStore.captureImage(currentPageUrl.get(), url)
                }
                resolveImageCandidates(
                    domImages = domImages,
                    interceptedImages = DouyinCaptureStore.capturedImageUrls(),
                    awaitingStructuredImages = imageSources.awaitingStructuredImages,
                    requiredCount = imageSources.expectedCount.takeIf { requiresCompleteGallery },
                )
            }
            if (capturedImages.isNotEmpty()) {
                completeCapture(
                    DouyinCaptureStore.CapturedMedia(
                        mediaUrl = "",
                        pageUrl = currentPageUrl.get(),
                        imageUrls = capturedImages,
                        imageExpectedCount = imageSources.expectedCount,
                        imageSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
                    ),
                )
            } else {
                handler.postDelayed(inspectVideoSource, VIDEO_SOURCE_POLL_MILLIS)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This Activity is only a background WebView network probe.  Suppress
        // both the framework window transition and the return transition: the
        // user must stay on the current downloader screen throughout parsing.
        window.setDimAmount(0f)
        window.setWindowAnimations(0)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        val sourceUrl = requireNotNull(intent.getStringExtra(EXTRA_URL)) {
            "缺少抖音页面地址"
        }
        requiresCompleteGallery = isDouyinNoteUrl(sourceUrl)
        DouyinCaptureStore.begin(sourceUrl)
        handler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MILLIS)

        val cookies = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            flush()
        }
        val canMuteWebView = WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)
        webView = WebView(this).apply {
            // 本 Activity 只承载后台媒体读取。页面仍可运行 JS/媒体加载，
            // 但不能覆盖首页而造成用户可见的网页闪屏。
            setBackgroundColor(Color.TRANSPARENT)
            alpha = 0f
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.loadWithOverviewMode = requiresCompleteGallery
            settings.useWideViewPort = requiresCompleteGallery
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            settings.userAgentString = if (requiresCompleteGallery) {
                DOUYIN_DESKTOP_USER_AGENT
            } else {
                mobileBrowserUserAgent(settings.userAgentString)
            }
            if (canMuteWebView) {
                WebViewCompat.setAudioMuted(this, true)
            }
            cookies.setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    view.evaluateJavascript(SILENCE_MEDIA_SCRIPT, null)
                    if (!requiresCompleteGallery && isDouyinNoteUrl(url) && !strictGalleryReloaded) {
                        // A short share can reveal `/note/` only after the
                        // first redirect. Restart that target once with the
                        // desktop page contract so the full React Flight
                        // gallery is available instead of the rendered slides.
                        strictGalleryReloaded = true
                        requiresCompleteGallery = true
                        DouyinCaptureStore.begin(url)
                        currentPageUrl.set(url)
                        view.settings.userAgentString = DOUYIN_DESKTOP_USER_AGENT
                        view.settings.loadWithOverviewMode = true
                        view.settings.useWideViewPort = true
                        view.settings.mediaPlaybackRequiresUserGesture = true
                        handler.removeCallbacks(captureTimeout)
                        handler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MILLIS)
                        view.stopLoading()
                        view.loadUrl(url, mapOf("Referer" to sourceUrl))
                        return
                    }
                    currentPageUrl.set(url)
                    DouyinCaptureStore.updatePage(url)
                    val canonicalUrl = canonicalDouyinVideoUrl(url)
                    if (!canonicalRedirected && canonicalUrl != null && canonicalUrl != url) {
                        canonicalRedirected = true
                        currentPageUrl.set(canonicalUrl)
                        DouyinCaptureStore.updatePage(canonicalUrl)
                        view.stopLoading()
                        view.loadUrl(canonicalUrl, mapOf("Referer" to sourceUrl))
                        return
                    }
                    super.onPageStarted(view, url, favicon)
                }

                override fun onLoadResource(view: WebView, url: String) {
                    if (requiresCompleteGallery) {
                        view.evaluateJavascript(SILENCE_MEDIA_SCRIPT, null)
                    }
                    super.onLoadResource(view, url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(SILENCE_MEDIA_SCRIPT, null)
                    super.onPageFinished(view, url)
                }

                override fun onPageCommitVisible(view: WebView, url: String) {
                    view.evaluateJavascript(SILENCE_MEDIA_SCRIPT, null)
                    super.onPageCommitVisible(view, url)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    if (
                        requiresCompleteGallery &&
                        isAudibleMediaRequest(request.url.toString(), request.requestHeaders)
                    ) {
                        return WebResourceResponse(
                            "application/octet-stream",
                            null,
                            java.io.ByteArrayInputStream(ByteArray(0)),
                        )
                    }
                    if (!requiresCompleteGallery) {
                        val captured = DouyinCaptureStore.capture(
                            pageUrl = currentPageUrl.get(),
                            requestUrl = request.url.toString(),
                        )
                        if (captured != null) view.post { completeCapture(captured) }
                    }
                    // Do not finish on an image request alone. Dynamic notes
                    // request a still preview before their playable stream;
                    // inspectVideoSource verifies that no video element exists
                    // before this target-page image fallback can complete.
                    DouyinCaptureStore.captureImage(
                        pageUrl = currentPageUrl.get(),
                        requestUrl = request.url.toString(),
                    )
                    return super.shouldInterceptRequest(view, request)
                }
            }
            if (canMuteWebView) loadUrl(sourceUrl)
        }
        setContentView(webView)
        if (!canMuteWebView) {
            finishWithError("系统 WebView 不支持后台读取静音，已停止读取")
            return
        }
        handler.post(inspectVideoSource)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(inspectVideoSource)
        handler.removeCallbacks(captureTimeout)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Android system back dispatches to this Activity directly")
    override fun onBackPressed() {
        finishWithError("已取消抖音页面读取")
    }

    private fun completeCapture(captured: DouyinCaptureStore.CapturedMedia) {
        if (completed || destroyed) return
        completed = true
        handler.removeCallbacks(inspectVideoSource)
        handler.removeCallbacks(captureTimeout)
        webView.evaluateJavascript(METADATA_SCRIPT) { rawMetadata ->
            val metadata = parseMetadata(rawMetadata)
            val workId = DouyinCaptureStore.extractWorkId(captured.pageUrl).orEmpty()
            val media = DouyinCapturedMedia(
                workId = workId,
                pageUrl = captured.pageUrl,
                mediaUrl = captured.mediaUrl,
                title = metadata.title.ifBlank { "抖音作品 $workId" },
                creator = metadata.creator.ifBlank { "抖音用户" },
                thumbnailUrl = metadata.thumbnailUrl,
                capturedAtMillis = System.currentTimeMillis(),
                imageUrls = captured.imageUrls,
                imageExpectedCount = captured.imageExpectedCount,
                imageSourceVersion = captured.imageSourceVersion,
            )
            runCatching {
                (application as NanzhufengApplication).container.douyinCaptures.save(media)
            }.onSuccess {
                Log.i(VIDEO_SOURCE_LOG_TAG, "抖音目标作品流已安全捕获")
                setResult(RESULT_OK, resultIntent(media))
                finishWithoutTransition()
            }.onFailure { error ->
                completed = false
                finishWithError(error.message ?: "抖音视频流未通过安全校验")
            }
        }
    }

    private fun finishWithError(message: String) {
        if (destroyed || isFinishing) return
        completed = true
        handler.removeCallbacks(inspectVideoSource)
        handler.removeCallbacks(captureTimeout)
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finishWithoutTransition()
    }

    private fun finishWithoutTransition() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun parseMetadata(raw: String): CaptureMetadata = runCatching {
        val encoded = JSONTokener(raw).nextValue() as? String ?: ""
        val json = JSONObject(encoded)
        CaptureMetadata(
            title = json.optString("title").removeSuffix(" - 抖音"),
            creator = json.optString("creator"),
            thumbnailUrl = json.optString("thumbnail"),
        )
    }.getOrDefault(CaptureMetadata())

    private fun parseVideoSource(raw: String): VideoSource = runCatching {
        val encoded = JSONTokener(raw).nextValue() as? String ?: ""
        val json = JSONObject(encoded)
        VideoSource(
            sourceUrl = json.optString("source"),
            hasVideoElement = json.optBoolean("hasVideo"),
        )
    }.getOrDefault(VideoSource())

    private fun parseImageSources(raw: String): ImageSources = runCatching {
        val encoded = JSONTokener(raw).nextValue() as? String ?: "{}"
        val json = JSONObject(encoded)
        val urls = json.optJSONArray("urls")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.orEmpty()
        ImageSources(
            urls = urls,
            awaitingStructuredImages = json.optBoolean("awaitFlight"),
            structuredComplete = json.optBoolean("structuredComplete"),
            expectedCount = json.optInt("expectedCount"),
        )
    }.getOrDefault(ImageSources())

    private data class CaptureMetadata(
        val title: String = "",
        val creator: String = "",
        val thumbnailUrl: String = "",
    )

    private data class VideoSource(
        val sourceUrl: String = "",
        val hasVideoElement: Boolean = false,
    )

    private data class ImageSources(
        val urls: List<String> = emptyList(),
        val awaitingStructuredImages: Boolean = false,
        val structuredComplete: Boolean = false,
        val expectedCount: Int = 0,
    )

    companion object {
        const val EXTRA_URL = "source_url"
        private const val EXTRA_WORK_ID = "work_id"
        private const val EXTRA_PAGE_URL = "page_url"
        private const val EXTRA_MEDIA_URL = "media_url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CREATOR = "creator"
        private const val EXTRA_THUMBNAIL = "thumbnail"
        private const val EXTRA_CAPTURED_AT = "captured_at"
        private const val EXTRA_IMAGE_URLS = "image_urls"
        private const val EXTRA_IMAGE_EXPECTED_COUNT = "image_expected_count"
        private const val EXTRA_IMAGE_SOURCE_VERSION = "image_source_version"
        private const val EXTRA_ERROR = "capture_error"
        private const val VIDEO_SOURCE_LOG_TAG = "DouyinVideoSource"
        private const val VIDEO_SOURCE_POLL_MILLIS = 500L
        private const val CAPTURE_TIMEOUT_MILLIS = 30_000L
        private const val DOUYIN_DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Safari/537.36"
        private const val VIDEO_SOURCE_SCRIPT = """
            (() => {
                const video = document.querySelector('video');
                const source = video?.querySelector('source');
                return JSON.stringify({
                    source: video?.currentSrc || video?.src || source?.src || '',
                    hasVideo: Boolean(video)
                });
            })()
        """
        private const val IMAGE_SOURCE_SCRIPT = """
            (() => {
                const workId = (location.pathname.match(/\/(?:note|video)\/(\d+)/) || [])[1];
                const structuredImages = [];
                let expectedCount = 0;
                let targetFound = false;
                const visited = new Set();
                const findTargetDetails = (value, depth = 0) => {
                    if (!value || typeof value !== 'object' || depth > 16 || visited.has(value)) {
                        return [];
                    }
                    visited.add(value);
                    const found = [];
                    if (value.awemeId === workId && Array.isArray(value.aweme?.detail?.images)) {
                        found.push(value.aweme.detail);
                    }
                    const children = Array.isArray(value) ? value : Object.values(value);
                    for (const child of children) {
                        found.push(...findTargetDetails(child, depth + 1));
                    }
                    return found;
                };
                const extractBalancedArray = (text, start) => {
                    let depth = 0;
                    let inString = false;
                    let escaped = false;
                    for (let index = start; index < text.length; index += 1) {
                        const character = text[index];
                        if (inString) {
                            if (escaped) escaped = false;
                            else if (character === '\\') escaped = true;
                            else if (character === '"') inString = false;
                            continue;
                        }
                        if (character === '"') inString = true;
                        else if (character === '[') depth += 1;
                        else if (character === ']') {
                            depth -= 1;
                            if (depth === 0) return text.slice(start, index + 1);
                        }
                    }
                    return '';
                };
                const appendImages = (images) => {
                    if (!Array.isArray(images) || images.length === 0) return;
                    targetFound = true;
                    expectedCount = Math.max(expectedCount, images.length);
                    for (const image of images) {
                        const urls = image?.urlList || image?.url_list ||
                            image?.displayImage?.urlList || image?.display_image?.url_list || [];
                        const source = urls.find((url) =>
                            typeof url === 'string' &&
                            url.startsWith('https://') &&
                            url.includes('tplv-dy-aweme-images') &&
                            !url.includes('tplv-dy-water'),
                        );
                        if (source) structuredImages.push(source);
                    }
                };
                // The public page's React Flight payload separates each
                // artwork's original display URL (`urlList`) from its
                // explicitly watermarked download rendition
                // (`downloadUrlList`).  A transparent Android WebView only
                // renders the current carousel slide, so use this target
                // work's structured list to obtain every original image.
                if (workId && Array.isArray(window.__pace_f)) {
                    for (const row of window.__pace_f) {
                        const payload = row?.[1];
                        if (typeof payload !== 'string' ||
                            (!payload.includes('\"awemeId\":\"' + workId + '\"') &&
                                !payload.includes('\"aweme_id\":\"' + workId + '\"'))) continue;
                        const decodedPayloads = [payload];
                        if (payload.startsWith('%7B') || payload.startsWith('%5B')) {
                            try { decodedPayloads.push(decodeURIComponent(payload)); } catch (_) {}
                        }
                        for (const decoded of decodedPayloads) {
                            const colon = decoded.indexOf(':');
                            const jsonCandidates = colon >= 0
                                ? [decoded.slice(colon + 1).trim(), decoded.trim()]
                                : [decoded.trim()];
                            for (const candidate of jsonCandidates) {
                                try {
                                    const data = JSON.parse(candidate);
                                    for (const detail of findTargetDetails(data)) {
                                        appendImages(detail.images);
                                    }
                                } catch (_) {
                                    // Flight rows can contain module references around
                                    // otherwise-valid target data. Extract only the
                                    // target work's balanced images array in that case.
                                }
                            }
                            const targetMarkers = [
                                '"awemeId":"' + workId + '"',
                                '"aweme_id":"' + workId + '"',
                            ];
                            for (const marker of targetMarkers) {
                                const targetIndex = decoded.indexOf(marker);
                                if (targetIndex < 0) continue;
                                const imagesKey = decoded.indexOf('"images":[', targetIndex);
                                if (imagesKey < 0) continue;
                                const arrayStart = decoded.indexOf('[', imagesKey);
                                const encodedImages = extractBalancedArray(decoded, arrayStart);
                                if (!encodedImages) continue;
                                try { appendImages(JSON.parse(encodedImages)); } catch (_) {}
                            }
                        }
                    }
                }
                // Fallback only for page variants without Flight data. Android
                // WebView does not share desktop carousel CSS classes and can
                // report zero layout dimensions for this background probe.
                const domImages = Array.from(document.images)
                    .flatMap((image) => [image.currentSrc, image.src])
                    .filter((url) => typeof url === 'string' &&
                        url.startsWith('https://') &&
                        !url.includes('tplv-dy-water'));
                const uniqueStructuredImages = Array.from(new Set(structuredImages));
                const structuredComplete = targetFound && expectedCount > 0 &&
                    uniqueStructuredImages.length === expectedCount;
                return JSON.stringify({
                    urls: structuredComplete ? uniqueStructuredImages : domImages,
                    // While Flight is available, an empty target image list
                    // means its chunk has not arrived yet. Waiting is safer
                    // than completing from the first rendered carousel slide.
                    awaitFlight: Array.isArray(window.__pace_f) && !structuredComplete,
                    structuredComplete,
                    expectedCount,
                });
            })()
        """
        private const val SILENCE_MEDIA_SCRIPT = """
            (() => {
                if (!document.documentElement) return false;
                const silence = (media) => {
                    if (!(media instanceof HTMLMediaElement)) return;
                    media.muted = true;
                    media.volume = 0;
                    media.setAttribute('muted', '');
                };
                document.querySelectorAll('video,audio').forEach(silence);
                if (window.__nanfengSilenceInstalled) return true;
                window.__nanfengSilenceInstalled = true;
                const originalPlay = HTMLMediaElement.prototype.play;
                HTMLMediaElement.prototype.play = function(...args) {
                    silence(this);
                    return originalPlay.apply(this, args);
                };
                document.addEventListener('volumechange', (event) => silence(event.target), true);
                new MutationObserver((records) => {
                    for (const record of records) {
                        for (const node of record.addedNodes) {
                            if (!(node instanceof Element)) continue;
                            silence(node);
                            node.querySelectorAll?.('video,audio').forEach(silence);
                        }
                    }
                }).observe(document.documentElement, { childList: true, subtree: true });
                return true;
            })()
        """
        private const val METADATA_SCRIPT = """
            (() => JSON.stringify({
                title: document.querySelector('meta[property="og:title"]')?.content || document.title || '',
                creator: document.querySelector('meta[name="author"]')?.content ||
                    document.querySelector('[data-e2e="video-author-uniqueid"]')?.textContent || '',
                thumbnail: document.querySelector('meta[property="og:image"]')?.content || ''
            }))()
        """

        fun createIntent(context: Context, sourceUrl: String): Intent =
            Intent(context, DouyinProbeActivity::class.java).putExtra(EXTRA_URL, sourceUrl)

        internal fun shouldTryImageFallback(hasVideoElement: Boolean): Boolean = !hasVideoElement

        internal fun resolveImageCandidates(
            domImages: List<String>,
            interceptedImages: List<String>,
            awaitingStructuredImages: Boolean = false,
            requiredCount: Int? = null,
        ): List<String> = if (awaitingStructuredImages) {
            emptyList()
        } else {
            val candidates = domImages.ifEmpty { interceptedImages }
                .filterNot(::isWatermarkedImage)
                .distinct()
            if (requiredCount != null && (requiredCount <= 0 || candidates.size != requiredCount)) {
                emptyList()
            } else {
                candidates
            }
        }

        internal fun isWatermarkedImage(url: String): Boolean =
            "tplv-dy-water" in url.lowercase()

        internal fun isAudibleMediaRequest(url: String, requestHeaders: Map<String, String>): Boolean {
            val lower = url.lowercase()
            val normalizedHeaders = requestHeaders.mapKeys { it.key.lowercase() }
            val accept = normalizedHeaders["accept"].orEmpty().lowercase()
            val destination = normalizedHeaders["sec-fetch-dest"].orEmpty().lowercase()
            return normalizedHeaders.containsKey("range") ||
                destination == "audio" || destination == "video" ||
                "audio/" in accept || "video/" in accept ||
                lower.contains("mime_type=audio") || lower.contains("mime_type=video") ||
                lower.contains("/aweme/v1/play/") || lower.contains("/playwm/") ||
                lower.contains("douyinvod.com") || lower.contains("bytevideo.com") ||
                lower.substringBefore('?').endsWith(".mp3") ||
                lower.substringBefore('?').endsWith(".m4a") ||
                lower.substringBefore('?').endsWith(".aac") ||
                lower.substringBefore('?').endsWith(".mp4")
        }

        fun capturedMedia(intent: Intent?): DouyinCapturedMedia? {
            val data = intent ?: return null
            val workId = data.getStringExtra(EXTRA_WORK_ID).orEmpty()
            if (workId.isBlank()) return null
            return DouyinCapturedMedia(
                workId = workId,
                pageUrl = data.getStringExtra(EXTRA_PAGE_URL).orEmpty(),
                mediaUrl = data.getStringExtra(EXTRA_MEDIA_URL).orEmpty(),
                title = data.getStringExtra(EXTRA_TITLE).orEmpty(),
                creator = data.getStringExtra(EXTRA_CREATOR).orEmpty(),
                thumbnailUrl = data.getStringExtra(EXTRA_THUMBNAIL).orEmpty(),
                capturedAtMillis = data.getLongExtra(EXTRA_CAPTURED_AT, 0L),
                imageUrls = data.getStringArrayListExtra(EXTRA_IMAGE_URLS).orEmpty(),
                imageExpectedCount = data.getIntExtra(EXTRA_IMAGE_EXPECTED_COUNT, 0),
                imageSourceVersion = data.getIntExtra(EXTRA_IMAGE_SOURCE_VERSION, 0),
            )
        }

        fun errorMessage(intent: Intent?): String =
            intent?.getStringExtra(EXTRA_ERROR).orEmpty()

        internal fun canonicalDouyinVideoUrl(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val host = uri.host.orEmpty().lowercase()
            if (host != "iesdouyin.com" && !host.endsWith(".iesdouyin.com")) return null
            val workId = DouyinCaptureStore.extractWorkId(url) ?: return null
            return "https://www.douyin.com/video/$workId"
        }

        internal fun mobileBrowserUserAgent(defaultUserAgent: String): String = defaultUserAgent
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
            .replace(Regex("\\s+"), " ")
            .trim()

        internal fun isDouyinNoteUrl(url: String): Boolean =
            runCatching { URI(url).path.orEmpty() }
                .getOrDefault("")
                .contains("/note/")

        private fun resultIntent(media: DouyinCapturedMedia) = Intent()
            .putExtra(EXTRA_WORK_ID, media.workId)
            .putExtra(EXTRA_PAGE_URL, media.pageUrl)
            .putExtra(EXTRA_MEDIA_URL, media.mediaUrl)
            .putExtra(EXTRA_TITLE, media.title)
            .putExtra(EXTRA_CREATOR, media.creator)
            .putExtra(EXTRA_THUMBNAIL, media.thumbnailUrl)
            .putExtra(EXTRA_CAPTURED_AT, media.capturedAtMillis)
            .putExtra(EXTRA_IMAGE_EXPECTED_COUNT, media.imageExpectedCount)
            .putExtra(EXTRA_IMAGE_SOURCE_VERSION, media.imageSourceVersion)
            .putStringArrayListExtra(EXTRA_IMAGE_URLS, ArrayList(media.imageUrls))
    }
}
