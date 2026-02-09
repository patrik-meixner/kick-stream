package com.kickstream.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PkceUtilTest {

    @Test
    fun `generateCodeVerifier returns base64url string of correct length`() {
        val verifier = PkceUtil.generateCodeVerifier()
        assertTrue("Verifier should be at least 43 chars", verifier.length >= 43)
        assertTrue(
            "Verifier should only contain base64url chars",
            verifier.matches(Regex("[A-Za-z0-9_-]+")),
        )
    }

    @Test
    fun `generateCodeVerifier returns unique values`() {
        val v1 = PkceUtil.generateCodeVerifier()
        val v2 = PkceUtil.generateCodeVerifier()
        assertNotEquals("Two verifiers should be different", v1, v2)
    }

    @Test
    fun `generateCodeChallenge returns valid SHA-256 base64url hash`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge = PkceUtil.generateCodeChallenge(verifier)

        assertTrue("Challenge should be non-empty", challenge.isNotEmpty())
        assertTrue(
            "Challenge should only contain base64url chars",
            challenge.matches(Regex("[A-Za-z0-9_-]+")),
        )
    }

    @Test
    fun `generateCodeChallenge is deterministic for same input`() {
        val verifier = "test-verifier-123"
        val c1 = PkceUtil.generateCodeChallenge(verifier)
        val c2 = PkceUtil.generateCodeChallenge(verifier)
        assertEquals("Same verifier should produce same challenge", c1, c2)
    }

    @Test
    fun `generateState returns unique values`() {
        val s1 = PkceUtil.generateState()
        val s2 = PkceUtil.generateState()
        assertNotEquals("Two states should be different", s1, s2)
    }
}
