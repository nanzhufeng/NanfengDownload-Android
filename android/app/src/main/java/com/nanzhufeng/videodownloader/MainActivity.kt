package com.nanzhufeng.videodownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nanzhufeng.videodownloader.navigation.NanzhufengApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NanzhufengApplication).container
        setContent {
            NanzhufengApp(
                container = container,
            )
        }
    }
}
