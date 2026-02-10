package com.kickstream.data.api

import com.kickstream.data.api.model.UnofficialChannelResponse
import com.kickstream.data.api.model.UnofficialFollowedResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Unofficial Kick.com web API -- used only for data not available
 * in the official API (chatroom ID, followed channels). Isolated here
 * for easy removal if/when official API adds these features.
 *
 * Note: Channel search uses Typesense (KickSearchApi) at search.kick.com,
 * not the main kick.com domain.
 */
interface KickUnofficialApi {

    @GET("api/v2/channels/{slug}")
    suspend fun getChannel(@Path("slug") slug: String): UnofficialChannelResponse

    @GET("api/v2/channels/followed")
    suspend fun getFollowedChannels(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: Int? = null,
    ): UnofficialFollowedResponse
}
