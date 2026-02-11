package com.kickstream

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kickstream.navigation.AppNavigation
import com.kickstream.ui.theme.KickStreamTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.kickstream.data.local.TokenStore
import com.kickstream.data.api.NetworkModule
import com.kickstream.data.repository.AuthRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // TV apps should be truly fullscreen — hide system bars entirely
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // Keep screen on during streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            KickStreamTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Proactively refresh token when app returns from background.
        // This prevents stale token errors on API calls after long sleep.
        val tokenStore = TokenStore(application)
        val authApi = NetworkModule.provideAuthApi()
        val authRepository = AuthRepository(authApi, tokenStore)
        MainScope().launch {
            authRepository.refreshTokenIfNeeded()
        }
    }
}
