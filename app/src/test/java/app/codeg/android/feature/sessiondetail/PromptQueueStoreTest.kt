package app.codeg.android.feature.sessiondetail

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptQueueStoreTest {

    @Test
    fun `round-trips queued prompts so reconnect can restore them`() {
        val queue = PromptQueue.enqueue(emptyList(), "first", id = "a")
            .let { PromptQueue.enqueue(it, "second", id = "b") }
        val restored = PromptQueueStore.decode(PromptQueueStore.encode(queue))
        assertEquals(listOf("a" to "first", "b" to "second"), restored.map { it.id to it.text })
    }

    @Test
    fun `update rewrites a queued prompt in place`() {
        val queue = listOf(QueuedPrompt("a", "old"), QueuedPrompt("b", "keep"))
        val next = PromptQueue.update(queue, "a", "  edited  ")
        assertEquals(listOf("edited", "keep"), next.map { it.text })
    }

    @Test
    fun `decode of blank is empty`() {
        assertEquals(emptyList<QueuedPrompt>(), PromptQueueStore.decode(null))
        assertEquals(emptyList<QueuedPrompt>(), PromptQueueStore.decode(""))
    }
}
