package com.nanzhufeng.videodownloader.probe

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI

/** Explicit target-player inspection. Never treats arbitrary page requests as a media match. */
class GenericWebProbeActivity : Activity() {
    private lateinit var browser: WebView
    private lateinit var status: TextView
    private var targetPage = ""
    private var targetTitle = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_REQUEST_ID, intent.getStringExtra(EXTRA_REQUEST_ID)))
        targetPage = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        if (!canInspectWebPlayer(targetPage)) {
            finishWithError("仅能打开已识别的目标作品页")
            return
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        status = TextView(this).apply {
            text = "请在网页中播放目标视频，再点“读取当前播放源”。不会从广告请求猜测作品。"
            setPadding(24, 20, 24, 20)
        }
        layout.addView(status)
        browser = WebView(this)
        configureBrowser()
        layout.addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Button(this).apply {
            text = "读取当前播放源"
            setOnClickListener { inspectPlayer() }
            actions.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        if (URI(targetPage).host.orEmpty().lowercase() in setOf("yhdm.one", "www.yhdm.one")) {
            Button(this).apply {
                text = "读取本作品剧集"
                setOnClickListener { inspectEpisodes() }
                actions.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        layout.addView(actions)
        setContentView(layout)
        browser.loadUrl(targetPage)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureBrowser() {
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme.orEmpty().lowercase()
                return scheme !in setOf("http", "https")
            }
        }
    }

    private fun inspectPlayer() {
        browser.evaluateJavascript(PLAYER_SCRIPT) { raw ->
            val data = decode(raw) ?: run {
                status.text = "播放器页面尚未就绪，请先播放目标视频后重试"
                return@evaluateJavascript
            }
            val videos = data.optJSONArray("videos") ?: JSONArray()
            if (videos.length() == 0) {
                val frames = data.optJSONArray("frames") ?: JSONArray()
                if (frames.length() == 0) {
                    status.text = "当前页面没有正在播放且可确认的 HTTP 视频；blob/DRM 媒体不可直接保存"
                } else {
                    val urls = (0 until frames.length()).map { frames.optString(it) }
                    AlertDialog.Builder(this).setTitle("选择目标播放器 iframe")
                        .setItems(urls.map { URI(it).host.orEmpty() }.toTypedArray()) { _, index ->
                            if (targetTitle.isBlank()) targetTitle = browser.title.orEmpty()
                            browser.loadUrl(urls[index])
                            status.text = "已打开所选播放器；请播放目标视频后再次读取"
                        }.setNegativeButton("取消", null).show()
                }
                return@evaluateJavascript
            }
            val options = (0 until videos.length()).map { videos.getJSONObject(it) }
            if (options.size == 1) finishWithVideo(options.single())
            else AlertDialog.Builder(this).setTitle("请选择正在播放的目标视频")
                .setItems(options.map { item ->
                    "${item.optString("width")}×${item.optString("height")} · " +
                        URI(item.optString("url")).host.orEmpty()
                }.toTypedArray()) { _, index -> finishWithVideo(options[index]) }
                .setNegativeButton("取消", null).show()
        }
    }

    private fun finishWithVideo(candidate: JSONObject) {
        val capture = runCatching {
            GenericWebCapture(
                pageUrl = targetPage,
                referer = candidate.optString("referer").ifBlank { browser.url.orEmpty() },
                mediaUrl = candidate.getString("url"),
                title = targetTitle.ifBlank { browser.title.orEmpty() }.ifBlank { "网页视频" },
            ).validate()
        }.getOrElse {
            status.text = it.message.orEmpty().ifBlank { "播放器未返回可保存的媒体地址" }
            return
        }
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_REQUEST_ID, intent.getStringExtra(EXTRA_REQUEST_ID))
            putExtra(EXTRA_PAGE, capture.pageUrl)
            putExtra(EXTRA_REFERER, capture.referer)
            putExtra(EXTRA_MEDIA, capture.mediaUrl)
            putExtra(EXTRA_TITLE, capture.title)
        })
        finish()
    }

    private fun inspectEpisodes() {
        browser.evaluateJavascript(EPISODE_SCRIPT) { raw ->
            val data = decode(raw)
            val anchors = data?.optJSONArray("anchors")
            val links = (0 until (anchors?.length() ?: 0)).map { anchors!!.optString(it) }
            val episodes = runCatching { YinghuaEpisodeParser.parse(targetPage, links) }.getOrElse {
                status.text = it.message.orEmpty()
                return@evaluateJavascript
            }
            if (episodes.isEmpty()) {
                status.text = "当前作品页没有可确认的剧集；请打开具体作品的合集页"
                return@evaluateJavascript
            }
            val array = JSONArray()
            episodes.forEach { array.put(JSONObject().put("url", it.url).put("title", it.title)) }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_EPISODES, array.toString())
                .putExtra(EXTRA_PAGE, targetPage)
                .putExtra(EXTRA_REQUEST_ID, intent.getStringExtra(EXTRA_REQUEST_ID)))
            finish()
        }
    }

    private fun decode(raw: String): JSONObject? = runCatching {
        JSONObject(JSONTokener(raw).nextValue() as String)
    }.getOrNull()

    private fun finishWithError(message: String) {
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message)
            .putExtra(EXTRA_REQUEST_ID, intent.getStringExtra(EXTRA_REQUEST_ID)))
        finish()
    }

    override fun onDestroy() {
        if (::browser.isInitialized) {
            browser.stopLoading()
            browser.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_REQUEST_ID = "capture_request_id"
        fun requestId(result: Intent?): String = result?.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        private const val EXTRA_PAGE = "page"
        private const val EXTRA_REFERER = "referer"
        private const val EXTRA_MEDIA = "media"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_EPISODES = "episodes"
        private const val EXTRA_ERROR = "error"

        fun createIntent(context: Context, url: String, requestId: String = "") = Intent(context, GenericWebProbeActivity::class.java)
            .putExtra(EXTRA_TARGET, url).putExtra(EXTRA_REQUEST_ID, requestId)

        fun capture(intent: Intent?): GenericWebCapture? = runCatching {
            GenericWebCapture(
                pageUrl = intent?.getStringExtra(EXTRA_PAGE).orEmpty(),
                referer = intent?.getStringExtra(EXTRA_REFERER).orEmpty(),
                mediaUrl = intent?.getStringExtra(EXTRA_MEDIA).orEmpty(),
                title = intent?.getStringExtra(EXTRA_TITLE).orEmpty(),
            ).validate()
        }.getOrNull()

        fun episodes(intent: Intent?): List<GenericWebEpisode> = runCatching {
            val array = JSONArray(intent?.getStringExtra(EXTRA_EPISODES).orEmpty())
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                GenericWebEpisode(item.getString("url"), item.getString("title"))
            }
        }.getOrDefault(emptyList())

        fun page(intent: Intent?): String = intent?.getStringExtra(EXTRA_PAGE).orEmpty()
        fun error(intent: Intent?): String = intent?.getStringExtra(EXTRA_ERROR).orEmpty()

        private const val PLAYER_SCRIPT = """(function(){
          const visible=(e,w)=>{const r=e.getBoundingClientRect(),s=w.getComputedStyle(e);
            return r.width>=160&&r.height>=90&&r.right>0&&r.bottom>0&&r.left<w.innerWidth&&r.top<w.innerHeight
              &&s.visibility!=='hidden'&&s.display!=='none'};
          const absolute=(u,base)=>{try{const x=new URL(u,base);return /^(https?):$/.test(x.protocol)?x.href:''}catch(e){return ''}};
          const videos=[],frames=[],seen=new WeakSet();
          const scan=(root,doc,frameDepth,shadowDepth)=>{
            if(!root||!doc||frameDepth>3||shadowDepth>8||!doc.defaultView||seen.has(root))return;
            seen.add(root);
            const w=doc.defaultView;
            for(const v of root.querySelectorAll('video')){
              if(!visible(v,w)||v.paused||v.readyState<2)continue;
              const url=absolute(v.currentSrc||v.src||v.querySelector('source')?.src||'',doc.baseURI);
              if(url)videos.push({url,referer:absolute(doc.URL,doc.baseURI)||location.href,
                width:Math.round(v.getBoundingClientRect().width),height:Math.round(v.getBoundingClientRect().height)});
            }
            for(const f of root.querySelectorAll('iframe')){
              if(!visible(f,w))continue;
              try{scan(f.contentDocument,f.contentDocument,frameDepth+1,0)}catch(e){}
              // Cross-origin players cannot be inspected here. Only offer visible frames
              // for the user's explicit selection; never treat their requests as a match.
              const src=f.getAttribute('src');
              if(src){const url=absolute(src,doc.baseURI);if(url)frames.push(url)}
            }
            for(const e of root.querySelectorAll('*')){
              if(e.shadowRoot)scan(e.shadowRoot,doc,frameDepth,shadowDepth+1);
            }
          };
          scan(document,document,0,0);
          return JSON.stringify({videos:videos,frames:[...new Set(frames)]});
        })()"""

        private const val EPISODE_SCRIPT = """(function(){
          return JSON.stringify({anchors:[...document.querySelectorAll('a[href]')]
            .slice(0,5000).map(a=>a.getAttribute('href')).filter(Boolean)});
        })()"""
    }
}
