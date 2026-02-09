package com.kickstream.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kickstream.data.api.NetworkModule
import com.kickstream.data.chat.EmoteParser
import com.kickstream.data.chat.ParsedChatMessage
import com.kickstream.data.local.FavoritesStore
import com.kickstream.data.local.TokenStore
import com.kickstream.data.repository.ChannelRepository
import com.kickstream.data.repository.ChatRepository
import com.kickstream.data.repository.Emote
import com.kickstream.data.repository.EmoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val channelName: String = "",
    val streamTitle: String? = null,
    val viewerCount: Int = 0,
    val categoryName: String? = null,
    val hlsUrl: String? = null,
    val chatroomId: Int? = null,
    val chatMessages: List<ParsedChatMessage> = emptyList(),
    val isChatVisible: Boolean = true,
    val isFollowed: Boolean = false,
    val showControls: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KickStream"
        private const val CONTROLS_AUTO_HIDE_MS = 3000L
    }

    private val tokenStore = TokenStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val kickApi = NetworkModule.provideKickApi(tokenStore)
    private val unofficialApi = NetworkModule.provideUnofficialApi()
    private val channelRepository = ChannelRepository(kickApi)
    private val chatRepository = ChatRepository()
    private val emoteRepository = EmoteRepository(NetworkModule.provideSevenTvApi())

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val maxChatMessages = 200
    private val chatBuffer = ArrayDeque<ParsedChatMessage>(maxChatMessages)
    private var emoteMap: Map<String, Emote> = emptyMap()
    private var controlsHideJob: Job? = null
    private var currentSlug: String = ""
    private var currentKickUserId: Int = 0

    fun loadChannel(slug: String) {
        currentSlug = slug
        viewModelScope.launch {
            try {
                // Check follow status
                val isFollowed = favoritesStore.isFavorite(slug)

                // Step 1: Get channel info from official API (HLS URL)
                Log.d(TAG, "Loading channel: $slug")
                val channelResult = channelRepository.getChannel(slug)
                val channel = channelResult.getOrNull()
                Log.d(TAG, "Official API stream.url: ${channel?.stream?.url}")

                // Treat empty string as null (official API sometimes returns "")
                var hlsUrl = channel?.stream?.url?.takeIf { it.isNotBlank() }

                // Step 2: Get chatroom ID + fallback HLS from unofficial API
                var chatroomId: Int? = null
                try {
                    val unofficial = unofficialApi.getChannel(slug)
                    chatroomId = unofficial.chatroom?.id
                    currentKickUserId = unofficial.id
                    Log.d(TAG, "Unofficial API chatroom ID: $chatroomId, kick user ID: $currentKickUserId")
                    // Use unofficial playback_url as fallback if official doesn't have it
                    if (hlsUrl.isNullOrBlank() && !unofficial.playbackUrl.isNullOrBlank()) {
                        hlsUrl = unofficial.playbackUrl
                        Log.d(TAG, "Using unofficial playback_url as fallback HLS")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get unofficial data: ${e.message}", e)
                }

                val streamTitle = channel?.streamTitle
                val viewerCount = channel?.stream?.viewerCount ?: 0
                val categoryName = channel?.category?.name

                if (hlsUrl.isNullOrBlank() && channel?.stream?.isLive != true) {
                    Log.w(TAG, "No HLS URL and not live -- showing error")
                    _uiState.value = PlayerUiState(
                        isLoading = false,
                        channelName = slug,
                        isFollowed = isFollowed,
                        error = "Channel is not live",
                    )
                    return@launch
                }

                Log.d(TAG, "Final HLS URL: $hlsUrl, chatroomId: $chatroomId")
                _uiState.value = PlayerUiState(
                    isLoading = false,
                    channelName = slug,
                    streamTitle = streamTitle,
                    viewerCount = viewerCount,
                    categoryName = categoryName,
                    hlsUrl = hlsUrl,
                    chatroomId = chatroomId,
                    isFollowed = isFollowed,
                )

                // Step 3: Load 7TV emotes concurrently (non-blocking for stream playback)
                if (currentKickUserId > 0) {
                    loadEmotes(currentKickUserId)
                }

                if (chatroomId != null) {
                    subscribeToChatroom(chatroomId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load channel: ${e.message}", e)
                _uiState.value = PlayerUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load channel",
                )
            }
        }
    }

    /**
     * Load 7TV global + channel emotes, then re-parse any messages
     * that arrived before the emote map was ready.
     */
    private fun loadEmotes(kickUserId: Int) {
        viewModelScope.launch {
            emoteRepository.loadGlobalEmotes()
            emoteRepository.loadChannelEmotes(kickUserId)
            emoteMap = emoteRepository.getEmoteMap(kickUserId)
            Log.d(TAG, "Emote map ready: ${emoteMap.size} emotes")
            reparseBuffer()
        }
    }

    /**
     * Re-parse all buffered messages with the current emote map.
     * Called once when emotes finish loading (messages may have arrived first).
     */
    private fun reparseBuffer() {
        if (emoteMap.isEmpty()) return
        val reparsed = ArrayDeque<ParsedChatMessage>(maxChatMessages)
        for (msg in chatBuffer) {
            reparsed.addLast(msg.copy(segments = EmoteParser.parse(msg.rawContent, emoteMap)))
        }
        chatBuffer.clear()
        chatBuffer.addAll(reparsed)
        _uiState.value = _uiState.value.copy(chatMessages = chatBuffer.toList())
    }

    private fun subscribeToChatroom(chatroomId: Int) {
        viewModelScope.launch {
            chatRepository.getChatMessages(chatroomId).collect { message ->
                val parsed = EmoteParser.parseMessage(message, emoteMap)
                chatBuffer.addLast(parsed)
                if (chatBuffer.size > maxChatMessages) {
                    chatBuffer.removeFirst()
                }
                _uiState.value = _uiState.value.copy(chatMessages = chatBuffer.toList())
            }
        }
    }

    fun toggleChat() {
        _uiState.value = _uiState.value.copy(isChatVisible = !_uiState.value.isChatVisible)
    }

    fun toggleFollow() {
        viewModelScope.launch {
            favoritesStore.toggleFavorite(currentSlug)
            val isNowFollowed = favoritesStore.isFavorite(currentSlug)
            _uiState.value = _uiState.value.copy(isFollowed = isNowFollowed)
            Log.d(TAG, "Follow toggled for $currentSlug: $isNowFollowed")
        }
    }

    fun showControls() {
        controlsHideJob?.cancel()
        _uiState.value = _uiState.value.copy(showControls = true)
        controlsHideJob = viewModelScope.launch {
            delay(CONTROLS_AUTO_HIDE_MS)
            _uiState.value = _uiState.value.copy(showControls = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatRepository.disconnect()
    }
}
