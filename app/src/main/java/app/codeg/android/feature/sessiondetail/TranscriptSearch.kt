package app.codeg.android.feature.sessiondetail

import app.codeg.android.core.model.ContentBlock
import app.codeg.android.core.model.MessageTurn
import app.codeg.android.core.model.TurnRole

data class TranscriptHit(
    val turnId: String,
    val role: TurnRole,
    val snippet: String,
)

/** Local in-session transcript find. No extra RPC — searches already-loaded turns. */
object TranscriptSearch {
    fun findHits(turns: List<MessageTurn>, rawQuery: String, limit: Int = 50): List<TranscriptHit> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<TranscriptHit>()
        for (turn in turns) {
            val text = turn.blocks
                .filterIsInstance<ContentBlock.Text>()
                .joinToString("\n") { it.text }
            if (text.isBlank()) continue
            val index = text.indexOf(query, ignoreCase = true)
            if (index < 0) continue
            out += TranscriptHit(turn.id, turn.role, snippetAround(text, index, query.length))
            if (out.size >= limit) break
        }
        return out
    }

    private fun snippetAround(text: String, index: Int, matchLen: Int, radius: Int = 42): String {
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + matchLen + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).replace('\n', ' ').trim() + suffix
    }
}
