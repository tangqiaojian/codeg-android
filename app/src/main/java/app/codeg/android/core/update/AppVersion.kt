package app.codeg.android.core.update

/** Dotted app version (`1.2.3` / `v1.2.3` / `0.4.0-beta`). Missing patch is 0. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    /**
     * Marketing versions accidentally shipped as 1.2–1.3; 0.4.x-beta is the
     * reset series and must still upgrade those installs.
     */
    fun isNewerThan(current: AppVersion): Boolean {
        if (this == current) return false
        val thisReset = major == 0 && minor >= 4
        val currentReset = current.major == 0 && current.minor >= 4
        val thisLegacy = major == 1 && minor in 2..3
        val currentLegacy = current.major == 1 && current.minor in 2..3
        if (thisReset && currentLegacy) return true
        if (thisLegacy && currentReset) return false
        return this > current
    }

    companion object {
        fun parse(raw: String): AppVersion? {
            val core = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .substringBefore("+")
            if (core.isEmpty()) return null
            val parts = core.split('.')
            if (parts.size !in 2..3) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = if (parts.size == 3) parts[2].toIntOrNull() ?: return null else 0
            return AppVersion(major, minor, patch)
        }
    }
}
