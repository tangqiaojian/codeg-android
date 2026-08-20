package app.codeg.android.feature.sessions

import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.FolderDetail

/** Optimistic local updates after deleting a session or closing a folder. */
object SessionListMutations {

    data class FolderRemoval(
        val folders: List<FolderDetail>,
        val conversations: List<ConversationSummary>,
    )

    /** Drop [id] and any delegation descendants still in [conversations]. */
    fun withoutConversation(
        conversations: List<ConversationSummary>,
        id: Int,
    ): List<ConversationSummary> {
        val drop = descendantIds(conversations, id)
        return conversations.filter { it.id !in drop }
    }

    /**
     * Drop [folderId] plus nested worktrees, and conversations that lived in those
     * folders. Files on disk are not touched — this matches server `close_folder`.
     */
    fun withoutFolder(
        folders: List<FolderDetail>,
        conversations: List<ConversationSummary>,
        folderId: Int,
    ): FolderRemoval {
        val drop = nestedFolderIds(folders, folderId)
        return FolderRemoval(
            folders = folders.filter { it.id !in drop },
            conversations = conversations.filter { it.folderId !in drop },
        )
    }

    private fun descendantIds(conversations: List<ConversationSummary>, rootId: Int): Set<Int> {
        val drop = hashSetOf(rootId)
        var growing = true
        while (growing) {
            growing = false
            for (conversation in conversations) {
                val parent = conversation.parentId ?: continue
                if (parent in drop && conversation.id !in drop) {
                    drop += conversation.id
                    growing = true
                }
            }
        }
        return drop
    }

    private fun nestedFolderIds(folders: List<FolderDetail>, rootId: Int): Set<Int> {
        val drop = hashSetOf(rootId)
        var growing = true
        while (growing) {
            growing = false
            for (folder in folders) {
                val parent = folder.parentId ?: continue
                if (parent in drop && folder.id !in drop) {
                    drop += folder.id
                    growing = true
                }
            }
        }
        return drop
    }
}
