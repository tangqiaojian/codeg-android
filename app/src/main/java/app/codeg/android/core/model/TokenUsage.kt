package app.codeg.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsageFilter(
    val start: String? = null,
    val end: String? = null,
    val folderIds: List<Int>? = null,
    val agentTypes: List<String>? = null,
    val models: List<String>? = null,
    val bucket: String = "day",
    val tzOffsetMinutes: Int = 0,
    val comparePrevious: Boolean = false,
)

@Serializable
data class TokenUsageTotals(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val totalTokens: Long = 0,
    val turnCount: Long = 0,
    val conversationCount: Long = 0,
    val durationMs: Long = 0,
    val activeDays: Int = 0,
)

@Serializable
data class TokenUsagePoint(
    val bucketKey: String = "",
    val start: String = "",
    val end: String = "",
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val totalTokens: Long = 0,
    val turnCount: Long = 0,
    val conversationCount: Long = 0,
)

@Serializable
data class TokenUsageBreakdownItem(
    val key: String = "",
    val label: String = "",
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val totalTokens: Long = 0,
    val turnCount: Long = 0,
    val conversationCount: Long = 0,
)

@Serializable
data class TokenUsageHeatCell(
    val weekday: Int = 0,
    val hour: Int = 0,
    val totalTokens: Long = 0,
    val turnCount: Long = 0,
)

@Serializable
data class TokenUsageConversationItem(
    val conversationId: Int,
    val title: String? = null,
    val agentType: String = "",
    val folderLabel: String? = null,
    val totalTokens: Long = 0,
    val turnCount: Long = 0,
    val lastActivityAt: String = "",
)

@Serializable
data class TokenUsageStreak(
    val longestDays: Int = 0,
    val currentDays: Int = 0,
    val currentEndsOn: String? = null,
)

@Serializable
data class TokenUsageReport(
    val rangeStart: String? = null,
    val rangeEnd: String? = null,
    val bucket: String = "day",
    val totals: TokenUsageTotals = TokenUsageTotals(),
    val previousTotals: TokenUsageTotals? = null,
    val series: List<TokenUsagePoint> = emptyList(),
    val byFolder: List<TokenUsageBreakdownItem> = emptyList(),
    val byAgent: List<TokenUsageBreakdownItem> = emptyList(),
    val byModel: List<TokenUsageBreakdownItem> = emptyList(),
    val heatmap: List<TokenUsageHeatCell> = emptyList(),
    val topConversations: List<TokenUsageConversationItem> = emptyList(),
    val streak: TokenUsageStreak = TokenUsageStreak(),
    val firstActivityAt: String? = null,
    val lastActivityAt: String? = null,
    val truncated: Boolean = false,
)

@Serializable
data class TokenUsageFolderFacet(
    val folderId: Int,
    val label: String,
    val name: String = "",
    val alias: String? = null,
    val path: String = "",
    val parentId: Int? = null,
)

@Serializable
data class TokenUsageFacets(
    val folders: List<TokenUsageFolderFacet> = emptyList(),
    val agents: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val dataStart: String? = null,
    val dataEnd: String? = null,
)

@Serializable
data class TokenUsageSyncStatus(
    val totalConversations: Int = 0,
    val syncedConversations: Int = 0,
    val staleConversations: Int = 0,
    val factRows: Int = 0,
    val lastSyncedAt: String? = null,
    val running: Boolean = false,
)

@Serializable
data class TokenUsageSyncResult(
    val scanned: Int = 0,
    val synced: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val lost: Int = 0,
    val turnsWritten: Int = 0,
    val tokensWritten: Long = 0,
    val prunedConversations: Int = 0,
)
