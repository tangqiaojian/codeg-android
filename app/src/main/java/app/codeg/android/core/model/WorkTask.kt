package app.codeg.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WorkTask(
    val id: Int,
    val folderId: Int,
    val title: String,
    val config: JsonObject? = null,
    val status: String,
    val failureReason: String? = null,
    val lastError: String? = null,
    val runSeq: Int = 0,
    val sortOrder: Int = 0,
    val worktreeFolderId: Int? = null,
    val worktreeMissing: Boolean = false,
    val agentType: String? = null,
    val conversationId: Int? = null,
    val connectionId: String? = null,
    val baseBranch: String? = null,
    val baseSha: String? = null,
    val workBranch: String? = null,
    val cleanupState: String? = null,
    val verdict: String? = null,
    val resultSummary: String? = null,
    val filesChanged: Int? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    val mergeCommit: String? = null,
    val preflight: JsonObject? = null,
    val mergeQueued: JsonObject? = null,
    val archivedAt: String? = null,
    val scheduledAt: String? = null,
    val latestProgress: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val startedAt: String? = null,
    val settledAt: String? = null,
    val finishedAt: String? = null,
)

@Serializable
data class WorkTaskConfig(
    @SerialName("prompt_blocks") val promptBlocks: List<PromptInputBlock> = emptyList(),
    @SerialName("display_text") val displayText: String = "",
    @SerialName("agent_type") val agentType: String? = null,
    @SerialName("mode_id") val modeId: String? = null,
    @SerialName("config_values") val configValues: Map<String, String> = emptyMap(),
)

@Serializable
data class WorkTaskDraft(
    @SerialName("folder_id") val folderId: Int,
    val title: String,
    val config: WorkTaskConfig,
)

@Serializable
data class WorkTaskEvent(
    val id: Int,
    val taskId: Int,
    val kind: String,
    val actor: String,
    val payload: JsonObject? = null,
    val createdAt: String? = null,
)

@Serializable
data class WorkTaskChangedFile(
    val file: String,
    val additions: Int = 0,
    val deletions: Int = 0,
)

@Serializable
data class WorkTaskFolderSettings(
    @SerialName("default_agent_type")
    val defaultAgentType: String? = null,
    @SerialName("mode_id")
    val modeId: String? = null,
    @SerialName("config_values")
    val configValues: Map<String, String> = emptyMap(),
    @SerialName("label_snapshot")
    val labelSnapshot: JsonObject? = null,
    @SerialName("auto_process")
    val autoProcess: Boolean = false,
    @SerialName("max_concurrent")
    val maxConcurrent: Int = 0,
    @SerialName("merge_strategy")
    val mergeStrategy: String = "squash",
    @SerialName("auto_merge")
    val autoMerge: Boolean = false,
    @SerialName("delete_worktree_default")
    val deleteWorktreeDefault: Boolean = false,
    @SerialName("worktree_root")
    val worktreeRoot: String? = null,
    @SerialName("preflight_command_id")
    val preflightCommandId: Int? = null,
    @SerialName("preflight_command")
    val preflightCommand: String? = null,
    @SerialName("init_command")
    val initCommand: String? = null,
    @SerialName("stage_prompts")
    val stagePrompts: Map<String, String>? = null,
)

@Serializable
data class WorkTaskTemplate(
    val id: Int,
    val name: String,
    val title: String,
    val config: WorkTaskConfig? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class WorkTaskTemplateDraft(
    val name: String,
    val title: String,
    val config: WorkTaskConfig,
)
