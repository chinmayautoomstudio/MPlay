package com.autoomstudio.mplay.data.account

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * One-time value that ties a Google ID token to this sign-in attempt. Google receives the SHA-256 hex of the raw
 * nonce and embeds it in the token; Supabase receives the raw nonce and checks that it hashes to the same value.
 */
object Nonce {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
