package com.nanzhufeng.videodownloader.feature.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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

class SessionLoginActivity : ComponentActivity() {
    private var webView: WebView? = null

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
                        LoginWebView(site, Modifier.fillMaxSize(), onCreated = { webView = it })
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
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val cookies = CookieManager.getInstance().apply { setAcceptCookie(true) }
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                cookies.setAcceptThirdPartyCookies(this, true)
                webViewClient = WebViewClient()
                loadUrl(site.loginUrl)
                onCreated(this)
            }
        },
    )
}
