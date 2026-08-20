package app.codeg.android.core.update

/** Dotted app version (`1.2.3` / `v1.2.3`). Missing patch is 0. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

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
