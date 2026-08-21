package app.codeg.android.feature.sessiondetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `round-trips clientMessageId and status`() {
        val queue = listOf(
            QueuedPrompt("a", "one", clientMessageId = "cid-a", status = QueuedPromptStatus.RETRY),
        )
        val restored = PromptQueueStore.decode(PromptQueueStore.encode(queue)).single()
        assertEquals("cid-a", restored.clientMessageId)
        assertEquals(QueuedPromptStatus.RETRY, restored.status)
    }

    @Test
    fun `legacy payloads without clientMessageId still decode`() {
        val raw = """[{"id":"a","text":"hello"}]"""
        val restored = PromptQueueStore.decode(raw).single()
        assertEquals("a", restored.id)
        assertEquals("hello", restored.text)
        assertEquals("a", restored.clientMessageId)
        assertEquals(QueuedPromptStatus.PENDING, restored.status)
    }

    @Test
    fun `empty queue encodes as a tombstone rather than a missing record`() {
        assertEquals("[]", PromptQueueStore.encode(emptyList()))
        assertEquals(emptyList<QueuedPrompt>(), PromptQueueStore.decode("[]"))
        assertTrue(PromptQueueStore.isRecord("[]"))
        assertTrue(PromptQueueStore.isRecord(""))
        assertTrue(!PromptQueueStore.isRecord(null))
    }

    @Test
    fun `decode of blank is empty`() {
        assertEquals(emptyList<QueuedPrompt>(), PromptQueueStore.decode(null))
        assertEquals(emptyList<QueuedPrompt>(), PromptQueueStore.decode(""))
    }
}
