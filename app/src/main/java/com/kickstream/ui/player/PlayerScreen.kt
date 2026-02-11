package com.kickstream.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickstream.util.LifecycleStartStopEffect
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

    // Back: dismiss quality menu → hide controls → navigate back
    BackHandler(enabled = true) {
        when {
            uiState.showQualityMenu -> viewModel.toggleQualityMenu()
            uiState.showControls -> {
                // Hide controls instead of navigating away
                viewModel.hideControls()
            }
            else -> onBack()
        }
    }

    LaunchedEffect(channelSlug) {
        viewModel.loadChannel(channelSlug)
    }

    // Pause player + chat when app goes to background, resume on foreground.
    // This prevents battery drain from video decoding and WebSocket connections
    // running while the TV is off or the user is in another app.
    LifecycleStartStopEffect(
        onStop = { viewModel.onPause() },
        onStart = { viewModel.onResume() },
    )

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
                            if (uiState.showQualityMenu) {
                                // Let D-pad navigate within the quality menu
                                false
                            } else {
                                viewModel.showControls()
                                true
                            }
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
                val offlineChatWidth by animateDpAsState(
                    targetValue = if (uiState.isChatVisible) 280.dp else 0.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "offlineChatWidth",
                )

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
                                modifier = Modifier
                                    .background(
                                        Color.White.copy(alpha = 0.06f),
                                        RoundedCornerShape(28.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Follow button — pill with label
                                Button(
                                    onClick = { viewModel.toggleFollow() },
                                    shape = ButtonDefaults.shape(
                                        shape = RoundedCornerShape(22.dp),
                                    ),
                                    colors = if (uiState.isFollowed) {
                                        ButtonDefaults.colors(
                                            containerColor = KickGreen.copy(alpha = 0.20f),
                                            contentColor = KickGreen,
                                            focusedContainerColor = KickGreen,
                                            focusedContentColor = Color.Black,
                                        )
                                    } else {
                                        ButtonDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.10f),
                                            contentColor = Color.White,
                                            focusedContainerColor = KickGreen,
                                            focusedContentColor = Color.Black,
                                        )
                                    },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (uiState.isFollowed) R.drawable.ic_star_filled
                                            else R.drawable.ic_star_outline,
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.isFollowed) "Following" else "Follow",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                // Chat pill — icon + label
                                Button(
                                    onClick = { viewModel.toggleChat() },
                                    shape = ButtonDefaults.shape(
                                        shape = RoundedCornerShape(22.dp),
                                    ),
                                    colors = if (uiState.isChatVisible) {
                                        ButtonDefaults.colors(
                                            containerColor = KickGreen.copy(alpha = 0.20f),
                                            contentColor = KickGreen,
                                            focusedContainerColor = KickGreen,
                                            focusedContentColor = Color.Black,
                                        )
                                    } else {
                                        ButtonDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.10f),
                                            contentColor = Color.White,
                                            focusedContainerColor = KickGreen,
                                            focusedContentColor = Color.Black,
                                        )
                                    },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (uiState.isChatVisible) R.drawable.ic_chat
                                            else R.drawable.ic_chat_off,
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Chat",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    // Chat sidebar (still works for offline channels)
                    if (offlineChatWidth > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(offlineChatWidth)
                                .fillMaxHeight()
                                .clipToBounds(),
                        ) {
                            ChatSidebar(
                                messages = uiState.chatMessages,
                                subscriberBadgeUrls = uiState.subscriberBadgeUrls,
                                modifier = Modifier.requiredWidth(280.dp),
                            )
                        }
                    }
                }
            }

            uiState.hlsUrl != null -> {
                val chatWidth by animateDpAsState(
                    targetValue = if (uiState.isChatVisible) 280.dp else 0.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "chatWidth",
                )

                val controlsFocusRequester = remember { FocusRequester() }

                // Main content: Video (with overlay inside) + Chat
                Row(modifier = Modifier.fillMaxSize()) {
                    // Video area — overlay lives INSIDE this Box so it only covers video
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        VideoPlayer(
                            hlsUrl = uiState.hlsUrl!!,
                            onBufferingChanged = { viewModel.onBufferingChanged(it) },
                            onQualitiesAvailable = { viewModel.onQualitiesAvailable(it) },
                            selectedQualityHeight = uiState.selectedQualityHeight,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                        )

                        // Controls overlay — scoped to video area only
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.showControls,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            // Auto-focus the chat button when controls overlay appears
                            LaunchedEffect(Unit) {
                                controlsFocusRequester.requestFocus()
                            }

                            // Track quality button's screen-X so we can place the menu above it
                            var qualityBtnRootX by remember { mutableStateOf(0f) }
                            var overlayBoxRootX by remember { mutableStateOf(0f) }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { coords ->
                                        overlayBoxRootX = coords.localToRoot(Offset.Zero).x
                                    },
                            ) {
                                // Semi-transparent scrim for text readability
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                )

                                // Top section: channel info
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

                                // Bottom section: action buttons + quality menu
                                val qualityFocusRequester = remember { FocusRequester() }

                                // Gradient scrim behind buttons
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.6f),
                                                    Color.Black.copy(alpha = 0.9f),
                                                ),
                                            ),
                                        ),
                                )

                                // Action buttons row — right-aligned near the chat sidebar
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 32.dp, bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Quality trigger button
                                    if (uiState.availableQualities.isNotEmpty()) {
                                        val currentLabel = uiState.availableQualities
                                            .firstOrNull { it.isSelected }?.label ?: "Auto"

                                        Button(
                                            onClick = {
                                                viewModel.toggleQualityMenu()
                                                viewModel.showControls()
                                            },
                                            shape = ButtonDefaults.shape(
                                                shape = RoundedCornerShape(22.dp),
                                            ),
                                            colors = ButtonDefaults.colors(
                                                containerColor = Color.White.copy(alpha = 0.10f),
                                                contentColor = Color.White,
                                                focusedContainerColor = KickGreen,
                                                focusedContentColor = Color.Black,
                                            ),
                                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                            modifier = Modifier.onGloballyPositioned { coords ->
                                                qualityBtnRootX = coords.localToRoot(Offset.Zero).x
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_settings),
                                                contentDescription = "Quality",
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = currentLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }

                                    // Follow pill
                                    Button(
                                        onClick = {
                                            viewModel.toggleFollow()
                                            viewModel.showControls()
                                        },
                                        shape = ButtonDefaults.shape(
                                            shape = RoundedCornerShape(22.dp),
                                        ),
                                        colors = if (uiState.isFollowed) {
                                            ButtonDefaults.colors(
                                                containerColor = KickGreen.copy(alpha = 0.20f),
                                                contentColor = KickGreen,
                                                focusedContainerColor = KickGreen,
                                                focusedContentColor = Color.Black,
                                            )
                                        } else {
                                            ButtonDefaults.colors(
                                                containerColor = Color.White.copy(alpha = 0.10f),
                                                contentColor = Color.White,
                                                focusedContainerColor = KickGreen,
                                                focusedContentColor = Color.Black,
                                            )
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                if (uiState.isFollowed) R.drawable.ic_star_filled
                                                else R.drawable.ic_star_outline,
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (uiState.isFollowed) "Following" else "Follow",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }

                                    // Chat pill
                                    Button(
                                        onClick = {
                                            viewModel.toggleChat()
                                            viewModel.showControls()
                                        },
                                        shape = ButtonDefaults.shape(
                                            shape = RoundedCornerShape(22.dp),
                                        ),
                                        colors = if (uiState.isChatVisible) {
                                            ButtonDefaults.colors(
                                                containerColor = KickGreen.copy(alpha = 0.20f),
                                                contentColor = KickGreen,
                                                focusedContainerColor = KickGreen,
                                                focusedContentColor = Color.Black,
                                            )
                                        } else {
                                            ButtonDefaults.colors(
                                                containerColor = Color.White.copy(alpha = 0.10f),
                                                contentColor = Color.White,
                                                focusedContainerColor = KickGreen,
                                                focusedContentColor = Color.Black,
                                            )
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.focusRequester(controlsFocusRequester),
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                if (uiState.isChatVisible) R.drawable.ic_chat
                                                else R.drawable.ic_chat_off,
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Chat",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }

                                // Quality menu — positioned directly above the Auto button
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = uiState.showQualityMenu,
                                    enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 2 },
                                    exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 2 },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(bottom = 64.dp)
                                        .offset {
                                            // Align menu's left edge with quality button's left edge
                                            val menuX = (qualityBtnRootX - overlayBoxRootX).toInt()
                                            IntOffset(menuX, 0)
                                        },
                                ) {
                                    LaunchedEffect(Unit) {
                                        qualityFocusRequester.requestFocus()
                                    }

                                    Column(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .background(
                                                Color(0xF0141417),
                                                RoundedCornerShape(12.dp),
                                            )
                                            .border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.08f),
                                                RoundedCornerShape(12.dp),
                                            )
                                            .padding(vertical = 6.dp),
                                    ) {
                                        uiState.availableQualities.forEach { quality ->
                                            val isQSelected = quality.isSelected
                                            Button(
                                                onClick = {
                                                    viewModel.selectQuality(quality.height)
                                                    viewModel.showControls()
                                                },
                                                shape = ButtonDefaults.shape(
                                                    shape = RoundedCornerShape(6.dp),
                                                ),
                                                colors = ButtonDefaults.colors(
                                                    containerColor = if (isQSelected)
                                                        KickGreen.copy(alpha = 0.12f)
                                                    else Color.Transparent,
                                                    contentColor = if (isQSelected)
                                                        KickGreen else Color.White,
                                                    focusedContainerColor = if (isQSelected)
                                                        KickGreen.copy(alpha = 0.25f)
                                                    else Color.White.copy(alpha = 0.10f),
                                                    focusedContentColor = if (isQSelected)
                                                        KickGreen else Color.White,
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp)
                                                    .then(
                                                        if (isQSelected) Modifier.focusRequester(qualityFocusRequester)
                                                        else Modifier,
                                                    ),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        text = quality.label,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isQSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    )
                                                    if (isQSelected) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_check),
                                                            contentDescription = null,
                                                            tint = KickGreen,
                                                            modifier = Modifier.size(14.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Buffering indicator overlay — also scoped to video area
                        if (uiState.isBuffering) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                KickLoader()
                            }
                        }
                    }

                    // Chat sidebar — separate from video Box, NOT covered by overlay
                    if (chatWidth > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(chatWidth)
                                .fillMaxHeight()
                                .clipToBounds(),
                        ) {
                            ChatSidebar(
                                messages = uiState.chatMessages,
                                subscriberBadgeUrls = uiState.subscriberBadgeUrls,
                                modifier = Modifier.requiredWidth(280.dp),
                            )
                        }
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
