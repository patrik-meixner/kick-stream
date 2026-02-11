package com.kickstream.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Invokes [onResume] every time the current LifecycleOwner reaches ON_RESUME.
 *
 * This fires:
 *  - When the composable first enters composition (initial resume)
 *  - When the app returns from background (home button, TV sleep/wake)
 *  - When navigating back to the screen via the back-stack
 *
 * Use this to refresh stale data when a screen becomes visible again.
 */
@Composable
fun LifecycleResumeEffect(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Invokes [onStart]/[onStop] when the LifecycleOwner transitions.
 *
 * Use this for expensive resources that should only run while the app
 * is in the foreground: ExoPlayer playback, WebSocket connections, etc.
 */
@Composable
fun LifecycleStartStopEffect(
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onStart()
                Lifecycle.Event.ON_STOP -> onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
