package app.codeg.android.feature.sessiondetail

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Codec for locally queued follow-up prompts across reconnect / process death. */
object PromptQueueStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(queue: List<QueuedPrompt>): String =
        if (queue.isEmpty()) "" else json.encodeToString(queue)

    fun decode(raw: String?): List<QueuedPrompt> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<QueuedPrompt>>(raw) }.getOrDefault(emptyList())
    }
}
