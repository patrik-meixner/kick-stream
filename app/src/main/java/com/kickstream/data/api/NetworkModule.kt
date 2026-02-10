package com.kickstream.data.api

import com.kickstream.data.local.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun authInterceptor(tokenStore: TokenStore): Interceptor = Interceptor { chain ->
        val token = tokenStore.getAccessTokenSync()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    // Ensures all requests explicitly ask for JSON (prevents HTML responses)
    private val jsonAcceptInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    // Shared base client — all other clients derive from this via newBuilder()
    // to share the connection pool and dispatcher thread pool
    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun buildOkHttpClient(tokenStore: TokenStore): OkHttpClient =
        baseClient.newBuilder()
            .addInterceptor(jsonAcceptInterceptor)
            .addInterceptor(authInterceptor(tokenStore))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    private val authOkHttp: OkHttpClient by lazy {
        baseClient.newBuilder().build()
    }

    private val converterFactory by lazy {
        json.asConverterFactory("application/json".toMediaType())
    }

    fun provideAuthApi(): KickAuthApi =
        Retrofit.Builder()
            .baseUrl("https://id.kick.com/")
            .client(authOkHttp)
            .addConverterFactory(converterFactory)
            .build()
            .create(KickAuthApi::class.java)

    fun provideKickApi(tokenStore: TokenStore): KickApi =
        Retrofit.Builder()
            .baseUrl("https://api.kick.com/")
            .client(buildOkHttpClient(tokenStore))
            .addConverterFactory(converterFactory)
            .build()
            .create(KickApi::class.java)

    // Unofficial web API -- used for chatroom ID and followed channels (not in official API)
    private val unofficialClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor(jsonAcceptInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    fun provideUnofficialApi(): KickUnofficialApi =
        Retrofit.Builder()
            .baseUrl("https://kick.com/")
            .client(unofficialClient)
            .addConverterFactory(converterFactory)
            .build()
            .create(KickUnofficialApi::class.java)

    // Unofficial API with auth -- used for /api/v2/channels/followed
    fun provideAuthenticatedUnofficialApi(tokenStore: TokenStore): KickUnofficialApi =
        Retrofit.Builder()
            .baseUrl("https://kick.com/")
            .client(buildOkHttpClient(tokenStore))
            .addConverterFactory(converterFactory)
            .build()
            .create(KickUnofficialApi::class.java)

    // 7TV API -- no auth required, used for global + channel emotes
    private val sevenTvClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor(jsonAcceptInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun provideSevenTvApi(): SevenTvApi =
        Retrofit.Builder()
            .baseUrl("https://7tv.io/")
            .client(sevenTvClient)
            .addConverterFactory(converterFactory)
            .build()
            .create(SevenTvApi::class.java)
}
