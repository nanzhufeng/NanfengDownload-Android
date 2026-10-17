package com.nanzhufeng.videodownloader

import com.chaquo.python.android.PyApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.nanzhufeng.videodownloader.core.media.LocalVideoPreviewFetcher
import com.nanzhufeng.videodownloader.core.media.LocalVideoPreviewKeyer

class NanzhufengApplication : PyApplication(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(com.nanzhufeng.videodownloader.domain.download.DownloadActivityVisibility)
        com.nanzhufeng.videodownloader.core.ui.IslandFeedback.initialize(this)
    }

    val container: AppContainer by lazy { AppContainer.create(this) }

    /** Registers MediaStore video previews plus animated GIF/WebP decoding for history media. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(LocalVideoPreviewKeyer())
            add(LocalVideoPreviewFetcher.Factory())
            add(ImageDecoderDecoder.Factory())
            add(VideoFrameDecoder.Factory())
        }
        .build()
}
