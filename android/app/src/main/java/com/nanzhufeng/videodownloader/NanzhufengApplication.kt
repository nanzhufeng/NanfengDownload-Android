package com.nanzhufeng.videodownloader

import com.chaquo.python.android.PyApplication

class NanzhufengApplication : PyApplication() {
    val container: AppContainer by lazy { AppContainer.create(this) }
}
