package com.kickstream.ui.home.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kickstream.data.repository.FollowedChannel
import com.kickstream.ui.theme.KickGreen
import com.kickstream.ui.theme.LocalExtendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun FollowedChannelCard(
    channel: FollowedChannel,
    onClick: () -> Unit,
) {
    val extendedColors = LocalExtendedColors.current

    Card(
        onClick = onClick,
        shape = CardDefaults.shape(
            shape = RoundedCornerShape(12.dp),
        ),
        modifier = Modifier.width(200.dp),
    ) {
        Column {
            // Compact thumbnail area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val thumbnailUrl = channel.thumbnail
                if (thumbnailUrl != null) {
                    var bitmap by remember(thumbnailUrl) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(thumbnailUrl) {
                        bitmap = withContext(Dispatchers.IO) {
                            try {
                                BitmapFactory.decodeStream(URL(thumbnailUrl).openStream())
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = channel.slug,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                // Live/Offline indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
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
