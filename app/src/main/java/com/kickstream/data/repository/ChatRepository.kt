package com.kickstream.data.repository

import com.kickstream.data.chat.ChatMessage
import com.kickstream.data.chat.PusherChatClient
import kotlinx.coroutines.flow.Flow

class ChatRepository {

    private val chatClient = PusherChatClient()

    fun getChatMessages(chatroomId: Int): Flow<ChatMessage> =
        chatClient.subscribeToChatroom(chatroomId)

    fun disconnect() {
        chatClient.disconnect()
    }
}
