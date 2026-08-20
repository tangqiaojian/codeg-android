package app.codeg.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateMirrorsTest {

    @Test
    fun `tries GitHub first then China-reachable prefixes`() {
        val url = "https://github.com/tangqiaojian/codeg-android/releases/download/v1.2.4/codeg-android-v1.2.4.apk"
        assertEquals(
            listOf(
                url,
                "https://ghproxy.net/$url",
                "https://ghfast.top/$url",
            ),
            UpdateMirrors.candidates(url),
        )
    }

    @Test
    fun `does not double-wrap an already-mirrored URL`() {
        val mirrored = "https://ghproxy.net/https://api.github.com/repos/tangqiaojian/codeg-android/releases/latest"
        assertEquals(listOf(mirrored), UpdateMirrors.candidates(mirrored))
    }
}
