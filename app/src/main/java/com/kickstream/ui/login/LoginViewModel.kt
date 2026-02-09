package com.kickstream.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kickstream.data.api.NetworkModule
import com.kickstream.data.local.TokenStore
import com.kickstream.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isCheckingToken: Boolean = true,
    val isAlreadyLoggedIn: Boolean = false,
    // QR code flow
    val authorizationUrl: String? = null,
    val isWaitingForCallback: Boolean = false,
    // WebView flow
    val showWebView: Boolean = false,
    val webViewAuthUrl: String? = null,
    // Manual code flow
    val showManualCodeInput: Boolean = false,
    // Common
    val isExchangingCode: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KickStream"
        /** WebView intercepts redirects starting with this prefix. */
        const val REDIRECT_PREFIX = "http://127.0.0.1:8374/callback"
    }

    private val tokenStore = TokenStore(application)
    private val authApi = NetworkModule.provideAuthApi()
    private val authRepository = AuthRepository(authApi, tokenStore)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var currentSession: AuthRepository.AuthSession? = null
    private var waitJob: Job? = null

    init {
        checkExistingToken()
    }

    private fun checkExistingToken() {
        viewModelScope.launch {
            val loggedIn = authRepository.isLoggedIn()
            if (loggedIn) {
                val refreshed = authRepository.refreshTokenIfNeeded()
                _uiState.value = LoginUiState(
                    isCheckingToken = false,
                    isAlreadyLoggedIn = refreshed,
                    isSuccess = refreshed,
                )
                if (!refreshed) startAuthFlow()
            } else {
                startAuthFlow()
            }
        }
    }

    /** Start the QR code + HTTP server flow (primary). */
    fun startAuthFlow() {
        // Cancel any previous waiting job
        waitJob?.cancel()
        authRepository.stopWaiting()

        viewModelScope.launch {
            try {
                val session = authRepository.createAuthSession(useLocalRedirect = false)
                currentSession = session
                Log.d(TAG, "Auth URL: ${session.authorizationUrl}")

                _uiState.value = LoginUiState(
                    isCheckingToken = false,
                    authorizationUrl = session.authorizationUrl,
                    isWaitingForCallback = true,
                )

                // Launch HTTP server listener in background
                waitJob = launch {
                    try {
                        val code = authRepository.waitForCallback(session.state)
                        exchangeCode(code, useLocalRedirect = false)
                    } catch (e: java.net.SocketTimeoutException) {
                        _uiState.value = _uiState.value.copy(
                            isWaitingForCallback = false,
                            error = "Authorization timed out. Please try again.",
                        )
                    } catch (e: java.net.BindException) {
                        Log.w(TAG, "Port 8374 already in use: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isWaitingForCallback = false,
                            error = "Port 8374 is in use. Please try again.",
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Callback failed: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isWaitingForCallback = false,
                            error = e.message ?: "Authentication failed",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState(
                    isCheckingToken = false,
                    error = e.message ?: "Authentication failed",
                )
            }
        }
    }

    /** Switch to WebView-based login (fallback). */
    fun switchToWebView() {
        waitJob?.cancel()
        authRepository.stopWaiting()

        viewModelScope.launch {
            try {
                val session = authRepository.createAuthSession(useLocalRedirect = true)
                currentSession = session
                Log.d(TAG, "WebView Auth URL: ${session.authorizationUrl}")

                _uiState.value = _uiState.value.copy(
                    showWebView = true,
                    webViewAuthUrl = session.authorizationUrl,
                    isWaitingForCallback = false,
                    showManualCodeInput = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to start WebView login: ${e.message}",
                )
            }
        }
    }

    /** Show manual code entry UI. */
    fun showManualCodeInput() {
        _uiState.value = _uiState.value.copy(
            showManualCodeInput = true,
            showWebView = false,
            error = null,
        )
    }

    /** Go back to QR code view from WebView or manual entry. */
    fun backToQrCode() {
        _uiState.value = _uiState.value.copy(
            showWebView = false,
            showManualCodeInput = false,
            webViewAuthUrl = null,
            error = null,
        )
        // Restart the QR flow if we're not already waiting
        if (!_uiState.value.isWaitingForCallback) {
            startAuthFlow()
        }
    }

    /**
     * Called by the WebView when it intercepts the localhost redirect.
     */
    fun onCallbackReceived(url: String) {
        val session = currentSession ?: return
        val code = extractQueryParam(url, "code") ?: run {
            _uiState.value = _uiState.value.copy(error = "No authorization code received")
            return
        }

        // Unblock the HTTP server wait loop if it's still running
        viewModelScope.launch {
            try {
                authRepository.submitManualCode(code)
            } catch (_: Exception) {
            }
        }

        exchangeCode(code, useLocalRedirect = true)
    }

    /** Called when user manually enters the code from the relay page. */
    fun onManualCodeSubmitted(code: String) {
        if (code.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter the authorization code")
            return
        }

        // Unblock the HTTP server wait loop
        viewModelScope.launch {
            try {
                authRepository.submitManualCode(code)
            } catch (_: Exception) {
            }
        }

        // Manual code comes from the QR flow (remote redirect URI)
        exchangeCode(code.trim(), useLocalRedirect = false)
    }

    private fun exchangeCode(code: String, useLocalRedirect: Boolean) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExchangingCode = true,
                isWaitingForCallback = false,
                error = null,
            )
            try {
                val redirectUri = if (useLocalRedirect) {
                    REDIRECT_PREFIX
                } else {
                    com.kickstream.BuildConfig.KICK_REDIRECT_URI
                }
                authRepository.exchangeCodeForToken(code, session.codeVerifier, redirectUri)
                _uiState.value = _uiState.value.copy(
                    isExchangingCode = false,
                    isSuccess = true,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Token exchange failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isExchangingCode = false,
                    error = "Sign-in failed: ${e.message}",
                )
            }
        }
    }

    private fun extractQueryParam(url: String, param: String): String? {
        val query = url.substringAfter("?", "")
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it[0] == param }
            ?.getOrNull(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    override fun onCleared() {
        super.onCleared()
        waitJob?.cancel()
        authRepository.stopWaiting()
    }
}
