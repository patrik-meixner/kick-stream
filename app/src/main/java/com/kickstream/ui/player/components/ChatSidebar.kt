package com.kickstream.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.kickstream.data.chat.ChatSegment
import com.kickstream.data.chat.ParsedChatMessage
import com.kickstream.ui.theme.OnDarkSurface

/** Background matching Kick's chat panel */
private val ChatBackground = Color(0xFF141517)

/** Subtle separator between header and messages */
private val ChatDivider = Color(0xFF2A2A2D)

private val EmoteSizeSp = 20.sp
private val MessageFontSize = 14.sp
private val MessageLineHeight = 22.sp
private val BadgeFontSize = 10.sp

// Badge colors matching Kick's native palette
private val ModeratorColor = Color(0xFF00B300) // Green sword
private val VipColor = Color(0xFFE91CFF)        // Purple diamond
private val SubscriberColor = Color(0xFF53FC18)  // Kick green
private val FounderColor = Color(0xFFFFD700)     // Gold
private val VerifiedColor = Color(0xFF1DA1F2)    // Blue check
private val OwnerColor = Color(0xFFFF3B3B)       // Red crown

/** Map badge type string → display label + color */
private fun badgeInfo(type: String): Pair<String, Color>? = when (type.lowercase()) {
    "moderator" -> "MOD" to ModeratorColor
    "vip" -> "VIP" to VipColor
    "subscriber" -> "SUB" to SubscriberColor
    "founder" -> "FOUNDER" to FounderColor
    "verified" -> "✓" to VerifiedColor
    "broadcaster", "owner" -> "OWNER" to OwnerColor
    "og" -> "OG" to FounderColor
    "sub_gifter" -> "GIFTER" to SubscriberColor
    else -> null // Unknown badge types are silently skipped
}

@Composable
fun ChatSidebar(
    messages: List<ParsedChatMessage>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val isNearBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems == 0 || lastVisible >= totalItems - 5
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ChatBackground),
    ) {
        // Header
        ChatHeader()

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ChatDivider),
        )

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Spacer(Modifier.height(6.dp)) }

            items(messages, key = { it.id }) { message ->
                ChatMessageRow(message)
            }

            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun ChatHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Chat",
            color = OnDarkSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChatMessageRow(message: ParsedChatMessage) {
    val userColor = try {
        Color(android.graphics.Color.parseColor(message.color))
    } catch (_: Exception) {
        Color(0xFF53FC18)
    }

    // Build a single AnnotatedString with inline emote placeholders.
    // This makes the entire message one text layout so word-wrapping
    // happens at character boundaries, not at composable boundaries.
    val inlineContent = mutableMapOf<String, InlineTextContent>()

    val annotatedText = buildAnnotatedString {
        // Badges (before username)
        message.badges.forEach { badgeType ->
            val info = badgeInfo(badgeType) ?: return@forEach
            val (label, color) = info
            withStyle(
                SpanStyle(
                    color = color,
                    fontSize = BadgeFontSize,
                    fontWeight = FontWeight.Bold,
                    background = color.copy(alpha = 0.15f),
                ),
            ) {
                append(" $label ")
            }
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
                    val placeholderId = "emote_${segment.emoteName}_${inlineContent.size}"
                    inlineContent[placeholderId] = InlineTextContent(
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
