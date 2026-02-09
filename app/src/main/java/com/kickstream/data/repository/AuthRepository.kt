package com.kickstream.data.repository

import android.os.Build
import android.util.Log
import com.kickstream.BuildConfig
import com.kickstream.data.api.KickAuthApi
import com.kickstream.data.local.TokenStore
import com.kickstream.util.PkceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder

class AuthRepository(
    private val authApi: KickAuthApi,
    private val tokenStore: TokenStore,
) {
    companion object {
        private const val TAG = "KickStream"
        private const val PORT = 8374
        private const val LOOPBACK_REDIRECT = "http://127.0.0.1:$PORT/callback"
    }

    private val clientId = BuildConfig.KICK_CLIENT_ID
    private val clientSecret = BuildConfig.KICK_CLIENT_SECRET

    /** The redirect URI registered at dev.kick.com (GitHub Pages relay page). */
    private val remoteRedirectUri: String = BuildConfig.KICK_REDIRECT_URI

    private val manualCodeChannel = Channel<String>(1)
    @Volatile private var activeServerSocket: ServerSocket? = null

    data class AuthSession(
        val authorizationUrl: String,
        val codeVerifier: String,
        val state: String,
    )

    /**
     * Create an OAuth authorization session.
     *
     * @param useLocalRedirect true for WebView flow (localhost redirect),
     *                         false for QR code flow (GitHub Pages relay + TV IP in state)
     */
    fun createAuthSession(useLocalRedirect: Boolean = false): AuthSession {
        val codeVerifier = PkceUtil.generateCodeVerifier()
        val codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier)
        val randomState = PkceUtil.generateState()
        val scopes = "user:read channel:read events:subscribe"

        val redirectUri: String
        val state: String

        if (useLocalRedirect) {
            // WebView flow: redirect to loopback, intercepted by shouldOverrideUrlLoading
            redirectUri = LOOPBACK_REDIRECT
            state = randomState
        } else {
            // QR code flow: redirect to relay page, state encodes TV's callback URL
            redirectUri = remoteRedirectUri
            val tvCallbackUrl = buildTvCallbackUrl()
            Log.d(TAG, "TV callback URL: $tvCallbackUrl")
            val encodedTvUrl = PkceUtil.encodeBase64Url(tvCallbackUrl)
            state = "$randomState:$encodedTvUrl"
        }

        val authUrl = buildString {
            append("https://id.kick.com/oauth/authorize?")
            append("client_id=").append(clientId)
            append("&response_type=code")
            append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"))
            append("&scope=").append(URLEncoder.encode(scopes, "UTF-8"))
            append("&state=").append(URLEncoder.encode(state, "UTF-8"))
            append("&code_challenge=").append(codeChallenge)
            append("&code_challenge_method=S256")
        }

        return AuthSession(authUrl, codeVerifier, randomState)
    }

    /**
     * Listen for the relayed OAuth callback on a local HTTP server.
     * Also checks for manual code submissions (from WebView or manual entry).
     */
    suspend fun waitForCallback(expectedState: String): String = withContext(Dispatchers.IO) {
        val serverSocket = ServerSocket(PORT)
        activeServerSocket = serverSocket
        serverSocket.soTimeout = 1_000 // 1-second accept timeout for polling

        try {
            val deadline = System.currentTimeMillis() + 300_000 // 5 minutes

            while (System.currentTimeMillis() < deadline) {
                // Check for manual code submission
                manualCodeChannel.tryReceive().getOrNull()?.let { return@withContext it }

                try {
                    val socket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val requestLine = reader.readLine() ?: continue

                    // Handle OPTIONS preflight for CORS
                    if (requestLine.startsWith("OPTIONS")) {
                        val corsResponse = buildString {
                            appendLine("HTTP/1.1 204 No Content")
                            appendLine("Access-Control-Allow-Origin: *")
                            appendLine("Access-Control-Allow-Methods: GET, OPTIONS")
                            appendLine("Access-Control-Allow-Headers: *")
                            appendLine("Connection: close")
                            appendLine()
                        }
                        socket.getOutputStream().write(corsResponse.toByteArray())
                        socket.close()
                        continue
                    }

                    // Parse GET /callback?code=xxx&state=yyy HTTP/1.1
                    val path = requestLine.split(" ").getOrNull(1) ?: continue
                    val queryString = path.substringAfter("?", "")
                    val params = queryString.split("&").associate {
                        val parts = it.split("=", limit = 2)
                        URLDecoder.decode(parts[0], "UTF-8") to
                            URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                    }

                    // Serve success page
                    val html = buildString {
                        appendLine("<html><body style=\"background:#0E0E10;color:#EFEFF1;font-family:sans-serif;")
                        appendLine("display:flex;justify-content:center;align-items:center;height:100vh;margin:0\">")
                        appendLine("<div style=\"text-align:center\">")
                        appendLine("<h1 style=\"color:#53FC18\">Authorized!</h1>")
                        appendLine("<p>You can close this tab and return to your TV.</p>")
                        appendLine("</div></body></html>")
                    }
                    val response = buildString {
                        appendLine("HTTP/1.1 200 OK")
                        appendLine("Content-Type: text/html; charset=utf-8")
                        appendLine("Access-Control-Allow-Origin: *")
                        appendLine("Connection: close")
                        appendLine()
                        append(html)
                    }
                    socket.getOutputStream().write(response.toByteArray())
                    socket.close()

                    val code = params["code"]
                        ?: throw IllegalStateException("No code in callback")
                    val callbackState = params["state"]
                        ?: throw IllegalStateException("No state in callback")

                    if (callbackState != expectedState) {
                        throw SecurityException("State mismatch — possible CSRF attack")
                    }

                    return@withContext code
                } catch (_: java.net.SocketTimeoutException) {
                    // No connection yet, keep polling
                }
            }
            throw java.net.SocketTimeoutException("Authorization timed out after 5 minutes")
        } finally {
            activeServerSocket = null
            serverSocket.close()
        }
    }

    /** Submit a code from WebView or manual entry, unblocking waitForCallback. */
    suspend fun submitManualCode(code: String) {
        manualCodeChannel.send(code)
    }

    /** Stop the HTTP server if it's running. */
    fun stopWaiting() {
        try {
            activeServerSocket?.close()
        } catch (_: Exception) {
        }
        activeServerSocket = null
    }

    suspend fun exchangeCodeForToken(code: String, codeVerifier: String) {
        // Use the same redirect URI that was used in the authorization request.
        // For QR flow = remote relay, for WebView flow = loopback.
        // Since we don't track which was used, try remote first; if the session used
        // loopback the caller should pass the right one. For simplicity, we pass
        // the redirect URI as a parameter.
        exchangeCodeForToken(code, codeVerifier, remoteRedirectUri)
    }

    suspend fun exchangeCodeForToken(code: String, codeVerifier: String, redirectUri: String) {
        val response = authApi.exchangeCodeForToken(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier,
        )
        tokenStore.saveTokens(response.accessToken, response.refreshToken, response.expiresIn)
    }

    suspend fun refreshTokenIfNeeded(): Boolean {
        if (!tokenStore.isTokenExpired()) return true
        val refreshToken = tokenStore.getRefreshToken() ?: return false
        return try {
            val response = authApi.refreshToken(
                clientId = clientId,
                clientSecret = clientSecret,
                refreshToken = refreshToken,
            )
            tokenStore.saveTokens(response.accessToken, response.refreshToken, response.expiresIn)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun refreshAndGetToken(): String? {
        val refreshToken = tokenStore.getRefreshToken() ?: return null
        return try {
            val response = authApi.refreshToken(
                clientId = clientId,
                clientSecret = clientSecret,
                refreshToken = refreshToken,
            )
            tokenStore.saveTokens(response.accessToken, response.refreshToken, response.expiresIn)
            response.accessToken
        } catch (_: Exception) {
            null
        }
    }

    suspend fun isLoggedIn(): Boolean = tokenStore.getAccessToken() != null

    suspend fun logout() {
        val token = tokenStore.getAccessToken()
        if (token != null) {
            try {
                authApi.revokeToken(token)
            } catch (_: Exception) {
            }
        }
        tokenStore.clear()
    }

    // --- Private helpers ---

    private fun buildTvCallbackUrl(): String {
        val host = if (isEmulator()) {
            val emulatorHost = BuildConfig.KICK_EMULATOR_HOST
            if (emulatorHost.isNotBlank()) {
                Log.d(TAG, "Emulator detected, using configured host: $emulatorHost")
                emulatorHost
            } else {
                Log.w(TAG, "Emulator detected but KICK_EMULATOR_HOST not set, using loopback")
                "127.0.0.1"
            }
        } else {
            getDeviceLanIp() ?: run {
                Log.w(TAG, "Could not detect LAN IP, falling back to loopback")
                "127.0.0.1"
            }
        }
        return "http://$host:$PORT/callback"
    }

    @Suppress("KotlinConstantConditions")
    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator")

    private fun getDeviceLanIp(): String? {
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect LAN IP: ${e.message}")
        }
        return null
    }
}
