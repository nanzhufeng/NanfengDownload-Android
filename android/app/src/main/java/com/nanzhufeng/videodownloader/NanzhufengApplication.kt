package com.nanzhufeng.videodownloader

import com.chaquo.python.android.PyApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class NanzhufengApplication : PyApplication(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer.create(this) }

    /** Registers actual MediaStore video-frame decoding for history previews. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(VideoFrameDecoder.Factory())
        }
        .build()
}
