package app.codeg.android.feature.sessiondetail.interactive

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks down the ported permission tool-call parser. */
class ParsedPermissionTest {

    private fun parse(json: String) = ParsedPermission.parse(Json.parseToJsonElement(json))

    @Test
    fun `extracts a bash command from rawInput`() {
        val p = parse("""{"kind":"bash","rawInput":{"command":"rm -rf /tmp/x"}}""")
        assertEquals("rm -rf /tmp/x", p.command)
        assertTrue(p.hasStructuredBody)
        assertFalse(p.isPlan)
    }

    @Test
    fun `extracts a command from a rawInput that is itself a JSON string`() {
        val p = parse("""{"kind":"execute","rawInput":"{\"command\":\"ls -la\"}"}""")
        assertEquals("ls -la", p.command)
    }

    @Test
    fun `synthesizes a diff from old and new strings`() {
        val p = parse(
            """{"kind":"edit","rawInput":{"file_path":"a.kt","old_string":"val x = 1","new_string":"val x = 2"}}""",
        )
        assertEquals(1, p.diffFiles.size)
        assertEquals("a.kt", p.diffFiles[0].path)
        assertTrue(p.diffFiles[0].additions >= 1)
    }

    @Test
    fun `parses plan entries and flags isPlan`() {
        val p = parse(
            """{"kind":"ExitPlanMode","rawInput":{"plan":[{"content":"Step one"},{"step":"Step two"}]}}""",
        )
        assertEquals(2, p.planEntries.size)
        assertEquals("Step one", p.planEntries[0].text)
        assertTrue(p.isPlan)
    }

    @Test
    fun `parses allowed prompts`() {
        val p = parse(
            """{"kind":"tool","rawInput":{"allowedPrompts":[{"prompt":"run tests","tool":"Bash"}]}}""",
        )
        assertEquals(1, p.allowedPrompts.size)
        assertEquals("run tests", p.allowedPrompts[0].prompt)
        assertEquals("Bash", p.allowedPrompts[0].tool)
    }

    @Test
    fun `falls back to a titleized kind when no title present`() {
        val p = parse("""{"kind":"web_fetch","rawInput":{"url":"https://x.com"}}""")
        assertEquals("Web Fetch", p.title)
        assertEquals("https://x.com", p.url)
        assertNull(p.command)
    }
}
