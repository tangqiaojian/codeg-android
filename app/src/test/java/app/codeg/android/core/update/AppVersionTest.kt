package app.codeg.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses dotted versions and optional v prefix`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("1.2"))
    }

    @Test
    fun `compares major minor patch`() {
        assertTrue(AppVersion.parse("1.2.4")!! > AppVersion.parse("1.2.3")!!)
        assertTrue(AppVersion.parse("1.3.0")!! > AppVersion.parse("1.2.9")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.9.9")!!)
        assertEquals(0, AppVersion.parse("1.2.3")!!.compareTo(AppVersion.parse("v1.2.3")!!))
    }

    @Test
    fun `parses a beta suffix by dropping the prerelease label`() {
        assertEquals(AppVersion(0, 4, 0), AppVersion.parse("0.4.0-beta"))
        assertEquals(AppVersion(0, 4, 0), AppVersion.parse("v0.4.0-beta"))
    }

    @Test
    fun `0_4 beta series supersedes the accidental 1_2-1_3 marketing versions`() {
        assertTrue(AppVersion.parse("0.4.0-beta")!!.isNewerThan(AppVersion.parse("1.3.1")!!))
        assertTrue(AppVersion.parse("0.4.1")!!.isNewerThan(AppVersion.parse("0.4.0")!!))
        assertFalse(AppVersion.parse("0.4.0")!!.isNewerThan(AppVersion.parse("0.4.0-beta")!!))
        assertFalse(AppVersion.parse("0.3.9")!!.isNewerThan(AppVersion.parse("1.3.1")!!))
        assertFalse(AppVersion.parse("1.3.1")!!.isNewerThan(AppVersion.parse("0.4.0-beta")!!))
        // Field 1.3.1 clients only have numeric `>` and will ignore 0.4.0-beta.
        assertFalse(AppVersion.parse("0.4.0-beta")!! > AppVersion.parse("1.3.1")!!)
        assertTrue(AppVersion.parse("1.4.0-beta")!! > AppVersion.parse("1.3.1")!!)
        assertTrue(AppVersion.parse("1.4.0-beta")!!.isNewerThan(AppVersion.parse("1.3.1")!!))
        assertTrue(AppVersion.parse("1.4.0-beta")!!.isNewerThan(AppVersion.parse("0.4.0-beta")!!))
    }

    @Test
    fun `rejects junk`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("debug"))
        assertNull(AppVersion.parse("v"))
    }
}
