package com.kickstream.data.repository

import android.util.Log
import com.kickstream.data.api.KickApi
import com.kickstream.data.local.FavoritesStore
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
 * Repository that provides followed channels from local favorites
 * enriched via the official API batch channel query.
 *
 * Note: The unofficial /api/v2/channels/followed endpoint requires
 * cookie-based session auth (not OAuth), so we use local-only storage.
 * The user follows/unfollows channels in-app via the Player screen's
 * Follow button, and slugs are persisted in DataStore.
 */
class FollowRepository(
    private val kickApi: KickApi,
    private val favoritesStore: FavoritesStore,
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
     * Loads locally followed channels, enriched with live status from the
     * official API (GET /public/v1/channels?slug=a&slug=b) and profile
     * pictures from the Users API (GET /public/v1/users?id=...).
     */
    suspend fun getFollowedChannels(): Result<List<FollowedChannel>> {
        return try {
            val slugs = favoritesStore.favoriteSlugs.first()
            if (slugs.isEmpty()) {
                return Result.success(emptyList())
            }

            // Step 1: Batch channel query for live status, thumbnails, banners
            val response = kickApi.getChannelBySlug(slugs.toList())

            // Step 2: Fetch profile pictures via Users API (uses broadcaster_user_id)
            val userIds = response.data.map { it.broadcasterUserId }
            val profilePicMap = fetchProfilePictures(userIds)

            val channels = response.data.map { ch ->
                val thumbnail = ch.stream?.thumbnail?.takeIf { it.isNotBlank() }
                val profilePic = profilePicMap[ch.broadcasterUserId]?.takeIf { it.isNotBlank() }
                    ?: ch.bannerPicture?.takeIf { it.isNotBlank() }
                Log.d(TAG, "Followed channel '${ch.slug}': thumbnail=$thumbnail, profilePic=$profilePic, banner=${ch.bannerPicture}, stream=${ch.stream}")
                FollowedChannel(
                    slug = ch.slug,
                    isLive = ch.stream?.isLive ?: false,
                    streamTitle = ch.streamTitle,
                    viewerCount = ch.stream?.viewerCount ?: 0,
                    thumbnail = thumbnail,
                    profilePicture = profilePic,
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

    /**
     * Batch-fetch profile pictures from the Users API.
     * Returns a map of userId → profilePictureUrl.
     * Fails silently (returns empty map) so channels still load without images.
     */
    private suspend fun fetchProfilePictures(userIds: List<Int>): Map<Int, String> {
        if (userIds.isEmpty()) return emptyMap()
        return try {
            val usersResponse = kickApi.getUsers(userIds)
            usersResponse.data
                .filter { it.profilePicture != null }
                .associate { it.userId to it.profilePicture!! }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch profile pictures: ${e.message}")
            emptyMap()
        }
    }
}
