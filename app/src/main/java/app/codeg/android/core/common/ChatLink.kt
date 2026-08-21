package app.codeg.android.core.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.AnnotatedString

/**
 * Opens chat hyperlinks. Only http(s), mailto, and in-app `codeg://` URLs are
 * allowed — javascript / file / data schemes are dropped.
 */
object ChatLink {
    const val URL_TAG = "URL"
    private val allowedSchemes = setOf("http", "https", "mailto", "codeg")

    data class Match(
        val raw: String,
        val href: String,
        val start: Int,
        val endExclusive: Int,
    )

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val candidate = when {
            trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
            else -> trimmed
        }
        val schemeEnd = candidate.indexOf(':')
        if (schemeEnd <= 0) return null
        val scheme = candidate.substring(0, schemeEnd).lowercase()
        if (scheme !in allowedSchemes) return null
        when (scheme) {
            "http", "https" -> {
                val host = candidate.substringAfter("://", missingDelimiterValue = "")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringBefore(':')
                if (host.isBlank() || host.startsWith('.')) return null
            }
            "mailto" -> {
                val addr = candidate.substringAfter("mailto:")
                if (addr.isBlank() || '@' !in addr) return null
            }
            "codeg" -> {
                if (!candidate.startsWith("codeg://", ignoreCase = true)) return null
            }
        }
        return candidate
    }

    fun matchAt(raw: String, index: Int): Match? {
        if (index < 0 || index >= raw.length) return null
        if (index > 0) {
            val prev = raw[index - 1]
            if (prev.isLetterOrDigit() || prev == '_' || prev == '/') return null
        }
        val rest = raw.substring(index)
        val prefixLength = when {
            rest.startsWith("https://", ignoreCase = true) -> 8
            rest.startsWith("http://", ignoreCase = true) -> 7
            rest.startsWith("mailto:", ignoreCase = true) -> 7
            rest.startsWith("codeg://", ignoreCase = true) -> 8
            rest.startsWith("www.", ignoreCase = true) -> 4
            else -> return null
        }
        if (index + prefixLength >= raw.length) return null
        var end = index + prefixLength
        while (end < raw.length) {
            val c = raw[end]
            if (c.isWhitespace() || c == '<' || c == '>' || c == '"' || c == '\'' || c == '[' || c == ']') break
            end++
        }
        var token = raw.substring(index, end)
        while (token.isNotEmpty()) {
            val last = token.last()
            val drop = when {
                last in ".,;:!?" -> true
                last == ')' && token.count { it == '(' } < token.count { it == ')' } -> true
                last == ']' && token.count { it == '[' } < token.count { it == ']' } -> true
                else -> false
            }
            if (!drop) break
            token = token.dropLast(1)
            end--
        }
        if (token.length <= prefixLength) return null
        val href = normalize(token) ?: return null
        return Match(raw = token, href = href, start = index, endExclusive = end)
    }

    fun urlsIn(text: AnnotatedString): List<String> =
        text.getStringAnnotations(URL_TAG, 0, text.length).map { it.item }

    fun open(context: Context, raw: String): Boolean {
        val url = normalize(raw) ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        } catch (_: Exception) {
            false
        }
    }
}
