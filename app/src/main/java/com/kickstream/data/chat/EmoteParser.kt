package com.kickstream.data.chat

import com.kickstream.data.repository.Emote

/**
 * Two-pass emote parser for Kick chat messages:
 *
 * 1. **Kick native emotes** — `[emote:ID:NAME]` tokens are extracted via regex
 *    and resolved to CDN images at `https://files.kick.com/emotes/{ID}/fullsize`.
 * 2. **7TV emotes** — remaining plain-text words are matched against the 7TV
 *    emote map by exact word boundary.
 *
 * Adjacent text segments are coalesced into a single [ChatSegment.Text].
 */
object EmoteParser {

    /** Matches Kick's native emote format: [emote:12345:KEKW] */
    private val KICK_EMOTE_REGEX = Regex("""\[emote:(\d+):([^\]]+)]""")

    private const val KICK_EMOTE_CDN = "https://files.kick.com/emotes"

    /**
     * Parse a raw content string into a list of [ChatSegment]s.
     *
     * Pass 1: Split on Kick native `[emote:ID:NAME]` tokens.
     * Pass 2: For each text chunk left over, split by spaces and match
     *         against the 7TV emote map.
     */
    fun parse(content: String, emoteMap: Map<String, Emote>): List<ChatSegment> {
        if (content.isBlank()) return emptyList()

        // --- Pass 1: extract Kick native emotes --------------------------------
        val rawSegments = mutableListOf<ChatSegment>()
        var cursor = 0
        for (match in KICK_EMOTE_REGEX.findAll(content)) {
            // Text before this emote
            if (match.range.first > cursor) {
                rawSegments.add(ChatSegment.Text(content.substring(cursor, match.range.first)))
            }
            val emoteId = match.groupValues[1]
            val emoteName = match.groupValues[2]
            rawSegments.add(
                ChatSegment.EmoteImage(
                    emoteName = emoteName,
                    url = "$KICK_EMOTE_CDN/$emoteId/fullsize",
                    animated = false, // Kick native emotes can be animated but we treat statically
                ),
            )
            cursor = match.range.last + 1
        }
        // Trailing text after last emote (or entire string if no emotes found)
        if (cursor < content.length) {
            rawSegments.add(ChatSegment.Text(content.substring(cursor)))
        }

        // If no 7TV emotes loaded, return pass-1 result (coalesced)
        if (emoteMap.isEmpty()) return coalesce(rawSegments)

        // --- Pass 2: match 7TV emotes inside text segments ----------------------
        val finalSegments = mutableListOf<ChatSegment>()
        for (segment in rawSegments) {
            if (segment is ChatSegment.Text) {
                finalSegments.addAll(matchSevenTv(segment.text, emoteMap))
            } else {
                finalSegments.add(segment)
            }
        }

        return coalesce(finalSegments)
    }

    /**
     * Split text by spaces and match each word against the 7TV emote map.
     */
    private fun matchSevenTv(text: String, emoteMap: Map<String, Emote>): List<ChatSegment> {
        val segments = mutableListOf<ChatSegment>()
        val textBuffer = StringBuilder()

        for (word in text.split(" ")) {
            val emote = emoteMap[word]
            if (emote != null) {
                if (textBuffer.isNotEmpty()) {
                    segments.add(ChatSegment.Text(textBuffer.toString()))
                    textBuffer.clear()
                }
                segments.add(
                    ChatSegment.EmoteImage(
                        emoteName = emote.name,
                        url = emote.url,
                        animated = emote.animated,
                    ),
                )
            } else {
                if (textBuffer.isNotEmpty()) textBuffer.append(" ")
                textBuffer.append(word)
            }
        }
        if (textBuffer.isNotEmpty()) {
            segments.add(ChatSegment.Text(textBuffer.toString()))
        }
        return segments
    }

    /**
     * Merge adjacent [ChatSegment.Text] segments and trim whitespace.
     */
    private fun coalesce(segments: List<ChatSegment>): List<ChatSegment> {
        val result = mutableListOf<ChatSegment>()
        val buf = StringBuilder()
        for (seg in segments) {
            if (seg is ChatSegment.Text) {
                if (buf.isNotEmpty()) buf.append(" ")
                buf.append(seg.text.trim())
            } else {
                if (buf.isNotEmpty()) {
                    val text = buf.toString().trim()
                    if (text.isNotEmpty()) result.add(ChatSegment.Text(text))
                    buf.clear()
                }
                result.add(seg)
            }
        }
        if (buf.isNotEmpty()) {
            val text = buf.toString().trim()
            if (text.isNotEmpty()) result.add(ChatSegment.Text(text))
        }
        return result
    }

    /**
     * Parse a [ChatMessage] into a [ParsedChatMessage] with emote segments.
     * All metadata (username, color, badges, timestamp) is preserved unchanged.
     */
    fun parseMessage(message: ChatMessage, emoteMap: Map<String, Emote>): ParsedChatMessage {
        return ParsedChatMessage(
            id = message.id,
            username = message.username,
            segments = parse(message.content, emoteMap),
            color = message.color,
            badges = message.badges,
            timestamp = message.timestamp,
            rawContent = message.content,
        )
    }
}
