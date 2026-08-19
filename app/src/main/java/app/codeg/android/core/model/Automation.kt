package app.codeg.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Automation(
    val id: Int,
    val name: String,
    val enabled: Boolean = true,
    val triggerKind: String,
    val cron: String? = null,
    val timezone: String = "UTC",
    val nextRunAt: String? = null,
    val agentType: String,
    val rootFolderId: Int? = null,
    val isolation: String = ISOLATION_WORKTREE,
    val branch: String? = null,
    val isRemoteBranch: Boolean = false,
    val config: JsonObject? = null,
    val lastRunAt: String? = null,
    val lastRunStatus: String? = null,
    val lastRunConversationId: Int? = null,
    val unseenFailures: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    fun displayPrompt(): String? {
        val raw = config?.get("display_text") as? JsonPrimitive ?: return null
        return raw.content.takeIf { it.isNotBlank() }
    }

    fun action(): String {
        val raw = (config?.get("action") as? JsonPrimitive)?.content
        return raw?.takeIf { it.isNotBlank() } ?: ACTION_LAUNCH_SESSION
    }

    companion object {
        const val TRIGGER_SCHEDULE = "schedule"
        const val TRIGGER_MANUAL = "manual"
        const val ISOLATION_WORKTREE = "worktree_per_run"
        const val ISOLATION_SHARED = "shared_in_root"
        const val ACTION_LAUNCH_SESSION = "launch_session"
        const val ACTION_ENQUEUE_TASK = "enqueue_task"
    }
}

@Serializable
data class AutomationRun(
    val id: Int,
    val automationId: Int,
    val status: String,
    val trigger: String,
    val scheduledFor: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val conversationId: Int? = null,
    val worktreeFolderId: Int? = null,
    val stopReason: String? = null,
    val error: String? = null,
    val summary: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class AutomationConfig(
    val action: String? = null,
    @SerialName("prompt_blocks") val promptBlocks: List<PromptInputBlock> = emptyList(),
    @SerialName("display_text") val displayText: String = "",
    @SerialName("mode_id") val modeId: String? = null,
    @SerialName("config_values") val configValues: Map<String, String> = emptyMap(),
)

@Serializable
data class AutomationDraft(
    val name: String,
    val enabled: Boolean = true,
    @SerialName("trigger_kind") val triggerKind: String,
    val cron: String? = null,
    val timezone: String,
    @SerialName("agent_type") val agentType: String,
    @SerialName("root_folder_id") val rootFolderId: Int? = null,
    val isolation: String,
    val branch: String? = null,
    @SerialName("is_remote_branch") val isRemoteBranch: Boolean = false,
    val config: AutomationConfig,
)
