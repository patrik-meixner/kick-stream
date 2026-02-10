package com.kickstream.data.repository

import com.kickstream.data.api.KickApi
import com.kickstream.data.api.model.ApiCategory
import com.kickstream.data.api.model.ApiStream
import com.kickstream.data.api.model.ChannelData
import com.kickstream.data.api.model.ChannelsApiResponse
import com.kickstream.data.api.model.LivestreamData
import com.kickstream.data.api.model.LivestreamsApiResponse
import com.kickstream.data.api.model.UsersApiResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRepositoryTest {

    private fun makeFakeApi(
        livestreams: List<LivestreamData> = emptyList(),
        channels: List<ChannelData> = emptyList(),
    ): KickApi = object : KickApi {
        override suspend fun getLivestreams(
            sort: String,
            limit: Int,
            language: String?,
            categoryId: Int?,
        ) = LivestreamsApiResponse(data = livestreams)

        override suspend fun getChannelBySlug(slug: List<String>) =
            ChannelsApiResponse(data = channels.filter { it.slug in slug })

        override suspend fun getUsers(ids: List<Int>) =
            UsersApiResponse()
    }

    @Test
    fun `getLivestreams returns success on successful API call`() = runTest {
        val expected = listOf(
            LivestreamData(
                broadcasterUserId = 1,
                slug = "test-streamer",
                streamTitle = "Test stream",
                viewerCount = 500,
            )
        )
        val repo = ChannelRepository(makeFakeApi(livestreams = expected))

        val result = repo.getLivestreams()
        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `getLivestreams returns empty list when no one is live`() = runTest {
        val repo = ChannelRepository(makeFakeApi())

        val result = repo.getLivestreams()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<LivestreamData>(), result.getOrNull())
    }

    @Test
    fun `getChannel returns success for existing channel`() = runTest {
        val channel = ChannelData(
            broadcasterUserId = 42,
            slug = "cool-streamer",
            streamTitle = "Playing Minecraft",
            stream = ApiStream(isLive = true, viewerCount = 1000),
        )
        val repo = ChannelRepository(makeFakeApi(channels = listOf(channel)))

        val result = repo.getChannel("cool-streamer")
        assertTrue(result.isSuccess)
        assertEquals(channel, result.getOrNull())
    }

    @Test
    fun `getChannel returns failure for non-existent channel`() = runTest {
        val repo = ChannelRepository(makeFakeApi())

        val result = repo.getChannel("non-existent")
        assertTrue(result.isFailure)
    }

    @Test
    fun `getLivestreams returns failure on API error`() = runTest {
        val errorApi = object : KickApi {
            override suspend fun getLivestreams(
                sort: String,
                limit: Int,
                language: String?,
                categoryId: Int?,
            ): LivestreamsApiResponse = throw RuntimeException("Network error")

            override suspend fun getChannelBySlug(slug: List<String>) =
                throw NotImplementedError()

            override suspend fun getUsers(ids: List<Int>) =
                throw NotImplementedError()
        }
        val repo = ChannelRepository(errorApi)

        val result = repo.getLivestreams()
        assertTrue(result.isFailure)
    }
}
