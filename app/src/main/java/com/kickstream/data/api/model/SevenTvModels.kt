package com.kickstream.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 7TV API response for GET /v3/emote-sets/global
 * Contains the global emote set with all emotes available on every channel.
 */
@Serializable
data class SevenTvEmoteSetResponse(
    val id: String,
    val name: String? = null,
    val emotes: List<SevenTvEmote> = emptyList(),
)

/**
 * 7TV API response for GET /v3/users/KICK/{userId}
 * Contains the user's active emote set for their channel.
 */
@Serializable
data class SevenTvUserResponse(
    val id: String,
    val platform: String? = null,
    val username: String? = null,
    @SerialName("emote_set") val emoteSet: SevenTvEmoteSetResponse? = null,
)

@Serializable
data class SevenTvEmote(
    val id: String,
    val name: String,
    val data: SevenTvEmoteData? = null,
)

@Serializable
data class SevenTvEmoteData(
    val animated: Boolean = false,
    val host: SevenTvEmoteHost? = null,
)

@Serializable
data class SevenTvEmoteHost(
    val url: String? = null,
    val files: List<SevenTvEmoteFile> = emptyList(),
)

@Serializable
data class SevenTvEmoteFile(
    val name: String,
    val width: Int = 0,
    val height: Int = 0,
    val format: String? = null,
)
