package com.kickstream.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import android.util.Log
import coil.compose.AsyncImage
import com.kickstream.data.repository.FollowedChannel
import com.kickstream.ui.theme.KickGreen
import com.kickstream.ui.theme.LocalExtendedColors

@Composable
fun FollowedChannelCard(
    channel: FollowedChannel,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val extendedColors = LocalExtendedColors.current

    Card(
        onClick = onClick,
        shape = CardDefaults.shape(
            shape = RoundedCornerShape(12.dp),
        ),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = BorderStroke(2.dp, Color(0xFF53FC18)),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        scale = CardDefaults.scale(
            focusedScale = 1.05f,
        ),
        modifier = Modifier
            .width(240.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            ),
    ) {
        Column {
            // Compact thumbnail area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                // Image priority: live thumbnail > banner/profile picture > initial letter
                val imageUrl = (channel.thumbnail ?: channel.profilePicture)
                    ?.takeIf { it.isNotBlank() }
                Log.d("KickStream", "Card '${channel.slug}': imageUrl=$imageUrl, thumbnail=${channel.thumbnail}, profilePic=${channel.profilePicture}")
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = channel.slug,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        onError = { Log.e("KickStream", "Coil failed to load image for '${channel.slug}': ${it.result.throwable.message}, url=$imageUrl") },
                    )
                } else {
                    // No image available — show styled initial letter in a circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(KickGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = channel.slug.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = KickGreen,
                        )
                    }
                }

                // Live/Offline indicator + viewer count
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (channel.isLive) extendedColors.liveRed
                                else Color.Gray.copy(alpha = 0.7f)
                            ),
                    ) {
                        Text(
                            text = if (channel.isLive) "LIVE" else "Offline",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                    if (channel.isLive && channel.viewerCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f)),
                        ) {
                            Text(
                                text = formatViewerCount(channel.viewerCount),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            // Info section
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = channel.slug,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (channel.isLive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (channel.isLive && channel.streamTitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = channel.streamTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatViewerCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
} + " viewers"
