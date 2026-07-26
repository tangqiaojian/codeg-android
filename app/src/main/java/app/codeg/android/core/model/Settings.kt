package app.codeg.android.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A reusable message template (`quick_messages_*`). */
@Serializable
data class QuickMessage(
    val id: Int,
    val title: String,
    val content: String,
    val sortOrder: Int = 0,
)

/** `{ "enabled": Bool }` — feedback / ask-question toggle settings. */
@Serializable
data class EnabledFlag(val enabled: Boolean)

/** `{ "settings": { "enabled": Bool } }` — request wrapper for the toggle setters. */
@Serializable
data class EnabledSettingsBody(val settings: EnabledFlag)

/** A custom OpenAI-compatible model endpoint (`*_model_provider`). */
@Serializable
data class ModelProviderInfo(
    val id: Int,
    val name: String,
    val apiUrl: String,
    val apiKeyMasked: String = "",
    val agentType: AgentType,
    val model: String? = null,
)

/** Server proxy settings (`get_system_proxy_settings`; decoded snake_case → camelCase). */
@Serializable
data class SystemProxySettings(
    val enabled: Boolean = false,
    val proxyUrl: String? = null,
)

/** A pending app update (`check_app_update`; camelCase wire). */
@Serializable
data class AppUpdateInfo(
    val version: String,
    val body: String = "",
    val date: String? = null,
)

/** `check_app_update` result (camelCase wire — decode with the camelCase codec). */
@Serializable
data class AppUpdateCheckResult(
    val currentVersion: String = "",
    val update: AppUpdateInfo? = null,
    val selfUpdateSupported: Boolean = false,
)

/** A locally-configured MCP server (`mcp_scan_local`). [spec] is a free-form JSON object. */
@Serializable
data class LocalMcpServer(
    val id: String,
    val spec: JsonElement,
    val apps: List<String> = emptyList(),
)

/** One agent skill (`acp_list_agent_skills`). */
@Serializable
data class AgentSkillItem(
    val id: String,
    val name: String = "",
    val scope: String = "global",
    val layout: String = "markdown_file",
    val path: String = "",
    val description: String? = null,
    val readOnly: Boolean = false,
)

/** `acp_list_agent_skills` result. */
@Serializable
data class AgentSkillsListResult(
    val supported: Boolean = true,
    val message: String? = null,
    val skills: List<AgentSkillItem> = emptyList(),
)

/** `acp_read_agent_skill` result. */
@Serializable
data class AgentSkillContent(
    val skill: AgentSkillItem,
    val content: String = "",
)

/** One catalog expert (`experts_list`). */
@Serializable
data class ExpertListItem(val metadata: ExpertMetadata) {
    val id: String get() = metadata.id
}

@Serializable
data class ExpertMetadata(
    val id: String,
    val category: String = "",
    val icon: String? = null,
    val sortOrder: Int = 0,
    val displayName: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
) {
    /** English (or first) display name. */
    fun title(): String = displayName["en"] ?: displayName.values.firstOrNull() ?: id
    fun desc(): String = description["en"] ?: description.values.firstOrNull() ?: ""
}

/** Per-agent link state of a built-in expert (`experts_get_install_status`).
 *  String enum *value* — unaffected by the snake_case key decoder. */
@Serializable
enum class ExpertLinkState {
    @kotlinx.serialization.SerialName("not_linked") NOT_LINKED,
    @kotlinx.serialization.SerialName("linked_to_codeg") LINKED_TO_CODEG,
    @kotlinx.serialization.SerialName("linked_elsewhere") LINKED_ELSEWHERE,
    @kotlinx.serialization.SerialName("blocked_by_real_directory") BLOCKED_BY_REAL_DIRECTORY,
    @kotlinx.serialization.SerialName("broken") BROKEN;

    /** Whether the expert is currently linked to this agent (toggle "on" state). */
    val isLinked: Boolean get() = this == LINKED_TO_CODEG
}

/** `experts_get_install_status` / `experts_link_to_agent` result.
 *  The server emits these fields in camelCase — decode with the camelCase codec. */
@Serializable
data class ExpertInstallStatus(
    val expertId: String = "",
    val agentType: AgentType,
    val state: ExpertLinkState = ExpertLinkState.NOT_LINKED,
    val linkPath: String = "",
    val targetPath: String? = null,
    val expectedTargetPath: String = "",
    val copyMode: Boolean = false,
)

/** `detect_git` / `test_git_path` result. */
@Serializable
data class GitDetectResult(
    val installed: Boolean = false,
    val version: String? = null,
    val path: String? = null,
)

/** `get_git_settings`. */
@Serializable
data class GitSettings(val customPath: String? = null)

/**
 * A linked GitHub account (`get_github_accounts` → `{accounts:[…]}`).
 *
 * [scopes]/[avatarUrl]/[createdAt] carry no meaning to the phone UI, but the
 * credential-retry flow appends a new account and sends the **whole** list back via
 * `update_github_accounts`; keeping these fields makes that round-trip lossless for
 * the accounts the user already had (they'd otherwise be stripped on re-serialize).
 */
@Serializable
data class GitHubAccount(
    val id: String,
    val serverUrl: String = "github.com",
    val username: String = "",
    val isDefault: Boolean = false,
    val scopes: List<String> = emptyList(),
    val avatarUrl: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class GitHubAccountList(val accounts: List<GitHubAccount> = emptyList())

/**
 * Result of `validate_github_token`. Note the flag is `success` (not `valid`), and
 * [message] carries the error text on failure.
 */
@Serializable
data class GitHubTokenValidation(
    val success: Boolean = false,
    val username: String? = null,
    val scopes: List<String> = emptyList(),
    val avatarUrl: String? = null,
    val message: String? = null,
)

/**
 * A registered agent (`acp_list_agents`). Carries both the list-row fields and the
 * per-agent config payloads the detail/config screen edits — the server sends all
 * of these and [CodegJson] ignores unknown keys, so adding them is non-breaking.
 *
 * The config payloads round-trip CLEARTEXT secrets (unlike model providers, the
 * server returns agent keys unmasked); they are held in-memory only.
 */
@Serializable
data class AcpAgentInfo(
    val agentType: AgentType,
    val name: String = "",
    val description: String = "",
    val available: Boolean = true,
    val enabled: Boolean = false,
    val sortOrder: Int = 0,
    val installedVersion: String? = null,
    val env: Map<String, String> = emptyMap(),
    val modelProviderId: Int? = null,
    /** Latest version from the npm/GitHub registry, when known. */
    val registryVersion: String? = null,
    /** `"binary" | "npx" | "uvx" | "system"` — how the agent is distributed. */
    val distributionType: String? = null,
    /** Display-only path of the on-disk config file (e.g. `~/.claude/settings.json`). */
    val configFilePath: String? = null,
    /** Raw `config.json` (or, for Hermes, a read-only projection). */
    val configJson: String? = null,
    /** `~/.config/opencode/auth.json` (OpenCode only). */
    val opencodeAuthJson: String? = null,
    /** `~/.codex/auth.json` (Codex only; OAuth tokens are read-only). */
    val codexAuthJson: String? = null,
    /** `~/.codex/config.toml` (Codex only). */
    val codexConfigToml: String? = null,
    /** `~/.hermes/config.yaml` (Hermes only). */
    val hermesConfigYaml: String? = null,
    /** Raw `~/.grok/config.toml` (Grok only; the Advanced escape-hatch editor source). */
    val grokConfigToml: String? = null,
    /** `grok` parsed scalar settings backing the structured controls — derived
     * server-side from [grokConfigToml] (`null` fields ⇒ the key is absent). */
    val grokSettings: GrokSettings? = null,
)

/**
 * Parsed scalar keys from `~/.grok/config.toml` backing the Grok panel's structured
 * controls (`null` = the key is absent). The server serializes these snake_case
 * (`permission_mode` / `default_reasoning_effort`); [CodegJson]'s response
 * SnakeCase strategy maps them to these camelCase properties.
 */
@Serializable
data class GrokSettings(
    val permissionMode: String? = null,
    val defaultReasoningEffort: String? = null,
)
