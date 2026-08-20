package app.codeg.android.feature.live

import app.codeg.android.core.model.ConversationStatus
import app.codeg.android.core.model.ConversationSummary
import java.time.Instant

/** The one session a notch notification / home widget should show. */
data class LiveTaskSnapshot(
    val conversationId: Int? = null,
    val title: String = "",
    val agentLabel: String = "",
    val status: ConversationStatus? = null,
    val updatedAt: Instant? = null,
) {
    val isIdle: Boolean get() = conversationId == null
}

/**
 * Desktop/web “latest dispatched task”: a live turn beats review, which beats
 * the newest conversation. Pure so unit tests lock the ranking.
 */
object LiveTaskPicker {
    fun pick(conversations: List<ConversationSummary>): LiveTaskSnapshot {
        if (conversations.isEmpty()) return LiveTaskSnapshot()
        val chosen = conversations.filter { it.status.isLive }.maxByOrNull { it.updatedAt }
            ?: conversations.filter { it.status == ConversationStatus.PENDING_REVIEW }.maxByOrNull { it.updatedAt }
            ?: conversations.maxByOrNull { it.updatedAt }
            ?: return LiveTaskSnapshot()
        return LiveTaskSnapshot(
            conversationId = chosen.id,
            title = chosen.trimmedTitle ?: "",
            agentLabel = chosen.agentType.shortName,
            status = chosen.status,
            updatedAt = chosen.updatedAt,
        )
    }
}
