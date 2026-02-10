package com.kickstream.data.chat

import com.kickstream.data.repository.Emote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoteParserTest {

    private val testEmotes = mapOf(
        "PepePls" to Emote("PepePls", "https://cdn.7tv.app/emote/abc/2x.webp", animated = true),
        "LULW" to Emote("LULW", "https://cdn.7tv.app/emote/def/2x.webp", animated = false),
        "catJAM" to Emote("catJAM", "https://cdn.7tv.app/emote/ghi/2x.webp", animated = true),
    )

    // ── 7TV emote tests ─────────────────────────────────────────────

    @Test
    fun `parse with empty emote map returns single text segment`() {
        val result = EmoteParser.parse("Hello world", emptyMap())
        assertEquals(1, result.size)
        assertTrue(result[0] is ChatSegment.Text)
        assertEquals("Hello world", (result[0] as ChatSegment.Text).text)
    }

    @Test
    fun `parse detects emote in middle of text`() {
        val result = EmoteParser.parse("Hello PepePls world", testEmotes)
        assertEquals(3, result.size)
        assertEquals("Hello", (result[0] as ChatSegment.Text).text)
        assertEquals("PepePls", (result[1] as ChatSegment.EmoteImage).emoteName)
        assertEquals("world", (result[2] as ChatSegment.Text).text)
    }

    @Test
    fun `parse detects emote at start`() {
        val result = EmoteParser.parse("PepePls hello", testEmotes)
        assertEquals(2, result.size)
        assertTrue(result[0] is ChatSegment.EmoteImage)
        assertEquals("PepePls", (result[0] as ChatSegment.EmoteImage).emoteName)
        assertEquals("hello", (result[1] as ChatSegment.Text).text)
    }

    @Test
    fun `parse detects emote at end`() {
        val result = EmoteParser.parse("hello LULW", testEmotes)
        assertEquals(2, result.size)
        assertEquals("hello", (result[0] as ChatSegment.Text).text)
        assertTrue(result[1] is ChatSegment.EmoteImage)
        assertEquals("LULW", (result[1] as ChatSegment.EmoteImage).emoteName)
    }

    @Test
    fun `parse handles multiple consecutive emotes`() {
        val result = EmoteParser.parse("PepePls LULW catJAM", testEmotes)
        assertEquals(3, result.size)
        assertTrue(result.all { it is ChatSegment.EmoteImage })
        assertEquals("PepePls", (result[0] as ChatSegment.EmoteImage).emoteName)
        assertEquals("LULW", (result[1] as ChatSegment.EmoteImage).emoteName)
        assertEquals("catJAM", (result[2] as ChatSegment.EmoteImage).emoteName)
    }

    @Test
    fun `parse does not match partial words`() {
        val result = EmoteParser.parse("PepePlsNot NotPepePls", testEmotes)
        assertEquals(1, result.size)
        assertTrue(result[0] is ChatSegment.Text)
        assertEquals("PepePlsNot NotPepePls", (result[0] as ChatSegment.Text).text)
    }

    @Test
    fun `parse handles message with no emotes`() {
        val result = EmoteParser.parse("just regular text here", testEmotes)
        assertEquals(1, result.size)
        assertEquals("just regular text here", (result[0] as ChatSegment.Text).text)
    }

    @Test
    fun `parse handles single emote only`() {
        val result = EmoteParser.parse("LULW", testEmotes)
        assertEquals(1, result.size)
        assertTrue(result[0] is ChatSegment.EmoteImage)
        assertEquals("LULW", (result[0] as ChatSegment.EmoteImage).emoteName)
    }

    @Test
    fun `parse preserves emote url and animated flag`() {
        val result = EmoteParser.parse("PepePls LULW", testEmotes)
        val pepePls = result[0] as ChatSegment.EmoteImage
        assertEquals("https://cdn.7tv.app/emote/abc/2x.webp", pepePls.url)
        assertTrue(pepePls.animated)

        val lulw = result[1] as ChatSegment.EmoteImage
        assertEquals("https://cdn.7tv.app/emote/def/2x.webp", lulw.url)
        assertEquals(false, lulw.animated)
    }

    @Test
    fun `parse coalesces adjacent text words`() {
        val result = EmoteParser.parse("hello world PepePls how are you", testEmotes)
        assertEquals(3, result.size)
        assertEquals("hello world", (result[0] as ChatSegment.Text).text)
        assertTrue(result[1] is ChatSegment.EmoteImage)
        assertEquals("how are you", (result[2] as ChatSegment.Text).text)
    }

    @Test
    fun `parse handles empty string`() {
        val result = EmoteParser.parse("", testEmotes)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseMessage preserves all message fields`() {
        val message = ChatMessage(
            id = "msg-123",
            username = "testuser",
            content = "hello PepePls",
            color = "#FF0000",
            badges = listOf(ChatBadgeInfo("subscriber"), ChatBadgeInfo("vip")),
            timestamp = "2024-01-01T00:00:00Z",
        )
        val parsed = EmoteParser.parseMessage(message, testEmotes)

        assertEquals("msg-123", parsed.id)
        assertEquals("testuser", parsed.username)
        assertEquals("#FF0000", parsed.color)
        assertEquals(listOf(ChatBadgeInfo("subscriber"), ChatBadgeInfo("vip")), parsed.badges)
        assertEquals("2024-01-01T00:00:00Z", parsed.timestamp)
        assertEquals(2, parsed.segments.size)
        assertEquals("hello", (parsed.segments[0] as ChatSegment.Text).text)
        assertEquals("PepePls", (parsed.segments[1] as ChatSegment.EmoteImage).emoteName)
    }

    @Test
    fun `parseMessage with empty emote map returns text-only segments`() {
        val message = ChatMessage(
            id = "msg-456",
            username = "user",
            content = "PepePls LULW",
            color = "#FFFFFF",
            badges = emptyList(),
            timestamp = "2024-01-01T00:00:00Z",
        )
        val parsed = EmoteParser.parseMessage(message, emptyMap())

        assertEquals(1, parsed.segments.size)
        assertEquals("PepePls LULW", (parsed.segments[0] as ChatSegment.Text).text)
    }

    // ── Kick native emote tests ─────────────────────────────────────

    @Test
    fun `parse detects kick native emote`() {
        val result = EmoteParser.parse("[emote:37226:KEKW]", emptyMap())
        assertEquals(1, result.size)
        assertTrue(result[0] is ChatSegment.EmoteImage)
        val emote = result[0] as ChatSegment.EmoteImage
        assertEquals("KEKW", emote.emoteName)
        assertEquals("https://files.kick.com/emotes/37226/fullsize", emote.url)
    }

    @Test
    fun `parse detects kick native emote mixed with text`() {
        val result = EmoteParser.parse("hello [emote:37226:KEKW] world", emptyMap())
        assertEquals(3, result.size)
        assertEquals("hello", (result[0] as ChatSegment.Text).text)
        assertEquals("KEKW", (result[1] as ChatSegment.EmoteImage).emoteName)
        assertEquals("world", (result[2] as ChatSegment.Text).text)
    }

    @Test
    fun `parse handles multiple kick native emotes`() {
        val result = EmoteParser.parse(
            "[emote:37226:KEKW] nice [emote:5006582:fattypillowsmoke]",
            emptyMap(),
        )
        assertEquals(3, result.size)
        assertEquals("KEKW", (result[0] as ChatSegment.EmoteImage).emoteName)
        assertEquals("nice", (result[1] as ChatSegment.Text).text)
        val second = result[2] as ChatSegment.EmoteImage
        assertEquals("fattypillowsmoke", second.emoteName)
        assertEquals("https://files.kick.com/emotes/5006582/fullsize", second.url)
    }

    @Test
    fun `parse handles kick native emotes and 7TV emotes together`() {
        val result = EmoteParser.parse(
            "hello [emote:37226:KEKW] PepePls world",
            testEmotes,
        )
        assertEquals(4, result.size)
        assertEquals("hello", (result[0] as ChatSegment.Text).text)
        assertEquals("KEKW", (result[1] as ChatSegment.EmoteImage).emoteName)
        assertEquals("https://files.kick.com/emotes/37226/fullsize", (result[1] as ChatSegment.EmoteImage).url)
        assertEquals("PepePls", (result[2] as ChatSegment.EmoteImage).emoteName)
        assertEquals("https://cdn.7tv.app/emote/abc/2x.webp", (result[2] as ChatSegment.EmoteImage).url)
        assertEquals("world", (result[3] as ChatSegment.Text).text)
    }

    @Test
    fun `parse handles kick emote at start of message`() {
        val result = EmoteParser.parse("[emote:12345:PogChamp] let's go", emptyMap())
        assertEquals(2, result.size)
        assertEquals("PogChamp", (result[0] as ChatSegment.EmoteImage).emoteName)
        assertEquals("let's go", (result[1] as ChatSegment.Text).text)
    }

    @Test
    fun `parse handles consecutive kick emotes`() {
        val result = EmoteParser.parse(
            "[emote:111:A][emote:222:B]",
            emptyMap(),
        )
        assertEquals(2, result.size)
        assertEquals("A", (result[0] as ChatSegment.EmoteImage).emoteName)
        assertEquals("B", (result[1] as ChatSegment.EmoteImage).emoteName)
    }

    @Test
    fun `parse with empty emote map still parses kick native emotes`() {
        val result = EmoteParser.parse("nice [emote:37226:KEKW] bro", emptyMap())
        assertEquals(3, result.size)
        assertEquals("nice", (result[0] as ChatSegment.Text).text)
        assertTrue(result[1] is ChatSegment.EmoteImage)
        assertEquals("bro", (result[2] as ChatSegment.Text).text)
    }
}
