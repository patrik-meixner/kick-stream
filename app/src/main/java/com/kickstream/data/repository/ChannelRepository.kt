package com.kickstream.data.repository

import com.kickstream.data.api.KickApi
import com.kickstream.data.api.model.ChannelData
import com.kickstream.data.api.model.LivestreamData

class ChannelRepository(private val api: KickApi) {

    suspend fun getLivestreams(limit: Int = 50): Result<List<LivestreamData>> =
        try {
            val response = api.getLivestreams(sort = "viewer_count", limit = limit)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun getChannel(slug: String): Result<ChannelData> =
        try {
            val response = api.getChannelBySlug(slug = listOf(slug))
            val channel = response.data.firstOrNull()
                ?: throw IllegalStateException("Channel '$slug' not found")
            Result.success(channel)
        } catch (e: Exception) {
            Result.failure(e)
        }
}
