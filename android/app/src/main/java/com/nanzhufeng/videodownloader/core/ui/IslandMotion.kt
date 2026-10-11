package com.nanzhufeng.videodownloader.core.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.OvershootInterpolator

/** Centered size changes, with one restrained spring-like settle and no sideways travel. */
internal object IslandMotion {
    fun enter(view: View, scaleX: Float = .88f, scaleY: Float = .90f,
              alpha: Float = 0f, onEnd: () -> Unit = {}) {
        view.animate().cancel()
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = scaleX
        view.scaleY = scaleY
        view.alpha = alpha
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f
            onEnd()
            return
        }
        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(360)
            .setInterpolator(OvershootInterpolator(1.1f)).withEndAction(onEnd).start()
    }
}
