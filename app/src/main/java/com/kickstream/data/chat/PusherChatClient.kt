package com.kickstream.data.chat

import android.util.Log
import com.kickstream.data.api.model.ChatMessageDto
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.Channel
import com.pusher.client.channel.SubscriptionEventListener
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json

class PusherChatClient {

    companion object {
        private const val TAG = "KickStream"
        private const val PUSHER_APP_KEY = "32cbd69e4b950bf97679"
        private const val PUSHER_CLUSTER = "us2"
        // Kick uses this event name for chat messages
        private const val CHAT_EVENT = "App\\Events\\ChatMessageEvent"
        private const val CHAT_EVENT_ALT = "App\\Events\\ChatMessageSentEvent"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var pusher: Pusher? = null
    private var currentChannel: Channel? = null

    private fun connect() {
        if (pusher != null) return
        val options = PusherOptions().apply {
            setCluster(PUSHER_CLUSTER)
        }
        pusher = Pusher(PUSHER_APP_KEY, options)
        pusher?.connect(object : ConnectionEventListener {
            override fun onConnectionStateChange(change: ConnectionStateChange) {
                Log.d(TAG, "Pusher connection: ${change.previousState} -> ${change.currentState}")
            }

            override fun onError(message: String?, code: String?, e: Exception?) {
                Log.e(TAG, "Pusher error: $message (code=$code)", e)
            }
        }, ConnectionState.ALL)
    }

    fun subscribeToChatroom(chatroomId: Int): Flow<ChatMessage> = callbackFlow {
        connect()

        val channelName = "chatrooms.$chatroomId.v2"
        Log.d(TAG, "Subscribing to Pusher channel: $channelName")
        currentChannel = pusher?.subscribe(channelName)

        val listener = SubscriptionEventListener { event ->
            Log.d(TAG, "Chat event: ${event.eventName}, data length: ${event.data?.length ?: 0}")
            try {
                val dto = json.decodeFromString<ChatMessageDto>(event.data)
                val message = ChatMessage(
                    id = dto.id,
                    username = dto.sender.username,
                    content = dto.content,
                    color = dto.sender.identity?.color ?: "#FFFFFF",
                    badges = dto.sender.identity?.badges?.map { it.type } ?: emptyList(),
                    timestamp = dto.createdAt,
                )
                trySend(message)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse chat message: ${e.message}")
                Log.d(TAG, "Raw chat data: ${event.data?.take(300)}")
            }
        }

        // Bind to both possible event names (Kick has used both historically)
        currentChannel?.bind(CHAT_EVENT, listener)
        currentChannel?.bind(CHAT_EVENT_ALT, listener)
        Log.d(TAG, "Bound to events: $CHAT_EVENT and $CHAT_EVENT_ALT")

        awaitClose {
            currentChannel?.unbind(CHAT_EVENT, listener)
            currentChannel?.unbind(CHAT_EVENT_ALT, listener)
            pusher?.unsubscribe(channelName)
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting Pusher")
        pusher?.disconnect()
        pusher = null
        currentChannel = null
    }
}
