package app.codeg.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AutomationCreateBody(val draft: AutomationDraft)

@Serializable
data class AutomationUpdateBody(val id: Int, val draft: AutomationDraft)

@Serializable
data class AutomationSetEnabledBody(val id: Int, val enabled: Boolean)

@Serializable
data class AutomationRunsBody(val automationId: Int, val limit: Int = 100)

@Serializable
data class AutomationRunNowBody(val automationId: Int)

@Serializable
data class AutomationCancelRunBody(val runId: Int)

@Serializable
data class AutomationComputeNextRunBody(val cron: String, val timezone: String)
