package com.nanzhufeng.videodownloader.domain.download

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowInsets
import android.view.MotionEvent
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
    private val windows get() = DownloadIslandWindows.select(context).manager
    private var attachedWindows: WindowManager? = null
    private var attachedMode: IslandWindowMode? = null
    private var view: LinearLayout? = null
    private var rendered: DownloadIslandState? = null
    private var window: FrameLayout? = null
    private var renderedExpanded: Boolean? = null
    private var renderedPlacement: DownloadIslandPlacement? = null
    private val configurationCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) { refreshGeometry(force = true) }
        override fun onLowMemory() = Unit
    }
    private fun refreshGeometry(force: Boolean = false) { handler.post {
        val state = rendered ?: return@post
        if (force) rendered = null // Font/config changes can retain identical pixel geometry.
        update(state)
    } }
    private var motionGeneration = 0L
    private val islandDisplay = DownloadIslandDisplayController()
    private val expanded get() = islandDisplay.expanded
    @Volatile private var closed = false

    init {
        synchronized(ownershipLock) { owner?.close(); owner = this }
        context.registerComponentCallbacks(configurationCallback)
    }
    companion object {
        private val ownershipLock = Any()
        @Volatile private var owner: DownloadIslandOverlay? = null
        fun hideForActivity() { owner?.hide() }
        fun hideForScreenOff() { owner?.hide() }
        fun onWindowHostChanged() { owner?.refreshGeometry(force = true) }
    }
    fun hide() { handler.post { removeView() } }
    fun update(state: DownloadIslandState) {
        handler.post {
            if (closed || owner !== this) return@post
            try {
            val mainVisible = DownloadActivityVisibility.isVisible
            val host = DownloadIslandWindows.select(context)
            if (host.mode == IslandWindowMode.NONE || mainVisible ||
                context.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
                !context.getSystemService(PowerManager::class.java).isInteractive) {
                removeView()
                return@post
            }
            if (rendered == state && view != null && renderedPlacement == placement() &&
                attachedWindows === host.manager && attachedMode == host.mode) return@post
            islandDisplay.onTask(state.taskId)
            render(state)
            } catch (error: RuntimeException) {
                // Permission or system-service failures only hide presentation, never stop downloads.
                android.util.Log.w("TaskIsland", "Island unavailable: ${error.javaClass.simpleName}")
                removeView()
            }
        }
    }

    private fun placement(): DownloadIslandPlacement {
        val metrics = context.resources.displayMetrics
        val width: Int
        val insets: WindowInsets?
        if (Build.VERSION.SDK_INT >= 30) {
            val current = windows.currentWindowMetrics
            width = current.bounds.width()
            insets = current.windowInsets
        } else {
            width = DisplayMetrics().also {
                @Suppress("DEPRECATION") windows.defaultDisplay.getRealMetrics(it)
            }.widthPixels
            insets = window?.rootWindowInsets
        }
        val statusHeight = if (Build.VERSION.SDK_INT >= 30 && insets != null)
            insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        else @Suppress("DEPRECATION") (insets?.stableInsetTop ?: dp(24))
        // Display cutout bounds use screen coordinates. Attached WindowInsets bounds use
        // window coordinates: translate those before comparing against the screen center.
        @Suppress("DEPRECATION")
        val displayCutout = windows.defaultDisplay.cutout
        val attachedCutout = window?.rootWindowInsets?.displayCutout
        val location = IntArray(2)
        window?.getLocationOnScreen(location)
        val cutouts = when {
            displayCutout != null -> displayCutout.boundingRects.map { IslandCutout(it.left, it.top, it.right, it.bottom) }
            attachedCutout != null -> attachedCutout.boundingRects.map {
                IslandCutout(it.left + location[0], it.top + location[1], it.right + location[0], it.bottom + location[1])
            }
            else -> insets?.displayCutout?.boundingRects.orEmpty().map { IslandCutout(it.left, it.top, it.right, it.bottom) }
        }
        val result = DownloadIslandPlacement.resolve(width, statusHeight, metrics.density, cutouts)
        return if (DownloadIslandWindows.select(context).mode == IslandWindowMode.ACCESSIBILITY && result.inStatusBar)
            result.copy(surfaceTop = (result.statusBarHeight - result.surfaceHeight) / 2)
        else result.copy(inStatusBar = false, cameraGap = 0, surfaceTop = dp(7),
            compactWidth = minOf(dp(220), width - dp(24)))
    }

    private fun render(state: DownloadIslandState) {
        val host = DownloadIslandWindows.select(context)
        if (host.mode == IslandWindowMode.NONE) { removeView(); return }
        if (window != null && (attachedWindows !== host.manager || attachedMode != host.mode)) {
            val keepExpanded = expanded
            removeView()
            if (keepExpanded) islandDisplay.toggle()
        }
        val windows = host.manager
        val placement = placement()
        val geometryChanged = renderedPlacement != null && renderedPlacement != placement
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
        val changeMotion = first || modeChanged || geometryChanged || !animationEnabled
        // Progress may arrive during a gesture animation: update content without cancelling
        // its animator or invalidating its completion callback.
        if (changeMotion) { root.animate().cancel(); ++motionGeneration }
        val generation = motionGeneration
        root.removeAllViews()
        val surface = GradientDrawable().apply {
            setColor(Color.rgb(22, 29, 33)); cornerRadius = dp(24).toFloat()
        }
        // The live accessibility host can draw and receive taps in the status bar.
        // Ordinary overlay hosts use the safe below-bar fallback instead.
        val touchHeight = maxOf(dp(48), placement.surfaceTop + placement.surfaceHeight + dp(7))
        root.background = if (expanded) surface else InsetDrawable(surface, 0, placement.surfaceTop, 0,
            touchHeight - placement.surfaceTop - placement.surfaceHeight)
        val horizontalPadding = if (expanded) dp(14) else dp(12)
        root.setPadding(horizontalPadding, if (expanded) 0 else placement.surfaceTop,
            horizontalPadding, if (expanded) dp(10) else touchHeight - placement.surfaceTop - placement.surfaceHeight)
        root.minimumWidth = if (expanded) 0 else placement.compactWidth
        root.minimumHeight = if (expanded) 0 else touchHeight
        root.setOnClickListener(if (expanded) null else View.OnClickListener { islandDisplay.toggle(); render(state) })
        root.isClickable = !expanded
        root.contentDescription = if (expanded) "南枫下载详情" else "展开下载详情，${state.compactText}"
        val accent = when (state.tone) {
            DownloadIslandTone.SUCCESS -> Color.rgb(89, 210, 144)
            DownloadIslandTone.ERROR -> Color.rgb(255, 113, 113)
            DownloadIslandTone.WARNING -> Color.rgb(235, 177, 93)
            DownloadIslandTone.RUNNING -> Color.rgb(83, 204, 222)
        }
        val headerHeight = if (expanded) maxOf(dp(48), placement.statusBarHeight + dp(8)) else placement.surfaceHeight
        val compact = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            if (placement.cameraGap > 0) {
                // Equal wings keep the empty exclusion zone exactly on the physical camera,
                // in compact AND expanded mode. No concatenated text can cross this spacer.
                addView(statusText(state.cameraStartText, accent), LinearLayout.LayoutParams(0, headerHeight, 1f))
                addView(View(context), LinearLayout.LayoutParams(placement.cameraGap, headerHeight))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    if (state.cameraEndText.isBlank()) addView(statusIcon(state, accent), LinearLayout.LayoutParams(dp(18), dp(18)))
                    else addView(statusText(state.cameraEndText, accent), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, headerHeight))
                }, LinearLayout.LayoutParams(0, headerHeight, 1f))
            } else {
                addView(statusIcon(state, accent), LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(7) })
                addView(statusText(state.compactText, accent), LinearLayout.LayoutParams(0, headerHeight, 1f))
            }
            contentDescription = if (expanded) "收起下载详情" else "展开下载详情，${state.compactText}"
            if (expanded) setOnClickListener { islandDisplay.toggle(); render(state) }
        }
        // Expanded details get a wider body; the status-bar header keeps the same safe
        // centered width so it never drifts into the system icons when the body grows.
        root.addView(compact, LinearLayout.LayoutParams(
            placement.compactWidth - horizontalPadding * 2, headerHeight).apply { gravity = Gravity.CENTER_HORIZONTAL })
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
        renderedPlacement = placement
        val maxWidth = placement.screenWidth - dp(24)
        val requestedWidth = if (expanded) minOf(dp(320), maxWidth) else placement.compactWidth
        root.measure(View.MeasureSpec.makeMeasureSpec(requestedWidth,
            View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val targetWidth = root.measuredWidth
        val targetHeight = root.measuredHeight
        val morph = !first && !geometryChanged && modeChanged && oldWidth > 0 && oldHeight > 0
        val animate = animationEnabled && (first || morph)
        val carrier = window ?: FrameLayout(context).apply {
            clipChildren = false; clipToPadding = false
            addView(root)
            setOnApplyWindowInsetsListener { _, insets -> refreshGeometry(); insets }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE && expanded) {
                    islandDisplay.collapse(); rendered?.let(::render)
                    true
                } else false
            }
        }
        root.layoutParams = FrameLayout.LayoutParams(targetWidth, targetHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        root.pivotX = targetWidth / 2f
        root.pivotY = 0f
        // Keep enough transparent window space for the old size and spring overshoot while shrinking.
        // A constant centered carrier prevents the compositor from shifting an old buffer
        // sideways while the app is still laying out its new capsule width.
        // A trusted top window must not intercept the system bar through transparent
        // carrier margins. Its input window follows the island's actual content width.
        val windowWidth = if (host.mode == IslandWindowMode.ACCESSIBILITY) targetWidth else minOf(dp(352), maxWidth)
        val windowHeight = if (animate || root.scaleY != 1f) (maxOf(targetHeight, oldHeight) * 1.08f).toInt() else targetHeight
        val params = WindowManager.LayoutParams(windowWidth, windowHeight,
            host.type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            y = if (placement.inStatusBar) 0 else placement.statusBarHeight + dp(6)
            title = "NanfengDownloadIsland"
            windowAnimations = 0 // System resize animations can move a centered capsule sideways.
        }
        if (changeMotion) {
            // Scaling the camera gap inward would put glyphs under the lens mid-transition.
            // Camera layouts retain their horizontal exclusion zone while height springs.
            root.scaleX = if (placement.cameraGap > 0) 1f else if (animate && morph) oldWidth.toFloat() / targetWidth else if (first && animate) .88f else 1f
            root.scaleY = if (animate && morph) oldHeight.toFloat() / targetHeight else if (first && animate) .90f else 1f
            root.alpha = if (first && animate) 0f else 1f
        }
        try {
            if (first) {
                windows.addView(carrier, params); window = carrier; view = root
                attachedWindows = windows; attachedMode = host.mode
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

    private fun statusIcon(state: DownloadIslandState, color: Int) = ImageView(context).apply {
        setImageResource(when (state.tone) {
            DownloadIslandTone.SUCCESS -> android.R.drawable.stat_sys_download_done
            DownloadIslandTone.ERROR -> android.R.drawable.ic_dialog_alert
            else -> android.R.drawable.stat_sys_download
        })
        imageTintList = ColorStateList.valueOf(color)
    }
    private fun statusText(value: String, color: Int) = text(value, 14f).apply {
        gravity = Gravity.CENTER
        setTextColor(color)
        typeface = IslandFeedback.titleTypeface() ?: android.graphics.Typeface.DEFAULT
        // Like the system clock, the fixed status-bar slot uses density-scaled glyphs.
        // Accessibility font scaling remains available in the full detail body.
        setAutoSizeTextTypeUniformWithConfiguration(9, 14, 1, TypedValue.COMPLEX_UNIT_DIP)
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
        window?.let { runCatching { attachedWindows?.removeViewImmediate(it) } }
        window = null; attachedWindows = null; attachedMode = null; view = null; rendered = null; renderedExpanded = null; renderedPlacement = null; islandDisplay.collapse()
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
        context.unregisterComponentCallbacks(configurationCallback)
        removeView()
        synchronized(ownershipLock) { if (owner === this) owner = null }
    }
}
