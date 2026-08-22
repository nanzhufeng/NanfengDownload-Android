package com.nanzhufeng.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nanzhufeng.videodownloader.domain.download.NotificationPermissionPolicy
import com.nanzhufeng.videodownloader.navigation.NanzhufengApp
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val incomingSharedText = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        acceptSharedText(intent)
        requestNotificationPermissionIfNeeded()
        val container = (application as NanzhufengApplication).container
        setContent {
            NanzhufengApp(
                container = container,
                incomingSharedText = incomingSharedText,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedText(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as NanzhufengApplication).container.sessions.refresh()
    }

    private fun acceptSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotBlank()) incomingSharedText.tryEmit(text)
    }

    private fun requestNotificationPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            NotificationPermissionPolicy.needsRuntimeRequest(Build.VERSION.SDK_INT, granted)
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
