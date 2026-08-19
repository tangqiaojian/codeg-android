package app.codeg.android.feature.sessiondetail

import java.util.UUID

/** One locally queued follow-up prompt, flushed when the live turn ends. */
data class QueuedPrompt(
    val id: String,
    val text: String,
)

/**
 * Pure FIFO queue used when the user sends while a turn is already in flight.
 * Matches Web `use-message-queue`: append on enqueue, bounce to the front on
 * `TurnInProgress`, never invents success.
 */
object PromptQueue {
    fun enqueue(queue: List<QueuedPrompt>, text: String, id: String = UUID.randomUUID().toString()): List<QueuedPrompt> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return queue
        return queue + QueuedPrompt(id, trimmed)
    }

    fun requeueFront(queue: List<QueuedPrompt>, text: String, id: String = UUID.randomUUID().toString()): List<QueuedPrompt> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return queue
        return listOf(QueuedPrompt(id, trimmed)) + queue
    }

    fun dequeue(queue: List<QueuedPrompt>): Pair<QueuedPrompt?, List<QueuedPrompt>> {
        if (queue.isEmpty()) return null to queue
        return queue.first() to queue.drop(1)
    }

    fun remove(queue: List<QueuedPrompt>, id: String): List<QueuedPrompt> = queue.filterNot { it.id == id }

    fun update(queue: List<QueuedPrompt>, id: String, text: String): List<QueuedPrompt> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return remove(queue, id)
        return queue.map { if (it.id == id) it.copy(text = trimmed) else it }
    }
}
