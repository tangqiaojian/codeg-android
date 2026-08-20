package app.codeg.android.feature.sessiondetail

/**
 * When to send the local follow-up queue. Immediate `prompt()` after
 * `turn_complete` often 409s (`turn_in_progress`); without a retry the item
 * sits in the queue forever.
 */
object PromptQueueFlush {
    fun shouldFlush(isInFlight: Boolean, queuedCount: Int): Boolean =
        !isInFlight && queuedCount > 0

    fun retryDelayMs(attempt: Int): Long =
        (750L shl attempt.coerceAtLeast(0)).coerceAtMost(8_000L)
}
