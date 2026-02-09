package com.kickstream.data.chat

/** Badge info from Pusher: type (e.g. "moderator") + optional text (e.g. month count for subscriber). */
data class ChatBadgeInfo(
    val type: String,
    val text: String? = null,
)

data class ChatMessage(
    val id: String,
    val username: String,
    val content: String,
    val color: String,
    val badges: List<ChatBadgeInfo>,
    val timestamp: String,
)
