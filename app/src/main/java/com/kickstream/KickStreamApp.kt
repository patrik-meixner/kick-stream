package com.kickstream

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.kickstream.data.api.NetworkModule

class KickStreamApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(cacheDir)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(NetworkModule.permissiveSslClient)
            .components {
                // ImageDecoderDecoder handles animated WebP natively on API 28+
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10) // 10% of app memory — emotes are tiny (~2-10KB each)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("emote_cache"))
                    .maxSizeBytes(10L * 1024 * 1024) // 10 MB disk cache
                    .build()
            }
            .crossfade(false) // Emotes in fast-scrolling chat should render instantly
            .build()
    }
}
