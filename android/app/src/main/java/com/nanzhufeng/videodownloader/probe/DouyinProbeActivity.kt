package com.nanzhufeng.videodownloader.probe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private val captureTimeout = Runnable {
        finishWithError("抖音页面没有在规定时间内返回视频或图文图片，请确认作品可播放并重新登录后重试")
    }
    private val inspectVideoSource = object : Runnable {
        override fun run() {
            if (destroyed || !::webView.isInitialized) return
            webView.evaluateJavascript(VIDEO_SOURCE_SCRIPT) { result ->
                if (destroyed) return@evaluateJavascript
                val mediaUrl = runCatching {
                    JSONTokener(result).nextValue() as? String
                }.getOrNull().orEmpty()
                if (mediaUrl.isNotBlank()) {
                    val captured = DouyinCaptureStore.capture(currentPageUrl.get(), mediaUrl)
                    if (captured != null) {
                        completeCapture(captured)
                        return@evaluateJavascript
                    }
                }
                webView.evaluateJavascript(IMAGE_SOURCE_SCRIPT) { rawImages ->
                    if (destroyed || completed) return@evaluateJavascript
                    val imageUrls = runCatching {
                        val encoded = JSONTokener(rawImages).nextValue() as? String ?: "[]"
                        val array = org.json.JSONArray(encoded)
                        buildList {
                            for (index in 0 until array.length()) {
                                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                            }
                        }
                    }.getOrDefault(emptyList())
                    val capturedImages = imageUrls.flatMap { url ->
                        DouyinCaptureStore.captureImage(currentPageUrl.get(), url)
                    }.distinct()
                    if (capturedImages.isNotEmpty()) {
                        completeCapture(
                            DouyinCaptureStore.CapturedMedia(
                                mediaUrl = "",
                                pageUrl = currentPageUrl.get(),
                                imageUrls = capturedImages,
                            ),
                        )
                    } else {
                        handler.postDelayed(this, VIDEO_SOURCE_POLL_MILLIS)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceUrl = requireNotNull(intent.getStringExtra(EXTRA_URL)) {
            "缺少抖音页面地址"
        }
        DouyinCaptureStore.begin(sourceUrl)
        handler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MILLIS)

        val cookies = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            flush()
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            settings.userAgentString = mobileBrowserUserAgent(settings.userAgentString)
            cookies.setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
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

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(START_VIDEO_SCRIPT, null)
                    super.onPageFinished(view, url)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val captured = DouyinCaptureStore.capture(
                        pageUrl = currentPageUrl.get(),
                        requestUrl = request.url.toString(),
                    )
                    if (captured != null) view.post { completeCapture(captured) }
                    DouyinCaptureStore.captureImage(
                        pageUrl = currentPageUrl.get(),
                        requestUrl = request.url.toString(),
                    )
                    return super.shouldInterceptRequest(view, request)
                }
            }
            loadUrl(sourceUrl)
        }
        setContentView(webView)
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
            )
            runCatching {
                (application as NanzhufengApplication).container.douyinCaptures.save(media)
            }.onSuccess {
                Log.i(VIDEO_SOURCE_LOG_TAG, "抖音目标作品流已安全捕获")
                setResult(RESULT_OK, resultIntent(media))
                finish()
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
        finish()
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

    private data class CaptureMetadata(
        val title: String = "",
        val creator: String = "",
        val thumbnailUrl: String = "",
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
        private const val EXTRA_ERROR = "capture_error"
        private const val VIDEO_SOURCE_LOG_TAG = "DouyinVideoSource"
        private const val VIDEO_SOURCE_POLL_MILLIS = 500L
        private const val CAPTURE_TIMEOUT_MILLIS = 30_000L
        private const val VIDEO_SOURCE_SCRIPT = """
            (() => {
                const video = document.querySelector('video');
                const source = video?.querySelector('source');
                return video?.currentSrc || video?.src || source?.src || '';
            })()
        """
        private const val IMAGE_SOURCE_SCRIPT = """
            (() => JSON.stringify(
                Array.from(document.querySelectorAll('img'))
                    .filter((image) => Math.max(image.naturalWidth || 0, image.clientWidth || 0) >= 200)
                    .map((image) => image.currentSrc || image.src)
                    .filter(Boolean)
            ))()
        """
        private const val START_VIDEO_SCRIPT = """
            (() => {
                const video = document.querySelector('video');
                if (!video) return false;
                video.muted = true;
                video.playsInline = true;
                video.play().catch(() => {});
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

        private fun resultIntent(media: DouyinCapturedMedia) = Intent()
            .putExtra(EXTRA_WORK_ID, media.workId)
            .putExtra(EXTRA_PAGE_URL, media.pageUrl)
            .putExtra(EXTRA_MEDIA_URL, media.mediaUrl)
            .putExtra(EXTRA_TITLE, media.title)
            .putExtra(EXTRA_CREATOR, media.creator)
            .putExtra(EXTRA_THUMBNAIL, media.thumbnailUrl)
            .putExtra(EXTRA_CAPTURED_AT, media.capturedAtMillis)
            .putStringArrayListExtra(EXTRA_IMAGE_URLS, ArrayList(media.imageUrls))
    }
}
