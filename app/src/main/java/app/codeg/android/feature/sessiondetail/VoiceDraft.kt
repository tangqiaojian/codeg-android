package app.codeg.android.feature.sessiondetail

/** Merge live speech into the composer without stacking partial results. */
object VoiceDraft {
    fun merge(prefix: String, spoken: String): String {
        val spokenTrim = spoken.trim()
        if (spokenTrim.isEmpty()) return prefix
        if (prefix.isBlank()) return spokenTrim
        return if (prefix.last().isWhitespace()) prefix + spokenTrim else "$prefix $spokenTrim"
    }

    fun shouldAutoSend(prefix: String): Boolean = prefix.isBlank()
}
