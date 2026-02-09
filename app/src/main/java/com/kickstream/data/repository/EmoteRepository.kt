package com.kickstream.data.repository

import android.util.Log
import com.kickstream.data.api.SevenTvApi
import com.kickstream.data.api.model.SevenTvEmote

/**
 * Lightweight domain model for a resolved emote.
 * Contains only what the UI needs: name for matching, URL for rendering.
 */
data class Emote(
    val name: String,
    val url: String,
    val animated: Boolean,
)

/**
 * Fetches and caches 7TV emote sets (global + per-channel).
 * Gracefully returns empty maps on failure — chat renders as plain text.
 */
class EmoteRepository(private val sevenTvApi: SevenTvApi) {

    companion object {
        private const val TAG = "KickStream"
    }

    // Global emotes loaded once per app session
    private var globalEmotes: Map<String, Emote> = emptyMap()
    private var globalLoaded = false

    // Channel emotes cached by Kick user ID
    private val channelEmotesCache = mutableMapOf<Int, Map<String, Emote>>()

    /**
     * Load 7TV global emotes (available on every channel).
     * Cached after first successful call.
     */
    suspend fun loadGlobalEmotes(): Map<String, Emote> {
        if (globalLoaded) return globalEmotes
        return try {
            val response = sevenTvApi.getGlobalEmotes()
            globalEmotes = response.emotes
                .mapNotNull { it.toDomainEmote() }
                .associateBy { it.name }
            globalLoaded = true
            Log.d(TAG, "Loaded ${globalEmotes.size} 7TV global emotes")
            globalEmotes
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load 7TV global emotes: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Load 7TV channel emotes for a specific Kick user.
     * Cached per user ID — returns empty map if user has no 7TV emotes.
     */
    suspend fun loadChannelEmotes(kickUserId: Int): Map<String, Emote> {
        channelEmotesCache[kickUserId]?.let { return it }
        return try {
            val response = sevenTvApi.getKickUserEmotes(kickUserId.toString())
            val emotes = response.emoteSet?.emotes
                ?.mapNotNull { it.toDomainEmote() }
                ?.associateBy { it.name }
                ?: emptyMap()
            channelEmotesCache[kickUserId] = emotes
            Log.d(TAG, "Loaded ${emotes.size} 7TV channel emotes for user $kickUserId")
            emotes
        } catch (e: Exception) {
            // 404 = user has no 7TV emotes, other errors = API issue — both are fine
            Log.w(TAG, "Failed to load 7TV channel emotes for $kickUserId: ${e.message}")
            channelEmotesCache[kickUserId] = emptyMap()
            emptyMap()
        }
    }

    /**
     * Get the combined emote map (channel emotes override global on name collision).
     * Call after [loadGlobalEmotes] and [loadChannelEmotes].
     */
    fun getEmoteMap(kickUserId: Int): Map<String, Emote> {
        return globalEmotes + (channelEmotesCache[kickUserId] ?: emptyMap())
    }

    /**
     * Map a 7TV API emote to our lightweight domain model.
     * Picks the 2x.webp file for optimal quality/size balance at 28dp TV rendering.
     */
    private fun SevenTvEmote.toDomainEmote(): Emote? {
        val host = data?.host ?: return null
        val baseUrl = host.url ?: return null

        // Prefer 2x.webp, fall back to 1x.webp
        val file = host.files.find { it.name == "2x.webp" }
            ?: host.files.find { it.name == "1x.webp" }
            ?: return null

        return Emote(
            name = name,
            url = "https:$baseUrl/${file.name}",
            animated = data?.animated ?: false,
        )
    }
}
