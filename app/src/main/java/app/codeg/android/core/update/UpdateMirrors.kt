package app.codeg.android.core.update

/** GitHub first, then China-reachable reverse proxies used for Releases. */
object UpdateMirrors {
    private val prefixes = listOf("https://ghproxy.net/", "https://ghfast.top/")

    fun candidates(url: String): List<String> {
        if (prefixes.any { url.startsWith(it) }) return listOf(url)
        return listOf(url) + prefixes.map { it + url }
    }
}
