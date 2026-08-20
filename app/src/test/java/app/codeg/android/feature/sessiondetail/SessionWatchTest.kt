package app.codeg.android.feature.sessiondetail

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionWatchTest {

    @Test
    fun `idle live conversation should reattach instead of going silent`() {
        assertEquals(
            SessionWatchAction.REATTACH,
            SessionWatch.tick(isInFlight = false, conversationLive = true, millisSinceEvent = 20_000),
        )
    }

    @Test
    fun `in-flight with a quiet socket should reconnect rather than wait forever`() {
        assertEquals(
            SessionWatchAction.RECONNECT,
            SessionWatch.tick(isInFlight = true, conversationLive = true, millisSinceEvent = 45_000),
        )
    }

    @Test
    fun `idle completed conversation only refetches occasionally`() {
        assertEquals(
            SessionWatchAction.REFETCH,
            SessionWatch.tick(isInFlight = false, conversationLive = false, millisSinceEvent = 20_000),
        )
    }

    @Test
    fun `fresh events stay on the wire`() {
        assertEquals(
            SessionWatchAction.WAIT,
            SessionWatch.tick(isInFlight = true, conversationLive = true, millisSinceEvent = 5_000),
        )
    }
}
