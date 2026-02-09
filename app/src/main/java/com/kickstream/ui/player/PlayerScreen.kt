package com.kickstream.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.R
import com.kickstream.ui.components.KickLoader
import com.kickstream.ui.player.components.ChatSidebar
import com.kickstream.ui.player.components.VideoPlayer
import com.kickstream.ui.theme.KickGreen
import com.kickstream.ui.theme.LiveRed

@Composable
fun PlayerScreen(
    channelSlug: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Use BackHandler for proper integration with Navigation back stack
    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(channelSlug) {
        viewModel.loadChannel(channelSlug)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Menu -> {
                            viewModel.toggleChat()
                            true
                        }

                        Key.DirectionRight -> {
                            // Only toggle chat when controls are hidden;
                            // when controls are visible, let D-pad navigate to Follow button
                            if (!uiState.showControls) {
                                viewModel.toggleChat()
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionLeft -> {
                            // Hide chat when pressing left (only when controls are hidden)
                            if (!uiState.showControls && uiState.isChatVisible) {
                                viewModel.toggleChat()
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionUp -> {
                            viewModel.showControls()
                            true
                        }

                        else -> false
                    }
                } else false
            }
            .focusable(),
    ) {
        when {
            uiState.isLoading -> {
                KickLoader()
            }

            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Press Back to return",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            uiState.hlsUrl != null -> {
                // Main content: Video + Chat
                Row(modifier = Modifier.fillMaxSize()) {
                    VideoPlayer(
                        hlsUrl = uiState.hlsUrl!!,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black),
                    )

                    AnimatedVisibility(
                        visible = uiState.isChatVisible,
                        enter = slideInHorizontally(initialOffsetX = { it }),
                        exit = slideOutHorizontally(targetOffsetX = { it }),
                    ) {
                        ChatSidebar(
                            messages = uiState.chatMessages,
                            modifier = Modifier.width(280.dp),
                        )
                    }
                }

                // Controls overlay
                AnimatedVisibility(
                    visible = uiState.showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.9f),
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                            .padding(horizontal = 32.dp, vertical = 20.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Left side: channel info
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                // Channel name
                                Text(
                                    text = uiState.channelName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // Stream title
                                val title = uiState.streamTitle
                                if (!title.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                // Metadata row: LIVE badge + viewer count + category
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // LIVE badge
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(
                                                LiveRed,
                                                RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )

                                    // Viewer count
                                    if (uiState.viewerCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_viewers),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = formatViewerCount(uiState.viewerCount),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.7f),
                                            )
                                        }
                                    }

                                    // Category
                                    val category = uiState.categoryName
                                    if (!category.isNullOrBlank()) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = KickGreen,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            // Right side: Follow button
                            Button(
                                onClick = { viewModel.toggleFollow() },
                                shape = ButtonDefaults.shape(
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            ) {
                                Text(
                                    text = if (uiState.isFollowed) "Unfollow" else "Follow",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            // Fallback: loaded but no HLS URL and no error
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No stream URL available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "The channel may not be live. Press Back to return.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatViewerCount(count: Int): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}
