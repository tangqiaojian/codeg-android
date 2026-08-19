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
}
