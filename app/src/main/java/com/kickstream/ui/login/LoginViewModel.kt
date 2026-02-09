package com.kickstream.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kickstream.data.api.NetworkModule
import com.kickstream.data.local.TokenStore
import com.kickstream.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isCheckingToken: Boolean = true,
    val isAlreadyLoggedIn: Boolean = false,
    val authorizationUrl: String? = null,
    val isWaitingForCallback: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application)
    private val authApi = NetworkModule.provideAuthApi()
    private val authRepository = AuthRepository(authApi, tokenStore)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var currentSession: AuthRepository.AuthSession? = null

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

    fun startAuthFlow() {
        viewModelScope.launch {
            try {
                val session = authRepository.createAuthSession()
                currentSession = session
                Log.d("KickStream", "Auth URL: ${session.authorizationUrl}")
                _uiState.value = LoginUiState(
                    isCheckingToken = false,
                    authorizationUrl = session.authorizationUrl,
                    isWaitingForCallback = true,
                )

                val code = authRepository.waitForCallback(session.state)
                authRepository.exchangeCodeForToken(code, session.codeVerifier)

                _uiState.value = _uiState.value.copy(
                    isWaitingForCallback = false,
                    isSuccess = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isWaitingForCallback = false,
                    error = e.message ?: "Authentication failed",
                )
            }
        }
    }
}
