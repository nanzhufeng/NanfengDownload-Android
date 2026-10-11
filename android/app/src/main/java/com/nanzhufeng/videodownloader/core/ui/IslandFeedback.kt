package com.nanzhufeng.videodownloader.core.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import com.nanzhufeng.videodownloader.R

/** App-owned feedback, above dialogs but without overlay permission or input focus. */
object IslandFeedback {
    private val main = Handler(Looper.getMainLooper())
    private val state = IslandNoticeState()
    private var initialized = false
    private var resumed = WeakReference<Activity>(null)
    private var dialog: Dialog? = null
    private var activeSurface: FeedbackSurface? = null
    private var owner = WeakReference<Activity>(null)
    private var timeout: Runnable? = null
    private var deferred: DeferredNotice? = null
    @Volatile private var titleFont: Typeface? = null
    @Volatile private var bodyFont: Typeface? = null

    internal fun titleTypeface(): Typeface? = titleFont
    internal fun bodyTypeface(): Typeface? = bodyFont

    fun initialize(application: Application) {
        if (initialized) return
        initialized = true
        val loader = Executors.newSingleThreadExecutor { Thread(it, "nanfeng-island-fonts").apply { isDaemon = true } }
        loader.execute {
            try {
                titleFont = Typeface.Builder(application.assets, "fonts/noto_sans_sc_variable.ttf")
                    .setFontVariationSettings("'wght' 550").setWeight(550).setFallback("sans-serif").build()
                bodyFont = Typeface.Builder(application.assets, "fonts/noto_sans_sc_variable.ttf")
                    .setFontVariationSettings("'wght' 400").setWeight(400).setFallback("sans-serif").build()
            } catch (_: RuntimeException) {
                // A cosmetic asset failure must not break downloads or the app.
            } finally { loader.shutdown() }
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumed = WeakReference(activity)
                val pending = deferred
                if (pending != null) {
                    if (SystemClock.uptimeMillis() - pending.createdAt >= 10_000L) deferred = null
                    else if (pending.origin.get() !== activity) {
                        deferred = null
                        render(activity, pending.notice)
                    }
                }
            }
            override fun onActivityStopped(activity: Activity) {
                if (owner.get() === activity) closeImmediately()
                if (resumed.get() === activity) resumed.clear()
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (owner.get() === activity) closeImmediately()
                if (resumed.get() === activity) resumed.clear()
            }
            override fun onActivityCreated(activity: Activity, bundle: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) {
                if (owner.get() === activity) closeImmediately()
                if (resumed.get() === activity) resumed.clear()
            }
            override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) = Unit
        })
    }

    fun success(context: Context, message: String, title: String? = null) = show(
        context, if (title == null) IslandNotice(message, tone = IslandTone.SUCCESS)
        else IslandNotice(title, message, IslandTone.SUCCESS),
    )

    fun error(context: Context, message: String, title: String = "操作未完成") =
        show(context, IslandNotice(title, message, IslandTone.ERROR))

    fun info(context: Context, message: String, title: String? = null) = show(
        context, if (title == null) IslandNotice(message) else IslandNotice(title, message),
    )

    /** The login window finishes immediately; deliver its result to the returning app window. */
    fun successOnReturn(context: Context, message: String) {
        main.post { deferred = DeferredNotice(WeakReference(context.activity()), IslandNotice(message, tone = IslandTone.SUCCESS), SystemClock.uptimeMillis()) }
    }

    private fun show(context: Context, notice: IslandNotice) {
        main.post {
            val activity = context.activity() ?: resumed.get() ?: return@post
            if (!activity.isFinishing && !activity.isDestroyed && resumed.get() === activity) render(activity, notice)
        }
    }

    private fun render(activity: Activity, notice: IslandNotice) {
        closeImmediately()
        val generation = state.present(notice)
        val accessibility = activity.getSystemService(AccessibilityManager::class.java)
        val duration = accessibility.getRecommendedTimeoutMillis(
            notice.readingMillis.toInt(),
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_CONTROLS,
        ).toLong()
        val popup = object : Dialog(activity) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) { dismiss(); return true }
                return super.onTouchEvent(event)
            }
        }
        popup.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val window = checkNotNull(popup.window)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        window.attributes = window.attributes.apply { dimAmount = 0f; windowAnimations = 0 }
        val surface = createSurface(activity, notice) {
            if (state.dismiss(generation)) exit(popup)
        }
        fun schedule() {
            if (dialog !== popup || state.generation != generation || state.current == null) return
            timeout?.let(main::removeCallbacks)
            timeout = Runnable { if (state.dismiss(generation)) exit(popup) }.also { main.postDelayed(it, duration) }
        }
        surface.onInteraction = { touching ->
            if (touching) timeout?.let(main::removeCallbacks) else schedule()
        }
        val root = FrameLayout(activity).apply {
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 14))
            clipChildren = false
            clipToPadding = false
            addView(surface, FrameLayout.LayoutParams(-1, -2))
        }
        popup.setContentView(root)
        // Installing the dialog decor applies its theme animation; override it afterwards.
        window.setWindowAnimations(0)
        popup.setOnDismissListener {
            if (dialog === popup) {
                activeSurface?.animate()?.cancel()
                activeSurface = null
                timeout?.let(main::removeCallbacks)
                timeout = null
                state.dismiss(generation)
                dialog = null
                owner.clear()
            }
        }
        dialog = popup
        activeSurface = surface
        owner = WeakReference(activity)
        try {
            popup.show()
        } catch (_: WindowManager.BadTokenException) {
            closeImmediately()
            return
        } catch (_: WindowManager.InvalidDisplayException) {
            closeImmediately()
            return
        }
        val metrics = activity.resources.displayMetrics
        val available = metrics.widthPixels - dp(activity, 24)
        val width = if (notice.detail.isBlank()) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = titleFont ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textSize = 15.5f * metrics.scaledDensity
            }
            maxOf(dp(activity, 240), paint.measureText(notice.title).toInt() + dp(activity, 112))
        } else dp(activity, 464)
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        window.setLayout(minOf(available, width), -2)
        // Dialog windows already fit the status bar; only use an explicit inset in edge-to-edge windows.
        window.attributes = window.attributes.apply { y = dp(activity, 4); horizontalMargin = 0f }
        surface.post {
            if (dialog !== popup || !popup.isShowing || state.current == null) return@post
            if (ValueAnimator.areAnimatorsEnabled()) {
                surface.pivotX = surface.width / 2f
                surface.pivotY = 0f
                IslandMotion.enter(surface)
            }
            surface.announceForAccessibility(notice.spokenText)
        }
        schedule()
    }

    private fun createSurface(activity: Activity, notice: IslandNotice, dismiss: () -> Unit): FeedbackSurface {
        val accent = when (notice.tone) {
            IslandTone.SUCCESS -> Color.rgb(157, 232, 193)
            IslandTone.ERROR -> Color.rgb(255, 180, 172)
            IslandTone.INFO -> Color.rgb(255, 196, 157)
        }
        val radius = dp(activity, if (notice.detail.isBlank()) 40 else 25).toFloat()
        val surface = FeedbackSurface(activity).apply {
            minimumHeight = dp(activity, if (notice.detail.isBlank()) 56 else 64)
            elevation = dp(activity, 10).toFloat()
            outlineAmbientShadowColor = Color.argb(32, 0, 0, 0)
            outlineSpotShadowColor = Color.argb(56, 0, 0, 0)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(40, 50, 52), Color.rgb(23, 31, 33))).apply {
                cornerRadius = radius
            }
            foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(28, 255, 255, 255)), null,
                GradientDrawable().apply { cornerRadius = radius; setColor(Color.WHITE) },
            )
            clipToOutline = true
            isClickable = true
            setOnClickListener { dismiss() }
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (notice.detail.isBlank()) Gravity.CENTER_VERTICAL else Gravity.TOP
            setPadding(dp(activity, 14), dp(activity, if (notice.detail.isBlank()) 12 else 14), dp(activity, 18), dp(activity, if (notice.detail.isBlank()) 12 else 14))
        }
        val icon = ImageView(activity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(24, Color.red(accent), Color.green(accent), Color.blue(accent))) }
            setPadding(dp(activity, 9), dp(activity, 9), dp(activity, 9), dp(activity, 9))
            setImageResource(when (notice.tone) { IslandTone.SUCCESS -> R.drawable.island_success; IslandTone.ERROR -> R.drawable.island_error; IslandTone.INFO -> R.drawable.island_info })
            imageTintList = android.content.res.ColorStateList.valueOf(accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(activity, if (notice.detail.isBlank()) 32 else 38), dp(activity, if (notice.detail.isBlank()) 32 else 38)).apply { marginEnd = dp(activity, 12) })
        val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val title = text(activity, notice.title, 15.5f, Color.rgb(247, 250, 249), true).apply {
            ViewCompat.setAccessibilityHeading(this, true)
        }
        text.addView(title, LinearLayout.LayoutParams(-1, -2))
        if (notice.detail.isNotBlank()) {
            val body = text(activity, notice.detail, 13.5f, Color.rgb(202, 213, 214), false)
            val scroll = object : ScrollView(activity) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val maxHeight = minOf(dp(activity, 300), (activity.resources.displayMetrics.heightPixels * .32f).toInt())
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
                }
            }.apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = true
                addView(body, FrameLayout.LayoutParams(-1, -2))
            }
            text.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 4) })
        }
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        surface.addView(row, FrameLayout.LayoutParams(-1, -2))
        return surface
    }

    private fun text(context: Context, value: String, size: Float, color: Int, heading: Boolean) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = if (heading) titleFont ?: Typeface.create("sans-serif-medium", Typeface.NORMAL) else bodyFont ?: Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        letterSpacing = if (heading) .015f else .01f
        setLineSpacing(dp(context, 3).toFloat(), 1f)
        setFontFeatureSettings("'kern' 1")
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
        hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
    }

    private fun exit(popup: Dialog) {
        val view = activeSurface ?: return popup.dismiss()
        if (!ValueAnimator.areAnimatorsEnabled()) popup.dismiss()
        else view.animate().alpha(0f).scaleX(.94f).scaleY(.94f).setDuration(150).withEndAction { popup.dismiss() }.start()
    }

    private fun closeImmediately() {
        timeout?.let(main::removeCallbacks)
        timeout = null
        activeSurface?.animate()?.cancel()
        activeSurface = null
        dialog?.window?.decorView?.animate()?.cancel()
        dialog?.dismiss()
        dialog = null
        owner.clear()
        state.dismiss(state.generation)
    }

    private fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
        else -> null
    }
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private data class DeferredNotice(val origin: WeakReference<Activity>, val notice: IslandNotice, val createdAt: Long)

    private class FeedbackSurface(context: Context) : FrameLayout(context) {
        var onInteraction: (Boolean) -> Unit = {}
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onInteraction(true)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) onInteraction(false)
            return super.dispatchTouchEvent(event)
        }
    }
}
