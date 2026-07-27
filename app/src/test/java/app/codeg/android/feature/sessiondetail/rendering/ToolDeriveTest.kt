package app.codeg.android.feature.sessiondetail.rendering

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun meta(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject

    @Test
    fun `grok plan-mode tools resolve from meta, not the mutating title`() {
        val enter = meta("""{"x.ai/tool":{"kind":"enter_plan","name":"enter_plan_mode"}}""")
        val exit = meta("""{"x.ai/tool":{"kind":"exit_plan","name":"exit_plan_mode"}}""")
        assertEquals("enter_plan_mode", ToolDerive.grokPlanModeName(enter))
        assertEquals("exit_plan_mode", ToolDerive.grokPlanModeName(exit))
        // Every other Grok tool and every other host keeps its own name resolution.
        assertNull(ToolDerive.grokPlanModeName(meta("""{"x.ai/tool":{"kind":"bash"}}""")))
        assertNull(ToolDerive.grokPlanModeName(meta("""{"contextCompaction":true}""")))
        assertNull(ToolDerive.grokPlanModeName(null))
    }

    @Test
    fun `plan-mode names are an exact set, so update_plan keeps its checklist`() {
        assertTrue(ToolDerive.isPlanModeName("exit_plan_mode"))
        assertTrue(ToolDerive.isPlanModeName("ExitPlanMode"))
        assertTrue(ToolDerive.isPlanModeName("enter_plan_mode"))
        assertTrue(ToolDerive.isPlanModeName("switch_mode"))
        assertTrue(ToolDerive.isPlanModeName("mcp__grok__exit_plan_mode"))
        // Codex's real checklist tool must NOT be treated as a mode transition.
        assertFalse(ToolDerive.isPlanModeName("update_plan"))
        assertFalse(ToolDerive.isPlanModeName("plan"))
        assertFalse(ToolDerive.isPlanModeName("TodoWrite"))
    }

    @Test
    fun `a live plan-mode call gets a stable title regardless of the streamed one`() {
        // Grok's title mutates across the lifecycle; the card must not follow it.
        val vm = ToolCallVM.of(
            id = "t1", rawName = "Plan mode entered", kind = "other", state = ToolCallState.DONE,
            input = null, output = null, content = null, isError = false,
            meta = meta("""{"x.ai/tool":{"kind":"enter_plan"}}"""),
        )
        assertEquals("enter_plan_mode", vm.rawName)
        assertEquals("Entered plan mode", vm.displayTitle)
        assertTrue(vm.isPlanMode)
    }

    @Test
    fun `a plan-mode call never folds into a tool group`() {
        fun tool(id: String, name: String, meta: JsonObject? = null) = RenderPart.Tool(
            ToolCallVM.of(id, name, "", ToolCallState.DONE, null, null, null, false, meta),
        )
        val grouped = MessageRender.groupConsecutiveTools(
            listOf(
                tool("a", "Read"),
                tool("b", "Plan: Enter", meta("""{"x.ai/tool":{"kind":"exit_plan"}}""")),
                tool("c", "Grep"),
            ),
        )
        // Read | plan-mode | Grep — three standalone parts, no ToolGroup.
        assertEquals(3, grouped.size)
        assertTrue(grouped.all { it is RenderPart.Tool })
        assertTrue((grouped[1] as RenderPart.Tool).vm.isPlanMode)
    }
}
