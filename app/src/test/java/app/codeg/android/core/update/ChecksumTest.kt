package app.codeg.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ChecksumTest {

    @Test
    fun `reads the leading hex digest from a sha256 file`() {
        val body = "2996d8f086b9e55ca4232126a340c3918991f04ac063e36efdebb845293c024a  codeg-android-v1.2.3.apk\n"
        assertEquals(
            "2996d8f086b9e55ca4232126a340c3918991f04ac063e36efdebb845293c024a",
            Checksum.parseSha256File(body),
        )
    }

    @Test
    fun `rejects a truncated digest`() {
        assertNull(Checksum.parseSha256File("abc123"))
        assertNull(Checksum.parseSha256File(""))
    }

    @Test
    fun `verifies a matching digest case-insensitively`() {
        val bytes = "hello".toByteArray()
        val hex = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertTrue(Checksum.matches(bytes, hex.uppercase()))
        assertFalse(Checksum.matches(bytes, "0".repeat(64)))
    }
}
