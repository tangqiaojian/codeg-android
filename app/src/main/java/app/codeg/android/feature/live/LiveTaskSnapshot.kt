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
    val fetchedAt: Instant? = null,
    val stale: Boolean = false,
) {
    val isIdle: Boolean get() = conversationId == null
}

object LiveTaskFreshness {
    const val STALE_AFTER_MS = 45_000L

    fun mark(snapshot: LiveTaskSnapshot, now: Instant = Instant.now()): LiveTaskSnapshot {
        if (snapshot.isIdle || snapshot.fetchedAt == null) return snapshot.copy(stale = snapshot.conversationId != null)
        val age = now.toEpochMilli() - snapshot.fetchedAt.toEpochMilli()
        return snapshot.copy(stale = age >= STALE_AFTER_MS)
    }
}

object LiveTaskSnapshotCodec {
    fun encode(snapshot: LiveTaskSnapshot): String {
        if (snapshot.isIdle) return ""
        return listOf(
            snapshot.conversationId?.toString().orEmpty(),
            snapshot.title.replace('\n', ' '),
            snapshot.agentLabel,
            snapshot.status?.wire.orEmpty(),
            snapshot.updatedAt?.toEpochMilli()?.toString().orEmpty(),
            snapshot.fetchedAt?.toEpochMilli()?.toString().orEmpty(),
        ).joinToString("\u001f")
    }

    fun decode(raw: String?): LiveTaskSnapshot {
        if (raw.isNullOrBlank()) return LiveTaskSnapshot()
        val parts = raw.split('\u001f')
        if (parts.size < 6) return LiveTaskSnapshot()
        val id = parts[0].toIntOrNull() ?: return LiveTaskSnapshot()
        return LiveTaskSnapshot(
            conversationId = id,
            title = parts[1],
            agentLabel = parts[2],
            status = parts[3].takeIf { it.isNotBlank() }?.let { ConversationStatus.fromWire(it) },
            updatedAt = parts[4].toLongOrNull()?.let { Instant.ofEpochMilli(it) },
            fetchedAt = parts[5].toLongOrNull()?.let { Instant.ofEpochMilli(it) },
        )
    }
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
