package com.kickstream.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kickstream.data.api.NetworkModule
import com.kickstream.data.api.model.LivestreamData
import com.kickstream.data.local.FavoritesStore
import com.kickstream.data.local.TokenStore
import com.kickstream.data.repository.ChannelRepository
import com.kickstream.data.repository.FollowRepository
import com.kickstream.data.repository.FollowedChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val livestreams: List<LivestreamData> = emptyList(),
    val followedChannels: List<FollowedChannel> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<LivestreamData>? = null, // null = no active search
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KickStream"
    }

    private val tokenStore = TokenStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val kickApi = NetworkModule.provideKickApi(tokenStore)
    private val unofficialApi = NetworkModule.provideAuthenticatedUnofficialApi(tokenStore)
    private val channelRepository = ChannelRepository(kickApi)
    private val followRepository = FollowRepository(
        unofficialApi = unofficialApi,
        kickApi = kickApi,
        favoritesStore = favoritesStore,
        tokenStore = tokenStore,
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Keep a cached copy of all livestreams for client-side search filtering
    private var allLivestreams: List<LivestreamData> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)

            // Load both followed channels and livestreams in parallel
            val followedDeferred = async {
                try {
                    followRepository.getFollowedChannels().getOrDefault(emptyList())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load followed channels: ${e.message}")
                    emptyList()
                }
            }

            val livestreamsDeferred = async {
                channelRepository.getLivestreams()
            }

            val followed = followedDeferred.await()
            val livestreamsResult = livestreamsDeferred.await()

            livestreamsResult
                .onSuccess { streams ->
                    allLivestreams = streams
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        livestreams = streams,
                        followedChannels = followed.sortedByDescending { it.isLive },
                    )
                }
                .onFailure { error ->
                    allLivestreams = emptyList()
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        followedChannels = followed.sortedByDescending { it.isLive },
                        error = error.message ?: "Failed to load livestreams",
                    )
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        val trimmedQuery = query.trim()
        _uiState.value = _uiState.value.copy(searchQuery = query)

        if (trimmedQuery.isEmpty()) {
            _uiState.value = _uiState.value.copy(searchResults = null)
            return
        }

        // Client-side filter on slug, title, and category name
        val filtered = allLivestreams.filter { stream ->
            stream.slug.contains(trimmedQuery, ignoreCase = true) ||
                stream.streamTitle?.contains(trimmedQuery, ignoreCase = true) == true ||
                stream.category?.name?.contains(trimmedQuery, ignoreCase = true) == true
        }
        _uiState.value = _uiState.value.copy(searchResults = filtered)
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = null,
        )
    }

    fun refresh() = loadData()
}
