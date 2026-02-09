package com.kickstream.data.repository

import android.util.Log
import com.kickstream.BuildConfig
import com.kickstream.data.api.KickAuthApi
import com.kickstream.data.local.TokenStore
import com.kickstream.util.PkceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import android.os.Build
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
    }

    private val clientId = BuildConfig.KICK_CLIENT_ID
    private val clientSecret = BuildConfig.KICK_CLIENT_SECRET
    private val port = 8374

    /** The redirect URI registered at dev.kick.com — the GitHub Pages relay page. */
    private val activeRedirectUri: String = BuildConfig.KICK_REDIRECT_URI

    data class AuthSession(
        val authorizationUrl: String,
        val codeVerifier: String,
        val state: String,
    )

    fun createAuthSession(): AuthSession {
        val codeVerifier = PkceUtil.generateCodeVerifier()
        val codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier)
        val randomState = PkceUtil.generateState()
        val scopes = "user:read channel:read events:subscribe"

        // Detect the TV's LAN IP so the relay page can forward the code here.
        // On emulators, the LAN IP is a virtual address (10.0.2.x) unreachable from
        // external devices. Use the host machine's IP from BuildConfig instead.
        val lanIp = if (isEmulator()) {
            Log.d(TAG, "Emulator detected, using BuildConfig redirect host override")
            null // fall through to BuildConfig
        } else {
            getDeviceLanIp()
        }
        val tvCallbackUrl = if (lanIp != null) {
            "http://$lanIp:$port/callback"
        } else {
            // Fallback: extract host from BuildConfig redirect URI or use loopback
            val overrideHost = BuildConfig.KICK_EMULATOR_HOST
            if (overrideHost.isNotEmpty()) {
                "http://$overrideHost:$port/callback"
            } else {
                Log.w(TAG, "Could not detect LAN IP, falling back to loopback")
                "http://127.0.0.1:$port/callback"
            }
        }
        Log.d(TAG, "TV callback URL: $tvCallbackUrl")

        // Encode TV's callback URL into state: "<random_token>:<base64url(tv_url)>"
        // Kick passes state through untouched — the relay page decodes it.
        val encodedTvUrl = PkceUtil.encodeBase64Url(tvCallbackUrl)
        val state = "$randomState:$encodedTvUrl"

        val authUrl = buildString {
            append("https://id.kick.com/oauth/authorize?")
            append("client_id=").append(clientId)
            append("&response_type=code")
            append("&redirect_uri=").append(URLEncoder.encode(activeRedirectUri, "UTF-8"))
            append("&scope=").append(URLEncoder.encode(scopes, "UTF-8"))
            append("&state=").append(URLEncoder.encode(state, "UTF-8"))
            append("&code_challenge=").append(codeChallenge)
            append("&code_challenge_method=S256")
        }

        return AuthSession(authUrl, codeVerifier, randomState)
    }

    /**
     * Listen for the relayed callback on a plain HTTP server.
     * The relay page sends: GET /callback?code=xxx&state=<random_token>
     */
    suspend fun waitForCallback(expectedState: String): String = withContext(Dispatchers.IO) {
        val serverSocket = ServerSocket(port)
        serverSocket.soTimeout = 300_000 // 5 minute timeout

        try {
            val socket = serverSocket.accept()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() // "GET /callback?code=xxx&state=yyy HTTP/1.1"

            val queryString = requestLine.split(" ")[1].substringAfter("?")
            val params = queryString.split("&").associate {
                val (key, value) = it.split("=", limit = 2)
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }

            // Respond with CORS headers so the relay page's fetch() succeeds
            val response = buildString {
                appendLine("HTTP/1.1 200 OK")
                appendLine("Content-Type: text/html")
                appendLine("Access-Control-Allow-Origin: *")
                appendLine("Connection: close")
                appendLine()
                appendLine("<html><body style=\"background:#0E0E10;color:#EFEFF1;font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0\">")
                appendLine("<div style=\"text-align:center\"><h1 style=\"color:#53FC18\">Authorized!</h1>")
                appendLine("<p>You can close this tab and return to your TV.</p></div>")
                appendLine("</body></html>")
            }
            socket.getOutputStream().write(response.toByteArray())
            socket.close()

            val code = params["code"] ?: throw IllegalStateException("No code in callback")
            val state = params["state"] ?: throw IllegalStateException("No state in callback")

            if (state != expectedState) {
                throw SecurityException("State mismatch — possible CSRF attack")
            }

            code
        } finally {
            serverSocket.close()
        }
    }

    suspend fun exchangeCodeForToken(code: String, codeVerifier: String) {
        val response = authApi.exchangeCodeForToken(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            redirectUri = activeRedirectUri,
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

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator")

    /**
     * Detect the device's LAN IPv4 address (e.g. 192.168.x.x).
     * Returns null if no suitable address is found.
     */
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
