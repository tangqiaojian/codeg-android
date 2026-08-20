package app.codeg.android.core.update

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateUrlPickerTest {

    @Test
    fun `picks a reachable mirror instead of a hanging origin`() = runTest {
        val origin = "https://github.com/tangqiaojian/codeg-android/releases/download/v1.2.6/app.apk"
        val urls = UpdateMirrors.candidates(origin)
        val picked = UpdateUrlPicker.pick(urls) { url ->
            if (url.startsWith("https://github.com")) {
                delay(60_000)
                true
            } else {
                url.contains("ghproxy.net")
            }
        }
        assertEquals("https://ghproxy.net/$origin", picked)
    }

    @Test
    fun `fails when every probe returns false`() = runTest {
        try {
            UpdateUrlPicker.pick(listOf("a", "b"), timeoutMs = 200) { false }
            throw AssertionError("expected failure")
        } catch (e: IllegalStateException) {
            assertEquals("no reachable URL", e.message)
        }
    }
}
