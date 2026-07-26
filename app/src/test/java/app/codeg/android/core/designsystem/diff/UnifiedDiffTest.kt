package app.codeg.android.core.designsystem.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks down the ported unified-diff parser against the three shapes the backend emits. */
class UnifiedDiffTest {

    @Test
    fun `git unified diff parses files, hunks, counts and line numbers`() {
        val raw = """
            diff --git a/src/foo.kt b/src/foo.kt
            index 111..222 100644
            --- a/src/foo.kt
            +++ b/src/foo.kt
            @@ -1,3 +1,3 @@
             fun a() {}
            -val x = 1
            +val x = 2
        """.trimIndent()
        val files = UnifiedDiff.parse(raw)!!
        assertEquals(1, files.size)
        val f = files[0]
        assertEquals("src/foo.kt", f.path)
        assertEquals(DiffFile.Mode.MODIFIED, f.mode)
        assertEquals(1, f.additions)
        assertEquals(1, f.deletions)
        val rows = f.hunks.flatMap { it.rows }
        assertEquals(DiffRow.Kind.CONTEXT, rows[0].kind)
        assertEquals(DiffRow.Kind.DELETED, rows[1].kind)
        assertEquals(DiffRow.Kind.ADDED, rows[2].kind)
        // line numbers tracked from the @@ header
        assertEquals(2, rows[1].oldLine)
        assertEquals(2, rows[2].newLine)
    }

    @Test
    fun `new file mode is detected as added`() {
        val raw = """
            diff --git a/new.txt b/new.txt
            new file mode 100644
            --- /dev/null
            +++ b/new.txt
            @@ -0,0 +1,1 @@
            +hello
        """.trimIndent()
        val f = UnifiedDiff.parse(raw)!!.single()
        assertEquals(DiffFile.Mode.ADDED, f.mode)
        assertTrue(f.isNewFile)
    }

    @Test
    fun `codex apply_patch block parses`() {
        val raw = """
            *** Begin Patch
            *** Update File: lib/x.ts
            @@
            -old line
            +new line
            *** End Patch
        """.trimIndent()
        val f = UnifiedDiff.parse(raw)!!.single()
        assertEquals("lib/x.ts", f.path)
        assertEquals(1, f.additions)
        assertEquals(1, f.deletions)
    }

    @Test
    fun `looksLikeDiff distinguishes diffs from prose`() {
        assertTrue(UnifiedDiff.looksLikeDiff("diff --git a/x b/x"))
        assertTrue(UnifiedDiff.looksLikeDiff("*** Begin Patch\n*** Add File: x"))
        assertFalse(UnifiedDiff.looksLikeDiff("just some console output\n- a bullet point"))
        assertNull(UnifiedDiff.parse("not a diff at all"))
    }
}
