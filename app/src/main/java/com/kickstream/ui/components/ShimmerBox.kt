package com.kickstream.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.kickstream.ui.theme.DarkSurface
import com.kickstream.ui.theme.DarkSurfaceVariant

/**
 * A shimmer loading placeholder. Use as a lightweight skeleton while content loads.
 * Animates a gradient sweep from left to right.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            DarkSurface,
            DarkSurfaceVariant,
            DarkSurface,
        ),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 0f),
    )

    Spacer(
        modifier = modifier.background(shimmerBrush),
    )
}
