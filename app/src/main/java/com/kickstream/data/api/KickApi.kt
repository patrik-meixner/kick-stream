package com.kickstream.data.api

import com.kickstream.data.api.model.ChannelsApiResponse
import com.kickstream.data.api.model.LivestreamsApiResponse
import com.kickstream.data.api.model.UsersApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface KickApi {

    @GET("public/v1/livestreams")
    suspend fun getLivestreams(
        @Query("sort") sort: String = "viewer_count",
        @Query("limit") limit: Int = 50,
        @Query("language") language: String? = null,
        @Query("category_id") categoryId: Int? = null,
    ): LivestreamsApiResponse

    @GET("public/v1/channels")
    suspend fun getChannelBySlug(
        @Query("slug") slug: List<String>,
    ): ChannelsApiResponse

    /** Batch user lookup — returns profile pictures. Max 50 IDs per request. */
    @GET("public/v1/users")
    suspend fun getUsers(
        @Query("id") ids: List<Int>,
    ): UsersApiResponse
}
