package app.codeg.android.feature.sessiondetail.rendering

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The narrow host-envelope peel Codex's live MCP wire needs. */
class McpResultEnvelopeTest {

    private fun obj(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
    private fun peel(raw: String) = McpResultEnvelope.peel(obj(raw), McpResultEnvelope::isCallToolResult)

    @Test
    fun `peels codex's live result wrapper down to the CallToolResult`() {
        val peeled = peel("""{"result":{"content":[{"type":"text","text":"hi"}]},"error":null}""")
        assertTrue(McpResultEnvelope.isCallToolResult(peeled.obj))
        assertNull(peeled.hostError)
        assertEquals("hi", ToolOutputFormat.mcpResultText("""{"result":{"content":[{"type":"text","text":"hi"}]},"error":null}"""))
    }

    @Test
    fun `peels the serde-tagged Ok variant codex writes into its rollout`() {
        val raw = """{"Ok":{"content":[{"type":"text","text":"from rollout"}]}}"""
        assertTrue(McpResultEnvelope.isCallToolResult(peel(raw).obj))
        assertEquals("from rollout", ToolOutputFormat.mcpResultText(raw))
        // Lowercase `ok` too.
        assertEquals("x", ToolOutputFormat.mcpResultText("""{"ok":{"content":[{"type":"text","text":"x"}]}}"""))
        // A wrapper key holding something that ISN'T a CallToolResult is not descended
        // into — matching the web peel, which requires the destination to be one.
        assertNull(ToolOutputFormat.mcpResultText("""{"result":{"Ok":{"content":[{"type":"text","text":"deep"}]}}}"""))
    }

    @Test
    fun `a failed host envelope surfaces its error string, not the envelope JSON`() {
        val peeled = peel("""{"result":null,"error":"tool crashed"}""")
        assertEquals("tool crashed", peeled.hostError)
        assertEquals("tool crashed", ToolOutputFormat.mcpResultText("""{"result":null,"error":"tool crashed"}"""))
        // A present result means the call didn't fail outright — no host error.
        assertNull(peel("""{"result":{"content":[]},"error":"ignored"}""").hostError)
        // Blank error strings don't count.
        assertNull(peel("""{"result":null,"error":"  "}""").hostError)
    }

    @Test
    fun `a child payload with its own result or error key is left exactly as it was`() {
        // The wrapper key is present but does NOT hold a CallToolResult, so nothing is
        // unwrapped and the caller sees the original object.
        val input = obj("""{"result":{"rows":3},"status":"completed"}""")
        val peeled = McpResultEnvelope.peel(input, McpResultEnvelope::isCallToolResult)
        assertSame(input, peeled.obj)
        assertNull(peeled.hostError)
        // Not a CallToolResult at all ⇒ the renderer falls back to today's behaviour.
        assertNull(ToolOutputFormat.mcpResultText("""{"result":{"rows":3},"status":"completed"}"""))
        assertNull(ToolOutputFormat.mcpResultText("""{"error":"my own field"}"""))
        assertNull(ToolOutputFormat.mcpResultText("plain text"))
    }

    @Test
    fun `an already-readable result is not peeled out from under the caller`() {
        // A CallToolResult that itself owns a `result` key must stay put.
        val input = obj("""{"content":[{"type":"text","text":"top"}],"result":{"content":[{"type":"text","text":"inner"}]}}""")
        val peeled = McpResultEnvelope.peel(input, McpResultEnvelope::isCallToolResult)
        assertSame(input, peeled.obj)
    }

    @Test
    fun `a content-less result falls back to its structured payload`() {
        val text = ToolOutputFormat.mcpResultText("""{"result":{"structuredContent":{"taskId":"t-1"}},"error":null}""")
        assertTrue(text!!.contains("\"taskId\""))
        assertTrue(text.contains("\"t-1\""))
        // Pretty-printed, not the raw one-liner.
        assertTrue(text.contains("\n"))
    }
}
