package com.kickstream.ui.player.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.kickstream.R
import com.kickstream.data.chat.ChatBadgeInfo
import com.kickstream.data.chat.ChatSegment
import com.kickstream.data.chat.ParsedChatMessage
import com.kickstream.ui.theme.KickGreen
import com.kickstream.ui.theme.OnDarkSurface
import kotlinx.coroutines.launch

/** Sidebar surface — slightly elevated from pure black video area */
private val ChatSurface = Color(0xFF0F1012)

/** Header surface — lifted from the message area */
private val ChatHeaderSurface = Color(0xFF151518)

/** Thin separator between sections */
private val ChatDivider = Color(0xFF2A2A2D)

private val EmoteSizeSp = 20.sp
private val MessageFontSize = 14.sp
private val MessageLineHeight = 22.sp
private val BadgeSizeSp = 16.sp

/** Map badge type string → drawable resource ID */
private fun badgeDrawable(type: String): Int? = when (type.lowercase()) {
    "moderator" -> R.drawable.ic_badge_moderator
    "vip" -> R.drawable.ic_badge_vip
    "subscriber" -> R.drawable.ic_badge_subscriber
    "founder" -> R.drawable.ic_badge_founder
    "verified" -> R.drawable.ic_badge_verified
    "broadcaster", "owner" -> R.drawable.ic_badge_broadcaster
    "og" -> R.drawable.ic_badge_og
    "staff" -> R.drawable.ic_badge_staff
    "sub_gifter" -> R.drawable.ic_badge_gifter
    else -> null
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun ChatSidebar(
    messages: List<ParsedChatMessage>,
    subscriberBadgeUrls: Map<Int, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var isScrollMode by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isNearBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems == 0 || lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty() && isNearBottom && !isScrollMode) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    // Left edge accent line (visible in scroll mode) + main content
    Row(modifier = modifier.fillMaxHeight()) {
        // Accent edge — thin green line on left when scrolling
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(
                    if (isScrollMode) KickGreen.copy(alpha = 0.7f)
                    else Color.Transparent,
                ),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(ChatSurface),
        ) {
            // Header
            ChatHeader(isScrollMode = isScrollMode)

            // Messages area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                        when {
                            event.key == Key.DirectionUp && isScrollMode -> {
                                coroutineScope.launch { listState.animateScrollBy(-120f) }
                                true
                            }
                            event.key == Key.DirectionDown && isScrollMode -> {
                                coroutineScope.launch { listState.animateScrollBy(120f) }
                                if (isNearBottom) isScrollMode = false
                                true
                            }
                            (event.key == Key.Back || event.key == Key.Escape) && isScrollMode -> {
                                coroutineScope.launch {
                                    if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
                                }
                                isScrollMode = false
                                true
                            }
                            event.key == Key.DirectionUp && !isScrollMode -> {
                                isScrollMode = true
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                // Top fade (header → messages transition)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ChatSurface, Color.Transparent),
                            ),
                        ),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }

                    items(messages, key = { it.id }) { message ->
                        ChatMessageRow(message, subscriberBadgeUrls)
                    }

                    item { Spacer(Modifier.height(40.dp)) }
                }

                // Bottom fade gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, ChatSurface),
                            ),
                        ),
                )

                // "New messages" pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isNearBottom && messages.isNotEmpty(),
                    enter = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = KickGreen,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable {
                                coroutineScope.launch {
                                    if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
                                }
                                isScrollMode = false
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "\u2193",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "New messages",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    isScrollMode: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatHeaderSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chat",
                color = OnDarkSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.weight(1f))

            // Scroll mode badge
            if (isScrollMode) {
                Text(
                    text = "PAUSED",
                    color = KickGreen,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            KickGreen.copy(alpha = 0.12f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // Gradient divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ChatDivider,
                            ChatDivider,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun ChatMessageRow(
    message: ParsedChatMessage,
    subscriberBadgeUrls: Map<Int, String>,
) {
    val userColor = remember(message.color) {
        try {
            Color(android.graphics.Color.parseColor(message.color))
        } catch (_: Exception) {
            Color(0xFF53FC18)
        }
    }

    val (annotatedText, inlineContent) = remember(message.id) {
        val contentMap = mutableMapOf<String, InlineTextContent>()

        val text = buildAnnotatedString {
            // Badges (before username) — rendered as inline icons
            message.badges.forEach { badge ->
                val badgeId = "badge_${badge.type}_${contentMap.size}"

                // Check if this is a subscriber badge with a custom channel image
                val subBadgeUrl = if (badge.type.equals("subscriber", ignoreCase = true)) {
                    val months = badge.text?.toIntOrNull() ?: 0
                    // Find the highest badge tier that the user qualifies for
                    subscriberBadgeUrls.keys
                        .filter { it <= months }
                        .maxOrNull()
                        ?.let { subscriberBadgeUrls[it] }
                } else null

                if (subBadgeUrl != null) {
                    // Custom channel subscriber badge — load from CDN
                    contentMap[badgeId] = InlineTextContent(
                        placeholder = Placeholder(
                            width = BadgeSizeSp,
                            height = BadgeSizeSp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        AsyncImage(
                            model = subBadgeUrl,
                            contentDescription = "sub ${badge.text}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    // Global badge — use local vector drawable
                    val drawableRes = badgeDrawable(badge.type) ?: return@forEach
                    contentMap[badgeId] = InlineTextContent(
                        placeholder = Placeholder(
                            width = BadgeSizeSp,
                            height = BadgeSizeSp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = drawableRes),
                            contentDescription = badge.type,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                appendInlineContent(badgeId, "[${badge.type}]")
                append(" ")
            }

            // Username
            withStyle(SpanStyle(color = userColor, fontWeight = FontWeight.Bold)) {
                append("${message.username}: ")
            }

            // Segments
            message.segments.forEach { segment ->
                when (segment) {
                    is ChatSegment.Text -> {
                        withStyle(SpanStyle(color = OnDarkSurface.copy(alpha = 0.92f))) {
                            append(segment.text)
                            append(" ")
                        }
                    }

                    is ChatSegment.EmoteImage -> {
                        // Each emote gets a unique placeholder ID
                        val placeholderId = "emote_${segment.emoteName}_${contentMap.size}"
                        contentMap[placeholderId] = InlineTextContent(
                            placeholder = Placeholder(
                                width = EmoteSizeSp,
                                height = EmoteSizeSp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                            ),
                        ) {
                            EmoteImage(
                                url = segment.url,
                                name = segment.emoteName,
                                animated = segment.animated,
                            )
                        }
                        appendInlineContent(placeholderId, "[${segment.emoteName}]")
                        append(" ")
                    }
                }
            }
        }

        Pair(text, contentMap)
    }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        fontSize = MessageFontSize,
        lineHeight = MessageLineHeight,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    )
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun EmoteImage(
    url: String,
    name: String,
    animated: Boolean,
) {
    val context = LocalContext.current
    val model = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .apply {
                if (animated) {
                    decoderFactory(ImageDecoderDecoder.Factory())
                }
            }
            .build()
    }

    AsyncImage(
        model = model,
        contentDescription = name,
        modifier = Modifier.size(28.dp),
        contentScale = ContentScale.Fit,
    )
}
