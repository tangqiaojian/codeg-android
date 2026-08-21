package app.codeg.android.feature.sessiondetail

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionWatchTest {

    @Test
    fun `idle live conversation with no socket should reattach`() {
        assertEquals(
            SessionWatchAction.REATTACH,
            SessionWatch.tick(
                isInFlight = false,
                conversationLive = true,
                streamOpen = false,
                millisSinceAcp = 20_000,
                millisSinceTransport = 20_000,
            ),
        )
    }

    @Test
    fun `in-flight with a dead transport should reconnect`() {
        assertEquals(
            SessionWatchAction.RECONNECT,
            SessionWatch.tick(
                isInFlight = true,
                conversationLive = true,
                streamOpen = true,
                millisSinceAcp = 5_000,
                millisSinceTransport = 45_000,
            ),
        )
    }

    @Test
    fun `in-flight with a healthy socket stays on the wire during a long think`() {
        assertEquals(
            SessionWatchAction.WAIT,
            SessionWatch.tick(
                isInFlight = true,
                conversationLive = true,
                streamOpen = true,
                millisSinceAcp = 80_000,
                millisSinceTransport = 5_000,
            ),
        )
    }

    @Test
    fun `in-flight with a healthy socket refetches after a very long ACP quiet`() {
        assertEquals(
            SessionWatchAction.REFETCH,
            SessionWatch.tick(
                isInFlight = true,
                conversationLive = true,
                streamOpen = true,
                millisSinceAcp = 120_000,
                millisSinceTransport = 5_000,
            ),
        )
    }

    @Test
    fun `closed stream while in flight reconnects`() {
        assertEquals(
            SessionWatchAction.RECONNECT,
            SessionWatch.tick(
                isInFlight = true,
                conversationLive = true,
                streamOpen = false,
                millisSinceAcp = 1_000,
                millisSinceTransport = 1_000,
            ),
        )
    }

    @Test
    fun `idle completed conversation only refetches occasionally`() {
        assertEquals(
            SessionWatchAction.REFETCH,
            SessionWatch.tick(
                isInFlight = false,
                conversationLive = false,
                streamOpen = false,
                millisSinceAcp = 20_000,
                millisSinceTransport = 20_000,
            ),
        )
    }

    @Test
    fun `fresh events stay on the wire`() {
        assertEquals(
            SessionWatchAction.WAIT,
            SessionWatch.tick(
                isInFlight = true,
                conversationLive = true,
                streamOpen = true,
                millisSinceAcp = 5_000,
                millisSinceTransport = 5_000,
            ),
        )
    }
}
