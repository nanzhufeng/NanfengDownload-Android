package com.nanzhufeng.videodownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.nanzhufeng.videodownloader.probe.DouyinProbeActivity
import com.nanzhufeng.videodownloader.probe.ProbeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ProbeScreen(onOpenDouyin = { url ->
                    startActivity(
                        Intent(this, DouyinProbeActivity::class.java)
                            .putExtra(DouyinProbeActivity.EXTRA_URL, url),
                    )
                })
            }
        }
    }
}
