package com.kickstream.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kickstream.data.api.NetworkModule
import com.kickstream.data.chat.EmoteParser
import com.kickstream.data.chat.ParsedChatMessage
import com.kickstream.ui.player.components.VideoQuality
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val channelName: String = "",
    val streamTitle: String? = null,
    val viewerCount: Int = 0,
    val categoryName: String? = null,
    val hlsUrl: String? = null,
    val chatroomId: Int? = null,
    /** Maps subscriber badge months → custom badge image URL from the channel */
    val subscriberBadgeUrls: Map<Int, String> = emptyMap(),
    val isChatVisible: Boolean = false,
    val isOffline: Boolean = false,
    val isFollowed: Boolean = false,
    val showControls: Boolean = false,
    val isBuffering: Boolean = false,
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQualityHeight: Int = 0, // 0 = auto
    val showQualityMenu: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KickStream"
        private const val CONTROLS_AUTO_HIDE_MS = 3000L
        private const val CHAT_FLUSH_INTERVAL_MS = 100L // Emit chat updates at most 10x/sec
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

    // Chat messages in a separate StateFlow to avoid recomposing the entire player
    // screen (video overlay, controls, quality menu) on every 100ms chat flush.
    private val _chatMessages = MutableStateFlow<List<ParsedChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ParsedChatMessage>> = _chatMessages.asStateFlow()

    private val maxChatMessages = 200
    private val chatBuffer = ArrayDeque<ParsedChatMessage>(maxChatMessages)
    @Volatile private var emoteMap: Map<String, Emote> = emptyMap()
    private var controlsHideJob: Job? = null
    private var chatJob: Job? = null
    private var chatFlushJob: Job? = null
    @Volatile private var chatDirty = false
    @Volatile private var currentSlug: String = ""
    @Volatile private var currentKickUserId: Int = 0

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

                // Step 2: Get chatroom ID + fallback HLS + subscriber badges from unofficial API
                var chatroomId: Int? = null
                var subBadgeUrls: Map<Int, String> = emptyMap()
                try {
                    val unofficial = unofficialApi.getChannel(slug)
                    chatroomId = unofficial.chatroom?.id
                    currentKickUserId = unofficial.id
                    Log.d(TAG, "Unofficial API chatroom ID: $chatroomId, kick user ID: $currentKickUserId")
                    // Use unofficial playback_url as fallback if official doesn't have it
                    // BUT only if the channel is actually live — unofficial API returns stale
                    // playback URLs for offline channels, which would trick the player into
                    // trying to connect to a dead stream (black screen + infinite reconnects).
                    if (hlsUrl.isNullOrBlank() && !unofficial.playbackUrl.isNullOrBlank()
                        && channel?.stream?.isLive == true
                    ) {
                        hlsUrl = unofficial.playbackUrl
                        Log.d(TAG, "Using unofficial playback_url as fallback HLS")
                    }
                    // Extract subscriber badge image URLs (months → image URL)
                    subBadgeUrls = unofficial.subscriberBadges
                        ?.mapNotNull { badge ->
                            val url = badge.badgeImage?.src?.takeIf { it.isNotBlank() }
                            if (url != null) badge.months to url else null
                        }
                        ?.toMap()
                        ?: emptyMap()
                    if (subBadgeUrls.isNotEmpty()) {
                        Log.d(TAG, "Subscriber badges: ${subBadgeUrls.size} (months: ${subBadgeUrls.keys})")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get unofficial data: ${e.message}", e)
                }

                val streamTitle = channel?.streamTitle
                val viewerCount = channel?.stream?.viewerCount ?: 0
                val categoryName = channel?.category?.name

                val isOffline = hlsUrl.isNullOrBlank() && channel?.stream?.isLive != true
                if (isOffline) {
                    Log.d(TAG, "Channel is offline, showing offline screen")
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
                    subscriberBadgeUrls = subBadgeUrls,
                    isOffline = isOffline,
                    isFollowed = isFollowed,
                )

                // Load 7TV emotes concurrently (non-blocking for stream playback)
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
        chatDirty = true
    }

    private fun subscribeToChatroom(chatroomId: Int) {
        // Cancel any previous chat subscription
        chatJob?.cancel()
        chatFlushJob?.cancel()

        // Periodic flush: emit buffered messages to UI at a fixed rate
        // instead of on every single message (prevents excessive recomposition)
        chatFlushJob = viewModelScope.launch {
            while (true) {
                delay(CHAT_FLUSH_INTERVAL_MS)
                if (chatDirty) {
                    chatDirty = false
                    _chatMessages.value = chatBuffer.toList()
                }
            }
        }

        chatJob = viewModelScope.launch {
            chatRepository.getChatMessages(chatroomId)
                .catch { e -> Log.e(TAG, "Chat stream error: ${e.message}", e) }
                .collect { message ->
                val parsed = EmoteParser.parseMessage(message, emoteMap)
                chatBuffer.addLast(parsed)
                if (chatBuffer.size > maxChatMessages) {
                    chatBuffer.removeFirst()
                }
                chatDirty = true
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

    fun onBufferingChanged(isBuffering: Boolean) {
        _uiState.value = _uiState.value.copy(isBuffering = isBuffering)
    }

    fun onQualitiesAvailable(qualities: List<VideoQuality>) {
        // Update available qualities, preserving current selection
        val current = _uiState.value.selectedQualityHeight
        val mapped = qualities.map { it.copy(isSelected = it.height == current) }
        if (_uiState.value.availableQualities == mapped) return
        _uiState.value = _uiState.value.copy(availableQualities = mapped)
    }

    fun selectQuality(height: Int) {
        _uiState.value = _uiState.value.copy(
            selectedQualityHeight = height,
            showQualityMenu = false,
            availableQualities = _uiState.value.availableQualities.map {
                it.copy(isSelected = it.height == height)
            },
        )
        Log.d(TAG, "Quality selected: ${if (height == 0) "Auto" else "${height}p"}")
    }

    fun toggleQualityMenu() {
        _uiState.value = _uiState.value.copy(showQualityMenu = !_uiState.value.showQualityMenu)
    }

    fun showControls() {
        controlsHideJob?.cancel()
        _uiState.value = _uiState.value.copy(showControls = true)
        controlsHideJob = viewModelScope.launch {
            delay(CONTROLS_AUTO_HIDE_MS)
            _uiState.value = _uiState.value.copy(showControls = false, showQualityMenu = false)
        }
    }

    fun hideControls() {
        controlsHideJob?.cancel()
        _uiState.value = _uiState.value.copy(showControls = false, showQualityMenu = false)
    }

    /** Pause expensive resources when app goes to background */
    fun onPause() {
        chatJob?.cancel()
        chatFlushJob?.cancel()
        chatRepository.disconnect()
        Log.d(TAG, "PlayerViewModel paused: chat disconnected")
    }

    /** Resume resources when app returns to foreground */
    fun onResume() {
        val slug = currentSlug.takeIf { it.isNotBlank() } ?: return

        // Clear stale chat messages accumulated while in background
        chatBuffer.clear()
        chatDirty = false
        _chatMessages.value = emptyList()

        // Reconnect chat if we have a chatroom
        val chatroomId = _uiState.value.chatroomId
        if (chatroomId != null) {
            subscribeToChatroom(chatroomId)
            Log.d(TAG, "PlayerViewModel resumed: chat reconnected to room $chatroomId")
        }

        // Re-check channel status — the stream may have gone offline while the TV was off.
        // This is a lightweight partial update: only stream-related fields change,
        // UI preferences (chat visibility, quality, follow state) are preserved.
        viewModelScope.launch {
            try {
                val channelResult = channelRepository.getChannel(slug)
                val channel = channelResult.getOrNull()
                var hlsUrl = channel?.stream?.url?.takeIf { it.isNotBlank() }

                // Fallback to unofficial playback_url (only if actually live)
                if (hlsUrl.isNullOrBlank()) {
                    try {
                        val unofficial = unofficialApi.getChannel(slug)
                        if (!unofficial.playbackUrl.isNullOrBlank() && channel?.stream?.isLive == true) {
                            hlsUrl = unofficial.playbackUrl
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Resume: failed to get unofficial data: ${e.message}")
                    }
                }

                val isOffline = hlsUrl.isNullOrBlank() && channel?.stream?.isLive != true
                val prev = _uiState.value

                // Only update if status actually changed to avoid unnecessary recomposition
                if (isOffline != prev.isOffline || hlsUrl != prev.hlsUrl) {
                    Log.d(TAG, "Resume: stream status changed — offline=$isOffline, hlsUrl=$hlsUrl")
                    _uiState.value = prev.copy(
                        hlsUrl = hlsUrl,
                        isOffline = isOffline,
                        streamTitle = channel?.streamTitle ?: prev.streamTitle,
                        viewerCount = channel?.stream?.viewerCount ?: 0,
                        categoryName = channel?.category?.name ?: prev.categoryName,
                    )
                } else {
                    Log.d(TAG, "Resume: stream status unchanged (offline=$isOffline)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Resume: failed to re-check channel status: ${e.message}")
                // Don't update UI on failure — keep showing whatever was there before
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatJob?.cancel()
        chatFlushJob?.cancel()
        chatRepository.disconnect()
    }
}
