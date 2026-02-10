package com.kickstream.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal DTO for the unofficial kick.com/api/v2/channels/{slug} endpoint.
 * Only extracts fields we can't get from the official API: chatroom ID
 * and playback_url (as fallback).
 */
@Serializable
data class UnofficialChannelResponse(
    val id: Int,
    val slug: String,
    @SerialName("playback_url") val playbackUrl: String? = null,
    val chatroom: UnofficialChatroom? = null,
    @SerialName("subscriber_badges") val subscriberBadges: List<UnofficialSubscriberBadge>? = null,
)

@Serializable
data class UnofficialChatroom(
    val id: Int,
)

@Serializable
data class UnofficialSubscriberBadge(
    val id: Int,
    @SerialName("channel_id") val channelId: Int? = null,
    val months: Int,
    @SerialName("badge_image") val badgeImage: UnofficialBadgeImage? = null,
)

@Serializable
data class UnofficialBadgeImage(
    val src: String? = null,
    val srcset: String? = null,
)

/**
 * DTO for the unofficial /api/v2/channels/followed endpoint.
 * Response is paginated: { nextCursor, channels: [...] }
 * Each channel has flattened fields (no nested user/livestream objects).
 */
@Serializable
data class UnofficialFollowedResponse(
    @SerialName("nextCursor") val nextCursor: Int? = null,
    val channels: List<UnofficialFollowedChannel> = emptyList(),
)

@Serializable
data class UnofficialFollowedChannel(
    @SerialName("channel_slug") val channelSlug: String,
    @SerialName("user_username") val userUsername: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
)
