package app.codeg.android.core.update

object UpdatePolicy {
    const val CHECK_INTERVAL_MS: Long = 12 * 60 * 60 * 1000L

    fun shouldNetworkCheck(
        lastCheckEpochMs: Long,
        nowMs: Long,
        force: Boolean,
        intervalMs: Long = CHECK_INTERVAL_MS,
    ): Boolean {
        if (force) return true
        if (lastCheckEpochMs <= 0L) return true
        return nowMs - lastCheckEpochMs >= intervalMs
    }

    fun shouldPrompt(availableTag: String, dismissedTag: String?): Boolean {
        if (dismissedTag.isNullOrBlank()) return true
        if (availableTag == dismissedTag) return false
        val available = AppVersion.parse(availableTag) ?: return true
        val dismissed = AppVersion.parse(dismissedTag) ?: return true
        return available.isNewerThan(dismissed)
    }
}
