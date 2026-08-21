package app.codeg.android.feature.sessiondetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VoiceDraftTest {

    @Test
    fun `empty composer becomes the spoken text and is not auto-sent`() {
        assertEquals("hello world", VoiceDraft.merge("", "  hello world "))
        assertFalse(VoiceDraft.shouldAutoSend(""))
        assertFalse(VoiceDraft.shouldAutoSend("already typing"))
    }

    @Test
    fun `partials replace after the prefix instead of stacking`() {
        val prefix = "Fix the"
        assertEquals("Fix the login bug", VoiceDraft.merge(prefix, "login bug"))
        assertEquals("Fix the login flow", VoiceDraft.merge(prefix, "login flow"))
        assertFalse(VoiceDraft.shouldAutoSend(prefix))
    }
}
