package com.nanzhufeng.videodownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nanzhufeng.videodownloader.navigation.NanzhufengApp
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val incomingSharedText = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptSharedText(intent)
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

    private fun acceptSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotBlank()) incomingSharedText.tryEmit(text)
    }
}
