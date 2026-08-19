package app.codeg.android.feature.sessiondetail.rendering

import app.codeg.android.core.model.AgentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegationCardTest {

    @Test
    fun `mcp delegate names are recognized`() {
        assertTrue(DelegationCard.isDelegationTool("delegate_to_agent"))
        assertTrue(DelegationCard.isDelegationTool("mcp__codeg-delegate__delegate_to_agent"))
        assertTrue(DelegationCard.isDelegationTool("DelegateToAgent"))
        assertFalse(DelegationCard.isDelegationTool("Read"))
        assertFalse(DelegationCard.isDelegationTool("get_delegation_status"))
    }

    @Test
    fun `input supplies agent type and task before the broker ack`() {
        val model = DelegationCard.parse(
            input = """{"agent_type":"grok","task":"Review the login flow"}""",
            output = null,
            meta = null,
            isError = false,
            state = ToolCallState.RUNNING,
        )
        assertTrue(model.hasModel)
        assertEquals(AgentType.GROK, model.agentType)
        assertEquals("Review the login flow", model.task)
        assertEquals(DelegationCardStatus.STARTING, model.status)
        assertNull(model.childConversationId)
    }

    @Test
    fun `wrapped MCP arguments still resolve`() {
        val model = DelegationCard.parse(
            input = """{"name":"delegate_to_agent","arguments":{"agent_type":"codex","task":"Fix tests"}}""",
            output = null,
            meta = null,
            isError = false,
            state = ToolCallState.RUNNING,
        )
        assertEquals(AgentType.CODEX, model.agentType)
        assertEquals("Fix tests", model.task)
    }

    @Test
    fun `meta binds the child conversation and terminal status`() {
        val meta = Json.parseToJsonElement(
            """{"codeg.delegation":{"status":"completed","child_conversation_id":42,"task_preview":"Ship it","task_id":"abc-123"}}""",
        ) as JsonObject
        val model = DelegationCard.parse(
            input = "{}",
            output = null,
            meta = meta,
            isError = false,
            state = ToolCallState.DONE,
        )
        assertEquals(42, model.childConversationId)
        assertEquals("Ship it", model.task)
        assertEquals("abc-123", model.taskId)
        assertEquals(DelegationCardStatus.OK, model.status)
    }

    @Test
    fun `running ack output exposes the child conversation without flipping to ok`() {
        val model = DelegationCard.parse(
            input = """{"agent_type":"grok","task":"Do work"}""",
            output = """{"status":"running","child_conversation_id":9}""",
            meta = null,
            isError = false,
            state = ToolCallState.DONE,
        )
        assertEquals(9, model.childConversationId)
        assertEquals(DelegationCardStatus.RUNNING, model.status)
    }

    @Test
    fun `failed output becomes an error card`() {
        val model = DelegationCard.parse(
            input = """{"agent_type":"grok","task":"Do work"}""",
            output = """{"status":"failed","message":"child crashed","child_conversation_id":9}""",
            meta = null,
            isError = true,
            state = ToolCallState.ERROR,
        )
        assertEquals(DelegationCardStatus.ERR, model.status)
        assertEquals("child crashed", model.errorText)
        assertEquals(9, model.childConversationId)
    }

    @Test
    fun `empty unparseable input without meta is not a card`() {
        val model = DelegationCard.parse(
            input = "{}",
            output = null,
            meta = null,
            isError = false,
            state = ToolCallState.RUNNING,
        )
        assertFalse(model.hasModel)
    }
}
