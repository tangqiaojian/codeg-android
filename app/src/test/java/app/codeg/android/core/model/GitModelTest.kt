package app.codeg.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Locks down the ported git-status derivation (path/renamedFrom + change category). */
class GitModelTest {

    @Test
    fun `rename status splits old and new paths`() {
        val e = GitStatusEntry(status = "R", file = "old/a.kt -> new/b.kt")
        assertEquals("new/b.kt", e.path)
        assertEquals("old/a.kt", e.renamedFrom)
        assertEquals(GitChange.RENAMED, e.change)
    }

    @Test
    fun `plain status keeps the file path`() {
        val e = GitStatusEntry(status = "M", file = "src/x.kt")
        assertEquals("src/x.kt", e.path)
        assertNull(e.renamedFrom)
        assertEquals(GitChange.MODIFIED, e.change)
    }

    @Test
    fun `change categories map from porcelain codes`() {
        assertEquals(GitChange.UNTRACKED, GitChange.from("??"))
        assertEquals(GitChange.CONFLICTED, GitChange.from("UU"))
        assertEquals(GitChange.ADDED, GitChange.from("A"))
        assertEquals(GitChange.DELETED, GitChange.from("D"))
    }

    @Test
    fun `git log entry derives subject, body and totals`() {
        val e = GitLogEntry(
            hash = "abc1234", fullHash = "abc1234full", author = "x", date = "",
            message = "feat: thing\n\nbody line one\nbody line two",
            files = listOf(GitLogFileChange("a", "M", 3, 1), GitLogFileChange("b", "A", 5, 0)),
        )
        assertEquals("feat: thing", e.subject)
        assertEquals("body line one\nbody line two", e.body)
        assertEquals(8, e.totalAdditions)
        assertEquals(1, e.totalDeletions)
    }
}
