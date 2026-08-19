package app.codeg.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * New-session workspace picker: nested worktrees, searchable by name/path,
 * and last-opened top-level folders first.
 */
class WorkspaceFoldersTest {

    private fun folder(
        id: Int,
        name: String,
        path: String = "/p/$id",
        parentId: Int? = null,
        lastOpened: Long? = null,
        branch: String? = null,
    ) = FolderDetail(
        id = id,
        name = name,
        path = path,
        parentId = parentId,
        gitBranch = branch,
        lastOpenedAt = lastOpened?.let { Instant.ofEpochSecond(it) },
    )

    @Test
    fun `nests worktrees under their workspace and prefers last opened`() {
        val folders = listOf(
            folder(1, "old", lastOpened = 10),
            folder(2, "new", lastOpened = 50),
            folder(3, "wt-old", parentId = 1, branch = "feat"),
            folder(4, "wt-new", parentId = 2, branch = "fix"),
        )
        val rows = WorkspaceFolders.choices(folders)
        assertEquals(listOf(2, 4, 1, 3), rows.map { it.folder.id })
        assertEquals("new / fix", rows.first { it.folder.id == 4 }.title)
        assertTrue(rows.first { it.folder.id == 4 }.isWorktree)
        assertEquals("/p/2", rows.first { it.folder.id == 2 }.subtitle)
    }

    @Test
    fun `search matches name path and breadcrumb`() {
        val folders = listOf(
            folder(1, "codeg-android", path = "/home/me/codeg-android"),
            folder(2, "wt", path = "/home/me/wt", parentId = 1, branch = "mobile"),
            folder(3, "other", path = "/tmp/other"),
        )
        assertEquals(listOf(1, 2), WorkspaceFolders.choices(folders, "android").map { it.folder.id })
        assertEquals(listOf(2), WorkspaceFolders.choices(folders, "mobile").map { it.folder.id })
        assertEquals(listOf(3), WorkspaceFolders.choices(folders, "tmp").map { it.folder.id })
        assertTrue(WorkspaceFolders.choices(folders, "nope").isEmpty())
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
