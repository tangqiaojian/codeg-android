package app.codeg.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Base64UrlTest {

    @Test
    fun `encodes the documented sample token`() {
        // From the codeg server reference: base64url(secret-token) used in the
        // `codeg-token.<...>` WebSocket subprotocol.
        assertEquals("c2VjcmV0LXRva2Vu", Base64Url.encode("secret-token"))
    }

    @Test
    fun `is url-safe and unpadded`() {
        // A token that produces '+' and '/' in standard base64 must be remapped,
        // and any '=' padding stripped.
        val encoded = Base64Url.encode("???>>>ÿþý")
        assertFalse("no plus", encoded.contains('+'))
        assertFalse("no slash", encoded.contains('/'))
        assertFalse("no padding", encoded.contains('='))
    }
}
