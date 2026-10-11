package com.nanzhufeng.videodownloader.domain.download

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Activity callbacks, never process importance (a bound foreground worker is not a visible screen). */
internal object DownloadActivityVisibility : Application.ActivityLifecycleCallbacks {
    private val visible = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Activity, Boolean>())
    val isVisible: Boolean get() = visible.isNotEmpty()
    override fun onActivityStarted(activity: Activity) { visible.add(activity); DownloadIslandOverlay.hideForActivity() }
    override fun onActivityStopped(activity: Activity) { visible.remove(activity) }
    override fun onActivityDestroyed(activity: Activity) { visible.remove(activity) }
    override fun onActivityCreated(activity: Activity, bundle: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) = Unit
}
