package app.codeg.android.feature.sessiondetail

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QueuedPromptStatus {
    @SerialName("pending") PENDING,
    @SerialName("sending") SENDING,
    @SerialName("retry") RETRY,
}

/** One locally queued follow-up prompt, flushed when the live turn ends. */
@Serializable
data class QueuedPrompt(
    val id: String,
    val text: String,
    val clientMessageId: String = id,
    val status: QueuedPromptStatus = QueuedPromptStatus.PENDING,
)

/**
 * Durable FIFO outbox: items stay until the server accepts the prompt.
 * [clientMessageId] is reused across retries so a lost response cannot
 * double-send. Editing the text mints a new id because the payload changed.
 */
object PromptQueue {
    fun enqueue(
        queue: List<QueuedPrompt>,
        text: String,
        id: String = UUID.randomUUID().toString(),
        clientMessageId: String = UUID.randomUUID().toString(),
    ): List<QueuedPrompt> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return queue
        return queue + QueuedPrompt(id, trimmed, clientMessageId, QueuedPromptStatus.PENDING)
    }

    fun requeueFront(
        queue: List<QueuedPrompt>,
        text: String,
        id: String = UUID.randomUUID().toString(),
        clientMessageId: String = UUID.randomUUID().toString(),
    ): List<QueuedPrompt> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return queue
        return listOf(QueuedPrompt(id, trimmed, clientMessageId, QueuedPromptStatus.RETRY)) + queue
    }

    fun dequeue(queue: List<QueuedPrompt>): Pair<QueuedPrompt?, List<QueuedPrompt>> {
        if (queue.isEmpty()) return null to queue
        return queue.first() to queue.drop(1)
    }

    fun peek(queue: List<QueuedPrompt>): QueuedPrompt? = queue.firstOrNull()

    fun remove(queue: List<QueuedPrompt>, id: String): List<QueuedPrompt> = queue.filterNot { it.id == id }

    fun markSending(queue: List<QueuedPrompt>, id: String): List<QueuedPrompt> =
        queue.map { if (it.id == id) it.copy(status = QueuedPromptStatus.SENDING) else it }

    fun markRetry(queue: List<QueuedPrompt>, id: String): List<QueuedPrompt> =
        queue.map { if (it.id == id) it.copy(status = QueuedPromptStatus.RETRY) else it }

    /** A process death mid-send never got an ack — treat it as retryable. */
    fun revive(queue: List<QueuedPrompt>): List<QueuedPrompt> =
        queue.map { if (it.status == QueuedPromptStatus.SENDING) it.copy(status = QueuedPromptStatus.RETRY) else it }

    /** Pull one item out (web edit: return it to the composer). Missing id is a no-op. */
    fun take(queue: List<QueuedPrompt>, id: String): Pair<QueuedPrompt?, List<QueuedPrompt>> {
        val item = queue.firstOrNull { it.id == id } ?: return null to queue
        return item to remove(queue, id)
    }

    fun update(queue: List<QueuedPrompt>, id: String, text: String): List<QueuedPrompt> {
        val current = queue.firstOrNull { it.id == id } ?: return queue
        if (current.status == QueuedPromptStatus.SENDING) return queue
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return remove(queue, id)
        return queue.map {
            if (it.id == id) {
                it.copy(
                    text = trimmed,
                    clientMessageId = UUID.randomUUID().toString(),
                    status = QueuedPromptStatus.PENDING,
                )
            } else {
                it
            }
        }
    }
}
