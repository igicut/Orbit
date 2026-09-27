package com.example.orbit.data.local

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Keystore postoji samo na uredjaju, pa je ovo instrumentovani test, a ne JVM test */
@RunWith(AndroidJUnit4::class)
class TokenCipherTest {

    private val cipher = TokenCipher()
    private val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.c2lnbmF0dXJl"

    @Test
    fun decryptReturnsTheOriginalToken() {
        assertEquals(token, cipher.decrypt(cipher.encrypt(token)))
    }

    @Test
    fun storedTextDoesNotContainTheToken() {
        val stored = cipher.encrypt(token)
        assertFalse(stored.contains(token))
        assertFalse(stored.contains("eyJ"))
    }

    @Test
    fun sameTokenEncryptsDifferentlyEachTime() {
        // Nov IV pri svakom sifrovanju
        assertNotEquals(cipher.encrypt(token), cipher.encrypt(token))
    }

    @Test
    fun changedCiphertextIsRejected() {
        val bytes = Base64.decode(cipher.encrypt(token), Base64.NO_WRAP)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        // GCM proverava oznaku, pa izmenjen sifrat ne daje pogresan token nego null
        assertNull(cipher.decrypt(Base64.encodeToString(bytes, Base64.NO_WRAP)))
    }

    @Test
    fun textThatIsNotBase64IsRejected() {
        assertNull(cipher.decrypt("not base64 at all!"))
    }
}
