package app.codeg.android.core.update

import org.junit.Assert.assertEquals
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
    fun `rejects junk`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("debug"))
        assertNull(AppVersion.parse("v"))
    }
}
