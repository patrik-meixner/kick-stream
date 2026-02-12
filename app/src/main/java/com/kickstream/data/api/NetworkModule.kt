package com.kickstream.data.api

import android.util.Log
import com.kickstream.BuildConfig
import com.kickstream.data.api.model.AuthTokenResponse
import com.kickstream.data.local.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.Cache
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object NetworkModule {

    private const val TAG = "KickStream"
    private const val HTTP_CACHE_SIZE = 10L * 1024 * 1024 // 10 MB

    private var httpCache: Cache? = null

    /** Call once from Application.onCreate() to enable HTTP disk caching. */
    fun init(cacheDir: File) {
        httpCache = Cache(File(cacheDir, "http_cache"), HTTP_CACHE_SIZE)
    }

    /**
     * SSL-permissive OkHttpClient for loading public CDN content (images, HLS streams).
     *
     * Android TV emulators (and some real TV devices with outdated CA stores) fail SSL
     * chain validation for *.kick.com CDN certificates because the intermediate CAs
     * aren't in the device's system trust store. Since these are public, unauthenticated
     * URLs (thumbnails, stream manifests, chunks), we use a permissive TrustManager.
     *
     * NOT used for API calls — those go through [baseClient] with platform-default
     * certificate validation.
     */
    val permissiveSslClient: OkHttpClient by lazy {
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

    /**
     * OkHttp Authenticator that transparently refreshes the access token on 401.
     *
     * When the Kick API returns 401 (expired/invalid token), this authenticator:
     * 1. Uses the stored refresh_token to obtain a new access_token
     * 2. Retries the original request with the fresh token
     * 3. If refresh fails, strips the Authorization header entirely so public
     *    endpoints (like /public/v1/channels) still work anonymously
     *
     * This avoids the "expired token poisons public requests" problem where
     * attaching an invalid Bearer token to a public endpoint causes 401 instead
     * of falling back to anonymous access.
     */
    private fun tokenAuthenticator(tokenStore: TokenStore): Authenticator =
        object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                // Prevent infinite retry loops — if we already tried refreshing, give up
                if (response.request.header("X-Token-Refreshed") != null) {
                    Log.w(TAG, "Token refresh already attempted, stripping auth header for anonymous fallback")
                    // Strip Authorization entirely so public endpoints work anonymously
                    return response.request.newBuilder()
                        .removeHeader("Authorization")
                        .build()
                }

                Log.d(TAG, "Got 401, attempting token refresh...")

                val newToken = try {
                    val refreshToken = tokenStore.getRefreshTokenSync() ?: run {
                        Log.w(TAG, "No refresh token available")
                        null
                    }
                    if (refreshToken != null) {
                        val formBody = FormBody.Builder()
                            .add("grant_type", "refresh_token")
                            .add("client_id", BuildConfig.KICK_CLIENT_ID)
                            .add("client_secret", BuildConfig.KICK_CLIENT_SECRET)
                            .add("refresh_token", refreshToken)
                            .build()
                        val refreshRequest = Request.Builder()
                            .url("https://id.kick.com/oauth/token")
                            .post(formBody)
                            .build()
                        val refreshResponse = authOkHttp.newCall(refreshRequest).execute()
                        val body = refreshResponse.body?.string()
                        if (refreshResponse.isSuccessful && body != null) {
                            val tokenResponse = json.decodeFromString<AuthTokenResponse>(body)
                            tokenStore.saveTokensSync(
                                tokenResponse.accessToken,
                                tokenResponse.refreshToken,
                                tokenResponse.expiresIn,
                            )
                            Log.d(TAG, "Token refreshed successfully")
                            tokenResponse.accessToken
                        } else {
                            Log.w(TAG, "Token refresh HTTP ${refreshResponse.code}: $body")
                            null
                        }
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Token refresh failed: ${e.message}")
                    null
                }

                return if (newToken != null) {
                    // Retry with the fresh token
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .header("X-Token-Refreshed", "true")
                        .build()
                } else {
                    // Refresh failed — strip auth header so public endpoints
                    // still work without authentication
                    Log.d(TAG, "Stripping Authorization header for anonymous fallback")
                    response.request.newBuilder()
                        .removeHeader("Authorization")
                        .header("X-Token-Refreshed", "true")
                        .build()
                }
            }
        }

    // Ensures all requests explicitly ask for JSON (prevents HTML responses)
    private val jsonAcceptInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    // Shared base client — all other clients derive from this via newBuilder()
    // to share the connection pool, dispatcher thread pool, and HTTP cache
    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .apply { httpCache?.let { cache(it) } }
            .build()
    }

    private fun buildOkHttpClient(tokenStore: TokenStore): OkHttpClient =
        baseClient.newBuilder()
            .addInterceptor(jsonAcceptInterceptor)
            .addInterceptor(authInterceptor(tokenStore))
            .authenticator(tokenAuthenticator(tokenStore))
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

    // Typesense search at search.kick.com — public API key, no auth needed.
    // Must replicate browser headers: text/plain Content-Type, Referer, Origin.
    // The API key is public (same one the website uses in every request).
    private val searchClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                // Re-create the body with text/plain media type to match what the browser sends.
                // Typesense proxied through Cloudflare rejects application/json Content-Type.
                val body = original.body
                val textPlain = "text/plain; charset=utf-8".toMediaType()
                val newBody = if (body != null) {
                    val buffer = okio.Buffer()
                    body.writeTo(buffer)
                    buffer.readByteString().toRequestBody(textPlain)
                } else null
                val request = original.newBuilder()
                    .method(original.method, newBody)
                    .header("X-Typesense-Api-Key", "nXIMW0iEN6sMujFYjFuhdrSwVow3pDQu")
                    .header("Referer", "https://kick.com/")
                    .header("Origin", "https://kick.com")
                    .build()
                chain.proceed(request)
            })
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    fun provideSearchApi(): KickSearchApi =
        Retrofit.Builder()
            .baseUrl("https://search.kick.com/")
            .client(searchClient)
            .addConverterFactory(converterFactory)
            .build()
            .create(KickSearchApi::class.java)

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
