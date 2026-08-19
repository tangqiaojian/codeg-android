package app.codeg.android.core.model

/**
 * A windowed transcript: [turns] is `full[turnsOffset ..]` of a conversation
 * whose true length is [turnsTotal]. [prefixHash] fingerprints the uncovered
 * prefix so an older page can only join when the seam still matches.
 *
 * Port of the web client's `isWindowedDetail` + prepend merge.
 */
data class TranscriptWindow(
    val turns: List<MessageTurn>,
    val turnsOffset: Int,
    val turnsTotal: Int,
    val prefixHash: String,
    val assistantTurnsBeforeOffset: Int = 0,
) {
    val hasOlder: Boolean get() = turnsOffset > 0

    fun prepend(page: ConversationTurnsPage): TranscriptWindow? {
        if (page.prefixHashBeforeIndex != prefixHash) return null
        return copy(
            turns = page.turns + turns,
            turnsOffset = page.turnsOffset,
            turnsTotal = page.turnsTotal,
            prefixHash = page.prefixHash,
            assistantTurnsBeforeOffset = page.assistantTurnsBeforeOffset,
        )
    }

    companion object {
        fun from(detail: ConversationDetail): TranscriptWindow? {
            val offset = detail.turnsOffset ?: return null
            val total = detail.turnsTotal ?: return null
            val hash = detail.prefixHash ?: return null
            return TranscriptWindow(
                turns = detail.turns,
                turnsOffset = offset,
                turnsTotal = total,
                prefixHash = hash,
                assistantTurnsBeforeOffset = detail.assistantTurnsBeforeOffset ?: 0,
            )
        }
    }
}
