package com.kickstream.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

            uiState.isExchangingCode -> {
                Text(
                    text = "Signing in...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            uiState.showWebView && uiState.webViewAuthUrl != null -> {
                KickAuthWebView(
                    authUrl = uiState.webViewAuthUrl!!,
                    onCallbackReceived = { url -> viewModel.onCallbackReceived(url) },
                    onBack = { viewModel.backToQrCode() },
                )
            }

            uiState.showManualCodeInput -> {
                ManualCodeEntry(
                    error = uiState.error,
                    onSubmit = { viewModel.onManualCodeSubmitted(it) },
                    onBack = { viewModel.backToQrCode() },
                )
            }

            uiState.authorizationUrl != null -> {
                QrCodeLoginContent(
                    authUrl = uiState.authorizationUrl!!,
                    isWaiting = uiState.isWaitingForCallback,
                    error = uiState.error,
                    onRetry = { viewModel.startAuthFlow() },
                    onWebView = { viewModel.switchToWebView() },
                    onManualCode = { viewModel.showManualCodeInput() },
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
                        Text("Try again")
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCodeLoginContent(
    authUrl: String,
    isWaiting: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onWebView: () -> Unit,
    onManualCode: () -> Unit,
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

        Spacer(modifier = Modifier.height(8.dp))

        if (isWaiting) {
            Text(
                text = "Waiting for authorization...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Try again")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onWebView) {
                Text("Sign in on this device")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onManualCode) {
                Text("Enter code manually")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KickAuthWebView(
    authUrl: String,
    onCallbackReceived: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Sign in on this device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    // Enable hardware acceleration for WebGL (needed by Kick's Kasada bot protection)
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode =
                        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Chromecast HD) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/131.0.0.0 Safari/537.36"

                    webChromeClient = WebChromeClient()

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith(LoginViewModel.REDIRECT_PREFIX)) {
                                onCallbackReceived(url)
                                return true
                            }
                            return false
                        }
                    }

                    loadUrl(authUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        )
    }
}

@Composable
private fun ManualCodeEntry(
    error: String?,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    var codeInput by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(48.dp),
    ) {
        Text(
            text = "Enter Authorization Code",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter the code shown on your phone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        BasicTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(codeInput) }),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .padding(12.dp),
                ) {
                    if (codeInput.isEmpty()) {
                        Text(
                            text = "Paste code here...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 18.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { onSubmit(codeInput) }) {
                Text("Sign in")
            }
        }
    }
}
