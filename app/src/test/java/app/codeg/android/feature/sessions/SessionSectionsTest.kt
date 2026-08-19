package app.codeg.android.feature.sessions

import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.ConversationStatus
import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.FolderDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Locks the grouped section list the Chats screen renders: ordering (Pinned →
 * folders in sortOrder/name order → Other), per-section sort, membership
 * (pinned excluded from folder groups), and the pre-resolved cross-folder tag.
 */
class SessionSectionsTest {

    private fun conv(
        id: Int,
        folderId: Int,
        updated: Long,
        pinnedAt: Long? = null,
    ) = ConversationSummary(
        id = id,
        folderId = folderId,
        title = "c$id",
        agentType = AgentType.CLAUDE_CODE,
        status = ConversationStatus.COMPLETED,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.ofEpochSecond(updated),
        pinnedAt = pinnedAt?.let { Instant.ofEpochSecond(it) },
    )

    private fun folder(id: Int, name: String, sortOrder: Int = 0, color: String = "") =
        FolderDetail(id = id, name = name, path = "/p/$id", sortOrder = sortOrder, color = color)

    @Test
    fun `sections are ordered pinned then folders then other`() {
        val folders = listOf(
            folder(1, "Beta", sortOrder = 1),
            folder(2, "Alpha", sortOrder = 0),
        )
        val convs = listOf(
            conv(10, folderId = 1, updated = 100, pinnedAt = 50),
            conv(11, folderId = 2, updated = 90, pinnedAt = 70),
            conv(20, folderId = 1, updated = 200),
            conv(21, folderId = 1, updated = 150),
            conv(30, folderId = 2, updated = 120),
            conv(40, folderId = 99, updated = 300), // orphan → Other
        )

        val sections = buildSessionSections(folders, convs)

        // sortedFolders orders by sortOrder then name → Alpha(0) before Beta(1).
        assertEquals(listOf("pinned", "folder-2", "folder-1", "other"), sections.map { it.id })
    }

    @Test
    fun `pinned section spans folders, sorts by pinnedAt desc, and tags the folder`() {
        val folders = listOf(folder(1, "Beta", sortOrder = 1), folder(2, "Alpha", sortOrder = 0))
        val convs = listOf(
            conv(10, folderId = 1, updated = 100, pinnedAt = 50),
            conv(11, folderId = 2, updated = 90, pinnedAt = 70),
            conv(30, folderId = 2, updated = 120),
        )

        val pinned = buildSessionSections(folders, convs).first { it.id == "pinned" }

        assertTrue(pinned.kind is SectionKind.Pinned)
        // pinnedAt 70 (conv 11) is more recent than 50 (conv 10).
        assertEquals(listOf(11, 10), pinned.rows.map { it.conversation.id })
        // Cross-folder rows carry the folder tag, pre-resolved.
        assertEquals(listOf("Alpha", "Beta"), pinned.rows.map { it.folderName })
    }

    @Test
    fun `folder group lists its non-pinned rows newest-first with no folder tag`() {
        val folders = listOf(folder(1, "Beta", sortOrder = 1), folder(2, "Alpha", sortOrder = 0))
        val convs = listOf(
            conv(11, folderId = 2, updated = 90, pinnedAt = 70), // pinned → NOT in folder-2
            conv(20, folderId = 1, updated = 200),
            conv(21, folderId = 1, updated = 150),
            conv(30, folderId = 2, updated = 120),
        )

        val sections = buildSessionSections(folders, convs)
        val beta = sections.first { it.id == "folder-1" }
        val alpha = sections.first { it.id == "folder-2" }

        assertEquals("Beta", (beta.kind as SectionKind.Folder).name)
        assertEquals(listOf(20, 21), beta.rows.map { it.conversation.id }) // newest-first
        assertTrue(beta.rows.all { it.folderName == null })               // header is the folder
        // The pinned conv 11 is excluded from its folder group; only conv 30 remains.
        assertEquals(listOf(30), alpha.rows.map { it.conversation.id })
    }

    @Test
    fun `empty folders keep a header so the section still shows`() {
        val sections = buildSessionSections(listOf(folder(1, "Solo")), emptyList())

        assertEquals(listOf("folder-1"), sections.map { it.id })
        assertEquals(0, sections.single().count)
    }

    @Test
    fun `no pinned or other section when there are none`() {
        val sections = buildSessionSections(
            listOf(folder(1, "A")),
            listOf(conv(1, folderId = 1, updated = 10)),
        )

        assertEquals(listOf("folder-1"), sections.map { it.id })
    }

    @Test
    fun `worktree folders nest under their workspace instead of sitting as peers`() {
        val folders = listOf(
            folder(1, "repo", sortOrder = 0),
            FolderDetail(id = 2, name = "repo-feat", path = "/p/2", sortOrder = 0, color = "#abc", parentId = 1, gitBranch = "feat"),
            folder(3, "other", sortOrder = 1),
        )
        val convs = listOf(
            conv(10, folderId = 1, updated = 100),
            conv(20, folderId = 2, updated = 90),
            conv(30, folderId = 3, updated = 80),
        )

        val sections = buildSessionSections(folders, convs)

        assertEquals(listOf("folder-1", "folder-3"), sections.map { it.id })
        val repo = sections.first { it.id == "folder-1" }
        val repoKind = repo.kind as SectionKind.Folder
        assertEquals("repo", repoKind.name)
        assertEquals(false, repoKind.isWorktree)
        assertEquals(0, repoKind.depth)
        assertEquals(listOf(10), repo.rows.map { it.conversation.id })
        assertEquals(listOf("folder-2"), repo.nested.map { it.id })
        val worktree = repo.nested.single()
        val wtKind = worktree.kind as SectionKind.Folder
        assertEquals(true, wtKind.isWorktree)
        assertEquals(1, wtKind.depth)
        assertEquals("repo", wtKind.workspaceName)
        assertEquals("repo / feat", worktree.breadcrumb)
        assertEquals(listOf(20), worktree.rows.map { it.conversation.id })
        assertEquals(1, worktree.rows.single().depth)
    }

    @Test
    fun `delegation children nest under their parent instead of appearing as siblings`() {
        val folders = listOf(folder(1, "repo"))
        val parent = conv(10, folderId = 1, updated = 200).copy(childCount = 1)
        val child = conv(11, folderId = 1, updated = 180).copy(parentId = 10)
        val sibling = conv(12, folderId = 1, updated = 160)

        val repo = buildSessionSections(folders, listOf(parent, child, sibling)).single()

        assertEquals(listOf(10, 12), repo.rows.map { it.conversation.id })
        assertEquals(listOf(11), repo.rows.first { it.conversation.id == 10 }.children.map { it.conversation.id })
        assertEquals(1, repo.rows.first { it.conversation.id == 10 }.children.single().depth)
        assertTrue(repo.rows.none { it.conversation.id == 11 })
    }

    @Test
    fun `orphan child without a loaded parent stays a top-level row`() {
        val folders = listOf(folder(1, "repo"))
        val orphan = conv(11, folderId = 1, updated = 180).copy(parentId = 99)

        val repo = buildSessionSections(folders, listOf(orphan)).single()

        assertEquals(listOf(11), repo.rows.map { it.conversation.id })
        assertEquals(0, repo.rows.single().depth)
    }

    @Test
    fun `orphan conversations fall into Other and resolve no folder tag`() {
        val sections = buildSessionSections(
            listOf(folder(1, "A")),
            listOf(conv(40, folderId = 99, updated = 300)),
        )
        val other = sections.first { it.id == "other" }

        assertTrue(other.kind is SectionKind.Other)
        assertEquals(listOf(40), other.rows.map { it.conversation.id })
        assertNull(other.rows.single().folderName) // unknown folder → no tag
    }

    @Test
    fun `search drops empty folders and keeps matching titles or agents`() {
        val folders = listOf(folder(1, "Alpha"), folder(2, "Beta"))
        val convs = listOf(
            conv(10, folderId = 1, updated = 100).copy(title = "Fix login"),
            conv(11, folderId = 1, updated = 90).copy(title = "Unrelated"),
            conv(20, folderId = 2, updated = 80).copy(title = "Docs"),
            conv(30, folderId = 1, updated = 70, pinnedAt = 10).copy(title = "Login follow-up"),
        )

        val sections = buildSessionSections(folders, convs, search = "login")

        assertEquals(listOf("pinned", "folder-1"), sections.map { it.id })
        assertEquals(listOf(30), sections.first { it.id == "pinned" }.rows.map { it.conversation.id })
        assertEquals(listOf(10), sections.first { it.id == "folder-1" }.rows.map { it.conversation.id })
    }

    @Test
    fun `search keeps a parent when only a child matches`() {
        val folders = listOf(folder(1, "repo"))
        val parent = conv(10, folderId = 1, updated = 200).copy(title = "Parent", childCount = 1)
        val child = conv(11, folderId = 1, updated = 180).copy(title = "delegate login", parentId = 10)

        val repo = buildSessionSections(folders, listOf(parent, child), search = "login").single()

        assertEquals(listOf(10), repo.rows.map { it.conversation.id })
        assertEquals(listOf(11), repo.rows.single().children.map { it.conversation.id })
    }
}
