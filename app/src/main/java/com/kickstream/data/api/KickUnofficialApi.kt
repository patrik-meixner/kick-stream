package com.kickstream.data.api

import com.kickstream.data.api.model.UnofficialChannelResponse
import com.kickstream.data.api.model.UnofficialFollowedChannel
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Unofficial Kick.com web API -- used only for data not available
 * in the official API (chatroom ID, followed channels). Isolated here
 * for easy removal if/when official API adds these features.
 */
interface KickUnofficialApi {

    @GET("api/v2/channels/{slug}")
    suspend fun getChannel(@Path("slug") slug: String): UnofficialChannelResponse

    @GET("api/v2/channels/followed")
    suspend fun getFollowedChannels(
        @Header("Authorization") authorization: String,
    ): List<UnofficialFollowedChannel>
}
