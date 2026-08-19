package app.codeg.android.core.model

import app.codeg.android.core.network.CodegJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTest {
    @Test
    fun decodesAutomationWithSnakeCaseFields() {
        val automation = CodegJson.response.decodeFromString<Automation>(automationJson())

        assertEquals(7, automation.id)
        assertEquals("Nightly audit", automation.name)
        assertTrue(automation.enabled)
        assertEquals("schedule", automation.triggerKind)
        assertEquals("0 9 * * 1-5", automation.cron)
        assertEquals("Asia/Shanghai", automation.timezone)
        assertEquals("2026-08-20T01:00:00Z", automation.nextRunAt)
        assertEquals("grok", automation.agentType)
        assertEquals(3, automation.rootFolderId)
        assertEquals("worktree_per_run", automation.isolation)
        assertEquals("main", automation.branch)
        assertFalse(automation.isRemoteBranch)
        assertTrue(automation.config is JsonObject)
        assertEquals("Audit dependencies", automation.displayPrompt())
        assertEquals("launch_session", automation.action())
        assertEquals("2026-08-18T09:00:00Z", automation.lastRunAt)
        assertEquals("succeeded", automation.lastRunStatus)
        assertEquals(88, automation.lastRunConversationId)
        assertEquals(2, automation.unseenFailures)
    }

    @Test
    fun nullConfigIsAllowedAndHelpersDegrade() {
        val automation = CodegJson.response.decodeFromString<Automation>(
            automationJson().replace(
                """"config": {"action": "launch_session", "display_text": "Audit dependencies", "prompt_blocks": []}""",
                """"config": null""",
            ),
        )

        assertNull(automation.config)
        assertNull(automation.displayPrompt())
        assertEquals("launch_session", automation.action())
    }

    @Test
    fun unknownTriggerAndFutureFieldsDoNotBreakDecoding() {
        val automation = CodegJson.response.decodeFromString<Automation>(
            automationJson()
                .replace("\"schedule\"", "\"webhook\"")
                .replace("}", ",\"future_field\":true}"),
        )

        assertEquals("webhook", automation.triggerKind)
    }

    @Test
    fun decodesAutomationRunWithSnakeCaseFields() {
        val run = CodegJson.response.decodeFromString<AutomationRun>(runJson())

        assertEquals(21, run.id)
        assertEquals(7, run.automationId)
        assertEquals("failed", run.status)
        assertEquals("schedule", run.trigger)
        assertEquals("2026-08-18T01:00:00Z", run.scheduledFor)
        assertEquals("2026-08-18T01:00:02Z", run.startedAt)
        assertEquals("2026-08-18T01:04:00Z", run.endedAt)
        assertEquals(88, run.conversationId)
        assertEquals(9, run.worktreeFolderId)
        assertEquals("agent_error", run.stopReason)
        assertEquals("boom", run.error)
        assertEquals("Audit failed", run.summary)
    }

    @Test
    fun createBodyKeepsNestedAutomationWireNames() {
        val encoded = CodegJson.request.encodeToString(
            AutomationCreateBody(
                draft = sampleDraft(),
            ),
        )

        assertTrue(encoded.contains("\"draft\":{"))
        assertTrue(encoded.contains("\"trigger_kind\":\"schedule\""))
        assertTrue(encoded.contains("\"agent_type\":\"grok\""))
        assertTrue(encoded.contains("\"root_folder_id\":3"))
        assertTrue(encoded.contains("\"is_remote_branch\":false"))
        assertTrue(encoded.contains("\"prompt_blocks\":[{\"type\":\"text\",\"text\":\"Audit dependencies\"}]"))
        assertTrue(encoded.contains("\"display_text\":\"Audit dependencies\""))
        assertTrue(encoded.contains("\"mode_id\":\"code\""))
        assertFalse(encoded.contains("\"triggerKind\""))
        assertFalse(encoded.contains("\"rootFolderId\""))
    }

    @Test
    fun updateAndToggleBodiesUseCamelCaseWrappers() {
        val update = CodegJson.request.encodeToString(AutomationUpdateBody(id = 7, draft = sampleDraft()))
        val enabled = CodegJson.request.encodeToString(AutomationSetEnabledBody(id = 7, enabled = false))
        val runs = CodegJson.request.encodeToString(AutomationRunsBody(automationId = 7, limit = 50))
        val runNow = CodegJson.request.encodeToString(AutomationRunNowBody(automationId = 7))
        val cancel = CodegJson.request.encodeToString(AutomationCancelRunBody(runId = 21))
        val next = CodegJson.request.encodeToString(AutomationComputeNextRunBody(cron = "0 9 * * 1-5", timezone = "UTC"))

        assertTrue(update.contains("\"id\":7"))
        assertTrue(update.contains("\"draft\":{"))
        assertEquals("""{"id":7,"enabled":false}""", enabled)
        assertEquals("""{"automationId":7,"limit":50}""", runs)
        assertEquals("""{"automationId":7}""", runNow)
        assertEquals("""{"runId":21}""", cancel)
        assertEquals("""{"cron":"0 9 * * 1-5","timezone":"UTC"}""", next)
    }

    @Test
    fun readsActionFromConfigOrDefaultsToLaunchSession() {
        val enqueue = CodegJson.response.decodeFromString<Automation>(
            automationJson().replace("\"launch_session\"", "\"enqueue_task\""),
        )
        assertEquals("enqueue_task", enqueue.action())
        assertEquals("Audit dependencies", enqueue.config?.get("display_text")?.jsonPrimitive?.content)
    }

    private fun sampleDraft(): AutomationDraft = AutomationDraft(
        name = "Nightly audit",
        enabled = true,
        triggerKind = "schedule",
        cron = "0 9 * * 1-5",
        timezone = "Asia/Shanghai",
        agentType = "grok",
        rootFolderId = 3,
        isolation = "worktree_per_run",
        branch = null,
        isRemoteBranch = false,
        config = AutomationConfig(
            action = "launch_session",
            promptBlocks = listOf(PromptInputBlock.Text("Audit dependencies")),
            displayText = "Audit dependencies",
            modeId = "code",
        ),
    )

    private fun automationJson(): String =
        """
        {
          "id": 7,
          "name": "Nightly audit",
          "enabled": true,
          "trigger_kind": "schedule",
          "cron": "0 9 * * 1-5",
          "timezone": "Asia/Shanghai",
          "next_run_at": "2026-08-20T01:00:00Z",
          "agent_type": "grok",
          "root_folder_id": 3,
          "isolation": "worktree_per_run",
          "branch": "main",
          "is_remote_branch": false,
          "config": {"action": "launch_session", "display_text": "Audit dependencies", "prompt_blocks": []},
          "last_run_at": "2026-08-18T09:00:00Z",
          "last_run_status": "succeeded",
          "last_run_conversation_id": 88,
          "unseen_failures": 2,
          "created_at": "2026-08-01T00:00:00Z",
          "updated_at": "2026-08-18T09:00:00Z"
        }
        """.trimIndent()

    private fun runJson(): String =
        """
        {
          "id": 21,
          "automation_id": 7,
          "status": "failed",
          "trigger": "schedule",
          "scheduled_for": "2026-08-18T01:00:00Z",
          "started_at": "2026-08-18T01:00:02Z",
          "ended_at": "2026-08-18T01:04:00Z",
          "conversation_id": 88,
          "worktree_folder_id": 9,
          "stop_reason": "agent_error",
          "error": "boom",
          "summary": "Audit failed",
          "created_at": "2026-08-18T01:00:00Z"
        }
        """.trimIndent()
}
