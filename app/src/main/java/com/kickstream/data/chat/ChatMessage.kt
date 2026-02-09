package com.kickstream.data.chat

data class ChatMessage(
    val id: String,
    val username: String,
    val content: String,
    val color: String,
    val badges: List<String>,
    val timestamp: String,
)
