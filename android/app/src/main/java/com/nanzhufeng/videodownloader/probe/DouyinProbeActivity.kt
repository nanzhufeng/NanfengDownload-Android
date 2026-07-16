package com.nanzhufeng.videodownloader.probe

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONTokener

class DouyinProbeActivity : Activity() {
    private lateinit var webView: WebView
    private val currentPageUrl = AtomicReference("")
    private val handler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private val inspectVideoSource = object : Runnable {
        override fun run() {
            if (destroyed || !::webView.isInitialized) return
            webView.evaluateJavascript(VIDEO_SOURCE_SCRIPT) { result ->
                if (destroyed) return@evaluateJavascript
                val mediaUrl = runCatching {
                    JSONTokener(result).nextValue() as? String
                }.getOrNull().orEmpty()
                if (mediaUrl.isNotBlank()) {
                    DouyinCaptureStore.capture(currentPageUrl.get(), mediaUrl)
                    if (DouyinCaptureStore.latestMediaUrl != null) {
                        Log.d(VIDEO_SOURCE_LOG_TAG, "已捕获目标作品流")
                        return@evaluateJavascript
                    }
                }
                handler.postDelayed(this, VIDEO_SOURCE_POLL_MILLIS)
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

        val cookies = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            flush()
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            cookies.setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    currentPageUrl.set(url)
                    DouyinCaptureStore.updatePage(url)
                    super.onPageStarted(view, url, favicon)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    DouyinCaptureStore.capture(
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
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "source_url"
        private const val VIDEO_SOURCE_LOG_TAG = "DouyinVideoSource"
        private const val VIDEO_SOURCE_POLL_MILLIS = 500L
        private const val VIDEO_SOURCE_SCRIPT = """
            (() => {
                const video = document.querySelector('video');
                const source = video?.querySelector('source');
                return video?.currentSrc || video?.src || source?.src || '';
            })()
        """
    }
}
