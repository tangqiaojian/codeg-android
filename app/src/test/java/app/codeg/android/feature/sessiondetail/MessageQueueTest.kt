package app.codeg.android.feature.sessiondetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports the Web `use-message-queue` FIFO / bounce-to-front contract. */
class MessageQueueTest {

    @Test
    fun `enqueue appends and dequeue pops the head`() {
        var queue = PromptQueue.enqueue(emptyList(), "first")
        queue = PromptQueue.enqueue(queue, "second")
        assertEquals(listOf("first", "second"), queue.map { it.text })
        val (head, rest) = PromptQueue.dequeue(queue)
        assertEquals("first", head?.text)
        assertEquals(listOf("second"), rest.map { it.text })
    }

    @Test
    fun `requeueFront puts a bounced prompt ahead of later items`() {
        val queued = PromptQueue.enqueue(emptyList(), "later")
        val bounced = PromptQueue.requeueFront(queued, "bounced")
        assertEquals(listOf("bounced", "later"), bounced.map { it.text })
    }

    @Test
    fun `remove drops one item and update rewrites its text`() {
        val a = PromptQueue.enqueue(emptyList(), "a")
        val both = PromptQueue.enqueue(a, "b")
        val removed = PromptQueue.remove(both, both[0].id)
        assertEquals(listOf("b"), removed.map { it.text })
        val updated = PromptQueue.update(both, both[1].id, "b-edited")
        assertEquals(listOf("a", "b-edited"), updated.map { it.text })
    }

    @Test
    fun `dequeue of an empty queue is a no-op`() {
        val empty = emptyList<QueuedPrompt>()
        val (head, rest) = PromptQueue.dequeue(empty)
        assertNull(head)
        assertSame(empty, rest)
    }

    @Test
    fun `blank text is not queued`() {
        assertTrue(PromptQueue.enqueue(emptyList(), "   ").isEmpty())
    }

    @Test
    fun `ack happens only after the server accepts so a failed flush keeps the head`() {
        val queued = PromptQueue.enqueue(emptyList(), "keep-me", id = "a", clientMessageId = "cid-a")
        val sending = PromptQueue.markSending(queued, "a")
        assertEquals(QueuedPromptStatus.SENDING, sending.single().status)
        assertEquals("cid-a", sending.single().clientMessageId)
        val retried = PromptQueue.markRetry(sending, "a")
        assertEquals(listOf("keep-me"), retried.map { it.text })
        assertEquals("cid-a", retried.single().clientMessageId)
        assertEquals(QueuedPromptStatus.RETRY, retried.single().status)
        val acked = PromptQueue.remove(retried, "a")
        assertTrue(acked.isEmpty())
    }

    @Test
    fun `process death mid-send revives the item as retry with the same client id`() {
        val sending = PromptQueue.markSending(
            PromptQueue.enqueue(emptyList(), "hello", id = "a", clientMessageId = "cid"),
            "a",
        )
        val revived = PromptQueue.revive(sending)
        assertEquals(QueuedPromptStatus.RETRY, revived.single().status)
        assertEquals("cid", revived.single().clientMessageId)
    }

    @Test
    fun `sending items cannot be edited or cleared by update`() {
        val sending = PromptQueue.markSending(
            PromptQueue.enqueue(emptyList(), "keep", id = "a", clientMessageId = "cid"),
            "a",
        )
        assertEquals(sending, PromptQueue.update(sending, "a", "nope"))
        assertEquals(sending, PromptQueue.update(sending, "a", "   "))
    }

    @Test
    fun `editing text mints a new clientMessageId`() {
        val queued = PromptQueue.enqueue(emptyList(), "old", id = "a", clientMessageId = "cid-old")
        val updated = PromptQueue.update(queued, "a", "new")
        assertEquals("new", updated.single().text)
        assertTrue(updated.single().clientMessageId != "cid-old")
        assertEquals(QueuedPromptStatus.PENDING, updated.single().status)
    }

    @Test
    fun `requeueFront reuses the bounced clientMessageId`() {
        val later = PromptQueue.enqueue(emptyList(), "later", id = "b", clientMessageId = "cid-b")
        val bounced = PromptQueue.requeueFront(later, "bounced", id = "a", clientMessageId = "cid-a")
        assertEquals(listOf("bounced", "later"), bounced.map { it.text })
        assertEquals("cid-a", bounced.first().clientMessageId)
        assertEquals(QueuedPromptStatus.RETRY, bounced.first().status)
    }

    @Test
    fun `take removes an item so edit can put it back in the composer`() {
        val a = PromptQueue.enqueue(emptyList(), "hello", id = "a")
        val both = PromptQueue.enqueue(a, "keep", id = "b")
        val (taken, rest) = PromptQueue.take(both, "a")
        assertEquals("hello", taken?.text)
        assertEquals(listOf("keep"), rest.map { it.text })
        val missing = PromptQueue.take(both, "nope")
        assertNull(missing.first)
        assertEquals(listOf("hello", "keep"), missing.second.map { it.text })
    }
}
