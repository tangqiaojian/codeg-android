package app.codeg.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks down the plaintext a copy action should put on the clipboard. */
class CopyableTextTest {

    @Test
    fun `joins visible text blocks with a blank line`() {
        val text = copyableTurnText(
            listOf(
                ContentBlock.Text("first"),
                ContentBlock.Text("second"),
            ),
        )
        assertEquals("first\n\nsecond", text)
    }

    @Test
    fun `skips blank text, tools, thinking, and bare images`() {
        val text = copyableTurnText(
            listOf(
                ContentBlock.Text("   "),
                ContentBlock.Thinking("scratchpad"),
                ContentBlock.ToolUse("t1", "Read", """{"path":"a"}"""),
                ContentBlock.ToolResult("t1", "ok", false),
                ContentBlock.Image(ImageData("abc")),
                ContentBlock.Unknown("future"),
                ContentBlock.Text("keep me"),
            ),
        )
        assertEquals("keep me", text)
    }

    @Test
    fun `includes an image-generation prompt when there is no other text`() {
        val text = copyableTurnText(
            listOf(ContentBlock.ImageGeneration("a red balloon", image = null)),
        )
        assertEquals("a red balloon", text)
    }

    @Test
    fun `empty turns copy as empty`() {
        assertEquals("", copyableTurnText(emptyList()))
        assertTrue(copyableTurnText(listOf(ContentBlock.Thinking("x"))).isEmpty())
    }
}
