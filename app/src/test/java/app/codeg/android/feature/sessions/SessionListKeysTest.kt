package app.codeg.android.feature.sessions

import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.ConversationStatus
import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.FolderDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Compose LazyColumn crashes if two items share a key. Chats and Recent both
 * list the same conversation, so keys must be scoped by section (and folder).
 */
class SessionListKeysTest {

    private fun conv(id: Int, folderId: Int, updated: Long) = ConversationSummary(
        id = id,
        folderId = folderId,
        title = "c$id",
        agentType = AgentType.CLAUDE_CODE,
        status = ConversationStatus.COMPLETED,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.ofEpochSecond(updated),
    )

    private fun folder(id: Int, name: String, parentId: Int? = null) =
        FolderDetail(id = id, name = name, path = "/p/$id", parentId = parentId)

    @Test
    fun `chats and recent sharing a conversation still have unique lazy keys`() {
        val sections = buildSessionSections(
            folders = listOf(folder(1, "repo")),
            conversations = listOf(conv(10, folderId = 1, updated = 100)),
        )
        val keys = sessionListKeys(sections)
        assertTrue(keys.contains("h-chats"))
        assertTrue(keys.contains("h-recent"))
        val rowKeys = keys.filter { it.startsWith("row-") }
        assertEquals(2, rowKeys.size)
        assertEquals(rowKeys.size, rowKeys.toSet().size)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `expanded folder sessions do not collide with chats or recent`() {
        val sections = buildSessionSections(
            folders = listOf(folder(1, "repo")),
            conversations = listOf(conv(10, folderId = 1, updated = 100)),
        )
        val keys = sessionListKeys(sections, expandedFolders = setOf(1))
        val rowKeys = keys.filter { it.startsWith("row-") }
        assertEquals(3, rowKeys.size)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `nested worktrees keep unique folder keys`() {
        val sections = buildSessionSections(
            folders = listOf(
                folder(1, "repo"),
                folder(2, "feat", parentId = 1),
            ),
            conversations = listOf(
                conv(10, folderId = 1, updated = 100),
                conv(20, folderId = 2, updated = 90),
            ),
        )
        val keys = sessionListKeys(sections, expandedFolders = setOf(1, 2))
        val folderKeys = keys.filter { it.startsWith("folder-") }
        assertEquals(listOf("folder-1", "folder-2").sorted(), folderKeys.sorted())
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `collapsed sections omit their row keys`() {
        val sections = buildSessionSections(
            folders = listOf(folder(1, "repo")),
            conversations = listOf(conv(10, folderId = 1, updated = 100)),
        )
        val keys = sessionListKeys(sections, collapsed = setOf("chats", "recent", "folders"))
        assertTrue(keys.none { it.startsWith("row-") || it.startsWith("folder-") })
        assertTrue(keys.contains("h-chats"))
        assertTrue(keys.contains("h-recent"))
        assertTrue(keys.contains("h-folders"))
    }

    @Test
    fun `delegation children use distinct keys from the parent`() {
        val parent = conv(10, folderId = 1, updated = 200).copy(childCount = 1)
        val child = conv(11, folderId = 1, updated = 180).copy(parentId = 10)
        val sections = buildSessionSections(
            folders = listOf(folder(1, "repo")),
            conversations = listOf(parent, child),
        )
        val keys = sessionListKeys(sections, collapsedChildren = emptySet())
        val rowKeys = keys.filter { it.startsWith("row-") }
        assertTrue(rowKeys.any { it.endsWith("-10") })
        assertTrue(rowKeys.any { it.endsWith("-11") })
        assertEquals(keys.size, keys.toSet().size)
    }
}
