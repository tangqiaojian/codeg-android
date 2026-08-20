package app.codeg.android.feature.sessiondetail

/**
 * Keepalive policy for an open session screen. The socket is closed after a
 * turn and [MAX_STREAM_RECONNECTS] used to give up; sitting on the screen then
 * went silent. This tick decides whether to reattach, force a reconnect, or
 * just refetch the transcript.
 */
enum class SessionWatchAction { REATTACH, RECONNECT, REFETCH, WAIT }

object SessionWatch {
    const val IDLE_LIVE_REATTACH_MS = 20_000L
    const val IN_FLIGHT_QUIET_RECONNECT_MS = 45_000L
    const val IDLE_REFETCH_MS = 20_000L
    const val TICK_MS = 10_000L

    fun tick(
        isInFlight: Boolean,
        conversationLive: Boolean,
        millisSinceEvent: Long,
    ): SessionWatchAction = when {
        isInFlight && millisSinceEvent >= IN_FLIGHT_QUIET_RECONNECT_MS -> SessionWatchAction.RECONNECT
        !isInFlight && conversationLive && millisSinceEvent >= IDLE_LIVE_REATTACH_MS -> SessionWatchAction.REATTACH
        !isInFlight && !conversationLive && millisSinceEvent >= IDLE_REFETCH_MS -> SessionWatchAction.REFETCH
        else -> SessionWatchAction.WAIT
    }
}
