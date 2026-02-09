package com.kickstream.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("chatroom_id") val chatroomId: Int,
    val content: String,
    val type: String? = null,
    @SerialName("created_at") val createdAt: String,
    val sender: ChatSender,
)

@Serializable
data class ChatSender(
    val id: Int,
    val username: String,
    val slug: String? = null,
    val identity: ChatIdentity? = null,
)

@Serializable
data class ChatIdentity(
    val color: String? = null,
    val badges: List<ChatBadge>? = null,
)

@Serializable
data class ChatBadge(
    val type: String,
    val text: String? = null,
)
