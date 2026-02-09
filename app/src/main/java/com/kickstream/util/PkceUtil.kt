package com.kickstream.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PkceUtil {

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun generateState(): String = generateCodeVerifier()

    fun encodeBase64Url(input: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(input.toByteArray(Charsets.UTF_8))

    fun decodeBase64Url(input: String): String =
        String(Base64.getUrlDecoder().decode(input), Charsets.UTF_8)
}
