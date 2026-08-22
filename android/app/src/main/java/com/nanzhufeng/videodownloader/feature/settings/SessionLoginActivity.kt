package com.nanzhufeng.videodownloader.feature.settings

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.nanzhufeng.videodownloader.core.ui.NanzhufengTheme
import com.nanzhufeng.videodownloader.domain.session.SessionSite
import com.nanzhufeng.videodownloader.domain.session.classifyAuthenticatedSession
import com.nanzhufeng.videodownloader.domain.session.mergeCookieHeaders

class SessionLoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var loginCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
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
                LoginWebView(
                    site = site,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
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
                settings.cacheMode = loginCacheMode(site)
                settings.userAgentString = mobileLoginUserAgent(settings.userAgentString)
                // Honor the official mobile page viewport. Disabling it makes
                // Bilibili's H5 login place the agreement row over the action.
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                settings.textZoom = 100
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                cookies.setAcceptThirdPartyCookies(this, site.requiresThirdPartyCookies)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean = request.isForMainFrame && !site.isTrustedLoginUrl(request.url.toString())

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
                            site == SessionSite.DOUYIN && site.ownsLoginPage(currentUrl)
                        ) {
                            view?.polishDouyinLoginPage()
                        }
                        if (site == SessionSite.TIKTOK && site.ownsLoginPage(currentUrl)) {
                            view?.assistTikTokLoginPage()
                        }
                        if (
                            site == SessionSite.BILIBILI && site.ownsLoginPage(currentUrl)
                        ) {
                            view?.polishBilibiliLoginPage()
                        }
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        if (site == SessionSite.DOUYIN && site.ownsLoginPage(url.orEmpty())) {
                            view?.polishDouyinLoginPage()
                        }
                        if (site == SessionSite.TIKTOK && site.ownsLoginPage(url.orEmpty())) {
                            view?.assistTikTokLoginPage()
                        }
                        if (site == SessionSite.BILIBILI && site.ownsLoginPage(url.orEmpty())) {
                            view?.polishBilibiliLoginPage()
                        }
                    }
                }
                // A TikTok login session can include short-lived anti-abuse
                // state in its WebView cache. Do not erase that state merely
                // because the user reopened the same login page.
                if (site != SessionSite.TIKTOK) clearCache(true)
                loadUrl(site.loginUrl)
                onCreated(this)
            }
        },
    )
}

internal fun mobileLoginUserAgent(defaultUserAgent: String): String = defaultUserAgent
    .replace("; wv", "")
    .replace("Version/4.0 ", "")

internal fun loginCacheMode(site: SessionSite): Int =
    if (site == SessionSite.TIKTOK) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_NO_CACHE

internal fun shouldFinishLoginAfterNavigation(
    site: SessionSite,
    currentUrl: String,
    cookieHeaders: List<String>,
    hadAuthenticatedSessionAtStart: Boolean,
): Boolean {
    if (hadAuthenticatedSessionAtStart) return false
    if (!classifyAuthenticatedSession(site, mergeCookieHeaders(cookieHeaders))) return false
    return when (site) {
        SessionSite.DOUYIN -> site.ownsLoginPage(currentUrl) && !currentUrl.contains("/login_page")
        SessionSite.TIKTOK -> site.ownsLoginPage(currentUrl) && !currentUrl.contains("/login")
        SessionSite.BILIBILI ->
            site.ownsLoginPage(currentUrl) && !currentUrl.contains("passport.bilibili.com") &&
                !currentUrl.contains("/login")
        SessionSite.XIAOHONGSHU -> site.ownsLoginPage(currentUrl) && !currentUrl.contains("/login")
        SessionSite.YOUTUBE -> false
    }
}

private fun WebView.polishDouyinLoginPage() {
    postDelayed(
        {
            scrollTo(0, 0)
            evaluateJavascript(POLISH_DOUYIN_LOGIN_PAGE_SCRIPT, null)
        },
        500L,
    )
    postDelayed(
        {
            scrollTo(0, 0)
            evaluateJavascript(POLISH_DOUYIN_LOGIN_PAGE_SCRIPT, null)
        },
        1_500L,
    )
}

private fun WebView.polishBilibiliLoginPage() {
    postDelayed({ evaluateJavascript(POLISH_BILIBILI_LOGIN_PAGE_SCRIPT, null) }, 500L)
    postDelayed({ evaluateJavascript(POLISH_BILIBILI_LOGIN_PAGE_SCRIPT, null) }, 1_500L)
}

private fun WebView.assistTikTokLoginPage() {
    // TikTok renders the form after the document shell. Install the listener
    // repeatedly while it is settling; the script itself is idempotent and
    // never sends, logs, or persists field values.
    listOf(500L, 1_500L, 3_000L).forEach { delayMillis ->
        postDelayed({ evaluateJavascript(tikTokLoginPageAssistScript(), null) }, delayMillis)
    }
}

internal fun tikTokLoginPageAssistScript(): String = TIKTOK_LOGIN_PAGE_ASSIST_SCRIPT

private const val POLISH_DOUYIN_LOGIN_PAGE_SCRIPT = """
    (() => {
      const styleId = 'nanzhufeng-douyin-login-surface';
      if (!document.getElementById(styleId)) {
        const style = document.createElement('style');
        style.id = styleId;
        style.textContent = [
          'html, body, #root { background: #ffffff !important; min-height: 100% !important;',
          'width: 100% !important; max-width: 100% !important; }',
          'body { height: auto !important; min-height: 100vh !important;',
          'justify-content: center !important; align-items: stretch !important;',
          'overflow-y: auto !important; }'
        ].join('');
        document.head.appendChild(style);
      }
      const apply = () => {
        [document.documentElement, document.body, document.getElementById('root')]
          .filter(Boolean)
          .forEach((node) => node.style.setProperty('background-color', '#ffffff', 'important'));
        window.scrollTo(0, 0);
      };
      apply();
      setTimeout(apply, 200);
    })();
"""

private const val POLISH_BILIBILI_LOGIN_PAGE_SCRIPT = """
    (() => {
      const tips = document.querySelector('.explain-tips');
      const form = document.querySelector('.login-sms-wrap');
      const loginButton = form?.querySelector('.form-btn.login-btn');
      if (!tips || !form || !loginButton) return;

      // Current Bilibili H5 places this sibling with absolute positioning,
      // directly over the login button inside Android WebView. Keep the
      // official text and links, but restore its logical form position.
      form.insertBefore(tips, loginButton);
      Object.assign(tips.style, {
        position: 'static',
        width: 'auto',
        height: 'auto',
        minHeight: '44px',
        margin: '18px 0 0',
        padding: '0 12px',
        lineHeight: '1.45',
        boxSizing: 'border-box'
      });
    })();
"""

private const val TIKTOK_LOGIN_PAGE_ASSIST_SCRIPT = """
    (() => {
      const inputMarker = 'nanzhufengTikTokCaretAssist';
      const toggleMarker = 'nanzhufengTikTokPasswordToggle';
      const supportedTypes = new Set(['text', 'tel', 'email', 'password']);

      const restoreCaretWhenTikTokResetsIt = (input) => {
        if (input.dataset[inputMarker] === 'true' || !supportedTypes.has(input.type)) return;
        input.dataset[inputMarker] = 'true';
        input.addEventListener('input', (event) => {
          if (event.isComposing || document.activeElement !== input) return;
          const restoreIfReset = () => {
            if (document.activeElement !== input || input.value.length === 0) return;
            if (input.selectionStart === 0 && input.selectionEnd === 0) {
              input.setSelectionRange(input.value.length, input.value.length);
            }
          };
          requestAnimationFrame(restoreIfReset);
          setTimeout(restoreIfReset, 32);
        });
      };

      const passwordIcon = (visible) => visible
        ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.7a2 2 0 0 0 2.7 2.7M9.9 4.3A10.8 10.8 0 0 1 12 4c5.2 0 8.9 4.2 10 8-0.5 1.6-1.5 3.1-2.8 4.3M6.3 6.3C4.7 7.8 3.6 9.8 3 12c1.1 3.8 4.8 8 9 8 1.3 0 2.5-0.3 3.6-0.8"/></svg>'
        : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7-9.5-7-9.5-7Zm9.5 3.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z"/></svg>';

      const addPasswordVisibilityToggle = (input) => {
        if (input.type !== 'password' || input.dataset[toggleMarker] === 'true') return;
        const host = input.parentElement;
        if (!host) return;
        input.dataset[toggleMarker] = 'true';
        if (getComputedStyle(host).position === 'static') host.style.position = 'relative';
        // TikTok owns the trailing clear button. Keep this compact: the eye
        // sits one icon-width to its left with a small visual gap, rather
        // than consuming the middle of the input row.
        input.style.paddingRight = '76px';
        const button = document.createElement('button');
        button.type = 'button';
        button.setAttribute('aria-label', '显示密码');
        button.setAttribute('aria-pressed', 'false');
        button.style.cssText = 'position:absolute;right:34px;top:50%;transform:translateY(-50%);width:32px;height:32px;padding:6px;border:0;background:transparent;color:#6b7280;z-index:2;display:flex;align-items:center;justify-content:center;';
        button.innerHTML = passwordIcon(false);
        button.querySelector('svg').style.cssText = 'width:20px;height:20px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round;';
        button.addEventListener('click', () => {
          const visible = input.type === 'password';
          input.type = visible ? 'text' : 'password';
          button.setAttribute('aria-label', visible ? '隐藏密码' : '显示密码');
          button.setAttribute('aria-pressed', String(visible));
          button.innerHTML = passwordIcon(visible);
          button.querySelector('svg').style.cssText = 'width:20px;height:20px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round;';
          input.focus();
          input.setSelectionRange(input.value.length, input.value.length);
        });
        host.appendChild(button);
      };

      const install = () => document.querySelectorAll('input').forEach((input) => {
        restoreCaretWhenTikTokResetsIt(input);
        addPasswordVisibilityToggle(input);
      });
      install();
      if (!document.documentElement.dataset.nanfengTikTokLoginObserver) {
        document.documentElement.dataset.nanfengTikTokLoginObserver = 'true';
        new MutationObserver(install).observe(document.documentElement, { childList: true, subtree: true });
      }
    })();
"""
