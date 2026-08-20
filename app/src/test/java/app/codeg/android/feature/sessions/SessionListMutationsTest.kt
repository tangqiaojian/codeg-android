package app.codeg.android.feature.sessions

import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.ConversationStatus
import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.FolderDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionListMutationsTest {

    private fun conv(id: Int, folderId: Int, parentId: Int? = null) = ConversationSummary(
        id = id,
        folderId = folderId,
        title = "c$id",
        agentType = AgentType.CLAUDE_CODE,
        status = ConversationStatus.COMPLETED,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        parentId = parentId,
    )

    private fun folder(id: Int, name: String, parentId: Int? = null) =
        FolderDetail(id = id, name = name, path = "/p/$id", parentId = parentId)

    @Test
    fun `deleting a conversation also drops its delegation children`() {
        val conversations = listOf(
            conv(10, folderId = 1),
            conv(11, folderId = 1, parentId = 10),
            conv(12, folderId = 1),
        )
        val remaining = SessionListMutations.withoutConversation(conversations, 10)
        assertEquals(listOf(12), remaining.map { it.id })
    }

    @Test
    fun `closing a workspace drops nested worktrees and their conversations`() {
        val folders = listOf(
            folder(1, "repo"),
            folder(2, "feat", parentId = 1),
            folder(3, "other"),
        )
        val conversations = listOf(
            conv(10, folderId = 1),
            conv(20, folderId = 2),
            conv(30, folderId = 3),
        )
        val result = SessionListMutations.withoutFolder(folders, conversations, folderId = 1)
        assertEquals(listOf(3), result.folders.map { it.id })
        assertEquals(listOf(30), result.conversations.map { it.id })
    }

    @Test
    fun `closing a worktree leaves the parent workspace`() {
        val folders = listOf(folder(1, "repo"), folder(2, "feat", parentId = 1))
        val conversations = listOf(conv(10, folderId = 1), conv(20, folderId = 2))
        val result = SessionListMutations.withoutFolder(folders, conversations, folderId = 2)
        assertEquals(listOf(1), result.folders.map { it.id })
        assertEquals(listOf(10), result.conversations.map { it.id })
        assertTrue(result.folders.none { it.id == 2 })
    }
}
