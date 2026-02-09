package com.kickstream.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Official API: GET /public/v1/channels ---

@Serializable
data class ChannelsApiResponse(
    val message: String? = null,
    val data: List<ChannelData> = emptyList(),
)

@Serializable
data class ChannelData(
    @SerialName("broadcaster_user_id") val broadcasterUserId: Int,
    val slug: String,
    @SerialName("channel_description") val channelDescription: String? = null,
    @SerialName("stream_title") val streamTitle: String? = null,
    @SerialName("banner_picture") val bannerPicture: String? = null,
    val category: ApiCategory? = null,
    val stream: ApiStream? = null,
)

@Serializable
data class ApiStream(
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    val thumbnail: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    val language: String? = null,
    @SerialName("is_mature") val isMature: Boolean = false,
    @SerialName("custom_tags") val customTags: List<String>? = null,
    val url: String? = null,
    val key: String? = null,
)

@Serializable
data class ApiCategory(
    val id: Int,
    val name: String,
    val thumbnail: String? = null,
)

// --- Official API: GET /public/v1/livestreams ---

@Serializable
data class LivestreamsApiResponse(
    val message: String? = null,
    val data: List<LivestreamData> = emptyList(),
)

@Serializable
data class LivestreamData(
    @SerialName("broadcaster_user_id") val broadcasterUserId: Int,
    @SerialName("channel_id") val channelId: Int? = null,
    val slug: String,
    @SerialName("stream_title") val streamTitle: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    val thumbnail: String? = null,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("started_at") val startedAt: String? = null,
    val language: String? = null,
    @SerialName("has_mature_content") val hasMatureContent: Boolean = false,
    @SerialName("custom_tags") val customTags: List<String>? = null,
    val category: ApiCategory? = null,
)

// --- Official API: GET /public/v1/users ---

@Serializable
data class UsersApiResponse(
    val message: String? = null,
    val data: List<UserData> = emptyList(),
)

@Serializable
data class UserData(
    @SerialName("user_id") val userId: Int,
    val name: String,
    val email: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
)

// --- Chatroom wrapper (used for player — fetched via channel detail) ---
// The official API includes stream.url in channel data, but no chatroom ID.
// Chat uses the unofficial Pusher channel format: chatrooms.{channelId}.v2
// We derive channelId from the broadcaster_user_id or channel slug.

@Serializable
data class Chatroom(
    val id: Int,
    @SerialName("slow_mode") val slowMode: Boolean = false,
    @SerialName("subscribers_mode") val subscribersMode: Boolean = false,
)
