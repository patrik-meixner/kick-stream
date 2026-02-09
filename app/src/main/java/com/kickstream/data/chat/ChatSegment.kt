package com.kickstream.data.chat

/**
 * A segment of a parsed chat message — either plain text or an emote image.
 * Used by the UI to render mixed text + inline emotes in a FlowRow.
 */
sealed interface ChatSegment {
    data class Text(val text: String) : ChatSegment
    data class EmoteImage(
        val emoteName: String,
        val url: String,
        val animated: Boolean,
    ) : ChatSegment
}

/**
 * A chat message with its content parsed into segments.
 * Created from [ChatMessage] + an emote map by [EmoteParser].
 */
data class ParsedChatMessage(
    val id: String,
    val username: String,
    val segments: List<ChatSegment>,
    val color: String,
    val badges: List<ChatBadgeInfo>,
    val timestamp: String,
    /** Original raw content string, kept for re-parsing when emotes load late. */
    val rawContent: String = "",
)
