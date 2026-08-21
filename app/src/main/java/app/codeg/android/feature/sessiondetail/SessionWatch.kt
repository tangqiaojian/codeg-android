package app.codeg.android.feature.sessiondetail

/**
 * Keepalive policy for an open session screen.
 *
 * Transport health (pong / any WS frame) is separate from ACP content quiet.
 * A long think with a healthy socket must WAIT, not tear the connection.
 */
enum class SessionWatchAction { REATTACH, RECONNECT, REFETCH, WAIT }

object SessionWatch {
    const val IDLE_LIVE_REATTACH_MS = 20_000L
    const val TRANSPORT_STALE_MS = 45_000L
    const val ACP_QUIET_REFETCH_MS = 120_000L
    const val IDLE_REFETCH_MS = 20_000L
    const val TICK_MS = 10_000L

    fun tick(
        isInFlight: Boolean,
        conversationLive: Boolean,
        streamOpen: Boolean,
        millisSinceAcp: Long,
        millisSinceTransport: Long,
    ): SessionWatchAction = when {
        isInFlight && (!streamOpen || millisSinceTransport >= TRANSPORT_STALE_MS) ->
            SessionWatchAction.RECONNECT
        isInFlight && millisSinceAcp >= ACP_QUIET_REFETCH_MS ->
            SessionWatchAction.REFETCH
        !isInFlight && conversationLive && !streamOpen && millisSinceAcp >= IDLE_LIVE_REATTACH_MS ->
            SessionWatchAction.REATTACH
        !isInFlight && !conversationLive && millisSinceAcp >= IDLE_REFETCH_MS ->
            SessionWatchAction.REFETCH
        else -> SessionWatchAction.WAIT
    }
}
