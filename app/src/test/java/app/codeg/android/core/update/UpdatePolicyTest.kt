package app.codeg.android.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun `auto-check waits 12 hours unless forced`() {
        val interval = 12 * 60 * 60 * 1000L
        val last = 1_000L
        assertFalse(UpdatePolicy.shouldNetworkCheck(lastCheckEpochMs = last, nowMs = last + interval - 1, force = false, intervalMs = interval))
        assertTrue(UpdatePolicy.shouldNetworkCheck(lastCheckEpochMs = last, nowMs = last + interval, force = false, intervalMs = interval))
        assertTrue(UpdatePolicy.shouldNetworkCheck(lastCheckEpochMs = last, nowMs = last + 1, force = true, intervalMs = interval))
        assertTrue(UpdatePolicy.shouldNetworkCheck(lastCheckEpochMs = 0, nowMs = 1, force = false, intervalMs = interval))
    }

    @Test
    fun `later snoozes that tag but not a newer one`() {
        assertFalse(UpdatePolicy.shouldPrompt(availableTag = "v1.2.4", dismissedTag = "v1.2.4"))
        assertTrue(UpdatePolicy.shouldPrompt(availableTag = "v1.2.5", dismissedTag = "v1.2.4"))
        assertTrue(UpdatePolicy.shouldPrompt(availableTag = "v1.2.4", dismissedTag = null))
    }
}
