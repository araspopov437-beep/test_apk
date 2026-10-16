package com.example.shorts

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/** Coil умеет доставать кадр из видео — используется для превью, если нет обложки. */
class ShortsApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        Notifier.schedule(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
