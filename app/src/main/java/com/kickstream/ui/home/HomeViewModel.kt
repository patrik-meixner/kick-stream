package com.kickstream.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kickstream.data.api.NetworkModule
import com.kickstream.data.api.model.TypesenseMultiSearchBody
import com.kickstream.data.api.model.TypesenseMultiSearchRequest
import com.kickstream.data.local.FavoritesStore
import com.kickstream.data.local.TokenStore
import com.kickstream.data.repository.AuthRepository
import com.kickstream.data.repository.FollowRepository
import com.kickstream.data.repository.FollowedChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val followedChannels: List<FollowedChannel> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<FollowedChannel>? = null, // null = no active search
    val isLoggingOut: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KickStream"
    }

    private val tokenStore = TokenStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val kickApi = NetworkModule.provideKickApi(tokenStore)
    private val searchApi = NetworkModule.provideSearchApi()
    private val authApi = NetworkModule.provideAuthApi()
    private val authRepository = AuthRepository(authApi, tokenStore)
    private val followRepository = FollowRepository(
        kickApi = kickApi,
        favoritesStore = favoritesStore,
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)

            try {
                val followed = followRepository.getFollowedChannels().getOrDefault(emptyList())
                _uiState.value = HomeUiState(
                    isLoading = false,
                    followedChannels = followed.sortedByDescending { it.isLive },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load followed channels: ${e.message}")
                _uiState.value = HomeUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load channels",
                )
            }
        }
    }

    /**
     * Search channels via Typesense (search.kick.com/multi_search) for fuzzy matching.
     * Falls back to the official API exact-slug query if Typesense is unreachable
     * (e.g. Cloudflare blocks the request).
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()

        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            _uiState.value = _uiState.value.copy(searchResults = null)
            return
        }

        searchJob = viewModelScope.launch {
            delay(400L) // Debounce network calls
            try {
                val results = searchViaTypesense(trimmedQuery)
                _uiState.value = _uiState.value.copy(searchResults = results)
            } catch (e: Exception) {
                Log.w(TAG, "Typesense search failed, trying official API: ${e.message}")
                try {
                    val results = searchViaOfficialApi(trimmedQuery)
                    _uiState.value = _uiState.value.copy(searchResults = results)
                } catch (e2: Exception) {
                    Log.w(TAG, "Official API search also failed: ${e2.message}")
                    _uiState.value = _uiState.value.copy(searchResults = emptyList())
                }
            }
        }
    }

    /** Primary search: Typesense fuzzy matching at search.kick.com, enriched via official API */
    private suspend fun searchViaTypesense(query: String): List<FollowedChannel> {
        Log.d(TAG, "Typesense search for: '$query'")
        val body = TypesenseMultiSearchBody(
            searches = listOf(
                TypesenseMultiSearchRequest(preset = "channel_search", q = query),
            )
        )
        val response = searchApi.multiSearch(body)
        val channelResults = response.results.firstOrNull()
        Log.d(TAG, "Typesense: ${response.results.size} result sets, channels found=${channelResults?.found ?: 0}")

        val slugs = channelResults?.hits
            ?.filter { !it.document.isBanned }
            ?.map { it.document.slug }
            ?: return emptyList()

        if (slugs.isEmpty()) return emptyList()

        // Enrich with official API data (stream info, thumbnails, profile pictures)
        val channelMap = try {
            val channelsResponse = kickApi.getChannelBySlug(slugs)
            channelsResponse.data.associateBy { it.slug }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enrich search results: ${e.message}")
            emptyMap()
        }

        // Fetch profile pictures via Users API
        val profilePicMap = try {
            val userIds = channelMap.values.map { it.broadcasterUserId }
            if (userIds.isNotEmpty()) {
                val usersResponse = kickApi.getUsers(userIds)
                usersResponse.data
                    .filter { it.profilePicture != null }
                    .associate { it.userId to it.profilePicture!! }
            } else emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch profile pics for search: ${e.message}")
            emptyMap()
        }

        // Merge: preserve Typesense ordering, overlay official API data
        return slugs.map { slug ->
            val ch = channelMap[slug]
            FollowedChannel(
                slug = slug,
                isLive = ch?.stream?.isLive ?: false,
                streamTitle = ch?.streamTitle,
                viewerCount = ch?.stream?.viewerCount ?: 0,
                thumbnail = ch?.stream?.thumbnail,
                profilePicture = profilePicMap[ch?.broadcasterUserId]
                    ?: ch?.bannerPicture,
                categoryName = ch?.category?.name,
            )
        }
    }

    /** Fallback search: official API exact slug match, enriched with profile pictures */
    private suspend fun searchViaOfficialApi(query: String): List<FollowedChannel> {
        val response = kickApi.getChannelBySlug(listOf(query))
        if (response.data.isEmpty()) return emptyList()

        // Fetch profile pictures in a separate call (channels API doesn't have them)
        val profilePicMap = try {
            val userIds = response.data.map { it.broadcasterUserId }
            val usersResponse = kickApi.getUsers(userIds)
            usersResponse.data
                .filter { it.profilePicture != null }
                .associate { it.userId to it.profilePicture!! }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch profile pics for search: ${e.message}")
            emptyMap()
        }

        return response.data.map { ch ->
            FollowedChannel(
                slug = ch.slug,
                isLive = ch.stream?.isLive ?: false,
                streamTitle = ch.streamTitle,
                viewerCount = ch.stream?.viewerCount ?: 0,
                thumbnail = ch.stream?.thumbnail,
                profilePicture = profilePicMap[ch.broadcasterUserId]
                    ?: ch.bannerPicture,
                categoryName = ch.category?.name,
            )
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = null,
        )
    }

    fun refresh() = loadData()

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            try {
                authRepository.logout()
            } catch (_: Exception) { }
            onLogoutComplete()
        }
    }
}
