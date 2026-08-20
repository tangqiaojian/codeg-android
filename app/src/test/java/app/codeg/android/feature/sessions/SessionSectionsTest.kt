package app.codeg.android.feature.sessions

import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.ConversationStatus
import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.FolderVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Locks the desktop-matching Chats list: Folders (workspace rows) / Chats
 * (conversation rows) / Recent, with optional Pinned and Other.
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

    private fun folder(id: Int, name: String, sortOrder: Int = 0, color: String = "", isChat: Boolean = false) =
        FolderDetail(id = id, name = name, path = "/p/$id", sortOrder = sortOrder, color = color, isChat = isChat)

    @Test
    fun `all scope is pinned, folders, chats, recent, other`() {
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
            conv(40, folderId = 99, updated = 300),
        )
        val sections = buildSessionSections(folders, convs)
        assertEquals(listOf("pinned", "folders", "chats", "recent", "other"), sections.map { it.id })
        val folderNames = sections.first { it.id == "folders" }.folders.map { it.folder.name }
        assertEquals(listOf("Alpha", "Beta"), folderNames)
    }

    @Test
    fun `Chat-named folders are not imported workspaces even without isChat`() {
        val folders = listOf(
            folder(1, "codeg-android"),
            folder(2, "Chat"),
            folder(3, "Chat"),
        )
        val convs = listOf(
            conv(10, folderId = 1, updated = 1),
            conv(20, folderId = 2, updated = 2),
            conv(21, folderId = 3, updated = 3),
        )
        val sections = buildSessionSections(folders, convs, scope = SessionListScope.WORKSPACES)
        assertEquals(listOf("codeg-android"), sections.single().folders.map { it.folder.name })
        val chats = buildSessionSections(folders, convs, scope = SessionListScope.CHATS)
        assertEquals(listOf(21, 20), chats.single().rows.map { it.conversation.id })
    }

    @Test
    fun `Chat children of a workspace are not nested as extra folder rows`() {
        val folders = listOf(
            folder(1, "repo"),
            FolderDetail(id = 2, name = "Chat", path = "/p/2", parentId = 1),
        )
        val convs = listOf(conv(10, folderId = 1, updated = 1), conv(20, folderId = 2, updated = 2))
        val foldersSection = buildSessionSections(folders, convs).first { it.id == "folders" }
        val repo = foldersSection.folders.single()
        assertTrue(repo.children.isEmpty())
        assertEquals(listOf(20), buildSessionSections(folders, convs).first { it.id == "chats" }.rows.map { it.conversation.id })
    }

    @Test
    fun `chat-folder conversations are flat chats, not a folder group`() {
        val folders = listOf(
            folder(1, "repo"),
            folder(2, "Chat", isChat = true),
        )
        val convs = listOf(
            conv(10, folderId = 1, updated = 1),
            conv(20, folderId = 2, updated = 2),
            conv(21, folderId = 2, updated = 3),
        )
        val sections = buildSessionSections(folders, convs)
        val chats = sections.first { it.id == "chats" }
        assertEquals(listOf(21, 20), chats.rows.map { it.conversation.id })
        assertTrue(sections.first { it.id == "folders" }.folders.none { FolderVisibility.isChatFolder(it.folder) })
        assertEquals(listOf(10), sections.first { it.id == "folders" }.folders.single().conversations.map { it.conversation.id })
    }

    @Test
    fun `without chat folders every conversation is a chat so they are not trapped under workspaces`() {
        val folders = listOf(folder(1, "repo"))
        val convs = listOf(conv(10, folderId = 1, updated = 1), conv(11, folderId = 1, updated = 2))
        val chats = buildSessionSections(folders, convs).first { it.id == "chats" }
        assertEquals(listOf(11, 10), chats.rows.map { it.conversation.id })
    }

    @Test
    fun `recent is newest unpinned conversations and is omitted while searching`() {
        val folders = listOf(folder(1, "repo"))
        val convs = (1..12).map { conv(it, folderId = 1, updated = it.toLong()) }
        val sections = buildSessionSections(folders, convs)
        val recent = sections.first { it.id == "recent" }
        assertEquals((12 downTo 3).toList(), recent.rows.map { it.conversation.id })

        val searched = buildSessionSections(folders, convs, search = "c12")
        assertTrue(searched.none { it.id == "recent" })
        assertEquals(listOf(12), searched.first { it.id == "chats" }.rows.map { it.conversation.id })
    }

    @Test
    fun `session worktrees are omitted from the Folders bucket`() {
        val folders = listOf(
            folder(1, "codeg-android"),
            folder(2, "e272c76627634921bd22a3da1ab2fcb7"),
            folder(3, "codeg-android-agent-mentions"),
            folder(4, "hxzh-dev-1"),
        )
        val convs = listOf(
            conv(10, folderId = 1, updated = 1),
            conv(20, folderId = 2, updated = 2),
            conv(30, folderId = 3, updated = 3),
            conv(40, folderId = 4, updated = 4),
        )
        val names = buildSessionSections(folders, convs).first { it.id == "folders" }.folders.map { it.folder.name }
        assertEquals(listOf("codeg-android", "hxzh-dev-1"), names)
    }

    @Test
    fun `chats scope hides workspace folders`() {
        val folders = listOf(folder(1, "repo"), folder(2, "Chat", isChat = true))
        val convs = listOf(conv(10, folderId = 1, updated = 1), conv(20, folderId = 2, updated = 2))
        val sections = buildSessionSections(folders, convs, scope = SessionListScope.CHATS)
        assertEquals(listOf("chats"), sections.map { it.id })
        assertEquals(listOf(20), sections.single().rows.map { it.conversation.id })
    }

    @Test
    fun `workspaces scope is only the folder bucket`() {
        val folders = listOf(folder(1, "repo"), folder(2, "Chat", isChat = true))
        val convs = listOf(conv(10, folderId = 1, updated = 1), conv(20, folderId = 2, updated = 2))
        val sections = buildSessionSections(folders, convs, scope = SessionListScope.WORKSPACES)
        assertEquals(listOf("folders"), sections.map { it.id })
        assertEquals(listOf("repo"), sections.single().folders.map { it.folder.name })
    }

    @Test
    fun `worktrees nest under their workspace folder entry`() {
        val folders = listOf(
            folder(1, "repo", sortOrder = 0),
            FolderDetail(id = 2, name = "repo-feat", path = "/p/2", parentId = 1, gitBranch = "feat"),
            folder(3, "other", sortOrder = 1),
        )
        val convs = listOf(
            conv(10, folderId = 1, updated = 100),
            conv(20, folderId = 2, updated = 90),
            conv(30, folderId = 3, updated = 80),
        )
        val foldersSection = buildSessionSections(folders, convs).first { it.id == "folders" }
        assertEquals(listOf(1, 3), foldersSection.folders.map { it.folder.id })
        val repo = foldersSection.folders.first { it.folder.id == 1 }
        assertEquals(listOf(2), repo.children.map { it.folder.id })
        assertEquals(listOf(20), repo.children.single().conversations.map { it.conversation.id })
        assertEquals(1, repo.children.single().depth)
    }

    @Test
    fun `pinned sits at the top and is excluded from chats`() {
        val folders = listOf(folder(1, "repo"))
        val convs = listOf(
            conv(10, folderId = 1, updated = 100, pinnedAt = 50),
            conv(11, folderId = 1, updated = 90),
        )
        val sections = buildSessionSections(folders, convs)
        val pinned = sections.first { it.id == "pinned" }
        assertEquals(listOf(10), pinned.rows.map { it.conversation.id })
        assertEquals("repo", pinned.rows.single().folderName)
        assertTrue(sections.first { it.id == "chats" }.rows.none { it.conversation.id == 10 })
    }

    @Test
    fun `delegation children nest under the parent chat row`() {
        val folders = listOf(folder(1, "repo"))
        val parent = conv(10, folderId = 1, updated = 200).copy(childCount = 1)
        val child = conv(11, folderId = 1, updated = 180).copy(parentId = 10)
        val sibling = conv(12, folderId = 1, updated = 160)
        val chats = buildSessionSections(folders, listOf(parent, child, sibling)).first { it.id == "chats" }
        assertEquals(listOf(10, 12), chats.rows.map { it.conversation.id })
        assertEquals(listOf(11), chats.rows.first { it.conversation.id == 10 }.children.map { it.conversation.id })
    }

    @Test
    fun `orphan child without a loaded parent stays a top-level chat`() {
        val folders = listOf(folder(1, "repo"))
        val orphan = conv(11, folderId = 1, updated = 180).copy(parentId = 99)
        val chats = buildSessionSections(folders, listOf(orphan)).first { it.id == "chats" }
        assertEquals(listOf(11), chats.rows.map { it.conversation.id })
        assertEquals(0, chats.rows.single().depth)
    }

    @Test
    fun `orphan conversations fall into Other`() {
        val sections = buildSessionSections(
            listOf(folder(1, "A")),
            listOf(conv(40, folderId = 99, updated = 300)),
        )
        val other = sections.first { it.id == "other" }
        assertTrue(other.kind is SectionKind.Other)
        assertEquals(listOf(40), other.rows.map { it.conversation.id })
        assertNull(other.rows.single().folderName)
    }

    @Test
    fun `empty workspace folders still appear so the Folders section is not blank`() {
        val sections = buildSessionSections(listOf(folder(1, "Solo")), emptyList())
        assertEquals(listOf("folders"), sections.map { it.id })
        assertEquals("Solo", sections.single().folders.single().folder.name)
        assertEquals(0, sections.single().folders.single().conversations.size)
    }

    @Test
    fun `search keeps a parent when only a child matches`() {
        val folders = listOf(folder(1, "repo"))
        val parent = conv(10, folderId = 1, updated = 200).copy(title = "Parent", childCount = 1)
        val child = conv(11, folderId = 1, updated = 180).copy(title = "delegate login", parentId = 10)
        val chats = buildSessionSections(folders, listOf(parent, child), search = "login").first { it.id == "chats" }
        assertEquals(listOf(10), chats.rows.map { it.conversation.id })
        assertEquals(listOf(11), chats.rows.single().children.map { it.conversation.id })
    }
}
