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
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
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
                    // Reset auto-hide timer on any key press while controls are visible
                    if (uiState.showControls) {
                        viewModel.showControls()
                    }

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
                            text = "The channel may not be live.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.loadChannel(channelSlug) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            uiState.isOffline -> {
                // Offline channel: no video, but chat + follow still available
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = uiState.channelName,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                            )
                            Text(
                                text = "Offline",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .background(
                                        Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Follow button
                                IconButton(
                                    onClick = { viewModel.toggleFollow() },
                                    colors = IconButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = if (uiState.isFollowed) KickGreen else Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.3f),
                                        focusedContentColor = if (uiState.isFollowed) KickGreen else Color.White,
                                    ),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (uiState.isFollowed) R.drawable.ic_star_filled
                                            else R.drawable.ic_star_outline,
                                        ),
                                        contentDescription = if (uiState.isFollowed) "Unfollow" else "Follow",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                // Chat toggle button
                                IconButton(
                                    onClick = { viewModel.toggleChat() },
                                    colors = IconButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.3f),
                                        focusedContentColor = Color.White,
                                    ),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (uiState.isChatVisible) R.drawable.ic_chat
                                            else R.drawable.ic_chat_off,
                                        ),
                                        contentDescription = if (uiState.isChatVisible) "Hide chat" else "Show chat",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Chat sidebar (still works for offline channels)
                    AnimatedVisibility(
                        visible = uiState.isChatVisible,
                        enter = slideInHorizontally(initialOffsetX = { it }),
                        exit = slideOutHorizontally(targetOffsetX = { it }),
                    ) {
                        ChatSidebar(
                            messages = uiState.chatMessages,
                            subscriberBadgeUrls = uiState.subscriberBadgeUrls,
                            modifier = Modifier.width(280.dp),
                        )
                    }
                }
            }

            uiState.hlsUrl != null -> {
                // Main content: Video + Chat
                Row(modifier = Modifier.fillMaxSize()) {
                    VideoPlayer(
                        hlsUrl = uiState.hlsUrl!!,
                        onBufferingChanged = { viewModel.onBufferingChanged(it) },
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
                            subscriberBadgeUrls = uiState.subscriberBadgeUrls,
                            modifier = Modifier.width(280.dp),
                        )
                    }
                }

                // Controls overlay (full-screen: top gradient for info, bottom gradient for buttons)
                AnimatedVisibility(
                    visible = uiState.showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── Top section: channel info ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
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
                            Column {
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
                        }

                        // ── Bottom section: action buttons (Follow + Chat toggle) ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Black.copy(alpha = 0.9f),
                                        ),
                                    ),
                                )
                                .padding(horizontal = 32.dp, vertical = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Follow / Unfollow button (star)
                                IconButton(
                                    onClick = {
                                        viewModel.toggleFollow()
                                        viewModel.showControls() // reset auto-hide timer
                                    },
                                    colors = IconButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = if (uiState.isFollowed) KickGreen else Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.3f),
                                        focusedContentColor = if (uiState.isFollowed) KickGreen else Color.White,
                                    ),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (uiState.isFollowed) R.drawable.ic_star_filled
                                            else R.drawable.ic_star_outline,
                                        ),
                                        contentDescription = if (uiState.isFollowed) "Unfollow" else "Follow",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                // Chat visibility toggle button (chat bubble)
                                IconButton(
                                    onClick = {
                                        viewModel.toggleChat()
                                        viewModel.showControls() // reset auto-hide timer
                                    },
                                    colors = IconButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.3f),
                                        focusedContentColor = Color.White,
                                    ),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (uiState.isChatVisible) R.drawable.ic_chat
                                            else R.drawable.ic_chat_off,
                                        ),
                                        contentDescription = if (uiState.isChatVisible) "Hide chat" else "Show chat",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Buffering indicator overlay
                if (uiState.isBuffering) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        KickLoader()
                    }
                }
            }

            // Fallback: loaded but no HLS URL and no error
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "No stream URL available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "The channel may not be live.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.loadChannel(channelSlug) }) {
                            Text("Retry")
                        }
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
