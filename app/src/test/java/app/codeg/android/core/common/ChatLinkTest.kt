package app.codeg.android.core.common

import androidx.compose.ui.graphics.Color
import app.codeg.android.core.designsystem.markdown.inlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLinkTest {

    @Test
    fun `normalize allows http https mailto and codeg`() {
        assertEquals("https://example.com/a", ChatLink.normalize("https://example.com/a"))
        assertEquals("http://example.com", ChatLink.normalize("http://example.com"))
        assertEquals("https://www.example.com", ChatLink.normalize("www.example.com"))
        assertEquals("mailto:hi@example.com", ChatLink.normalize("mailto:hi@example.com"))
        assertEquals("codeg://conversation/12", ChatLink.normalize("codeg://conversation/12"))
        assertEquals("http://192.168.1.8:3080/x", ChatLink.normalize("http://192.168.1.8:3080/x"))
        assertEquals("http://localhost:3080", ChatLink.normalize("http://localhost:3080"))
    }

    @Test
    fun `normalize rejects dangerous or empty schemes`() {
        assertNull(ChatLink.normalize("javascript:alert(1)"))
        assertNull(ChatLink.normalize("file:///etc/passwd"))
        assertNull(ChatLink.normalize("data:text/html,hi"))
        assertNull(ChatLink.normalize("ftp://example.com"))
        assertNull(ChatLink.normalize("not a link"))
        assertNull(ChatLink.normalize("https://"))
    }

    @Test
    fun `matchAt finds a bare url and trims trailing punctuation`() {
        val text = "See https://example.com/path, please"
        val match = ChatLink.matchAt(text, 4)!!
        assertEquals("https://example.com/path", match.raw)
        assertEquals("https://example.com/path", match.href)
        assertEquals(4, match.start)
        assertEquals(text.indexOf(','), match.endExclusive)
    }

    @Test
    fun `matchAt keeps balanced parentheses in wikipedia-style urls`() {
        val url = "https://en.wikipedia.org/wiki/Foo_(bar)"
        val match = ChatLink.matchAt(url, 0)!!
        assertEquals(url, match.href)
    }

    @Test
    fun `inline markdown links and autolinks are annotated`() {
        val colors = Color.Blue
        val md = inlineMarkdown(
            raw = "Go [here](https://example.com) and https://codeg.app too",
            linkColor = colors,
            codeBg = Color.Gray,
            codeColor = Color.White,
        )
        assertEquals(
            listOf("https://example.com", "https://codeg.app"),
            ChatLink.urlsIn(md),
        )
    }

    @Test
    fun `markdown link keeps parentheses in the target url`() {
        val url = "https://en.wikipedia.org/wiki/Foo_(bar)"
        val md = inlineMarkdown(
            raw = "[Foo]($url)",
            linkColor = Color.Blue,
            codeBg = Color.Gray,
            codeColor = Color.White,
        )
        assertEquals("Foo", md.text)
        assertEquals(listOf(url), ChatLink.urlsIn(md))
    }

    @Test
    fun `javascript markdown href is not annotated`() {
        val md = inlineMarkdown(
            raw = "[x](javascript:alert(1))",
            linkColor = Color.Blue,
            codeBg = Color.Gray,
            codeColor = Color.White,
        )
        assertTrue(ChatLink.urlsIn(md).isEmpty())
        assertEquals("x", md.text)
    }

    @Test
    fun `urls inside inline code are not autolinked`() {
        val md = inlineMarkdown(
            raw = "Use `https://example.com` in code",
            linkColor = Color.Blue,
            codeBg = Color.Gray,
            codeColor = Color.White,
        )
        assertTrue(ChatLink.urlsIn(md).isEmpty())
    }

    @Test
    fun `markdown image bang is omitted and the url is still a link`() {
        val md = inlineMarkdown(
            raw = "See ![shot](https://cdn.example.com/a.png)",
            linkColor = Color.Blue,
            codeBg = Color.Gray,
            codeColor = Color.White,
        )
        assertEquals("See shot", md.text)
        assertEquals(listOf("https://cdn.example.com/a.png"), ChatLink.urlsIn(md))
    }
}
