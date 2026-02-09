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
)

@Serializable
data class UnofficialChatroom(
    val id: Int,
)

/**
 * DTO for the unofficial /api/v2/channels/followed endpoint.
 * The response is a list of these objects. All fields are optional
 * except slug, since the format may change.
 */
@Serializable
data class UnofficialFollowedChannel(
    val id: Int? = null,
    val slug: String,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("playback_url") val playbackUrl: String? = null,
    @SerialName("vod_enabled") val vodEnabled: Boolean = false,
    val user: UnofficialUser? = null,
    val livestream: UnofficialLivestream? = null,
)

@Serializable
data class UnofficialUser(
    val username: String? = null,
    @SerialName("profile_pic") val profilePic: String? = null,
)

@Serializable
data class UnofficialLivestream(
    val id: Int? = null,
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    val viewers: Int = 0,
    val thumbnail: UnofficialThumbnail? = null,
    val categories: List<UnofficialCategory>? = null,
)

@Serializable
data class UnofficialThumbnail(
    val url: String? = null,
)

@Serializable
data class UnofficialCategory(
    val id: Int? = null,
    val name: String? = null,
)
