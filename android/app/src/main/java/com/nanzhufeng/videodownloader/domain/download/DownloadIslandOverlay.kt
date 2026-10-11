package com.nanzhufeng.videodownloader.domain.download

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import com.nanzhufeng.videodownloader.core.ui.IslandFeedback
import com.nanzhufeng.videodownloader.core.ui.IslandMotion

/** App-owned island, not an OEM whitelist claim. No view/window is created without user consent. */
internal class DownloadIslandOverlay(
    private val context: Context,
    private val onOpen: (String) -> Unit,
    private val onCancel: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var rendered: DownloadIslandState? = null
    private var window: FrameLayout? = null
    private var renderedExpanded: Boolean? = null
    private var motionGeneration = 0L
    private val islandDisplay = DownloadIslandDisplayController()
    private val expanded get() = islandDisplay.expanded
    @Volatile private var closed = false

    init { synchronized(ownershipLock) { owner?.close(); owner = this } }
    companion object {
        private val ownershipLock = Any()
        @Volatile private var owner: DownloadIslandOverlay? = null
        fun hideForActivity() { owner?.hide() }
    }
    fun hide() { handler.post { removeView() } }
    fun update(state: DownloadIslandState) {
        handler.post {
            if (closed || owner !== this) return@post
            try {
            val mainVisible = DownloadActivityVisibility.isVisible
            if (!Settings.canDrawOverlays(context) || mainVisible ||
                context.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
                !context.getSystemService(PowerManager::class.java).isInteractive) {
                removeView()
                return@post
            }
            if (rendered == state && view != null) return@post
            islandDisplay.onTask(state.taskId)
            render(state)
            } catch (error: RuntimeException) {
                // Permission or system-service failures only hide presentation, never stop downloads.
                android.util.Log.w("TaskIsland", "Island unavailable: ${error.javaClass.simpleName}")
                removeView()
            }
        }
    }

    private fun render(state: DownloadIslandState) {
        val root = view ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { setColor(Color.rgb(22, 29, 33)); cornerRadius = dp(24).toFloat() }
            elevation = dp(8).toFloat()
            clipToOutline = true
            contentDescription = "南枫下载灵动岛"
        }
        val oldWidth = (root.width * root.scaleX).toInt()
        val oldHeight = (root.height * root.scaleY).toInt()
        val first = window == null
        val modeChanged = renderedExpanded != null && renderedExpanded != expanded
        val animationEnabled = ValueAnimator.areAnimatorsEnabled()
        val changeMotion = first || modeChanged || !animationEnabled
        // Progress may arrive during a gesture animation: update content without cancelling
        // its animator or invalidating its completion callback.
        if (changeMotion) { root.animate().cancel(); ++motionGeneration }
        val generation = motionGeneration
        root.removeAllViews()
        val surface = GradientDrawable().apply {
            setColor(Color.rgb(22, 29, 33)); cornerRadius = dp(24).toFloat()
        }
        // Keep the 48dp touch region, but make the visible compact capsule only 34dp tall.
        root.background = if (expanded) surface else InsetDrawable(surface, 0, dp(7), 0, dp(7))
        // Reapply after background assignment: Drawable padding otherwise erases our safe margins.
        root.setPadding(dp(if (expanded) 14 else 24), dp(10), dp(if (expanded) 14 else 24), dp(10))
        root.minimumWidth = if (expanded) 0 else minOf(dp(220), context.resources.displayMetrics.widthPixels - dp(24))
        root.minimumHeight = if (expanded) 0 else dp(48)
        root.setOnClickListener(if (expanded) null else View.OnClickListener { islandDisplay.toggle(); render(state) })
        root.isClickable = !expanded
        root.contentDescription = if (expanded) "南枫下载详情" else "展开下载详情，${state.compactText}"
        val accent = when (state.tone) {
            DownloadIslandTone.SUCCESS -> Color.rgb(89, 210, 144)
            DownloadIslandTone.ERROR -> Color.rgb(255, 113, 113)
            DownloadIslandTone.WARNING -> Color.rgb(235, 177, 93)
            DownloadIslandTone.RUNNING -> Color.rgb(83, 204, 222)
        }
        val compact = LinearLayout(context).apply {
            gravity = if (expanded) Gravity.CENTER_VERTICAL else Gravity.CENTER
            minimumHeight = dp(if (expanded) 48 else 28)
            addView(ImageView(context).apply {
                setImageResource(when(state.tone) {
                    DownloadIslandTone.SUCCESS -> android.R.drawable.stat_sys_download_done
                    DownloadIslandTone.ERROR -> android.R.drawable.ic_dialog_alert
                    else -> android.R.drawable.stat_sys_download
                })
                imageTintList = ColorStateList.valueOf(accent)
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(9) })
            addView(text(state.compactText, 14f).apply {
                setTextColor(accent); typeface = IslandFeedback.titleTypeface() ?: android.graphics.Typeface.DEFAULT
                maxWidth = minOf(dp(243), context.resources.displayMetrics.widthPixels - dp(101))
            })
            contentDescription = if (expanded) "收起下载详情" else "展开下载详情，${state.compactText}"
            if (expanded) setOnClickListener { islandDisplay.toggle(); render(state) }
        }
        root.addView(compact)
        if (expanded) {
            root.addView(text(state.title, 14f).apply { setPadding(0, dp(10), 0, dp(4)); maxLines = 2 })
            root.addView(text(state.detail, 12f).apply { maxLines = 3; setPadding(0, dp(5), 0, dp(5)) })
            if (state.ongoing) {
                root.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    isIndeterminate = state.percentage == null
                    progress = state.percentage ?: 0
                    progressTintList = ColorStateList.valueOf(accent)
                    indeterminateTintList = ColorStateList.valueOf(accent)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)))
            }
            root.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                addView(action("查看下载", accent) {
                    onOpen(state.taskId); removeView()
                }, LinearLayout.LayoutParams(dp(108), dp(48)))
                if (state.ongoing) addView(action("停止下载", Color.rgb(245, 130, 130)) {
                    onCancel(state.taskId)
                }, LinearLayout.LayoutParams(dp(108), dp(48)).apply { marginStart = dp(10) })
            })
        }
        rendered = state
        renderedExpanded = expanded
        val maxWidth = context.resources.displayMetrics.widthPixels - dp(24)
        val requestedWidth = minOf(dp(320), maxWidth)
        root.measure(View.MeasureSpec.makeMeasureSpec(requestedWidth,
            if (expanded) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val targetWidth = root.measuredWidth
        val targetHeight = root.measuredHeight
        val morph = !first && modeChanged && oldWidth > 0 && oldHeight > 0
        val animate = animationEnabled && (first || morph)
        val carrier = window ?: FrameLayout(context).apply {
            clipChildren = false; clipToPadding = false
            addView(root)
        }
        root.layoutParams = FrameLayout.LayoutParams(targetWidth, targetHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        root.pivotX = targetWidth / 2f
        root.pivotY = 0f
        // Keep enough transparent window space for the old size and spring overshoot while shrinking.
        // A constant centered carrier prevents the compositor from shifting an old buffer
        // sideways while the app is still laying out its new capsule width.
        val windowWidth = minOf(dp(352), maxWidth)
        val windowHeight = if (animate || root.scaleY != 1f) (maxOf(targetHeight, oldHeight) * 1.08f).toInt() else targetHeight
        val params = WindowManager.LayoutParams(windowWidth, windowHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(6)
            title = "NanfengDownloadIsland"
            windowAnimations = 0 // System resize animations can move a centered capsule sideways.
        }
        if (changeMotion) {
            root.scaleX = if (animate && morph) oldWidth.toFloat() / targetWidth else if (first && animate) .88f else 1f
            root.scaleY = if (animate && morph) oldHeight.toFloat() / targetHeight else if (first && animate) .90f else 1f
            root.alpha = if (first && animate) 0f else 1f
        }
        try {
            if (first) {
                windows.addView(carrier, params); window = carrier; view = root
            } else windows.updateViewLayout(carrier, params)
            if (animate) root.doOnPreDraw {
                if (view !== root || window !== carrier || motionGeneration != generation) return@doOnPreDraw
                IslandMotion.enter(root, root.scaleX, root.scaleY, root.alpha) {
                    if (view === root && window === carrier && motionGeneration == generation) {
                        params.height = root.measuredHeight
                        runCatching { windows.updateViewLayout(carrier, params) }.onFailure { removeView() }
                    }
                }
            }
        } catch (error: RuntimeException) {
            android.util.Log.w("TaskIsland", "Window unavailable: ${error.javaClass.simpleName}")
            removeView()
        }
    }

    private fun text(value: String, size: Float) = TextView(context).apply {
        text = value; textSize = size; setTextColor(Color.WHITE)
        typeface = IslandFeedback.bodyTypeface() ?: android.graphics.Typeface.DEFAULT
        includeFontPadding = false; fontFeatureSettings = "tnum"; setLineSpacing(dp(2).toFloat(), 1f)
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private fun action(label: String, color: Int, click: () -> Unit) = text(label, 12f).apply {
        gravity = Gravity.CENTER; setTextColor(color)
        // A slim visible capsule inside the retained 48dp touch target; ripple shares its outline.
        val shape = GradientDrawable().apply {
            setColor(Color.argb(13, 255, 255, 255)); cornerRadius = dp(100).toFloat()
            setStroke(maxOf(1, dp(1)), Color.argb(35, Color.red(color), Color.green(color), Color.blue(color)))
        }
        background = InsetDrawable(RippleDrawable(ColorStateList.valueOf(Color.argb(28, 255, 255, 255)),
            shape, GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(100).toFloat() }),
            0, dp(7), 0, dp(7))
        clipToOutline = true; setOnClickListener { click() }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun removeView() {
        ++motionGeneration
        view?.animate()?.cancel()
        window?.let { runCatching { windows.removeViewImmediate(it) } }
        window = null; view = null; rendered = null; renderedExpanded = null; islandDisplay.collapse()
    }
    fun close(terminal: DownloadIslandState? = null) {
        if (terminal == null) {
            closed = true
            handler.post { dispose() }
        } else handler.post {
            if (!closed && owner === this) {
                update(terminal)
                handler.post {
                    closed = true
                    handler.postDelayed({ dispose() }, 2200)
                }
            }
        }
    }
    private fun dispose() {
        removeView()
        synchronized(ownershipLock) { if (owner === this) owner = null }
    }
}
