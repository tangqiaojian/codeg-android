package app.codeg.android.core.designsystem.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.codeg.android.core.common.ChatLink
import app.codeg.android.core.designsystem.theme.CodegTheme

/**
 * Inline-Markdown → [AnnotatedString] (the Compose analogue of iOS
 * `MarkdownText.attributed(from:)`, which used Apple's inline parser). Supports
 * `**bold**` / `__bold__`, `*italic*` / `_italic_`, `~~strike~~`, `` `code` ``,
 * `[text](url)` links, `![alt](url)` image-links, and bare http(s) URLs.
 * Nesting is handled by recursion; code spans are literal. Openable URLs are
 * attached as a string annotation under [ChatLink.URL_TAG] and as a
 * [LinkAnnotation.Url] so selection and tap-to-open can coexist.
 */
const val URL_TAG = ChatLink.URL_TAG

private class LinkClickHolder {
    var onOpen: (String) -> Unit = {}
}

/** Cached inline-markdown for the current theme colors (recomputed if colors change).
 *  Slot-scoped `remember` only — deliberately NOT a shared cache, so the growing tail of
 *  a streaming block can't churn/evict persisted spans. */
@Composable
fun rememberInlineMarkdown(raw: String, onOpen: (String) -> Unit = {}): AnnotatedString {
    val colors = CodegTheme.colors
    val holder = remember { LinkClickHolder() }
    holder.onOpen = onOpen
    return remember(raw, colors.accent, colors.codeSurface, colors.textPrimary) {
        inlineMarkdown(
            raw = raw,
            linkColor = colors.accent,
            codeBg = colors.codeSurface,
            codeColor = colors.textPrimary,
            onOpen = { holder.onOpen(it) },
        )
    }
}

fun inlineMarkdown(
    raw: String,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color,
    onOpen: (String) -> Unit = {},
): AnnotatedString = buildAnnotatedString {
    parseInline(this, raw, linkColor, codeBg, codeColor, onOpen, autolink = true)
}

private fun parseInline(
    builder: AnnotatedString.Builder,
    raw: String,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color,
    onOpen: (String) -> Unit,
    autolink: Boolean,
) {
    var i = 0
    val n = raw.length
    val plain = StringBuilder()

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            builder.append(plain.toString())
            plain.clear()
        }
    }

    while (i < n) {
        val c = raw[i]
        when {
            // Inline code — literal content, no recursion.
            c == '`' -> {
                val end = raw.indexOf('`', i + 1)
                if (end > i) {
                    flushPlain()
                    builder.withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor),
                    ) { append(raw.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    plain.append(c); i++
                }
            }

            // Bold (** or __).
            (c == '*' || c == '_') && i + 1 < n && raw[i + 1] == c -> {
                val marker = "$c$c"
                val end = raw.indexOf(marker, i + 2)
                if (end > i + 1) {
                    flushPlain()
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        parseInline(builder, raw.substring(i + 2, end), linkColor, codeBg, codeColor, onOpen, autolink)
                    }
                    i = end + 2
                } else {
                    plain.append(c); i++
                }
            }

            // Strikethrough (~~).
            c == '~' && i + 1 < n && raw[i + 1] == '~' -> {
                val end = raw.indexOf("~~", i + 2)
                if (end > i + 1) {
                    flushPlain()
                    builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        parseInline(builder, raw.substring(i + 2, end), linkColor, codeBg, codeColor, onOpen, autolink)
                    }
                    i = end + 2
                } else {
                    plain.append(c); i++
                }
            }

            // Italic (single * or _).
            (c == '*' || c == '_') -> {
                val end = raw.indexOf(c, i + 1)
                if (end > i) {
                    flushPlain()
                    builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        parseInline(builder, raw.substring(i + 1, end), linkColor, codeBg, codeColor, onOpen, autolink)
                    }
                    i = end + 1
                } else {
                    plain.append(c); i++
                }
            }

            // Markdown image ![alt](url) — skip the bang; the following '[' is a link.
            c == '!' && i + 1 < n && raw[i + 1] == '[' -> i++

            // Link [text](url).
            c == '[' -> {
                val close = raw.indexOf(']', i + 1)
                if (close > i && close + 1 < n && raw[close + 1] == '(') {
                    val urlEnd = closingParen(raw, close + 1)
                    if (urlEnd > close) {
                        val text = raw.substring(i + 1, close)
                        val url = raw.substring(close + 2, urlEnd)
                        flushPlain()
                        emitLink(builder, text, url, linkColor, codeBg, codeColor, onOpen)
                        i = urlEnd + 1
                    } else {
                        plain.append(c); i++
                    }
                } else {
                    plain.append(c); i++
                }
            }

            else -> {
                val match = if (autolink) ChatLink.matchAt(raw, i) else null
                if (match != null) {
                    flushPlain()
                    emitLink(builder, match.raw, match.href, linkColor, codeBg, codeColor, onOpen)
                    i = match.endExclusive
                } else {
                    plain.append(c); i++
                }
            }
        }
    }
    flushPlain()
}

private fun emitLink(
    builder: AnnotatedString.Builder,
    display: String,
    url: String,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color,
    onOpen: (String) -> Unit,
) {
    val href = ChatLink.normalize(url)
    if (href == null) {
        parseInline(builder, display, linkColor, codeBg, codeColor, onOpen, autolink = false)
        return
    }
    val styles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        pressedStyle = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
            background = linkColor.copy(alpha = 0.16f),
        ),
    )
    builder.pushStringAnnotation(URL_TAG, href)
    builder.withLink(
        LinkAnnotation.Url(
            url = href,
            styles = styles,
            linkInteractionListener = { onOpen(href) },
        ),
    ) {
        parseInline(builder, display, linkColor, codeBg, codeColor, onOpen, autolink = false)
    }
    builder.pop()
}

private fun closingParen(raw: String, openIndex: Int): Int {
    var depth = 0
    for (i in openIndex until raw.length) {
        when (raw[i]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return -1
}
