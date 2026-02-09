package com.kickstream.data.api

import com.kickstream.data.api.model.SevenTvEmoteSetResponse
import com.kickstream.data.api.model.SevenTvUserResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 7TV REST API for fetching emote sets.
 * Base URL: https://7tv.io/
 * No authentication required.
 */
interface SevenTvApi {

    /** Global emotes available on every channel. */
    @GET("v3/emote-sets/global")
    suspend fun getGlobalEmotes(): SevenTvEmoteSetResponse

    /** Channel-specific emotes for a Kick user (by numeric Kick user ID). */
    @GET("v3/users/KICK/{userId}")
    suspend fun getKickUserEmotes(
        @Path("userId") kickUserId: String,
    ): SevenTvUserResponse
}
