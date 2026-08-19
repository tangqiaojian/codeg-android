package app.codeg.android.core.model

import app.codeg.android.core.model.wire.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A conversation/session persisted on a server (Rust `DbConversationSummary`).
 * Mutable-feeling optimistic fields on iOS (`title`, `status`, `pinnedAt`) are
 * `val`s here; the UI layer holds optimistic copies in its state.
 */
@Serializable
data class ConversationSummary(
    val id: Int,
    val folderId: Int,
    val title: String? = null,
    val agentType: AgentType,
    val status: ConversationStatus,
    val model: String? = null,
    val gitBranch: String? = null,
    val externalId: String? = null,
    val messageCount: Int = 0,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
    /** Server `pinned_at`, or null when not pinned. Pinning does NOT bump
     * `updated_at` — the "Pinned" group sorts by this instead. */
    @Serializable(with = InstantSerializer::class)
    val pinnedAt: Instant? = null,
    /** Direct non-deleted delegation children (`fill_child_counts`). */
    val childCount: Int = 0,
    /** Parent conversation when this row is a delegation child. */
    val parentId: Int? = null,
    val parentToolUseId: String? = null,
    val delegationCallId: String? = null,
) {
    /** Non-empty, trimmed user title, or null for an unnamed session. Render
     * verbatim (user data); fall back to a localized "Untitled session" only
     * when this is null. */
    val trimmedTitle: String?
        get() = title?.takeIf { it.isNotBlank() }

    val isPinned: Boolean get() = pinnedAt != null
}

/** Aggregate token/timing stats for a session (Rust `SessionStats`). */
@Serializable
data class SessionStats(
    val totalUsage: TurnUsage? = null,
    val totalTokens: Long? = null,
    val totalDurationMs: Long = 0,
    val contextWindowUsedTokens: Long? = null,
    val contextWindowMaxTokens: Long? = null,
    val contextWindowUsagePercent: Double? = null,
)

/**
 * Full session detail incl. message history (Rust `DbConversationDetail`).
 * Decode-only. [turns] uses the hand-written `ContentBlock` serializer for its
 * polymorphic blocks.
 *
 * Window fields (`turnsOffset` / `turnsTotal` / `prefixHash`) are present only
 * when the request asked for a tail (`tailTurns`) or a `fromIndex` slice.
 * Their absence means a legacy full response.
 */
@Serializable
data class ConversationDetail(
    val summary: ConversationSummary,
    val turns: List<MessageTurn> = emptyList(),
    val sessionStats: SessionStats? = null,
    val inFlightUserTurnId: String? = null,
    val turnsOffset: Int? = null,
    val turnsTotal: Int? = null,
    val assistantTurnsBeforeOffset: Int? = null,
    val prefixHash: String? = null,
    @Serializable(with = InstantSerializer::class)
    val uncoveredPrefixMaxTs: Instant? = null,
)

/**
 * One page of older history (`get_folder_conversation_turns`):
 * `full[turnsOffset until turnsOffset + turns.size)`, ending just before
 * the `beforeIndex` the client asked for.
 */
@Serializable
data class ConversationTurnsPage(
    val turns: List<MessageTurn> = emptyList(),
    val turnsOffset: Int = 0,
    val turnsTotal: Int = 0,
    val assistantTurnsBeforeOffset: Int = 0,
    val prefixHash: String = "",
    val prefixHashBeforeIndex: String = "",
    @Serializable(with = InstantSerializer::class)
    val uncoveredPrefixMaxTs: Instant? = null,
)

/** Default recent-window size, matching the web client's `tailTurns: 120`. */
const val CONVERSATION_TAIL_TURNS = 120

/** Returned by `acp_find_connection_for_conversation` when a live ACP
 * connection already owns a conversation. */
@Serializable
data class ConversationConnectionInfo(
    val connectionId: String,
    val eventSeq: Long = 0,
)

/** `health` endpoint response — used to validate a server profile. */
@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
)
