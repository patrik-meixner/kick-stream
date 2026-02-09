package com.kickstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kickstream.navigation.AppNavigation
import com.kickstream.ui.theme.KickStreamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KickStreamTheme {
                AppNavigation()
            }
        }
    }
}
