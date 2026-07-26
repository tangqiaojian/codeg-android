package app.codeg.android.feature.sessiondetail.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Locks down the ported tool title / bucket / diff derivation. */
class ToolDeriveTest {

    @Test
    fun `read title shortens the path`() {
        val parsed = ToolDerive.parseJson("""{"file_path":"/a/b/c/foo.kt"}""")
        assertEquals("Read c/foo.kt", ToolDerive.title("Read", parsed))
    }

    @Test
    fun `bash title simplifies the command`() {
        val parsed = ToolDerive.parseJson("""{"command":"pnpm test --watch"}""")
        assertEquals("pnpm test --watch", ToolDerive.title("Bash", parsed))
    }

    @Test
    fun `mcp-style namespaced names canonicalize`() {
        // "server__read_file" → canonical "read_file" → Read bucket + title.
        assertEquals(ToolKindBucket.READ, ToolDerive.bucket("mcp__server__read_file", ""))
    }

    @Test
    fun `bucket classification by name`() {
        assertEquals(ToolKindBucket.EDIT, ToolDerive.bucket("Edit", ""))
        assertEquals(ToolKindBucket.EXECUTE, ToolDerive.bucket("Bash", ""))
        assertEquals(ToolKindBucket.SEARCH, ToolDerive.bucket("Grep", ""))
        assertEquals(ToolKindBucket.TODO, ToolDerive.bucket("TodoWrite", ""))
        assertEquals(ToolKindBucket.TASK, ToolDerive.bucket("Task", ""))
        assertEquals(ToolKindBucket.WEB, ToolDerive.bucket("WebFetch", ""))
        // kind wins over name when present
        assertEquals(ToolKindBucket.READ, ToolDerive.bucket("anything", "read"))
    }

    @Test
    fun `exact-set membership stops MCP over-bucketing`() {
        // Old substring matching mis-classified these (contains "run" / "agent"); the
        // exact canonical-name set leaves unknown MCP tools as OTHER.
        assertEquals(ToolKindBucket.OTHER, ToolDerive.bucket("mcp__db__run_query", ""))
        assertEquals(ToolKindBucket.OTHER, ToolDerive.bucket("get_agent_status", ""))
        assertEquals(ToolKindBucket.OTHER, ToolDerive.bucket("mcp__x__list_widgets", ""))
    }

    @Test
    fun `edit synthesizes a diff from old and new strings`() {
        val parsed = ToolDerive.parseJson("""{"file_path":"x.kt","old_string":"a","new_string":"b"}""")
        val diff = ToolDerive.diff(ToolKindBucket.EDIT, null, null, null, parsed)
        assertNotNull(diff)
        assertEquals("x.kt", diff!!.single().path)
    }
}
