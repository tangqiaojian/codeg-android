package app.codeg.android.feature.automations

import androidx.annotation.StringRes
import app.codeg.android.R
import app.codeg.android.core.model.Automation
import app.codeg.android.core.model.AutomationConfig
import app.codeg.android.core.model.AutomationDraft
import app.codeg.android.core.model.PromptInputBlock
import java.util.TimeZone

data class AutomationCronPreset(val key: String, val cron: String, @StringRes val labelRes: Int)

enum class AutomationValidation(@StringRes val messageRes: Int) {
    NAME_REQUIRED(R.string.automations_error_name),
    PROMPT_REQUIRED(R.string.automations_error_prompt),
    FOLDER_REQUIRED(R.string.automations_error_folder),
    CRON_REQUIRED(R.string.automations_error_cron),
}

object AutomationDrafts {
    fun create(
        name: String,
        prompt: String,
        folderId: Int?,
        agentType: String,
        triggerKind: String,
        cron: String?,
        timezone: String,
        action: String,
        isolation: String,
        branch: String? = null,
        enabled: Boolean = true,
    ): AutomationDraft {
        val enqueue = action == Automation.ACTION_ENQUEUE_TASK
        val scheduled = triggerKind == Automation.TRIGGER_SCHEDULE
        val shared = !enqueue && isolation == Automation.ISOLATION_SHARED
        val trimmedBranch = branch?.trim()?.takeIf { it.isNotEmpty() }
        return AutomationDraft(
            name = name.trim(),
            enabled = enabled,
            triggerKind = if (scheduled) Automation.TRIGGER_SCHEDULE else Automation.TRIGGER_MANUAL,
            cron = if (scheduled) cron?.trim()?.takeIf { it.isNotEmpty() } else null,
            timezone = timezone,
            agentType = agentType,
            rootFolderId = folderId,
            isolation = if (enqueue) Automation.ISOLATION_WORKTREE else isolation,
            branch = if (shared) trimmedBranch else null,
            isRemoteBranch = false,
            config = AutomationConfig(
                action = action,
                promptBlocks = listOf(PromptInputBlock.Text(prompt.trim())),
                displayText = prompt.trim(),
            ),
        )
    }

    fun validate(
        name: String,
        prompt: String,
        folderId: Int?,
        triggerKind: String,
        cron: String?,
    ): AutomationValidation? {
        if (name.trim().isEmpty()) return AutomationValidation.NAME_REQUIRED
        if (prompt.trim().isEmpty()) return AutomationValidation.PROMPT_REQUIRED
        if (folderId == null) return AutomationValidation.FOLDER_REQUIRED
        if (triggerKind == Automation.TRIGGER_SCHEDULE && cron.orEmpty().trim().isEmpty()) {
            return AutomationValidation.CRON_REQUIRED
        }
        return null
    }

    fun defaultTimezone(): String = TimeZone.getDefault().id.ifBlank { "UTC" }
}

fun sortAutomations(items: List<Automation>): List<Automation> =
    items.sortedWith(
        compareByDescending<Automation> { it.unseenFailures > 0 }
            .thenByDescending { it.enabled }
            .thenBy { it.name.lowercase() },
    )

@StringRes
fun automationTriggerLabelRes(kind: String): Int = when (kind) {
    Automation.TRIGGER_SCHEDULE -> R.string.automations_trigger_schedule
    Automation.TRIGGER_MANUAL -> R.string.automations_trigger_manual
    else -> R.string.automations_trigger_manual
}

@StringRes
fun automationRunStatusLabelRes(status: String?): Int = when (status) {
    "running" -> R.string.automations_status_running
    "succeeded" -> R.string.automations_status_succeeded
    "failed" -> R.string.automations_status_failed
    "cancelled" -> R.string.automations_status_cancelled
    "skipped" -> R.string.automations_status_skipped
    null, "" -> R.string.automations_status_never
    else -> R.string.automations_status_unknown
}

@StringRes
fun automationActionLabelRes(action: String): Int = when (action) {
    Automation.ACTION_LAUNCH_SESSION -> R.string.automations_action_launch_session
    Automation.ACTION_ENQUEUE_TASK -> R.string.automations_action_enqueue_task
    else -> R.string.automations_action_launch_session
}

@StringRes
fun automationIsolationLabelRes(isolation: String): Int = when (isolation) {
    Automation.ISOLATION_WORKTREE -> R.string.automations_isolation_worktree
    Automation.ISOLATION_SHARED -> R.string.automations_isolation_shared
    else -> R.string.automations_isolation_worktree
}

fun automationCronPresets(): List<AutomationCronPreset> = listOf(
    AutomationCronPreset("hourly", "0 * * * *", R.string.automations_cron_hourly),
    AutomationCronPreset("daily", "0 9 * * *", R.string.automations_cron_daily),
    AutomationCronPreset("weekdays", "0 9 * * 1-5", R.string.automations_cron_weekdays),
)

@StringRes
fun automationScheduleCaptionRes(cron: String?): Int? {
    val raw = cron?.trim().orEmpty()
    if (raw.isEmpty()) return R.string.automations_trigger_manual
    return automationCronPresets().firstOrNull { it.cron == raw }?.labelRes
}
