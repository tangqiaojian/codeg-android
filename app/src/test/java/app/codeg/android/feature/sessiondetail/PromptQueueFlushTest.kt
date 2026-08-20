package app.codeg.android.feature.sessiondetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptQueueFlushTest {

    @Test
    fun `does not flush while a turn is in flight`() {
        assertFalse(PromptQueueFlush.shouldFlush(isInFlight = true, queuedCount = 2))
    }

    @Test
    fun `flushes when idle with queued prompts`() {
        assertTrue(PromptQueueFlush.shouldFlush(isInFlight = false, queuedCount = 1))
        assertFalse(PromptQueueFlush.shouldFlush(isInFlight = false, queuedCount = 0))
    }

    @Test
    fun `retries back off after turn_in_progress instead of giving up`() {
        assertEquals(750L, PromptQueueFlush.retryDelayMs(0))
        assertEquals(1_500L, PromptQueueFlush.retryDelayMs(1))
        assertEquals(3_000L, PromptQueueFlush.retryDelayMs(2))
        assertEquals(8_000L, PromptQueueFlush.retryDelayMs(8))
    }
}
