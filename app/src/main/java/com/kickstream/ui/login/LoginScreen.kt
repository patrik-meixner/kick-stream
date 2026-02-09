package com.kickstream.ui.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.util.QrCodeUtil

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSuccess, uiState.isAlreadyLoggedIn) {
        if (uiState.isSuccess || uiState.isAlreadyLoggedIn) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            uiState.isCheckingToken -> {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            uiState.authorizationUrl != null -> {
                LoginContent(
                    authUrl = uiState.authorizationUrl!!,
                    isWaiting = uiState.isWaitingForCallback,
                    error = uiState.error,
                    onRetry = { viewModel.startAuthFlow() },
                )
            }

            uiState.error != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Error: ${uiState.error}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.startAuthFlow() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginContent(
    authUrl: String,
    isWaiting: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(48.dp),
    ) {
        Text(
            text = "Sign in to Kick",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(32.dp))

        val qrBitmap: Bitmap? = remember(authUrl) {
            QrCodeUtil.generateQrCode(authUrl, 300)
        }
        qrBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Scan to authorize",
                modifier = Modifier.size(200.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Scan the QR code with your phone",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "or open this URL on your computer:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = authUrl,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isWaiting) {
            Text(
                text = "Waiting for authorization...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error: $error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}
