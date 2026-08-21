package app.codeg.android.feature.sessiondetail

/** Merge live speech into the composer without stacking partial results. */
object VoiceDraft {
    fun merge(prefix: String, spoken: String): String {
        val spokenTrim = spoken.trim()
        if (spokenTrim.isEmpty()) return prefix
        if (prefix.isBlank()) return spokenTrim
        return if (prefix.last().isWhitespace()) prefix + spokenTrim else "$prefix $spokenTrim"
    }

    /** Dictation always lands in the composer so the user can edit before sending. */
    fun shouldAutoSend(@Suppress("UNUSED_PARAMETER") prefix: String): Boolean = false
}
