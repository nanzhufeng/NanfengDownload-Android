package com.nanzhufeng.videodownloader.domain.download

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/** Drawing token only: no event subscription, window retrieval, gestures, or task ownership. */
class DownloadIslandTopService : AccessibilityService() {
    private var receiverRegistered = false
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            DownloadIslandOverlay.hideForScreenOff()
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        // The service's WindowManager carries the system-issued accessibility window token.
        DownloadIslandWindows.connect(this, getSystemService(WindowManager::class.java))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        DownloadIslandWindows.disconnect(this)
        if (receiverRegistered) { unregisterReceiver(screenOff); receiverRegistered = false }
    }
}
