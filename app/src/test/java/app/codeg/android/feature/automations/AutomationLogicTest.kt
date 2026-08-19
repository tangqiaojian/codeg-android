package app.codeg.android.feature.automations

import app.codeg.android.core.model.Automation
import app.codeg.android.core.model.PromptInputBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationLogicTest {
    @Test
    fun buildDraftUsesScheduleCronAndTextPrompt() {
        val draft = AutomationDrafts.create(
            name = " Nightly audit ",
            prompt = " Audit dependencies ",
            folderId = 3,
            agentType = "grok",
            triggerKind = "schedule",
            cron = " 0 9 * * 1-5 ",
            timezone = "Asia/Shanghai",
            action = "launch_session",
            isolation = "shared_in_root",
            branch = " main ",
        )

        assertEquals("Nightly audit", draft.name)
        assertTrue(draft.enabled)
        assertEquals("schedule", draft.triggerKind)
        assertEquals("0 9 * * 1-5", draft.cron)
        assertEquals("Asia/Shanghai", draft.timezone)
        assertEquals("grok", draft.agentType)
        assertEquals(3, draft.rootFolderId)
        assertEquals("shared_in_root", draft.isolation)
        assertEquals("main", draft.branch)
        assertFalse(draft.isRemoteBranch)
        assertEquals("launch_session", draft.config.action)
        assertEquals("Audit dependencies", draft.config.displayText)
        assertEquals(listOf(PromptInputBlock.Text("Audit dependencies")), draft.config.promptBlocks)
    }

    @Test
    fun buildDraftClearsScheduleFieldsForManualAndEnqueue() {
        val draft = AutomationDrafts.create(
            name = "Park a task",
            prompt = "Queue review",
            folderId = 4,
            agentType = "claude_code",
            triggerKind = "manual",
            cron = "0 9 * * 1-5",
            timezone = "UTC",
            action = "enqueue_task",
            isolation = "shared_in_root",
            branch = "main",
        )

        assertEquals("manual", draft.triggerKind)
        assertNull(draft.cron)
        assertEquals("worktree_per_run", draft.isolation)
        assertNull(draft.branch)
        assertEquals("enqueue_task", draft.config.action)
    }

    @Test
    fun createValidationRequiresNamePromptFolderAndCron() {
        assertEquals(AutomationValidation.NAME_REQUIRED, AutomationDrafts.validate(name = "", prompt = "p", folderId = 1, triggerKind = "manual", cron = ""))
        assertEquals(AutomationValidation.PROMPT_REQUIRED, AutomationDrafts.validate(name = "n", prompt = "  ", folderId = 1, triggerKind = "manual", cron = ""))
        assertEquals(AutomationValidation.FOLDER_REQUIRED, AutomationDrafts.validate(name = "n", prompt = "p", folderId = null, triggerKind = "manual", cron = ""))
        assertEquals(AutomationValidation.CRON_REQUIRED, AutomationDrafts.validate(name = "n", prompt = "p", folderId = 1, triggerKind = "schedule", cron = "  "))
        assertNull(AutomationDrafts.validate(name = "n", prompt = "p", folderId = 1, triggerKind = "schedule", cron = "0 9 * * *"))
    }

    @Test
    fun sortPutsFailuresAndEnabledAutomationsFirst() {
        val failed = automation(id = 1, name = "z", enabled = true, unseenFailures = 2)
        val enabled = automation(id = 2, name = "b", enabled = true, unseenFailures = 0)
        val disabled = automation(id = 3, name = "a", enabled = false, unseenFailures = 0)

        assertEquals(
            listOf(1, 2, 3),
            sortAutomations(listOf(disabled, enabled, failed)).map { it.id },
        )
    }

    @Test
    fun labelsCoverTriggerRunStatusActionAndIsolation() {
        assertEquals(app.codeg.android.R.string.automations_trigger_schedule, automationTriggerLabelRes("schedule"))
        assertEquals(app.codeg.android.R.string.automations_trigger_manual, automationTriggerLabelRes("manual"))
        assertEquals(app.codeg.android.R.string.automations_status_running, automationRunStatusLabelRes("running"))
        assertEquals(app.codeg.android.R.string.automations_status_succeeded, automationRunStatusLabelRes("succeeded"))
        assertEquals(app.codeg.android.R.string.automations_status_failed, automationRunStatusLabelRes("failed"))
        assertEquals(app.codeg.android.R.string.automations_status_cancelled, automationRunStatusLabelRes("cancelled"))
        assertEquals(app.codeg.android.R.string.automations_status_skipped, automationRunStatusLabelRes("skipped"))
        assertEquals(app.codeg.android.R.string.automations_action_launch_session, automationActionLabelRes("launch_session"))
        assertEquals(app.codeg.android.R.string.automations_action_enqueue_task, automationActionLabelRes("enqueue_task"))
        assertEquals(app.codeg.android.R.string.automations_isolation_worktree, automationIsolationLabelRes("worktree_per_run"))
        assertEquals(app.codeg.android.R.string.automations_isolation_shared, automationIsolationLabelRes("shared_in_root"))
    }

    @Test
    fun cronPresetsMatchWebDefaults() {
        val byKey = automationCronPresets().associate { it.key to it.cron }
        assertEquals("0 * * * *", byKey["hourly"])
        assertEquals("0 9 * * *", byKey["daily"])
        assertEquals("0 9 * * 1-5", byKey["weekdays"])
    }

    private fun automation(
        id: Int,
        name: String,
        enabled: Boolean,
        unseenFailures: Int,
    ) = Automation(
        id = id,
        name = name,
        enabled = enabled,
        triggerKind = "manual",
        timezone = "UTC",
        agentType = "grok",
        isolation = "worktree_per_run",
        unseenFailures = unseenFailures,
    )
}
