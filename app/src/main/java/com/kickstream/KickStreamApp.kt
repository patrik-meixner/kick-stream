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
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class KickStreamApp : Application(), ImageLoaderFactory {

    /**
     * OkHttpClient for loading public CDN images (thumbnails, profile pics).
     *
     * Android TV emulators (and some real TV devices with outdated CA stores)
     * fail SSL chain validation for images.kick.com / files.kick.com because
     * the CDN certificate chain uses intermediate CAs not present in the
     * device's system trust store. Since these are public, unauthenticated
     * image URLs, we use a permissive TrustManager for image loading only.
     *
     * This client is NOT used for API calls — those go through NetworkModule
     * with the platform's default certificate validation.
     */
    private val imageHttpClient: OkHttpClient by lazy {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(imageHttpClient)
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
