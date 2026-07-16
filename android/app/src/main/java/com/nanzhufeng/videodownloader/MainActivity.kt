package com.nanzhufeng.videodownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nanzhufeng.videodownloader.navigation.NanzhufengApp
import com.nanzhufeng.videodownloader.probe.DouyinProbeActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NanzhufengApplication).container
        setContent {
            NanzhufengApp(
                container = container,
                onOpenDouyin = { url ->
                    startActivity(
                        Intent(this, DouyinProbeActivity::class.java)
                            .putExtra(DouyinProbeActivity.EXTRA_URL, url),
                    )
                },
            )
        }
    }
}
