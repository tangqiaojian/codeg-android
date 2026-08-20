package app.codeg.android.core.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Picks the first URL that [probe] accepts. Probes run in parallel so a hanging
 * GitHub origin does not block China-reachable mirrors for minutes.
 */
object UpdateUrlPicker {
    suspend fun pick(
        urls: List<String>,
        timeoutMs: Long = 10_000,
        probe: suspend (String) -> Boolean,
    ): String {
        require(urls.isNotEmpty())
        return coroutineScope {
            val winner = CompletableDeferred<String>()
            val jobs = urls.map { url ->
                launch {
                    try {
                        if (probe(url)) winner.complete(url)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
            val watchdog = launch {
                kotlinx.coroutines.delay(timeoutMs)
                if (!winner.isCompleted) {
                    winner.completeExceptionally(IllegalStateException("no reachable URL"))
                }
            }
            val waiter = launch {
                jobs.forEach { it.join() }
                if (!winner.isCompleted) {
                    winner.completeExceptionally(IllegalStateException("no reachable URL"))
                }
            }
            try {
                winner.await()
            } finally {
                jobs.forEach { it.cancel() }
                watchdog.cancel()
                waiter.cancel()
            }
        }
    }
}
