package com.autoomstudio.mp3studio.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NonceTest {

    @Test
    fun hashesToLowercaseSha256Hex() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Nonce.sha256Hex("abc"),
        )
    }

    @Test
    fun generatesUrlSafeUniqueValues() {
        val first = Nonce.generate()
        val second = Nonce.generate()
        assertNotEquals(first, second)
        // 32 bytes in unpadded Base64.
        assertEquals(43, first.length)
        assertTrue(first.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }
}
