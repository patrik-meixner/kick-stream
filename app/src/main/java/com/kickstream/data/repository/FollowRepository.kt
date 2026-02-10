package com.kickstream.data.repository

import android.util.Log
import com.kickstream.data.api.KickApi
import com.kickstream.data.api.KickUnofficialApi
import com.kickstream.data.local.FavoritesStore
import com.kickstream.data.local.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Domain model for a followed channel — enriched with live status.
 */
data class FollowedChannel(
    val slug: String,
    val isLive: Boolean,
    val streamTitle: String?,
    val viewerCount: Int,
    val thumbnail: String?,
    val profilePicture: String?,
    val categoryName: String?,
)

/**
 * Repository that provides followed channels from either:
 * 1. The unofficial Kick API (if the user's token works with it), or
 * 2. Local favorites enriched via the official API batch channel query.
 */
class FollowRepository(
    private val unofficialApi: KickUnofficialApi,
    private val kickApi: KickApi,
    private val favoritesStore: FavoritesStore,
    private val tokenStore: TokenStore,
) {
    companion object {
        private const val TAG = "KickStream"
    }

    val favoriteSlugs: Flow<Set<String>> = favoritesStore.favoriteSlugs

    suspend fun toggleFollow(slug: String) {
        favoritesStore.toggleFavorite(slug)
    }

    suspend fun isFollowed(slug: String): Boolean =
        favoritesStore.isFavorite(slug)

    /**
     * Tries unofficial API first, falls back to local favorites enriched
     * via official API.
     */
    suspend fun getFollowedChannels(): Result<List<FollowedChannel>> {
        // Strategy 1: Try unofficial /api/v2/channels/followed (paginated)
        try {
            val token = tokenStore.getAccessToken()
            if (token != null) {
                val allChannels = mutableListOf<com.kickstream.data.api.model.UnofficialFollowedChannel>()
                var cursor: Int? = null

                // Fetch all pages (typically 1-2 for most users)
                do {
                    val response = unofficialApi.getFollowedChannels("Bearer $token", cursor)
                    allChannels.addAll(response.channels)
                    cursor = response.nextCursor
                } while (cursor != null)

                Log.d(TAG, "Unofficial followed API returned ${allChannels.size} channels")

                // Sync server-side follows to local favorites
                val slugs = allChannels.map { it.channelSlug }.toSet()
                for (slug in slugs) {
                    if (!favoritesStore.isFavorite(slug)) {
                        favoritesStore.addFavorite(slug)
                    }
                }

                return Result.success(allChannels.map { ch ->
                    FollowedChannel(
                        slug = ch.channelSlug,
                        isLive = ch.isLive,
                        streamTitle = ch.sessionTitle,
                        viewerCount = ch.viewerCount,
                        thumbnail = null, // New API doesn't include thumbnails
                        profilePicture = ch.profilePicture,
                        categoryName = ch.categoryName.takeIf { it?.isNotBlank() == true },
                    )
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unofficial followed API failed, using local fallback: ${e.message}")
        }

        // Strategy 2: Fall back to local favorites enriched via official API
        return getLocalFollowedChannels()
    }

    private suspend fun getLocalFollowedChannels(): Result<List<FollowedChannel>> {
        return try {
            val slugs = favoritesStore.favoriteSlugs.first()
            if (slugs.isEmpty()) {
                return Result.success(emptyList())
            }

            // Official API supports batch slug query: GET /public/v1/channels?slug=a&slug=b
            val response = kickApi.getChannelBySlug(slugs.toList())
            val channels = response.data.map { ch ->
                FollowedChannel(
                    slug = ch.slug,
                    isLive = ch.stream?.isLive ?: false,
                    streamTitle = ch.streamTitle,
                    viewerCount = ch.stream?.viewerCount ?: 0,
                    thumbnail = ch.stream?.thumbnail,
                    profilePicture = ch.bannerPicture,
                    categoryName = ch.category?.name,
                )
            }

            // Include local favorites that weren't found in the API (offline channels with no data)
            val foundSlugs = channels.map { it.slug }.toSet()
            val missingChannels = (slugs - foundSlugs).map { slug ->
                FollowedChannel(
                    slug = slug,
                    isLive = false,
                    streamTitle = null,
                    viewerCount = 0,
                    thumbnail = null,
                    profilePicture = null,
                    categoryName = null,
                )
            }

            Result.success(channels + missingChannels)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich local favorites: ${e.message}", e)
            // Still return the slugs, just without enrichment
            val slugs = favoritesStore.favoriteSlugs.first()
            Result.success(slugs.map { slug ->
                FollowedChannel(
                    slug = slug,
                    isLive = false,
                    streamTitle = null,
                    viewerCount = 0,
                    thumbnail = null,
                    profilePicture = null,
                    categoryName = null,
                )
            })
        }
    }
}
