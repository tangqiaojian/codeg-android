package app.codeg.android.core.designsystem.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the incremental live parser to the invariant that matters: feeding text as it
 * streams in must always yield exactly what a full re-parse of the text-so-far would —
 * while only re-parsing the growing tail, not the stable prefix.
 */
class LiveBlockParserTest {

    /** Grow [full] [step] chars at a time; every prefix must equal a full re-parse. */
    private fun assertIncrementalMatchesFull(key: String, full: String, step: Int = 1) {
        var i = 0
        while (i < full.length) {
            i = minOf(full.length, i + step)
            val soFar = full.substring(0, i)
            assertEquals(
                "incremental parse diverged at length $i",
                parseMarkdownBlocks(soFar),
                LiveBlockParser.parse(key, soFar),
            )
        }
    }

    @Test
    fun `incremental multi-block prose matches full parse`() {
        assertIncrementalMatchesFull(
            "prose",
            "# Title\n\nfirst paragraph\n\n- one\n- two\n\nsecond paragraph",
        )
    }

    @Test
    fun `char-by-char streaming matches full parse`() {
        assertIncrementalMatchesFull(
            "chars",
            "para a\n\npara b\n\n1. x\n2. y\n\ndone",
            step = 1,
        )
    }

    @Test
    fun `a code fence with an internal blank line is never split mid-fence`() {
        assertIncrementalMatchesFull(
            "fence",
            "intro\n\n```kotlin\nval a = 1\n\nval b = 2\n```\n\nafter the fence",
        )
    }

    @Test
    fun `a single unbroken growing block parses correctly`() {
        assertIncrementalMatchesFull("single", "one long paragraph with no blank line at all", step = 3)
    }

    @Test
    fun `a shrink or divergence resets the segment and reparses`() {
        val key = "reset"
        LiveBlockParser.parse(key, "alpha\n\nbeta\n\ngamma")
        // A different, shorter text — a new turn reusing the same node-id slot.
        val fresh = "different\n\ntext"
        assertEquals(parseMarkdownBlocks(fresh), LiveBlockParser.parse(key, fresh))
    }

    @Test
    fun `a backtick fence body containing a tilde line is never split mid-block`() {
        // The ~~~ line and the blank line inside the ``` fence must not toggle fence state.
        assertIncrementalMatchesFull(
            "mixed-backtick",
            "before\n\n```\ncode line\n\n~~~\n\nmore code\n```\n\nafter",
        )
    }

    @Test
    fun `a tilde fence body containing a backtick line stays one block`() {
        assertIncrementalMatchesFull(
            "mixed-tilde",
            "intro\n\n~~~\na\n\n```\nb\n~~~\n\nend",
        )
    }

    @Test
    fun `an unterminated code fence keeps its growing tail as one block`() {
        assertIncrementalMatchesFull(
            "unterminated",
            "note\n\n```kotlin\nval a = 1\nval b = 2\nval c = 3",
        )
    }

    @Test
    fun `the stable prefix is not re-parsed on every flush`() {
        val key = "amortized"
        val text = "x\n\n".repeat(20).trimEnd() // 20 tiny blank-separated blocks
        val before = LiveBlockParser.parsedChars
        var i = 0
        while (i < text.length) {
            i++
            LiveBlockParser.parse(key, text.substring(0, i))
        }
        val used = LiveBlockParser.parsedChars - before
        // Re-parsing the whole prefix on every flush would sum to ~n²/2; prefix reuse must
        // stay far below that (this fails loudly if the stable prefix is ever re-scanned).
        val naiveWholeReparse = text.length.toLong() * (text.length + 1) / 2
        assertTrue(
            "parsed $used chars over the stream; a whole-prefix reparse would be ~$naiveWholeReparse",
            used < naiveWholeReparse / 3,
        )
    }
}
