package app.codeg.android.core.model

import app.codeg.android.core.model.wire.arrayOrNull
import app.codeg.android.core.model.wire.boolOrNull
import app.codeg.android.core.model.wire.intOrNull
import app.codeg.android.core.model.wire.stringOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One JetBrains AIR typed session-failure record (Web `SessionFailureRecord`).
 * The wire carries UPSERTS ONLY; [resolved] / [dismissed] are client-inferred.
 */
data class SessionFailureRecord(
    val id: String,
    val revision: Int,
    val category: String,
    val severity: String,
    val title: String,
    val details: String? = null,
    val actions: List<String> = emptyList(),
    val resolved: Boolean = false,
    val dismissed: Boolean = false,
) {
    companion object {
        fun fromWire(obj: JsonObject): SessionFailureRecord? {
            val id = obj.stringOrNull("id").orEmpty()
            val revision = obj.intOrNull("revision") ?: 0
            if (id.isEmpty() || revision < 1) return null
            return SessionFailureRecord(
                id = id,
                revision = revision,
                category = obj.stringOrNull("category") ?: "unknown",
                severity = obj.stringOrNull("severity") ?: "error",
                title = obj.stringOrNull("title").orEmpty(),
                details = obj.stringOrNull("details"),
                actions = obj.arrayOrNull("actions")
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty(),
                resolved = obj.boolOrNull("resolved") ?: false,
                dismissed = obj.boolOrNull("dismissed") ?: false,
            )
        }
    }
}

/** Latest agent/runtime error recoverable after reconnect (Web `SessionLastError`). */
data class SessionLastError(
    val message: String,
    val code: String? = null,
    val details: String? = null,
) {
    companion object {
        fun fromWire(obj: JsonObject): SessionLastError? {
            val message = obj.stringOrNull("message") ?: return null
            return SessionLastError(
                message = message,
                code = obj.stringOrNull("code"),
                details = obj.stringOrNull("details"),
            )
        }
    }
}

enum class SessionFailureSettleScope { RETRY_INCIDENTS, WARNINGS, ALL }

enum class SessionFailureAction(val wire: String) {
    RETRY("retry"),
    LOGIN("login"),
    NEW_SESSION("new_session"),
    ;

    companion object {
        val known = entries
        fun fromWire(raw: String): SessionFailureAction? = entries.firstOrNull { it.wire == raw }
    }
}

data class ActiveSessionFailureView(
    val errors: List<SessionFailureRecord>,
    val warning: SessionFailureRecord?,
    val hiddenWarnings: Int,
    val warningIds: List<String>,
)

/**
 * Client half of the AIR session-failure contract, ported from Web
 * `src/lib/session-failures.ts`. Kept dependency-free so unit tests can
 * lock the state machine without a ViewModel.
 */
object SessionFailures {

    fun upsert(
        current: List<SessionFailureRecord>,
        record: SessionFailureRecord,
    ): List<SessionFailureRecord> = merge(current, listOf(record))

    fun merge(
        current: List<SessionFailureRecord>,
        incoming: List<SessionFailureRecord>?,
    ): List<SessionFailureRecord> {
        if (incoming.isNullOrEmpty()) return current
        var next: MutableList<SessionFailureRecord>? = null
        for (record in incoming) {
            if (record.id.isEmpty() || record.revision < 1) continue
            val target = next ?: current
            val index = target.indexOfFirst { it.id == record.id }
            val stored = if (index >= 0) target[index] else null
            if (stored != null && record.revision < stored.revision) continue
            if (stored != null && record.revision == stored.revision) {
                if (!record.resolved || stored.resolved) continue
                val copy = next ?: current.toMutableList().also { next = it }
                val at = copy.indexOfFirst { it.id == record.id }
                copy[at] = copy[at].copy(resolved = true)
                continue
            }
            val copy = next ?: current.toMutableList().also { next = it }
            val accepted = record.copy(resolved = record.resolved, dismissed = false)
            val nextIndex = copy.indexOfFirst { it.id == record.id }
            if (nextIndex >= 0) copy[nextIndex] = accepted else copy.add(accepted)
        }
        return next ?: current
    }

    fun settle(
        failures: List<SessionFailureRecord>,
        scope: SessionFailureSettleScope,
    ): List<SessionFailureRecord> {
        fun settles(f: SessionFailureRecord): Boolean {
            if (f.resolved) return false
            return when (scope) {
                SessionFailureSettleScope.ALL -> true
                SessionFailureSettleScope.WARNINGS -> f.severity == "warning"
                SessionFailureSettleScope.RETRY_INCIDENTS -> isRetryIncident(f)
            }
        }
        if (failures.none(::settles)) return failures
        return failures.map { if (settles(it)) it.copy(resolved = true) else it }
    }

    fun dismiss(
        failures: List<SessionFailureRecord>,
        ids: List<String>,
    ): List<SessionFailureRecord> {
        if (ids.isEmpty()) return failures
        val targets = ids.toSet()
        fun changes(f: SessionFailureRecord) = f.id in targets && !(f.resolved && f.dismissed)
        if (failures.none(::changes)) return failures
        return failures.map { if (changes(it)) it.copy(resolved = true, dismissed = true) else it }
    }

    fun active(failures: List<SessionFailureRecord>): List<SessionFailureRecord> =
        failures.filter { !it.resolved }

    fun hasSettleableRetryIncident(failures: List<SessionFailureRecord>): Boolean =
        failures.any { !it.resolved && isRetryIncident(it) }

    fun activeView(failures: List<SessionFailureRecord>): ActiveSessionFailureView {
        val live = active(failures)
        val warnings = live.filter { it.severity == "warning" }
        return ActiveSessionFailureView(
            errors = live.filter { it.severity != "warning" },
            warning = warnings.lastOrNull(),
            hiddenWarnings = (warnings.size - 1).coerceAtLeast(0),
            warningIds = warnings.map { it.id },
        )
    }

    fun mostRecentRecoveredWarning(failures: List<SessionFailureRecord>): SessionFailureRecord? {
        for (i in failures.indices.reversed()) {
            val f = failures[i]
            if (f.resolved && !f.dismissed && f.severity == "warning") return f
        }
        return null
    }

    fun knownActions(record: SessionFailureRecord): List<SessionFailureAction> {
        val actions = record.actions.toSet()
        return SessionFailureAction.known.filter { it.wire in actions }
    }

    fun lastUserPromptText(turns: List<MessageTurn>): String? {
        for (turn in turns.asReversed()) {
            if (turn.role != TurnRole.USER) continue
            val text = turn.blocks
                .filterIsInstance<ContentBlock.Text>()
                .joinToString("\n") { it.text }
                .trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    fun syntheticRetryWarning(message: String, revision: Int): SessionFailureRecord =
        SessionFailureRecord(
            id = "turn_retrying",
            revision = revision.coerceAtLeast(1),
            category = "service",
            severity = "warning",
            title = message,
        )

    fun lastErrorAsFailure(error: SessionLastError): SessionFailureRecord =
        SessionFailureRecord(
            id = "last_error",
            revision = 1,
            category = "service",
            severity = "error",
            title = error.message,
            details = error.details ?: error.code,
            actions = listOf("retry", "new_session"),
        )

    private fun isRetryIncident(f: SessionFailureRecord): Boolean =
        f.severity == "warning" && f.category != "unknown"
}
