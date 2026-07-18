package com.nanzhufeng.videodownloader.feature.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.nanzhufeng.videodownloader.core.ui.NanzhufengTheme
import com.nanzhufeng.videodownloader.domain.session.SessionSite
import com.nanzhufeng.videodownloader.domain.session.classifyAuthenticatedSession
import com.nanzhufeng.videodownloader.domain.session.mergeCookieHeaders

class SessionLoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var loginCompleted = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = intent.getStringExtra(EXTRA_SITE)
            ?.let { runCatching { SessionSite.valueOf(it) }.getOrNull() }
            ?.takeIf { it != SessionSite.YOUTUBE }
            ?: run {
                finish()
                return
            }
        setContent {
            NanzhufengTheme {
                BackHandler {
                    if (webView?.canGoBack() == true) webView?.goBack() else finish()
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("登录 ${site.label}") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        LoginWebView(
                            site = site,
                            modifier = Modifier.fillMaxSize(),
                            onCreated = { webView = it },
                            onAuthenticated = {
                                if (!loginCompleted) {
                                    loginCompleted = true
                                    setResult(RESULT_OK)
                                    Toast.makeText(
                                        this@SessionLoginActivity,
                                        "${site.label}登录成功",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    finish()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SITE = "session_site"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
private fun LoginWebView(
    site: SessionSite,
    modifier: Modifier,
    onCreated: (WebView) -> Unit,
    onAuthenticated: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val cookies = CookieManager.getInstance().apply { setAcceptCookie(true) }
            val hadAuthenticatedSessionAtStart = classifyAuthenticatedSession(
                site,
                mergeCookieHeaders(site.cookieScopeUrls.map { url -> cookies.getCookie(url).orEmpty() }),
            )
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.userAgentString = mobileLoginUserAgent(settings.userAgentString)
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                settings.textZoom = 100
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                cookies.setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        cookies.flush()
                        val currentUrl = url.orEmpty()
                        val cookieHeaders = buildList {
                            if (currentUrl.isNotBlank()) add(cookies.getCookie(currentUrl).orEmpty())
                            addAll(site.cookieScopeUrls.map { scope -> cookies.getCookie(scope).orEmpty() })
                        }
                        if (
                            shouldFinishLoginAfterNavigation(
                                site = site,
                                currentUrl = currentUrl,
                                cookieHeaders = cookieHeaders,
                                hadAuthenticatedSessionAtStart = hadAuthenticatedSessionAtStart,
                            )
                        ) {
                            onAuthenticated()
                            return
                        }
                        if (
                            site == SessionSite.DOUYIN &&
                            currentUrl.contains("douyin.com")
                        ) {
                            view?.postDelayed(
                                {
                                    view.scrollTo(0, 0)
                                    view.evaluateJavascript(FIX_DOUYIN_MOBILE_LAYOUT_SCRIPT, null)
                                    view.postDelayed({ view.scrollTo(0, 0) }, 800L)
                                },
                                1_500L,
                            )
                        }
                    }
                }
                clearCache(true)
                loadUrl(site.loginUrl)
                onCreated(this)
            }
        },
    )
}

internal fun mobileLoginUserAgent(defaultUserAgent: String): String = defaultUserAgent
    .replace("; wv", "")
    .replace("Version/4.0 ", "")

internal fun shouldFinishLoginAfterNavigation(
    site: SessionSite,
    currentUrl: String,
    cookieHeaders: List<String>,
    hadAuthenticatedSessionAtStart: Boolean,
): Boolean {
    if (hadAuthenticatedSessionAtStart) return false
    if (!classifyAuthenticatedSession(site, mergeCookieHeaders(cookieHeaders))) return false
    return when (site) {
        SessionSite.DOUYIN -> !currentUrl.contains("/login_page")
        SessionSite.TIKTOK -> !currentUrl.contains("/login")
        SessionSite.YOUTUBE -> false
    }
}

private const val FIX_DOUYIN_MOBILE_LAYOUT_SCRIPT = """
    (() => {
      const applyMobileHeight = () => {
        const height = window.innerHeight + 'px';
        [document.documentElement, document.body].forEach((element) => {
          element.style.setProperty('height', height, 'important');
          element.style.setProperty('min-height', height, 'important');
          element.style.setProperty('width', '100%', 'important');
        });
        document.body.style.setProperty('align-items', 'center', 'important');
        document.body.style.setProperty('justify-content', 'center', 'important');
        document.body.style.setProperty('overflow', 'auto', 'important');
        window.scrollTo(0, 0);
      };
      applyMobileHeight();
      if (!window.__nanzhufengMobileLoginLayoutInstalled) {
        window.__nanzhufengMobileLoginLayoutInstalled = true;
        window.addEventListener('resize', () => setTimeout(applyMobileHeight, 100));
      }
    })();
"""
