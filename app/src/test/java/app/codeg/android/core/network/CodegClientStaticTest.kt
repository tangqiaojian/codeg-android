package app.codeg.android.core.network

import app.codeg.android.core.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bare-value / null / URL helpers on [CodegClient]. */
class CodegClientStaticTest {

    @Test
    fun `decodeConnectionId reads a bare JSON string`() {
        assertEquals("conn-123", CodegClient.decodeConnectionId("\"conn-123\""))
    }

    @Test
    fun `decodeConnectionId rejects null and empty`() {
        assertThrows(ApiError.Decoding::class.java) { CodegClient.decodeConnectionId("null") }
        assertThrows(ApiError.Decoding::class.java) { CodegClient.decodeConnectionId("\"\"") }
    }

    @Test
    fun `isJsonNull recognises null and empty bodies only`() {
        assertTrue(CodegClient.isJsonNull("null"))
        assertTrue(CodegClient.isJsonNull("   "))
        assertTrue(CodegClient.isJsonNull(""))
        assertFalse(CodegClient.isJsonNull("{}"))
        assertFalse(CodegClient.isJsonNull("\"x\""))
    }

    @Test
    fun `normalizeBaseUrl trims trailing slash and whitespace`() {
        assertEquals("http://host:3080", CodegClient.normalizeBaseUrl("  http://host:3080/  "))
        assertEquals("https://example.com", CodegClient.normalizeBaseUrl("https://example.com"))
    }

    @Test
    fun `decodeAgentList decodes the newest agent types and drops still-unknown ones`() {
        // code_buddy / kimi_code / pi / grok are now first-class AgentType values, so
        // they decode (grok also carries its own config payload). A genuinely-unknown
        // future wire must still be dropped, not collapsed onto claude_code.
        val json = """
            [
              {"agent_type":"claude_code","name":"Claude Code"},
              {"agent_type":"code_buddy","name":"CodeBuddy"},
              {"agent_type":"codex","name":"Codex"},
              {"agent_type":"kimi_code","name":"Kimi Code"},
              {"agent_type":"pi","name":"Pi"},
              {"agent_type":"grok","name":"Grok","grok_config_toml":"[ui]\n","grok_settings":{"permission_mode":"ask","default_reasoning_effort":"high"}},
              {"agent_type":"future_agent_9000","name":"Future"}
            ]
        """.trimIndent()
        val agents = CodegClient.decodeAgentList(json)
        assertEquals(
            listOf(
                AgentType.CLAUDE_CODE, AgentType.CODE_BUDDY, AgentType.CODEX,
                AgentType.KIMI_CODE, AgentType.PI, AgentType.GROK,
            ),
            agents.map { it.agentType },
        )
        // The grok row's snake_case config payload maps onto the camelCase fields.
        val grok = agents.first { it.agentType == AgentType.GROK }
        assertEquals("ask", grok.grokSettings?.permissionMode)
        assertEquals("high", grok.grokSettings?.defaultReasoningEffort)
    }

    @Test
    fun `decodeAgentList never yields duplicate agent-type keys`() {
        // Several unknown future types alongside the real claude_code must NOT collapse
        // into duplicate claude_code rows — that duplicate `agentType` key crashes
        // LazyColumn.
        val json = """
            [
              {"agent_type":"future_a","name":"A"},
              {"agent_type":"claude_code","name":"Claude Code"},
              {"agent_type":"future_b","name":"B"}
            ]
        """.trimIndent()
        val agents = CodegClient.decodeAgentList(json)
        assertEquals(listOf(AgentType.CLAUDE_CODE), agents.map { it.agentType })
        val keys = agents.map { it.agentType.wire }
        assertEquals("keys must be unique", keys.distinct().size, keys.size)
    }
}
