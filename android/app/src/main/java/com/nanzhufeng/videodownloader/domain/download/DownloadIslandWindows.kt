package com.nanzhufeng.videodownloader.domain.download

import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class IslandWindowHost(val mode: IslandWindowMode, val manager: WindowManager) {
    val type: Int get() = when (mode) {
        IslandWindowMode.ACCESSIBILITY -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }
}

/** Main-thread service lifecycle. Only a live service supplies the accessibility window token. */
internal object DownloadIslandWindows {
    private val top = IslandTopHostStore<WindowManager>()
    private val connected = MutableStateFlow(false)
    val topConnected = connected.asStateFlow()

    fun connect(owner: Any, manager: WindowManager) {
        top.connect(owner, manager)
        connected.value = true
        DownloadIslandOverlay.onWindowHostChanged()
    }

    fun disconnect(owner: Any) {
        if (!top.disconnect(owner)) return
        connected.value = false
        DownloadIslandOverlay.onWindowHostChanged()
    }

    fun select(context: Context): IslandWindowHost {
        val manager = top.host
        return IslandWindowHost(
            IslandWindowMode.resolve(manager != null, Settings.canDrawOverlays(context)),
            manager ?: context.getSystemService(WindowManager::class.java),
        )
    }
}
