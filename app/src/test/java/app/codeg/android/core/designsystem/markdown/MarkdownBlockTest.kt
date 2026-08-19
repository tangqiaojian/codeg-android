package app.codeg.android.core.designsystem.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks down the ported block-level Markdown parser. */
class MarkdownBlockTest {

    @Test
    fun `headings, paragraphs, and rules parse`() {
        val blocks = parseMarkdownBlocks("# Title\n\nbody text\n\n---")
        assertEquals(3, blocks.size)
        assertEquals(MarkdownBlock.Heading(1, "Title"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("body text"), blocks[1])
        assertEquals(MarkdownBlock.Rule, blocks[2])
    }

    @Test
    fun `bullet and numbered lists parse`() {
        val bullets = parseMarkdownBlocks("- one\n- two")
        assertEquals(MarkdownBlock.BulletList(listOf("one", "two")), bullets.single())

        val numbered = parseMarkdownBlocks("1. first\n2. second")
        val list = numbered.single() as MarkdownBlock.NumberedList
        assertEquals(listOf("1." to "first", "2." to "second"), list.items)
    }

    @Test
    fun `fenced code block keeps language and body`() {
        val blocks = parseMarkdownBlocks("```kotlin\nval x = 1\nval y = 2\n```")
        val code = blocks.single() as MarkdownBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\nval y = 2", code.code)
    }

    @Test
    fun `GFM table parses header and rows`() {
        val blocks = parseMarkdownBlocks("| A | B |\n| --- | --- |\n| 1 | 2 |")
        val table = blocks.single() as MarkdownBlock.Table
        assertEquals(listOf("A", "B"), table.header)
        assertEquals(listOf(listOf("1", "2")), table.rows)
    }

    @Test
    fun `blockquote groups consecutive lines`() {
        val blocks = parseMarkdownBlocks("> line one\n> line two")
        assertTrue(blocks.single() is MarkdownBlock.Quote)
    }

    @Test
    fun `plain text of a list and a table is copyable`() {
        val list = parseMarkdownBlocks("- one\n- two").single()
        assertEquals("• one\n• two", markdownBlockPlainText(list))

        val table = parseMarkdownBlocks("| A | B |\n| --- | --- |\n| 1 | 2 |").single()
        assertEquals("A\tB\n1\t2", markdownBlockPlainText(table))
    }
}
