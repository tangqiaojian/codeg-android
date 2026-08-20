package app.codeg.android.feature.sessions

import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.FolderVisibility

/** How many conversations the Recent bucket keeps, matching the desktop sidebar. */
internal const val RECENT_LIMIT = 10

/**
 * One rendered group in the Chats list, fully sorted and filtered.
 *
 * Desktop sidebar buckets: Folders (workspace rows) / Chats (conversation
 * rows) / Recent. Pinned and Other stay as extra groups when they have rows.
 */
data class SessionSection(
    val id: String,
    val kind: SectionKind,
    val rows: List<SessionRowItem> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
    val depth: Int = 0,
    val breadcrumb: String? = null,
) {
    val count: Int
        get() = when (kind) {
            SectionKind.Folders -> folders.sumOf { it.entryCount }
            else -> rows.size
        }
}

/** A workspace/worktree row in the Folders bucket. */
data class FolderEntry(
    val folder: FolderDetail,
    val depth: Int = 0,
    val breadcrumb: String? = null,
    val conversations: List<SessionRowItem> = emptyList(),
    val children: List<FolderEntry> = emptyList(),
) {
    val entryCount: Int get() = 1 + children.sumOf { it.entryCount }
    val sessionCount: Int get() = conversations.size + children.sumOf { it.sessionCount }
}

sealed interface SectionKind {
    data object Pinned : SectionKind
    data object Folders : SectionKind
    data object Chats : SectionKind
    data object Recent : SectionKind
    data object Other : SectionKind
}

data class SessionRowItem(
    val conversation: ConversationSummary,
    val folderName: String?,
    val depth: Int = 0,
    val children: List<SessionRowItem> = emptyList(),
)

enum class SessionListScope {
    ALL,
    WORKSPACES,
    CHATS,
    ;

    fun includesWorkspace(): Boolean = this != CHATS
    fun includesChats(): Boolean = this != WORKSPACES
}

fun buildSessionSections(
    folders: List<FolderDetail>,
    conversations: List<ConversationSummary>,
    search: String = "",
    scope: SessionListScope = SessionListScope.ALL,
    openFolderIds: Set<Int>? = null,
): List<SessionSection> {
    val query = search.trim()
    val searching = query.isNotEmpty()
    val visible = if (searching) SessionGrouping.matchingWithParents(conversations, query) else conversations
    val folderNames = SessionGrouping.folderNames(folders)
    val folderById = folders.associateBy { it.id }
    val knownIds = visible.map { it.id }.toSet()
    val childrenByParent = visible
        .filter { it.parentId != null && it.parentId in knownIds }
        .groupBy { it.parentId!! }
    val topLevel = visible.filter { it.parentId == null || it.parentId !in knownIds }
    val hasChatFolders = folders.any { FolderVisibility.isChatFolder(it) }
    val knownFolderIds = folders.map { it.id }.toSet()
    val unpinned = topLevel.filter { !it.isPinned }
    val unpinnedByFolder = unpinned.groupBy { it.folderId }
    val worktreesByParent = folders
        .filter { it.parentId != null && !FolderVisibility.isChatFolder(it) }
        .groupBy { it.parentId!! }
    val out = ArrayList<SessionSection>()

    fun isChatConversation(conversation: ConversationSummary): Boolean {
        val folder = folderById[conversation.folderId] ?: return false
        return if (hasChatFolders) FolderVisibility.isChatFolder(folder) else true
    }

    if (scope.includesChats()) {
        val pinned = SessionGrouping.pinned(topLevel).filter(::isChatConversation)
        if (pinned.isNotEmpty()) {
            out += SessionSection(
                id = "pinned",
                kind = SectionKind.Pinned,
                rows = pinned.map { attachChildren(it, childrenByParent, folderNames[it.folderId], depth = 0) },
            )
        }
    }

    if (scope.includesWorkspace()) {
        val roots = SessionGrouping.sortedFolders(FolderVisibility.filterProjectFolders(folders, openFolderIds))
        val entries = roots.mapNotNull { folder ->
            folderEntry(
                folder = folder,
                folders = folders,
                unpinnedByFolder = unpinnedByFolder,
                worktreesByParent = worktreesByParent,
                childrenByParent = childrenByParent,
                depth = 0,
                query = query,
            )
        }
        if (entries.isNotEmpty()) {
            out += SessionSection(id = "folders", kind = SectionKind.Folders, folders = entries)
        }
    }

    if (scope.includesChats()) {
        val chatRows = unpinned
            .filter(::isChatConversation)
            .sortedByDescending { it.updatedAt }
            .map { attachChildren(it, childrenByParent, folderName = if (hasChatFolders) null else folderNames[it.folderId], depth = 0) }
        if (chatRows.isNotEmpty()) {
            out += SessionSection(id = "chats", kind = SectionKind.Chats, rows = chatRows)
        }
    }

    if (scope == SessionListScope.ALL && !searching) {
        val recent = unpinned
            .sortedByDescending { it.updatedAt }
            .take(RECENT_LIMIT)
            .map { attachChildren(it, childrenByParent, folderNames[it.folderId], depth = 0) }
        if (recent.isNotEmpty()) {
            out += SessionSection(id = "recent", kind = SectionKind.Recent, rows = recent)
        }
    }

    if (scope == SessionListScope.ALL) {
        val other = SessionGrouping.ungrouped(folders, unpinned)
        if (other.isNotEmpty()) {
            out += SessionSection(
                id = "other",
                kind = SectionKind.Other,
                rows = other.map { attachChildren(it, childrenByParent, folderNames[it.folderId], depth = 0) },
            )
        }
    }

    // Orphan worktrees (parent missing from the folder list) still belong in Folders.
    if (scope.includesWorkspace()) {
        val orphans = SessionGrouping.sortedFolders(
            folders.filter {
                it.parentId != null && it.parentId !in knownFolderIds && !FolderVisibility.isChatFolder(it)
            },
        ).mapNotNull { folder ->
            folderEntry(
                folder = folder,
                folders = folders,
                unpinnedByFolder = unpinnedByFolder,
                worktreesByParent = worktreesByParent,
                childrenByParent = childrenByParent,
                depth = 0,
                query = query,
            )
        }
        if (orphans.isNotEmpty()) {
            val existing = out.indexOfFirst { it.id == "folders" }
            if (existing >= 0) {
                val current = out[existing]
                out[existing] = current.copy(folders = current.folders + orphans)
            } else {
                out += SessionSection(id = "folders", kind = SectionKind.Folders, folders = orphans)
            }
        }
    }

    return out
}

private fun folderEntry(
    folder: FolderDetail,
    folders: List<FolderDetail>,
    unpinnedByFolder: Map<Int, List<ConversationSummary>>,
    worktreesByParent: Map<Int, List<FolderDetail>>,
    childrenByParent: Map<Int, List<ConversationSummary>>,
    depth: Int,
    query: String,
): FolderEntry? {
    val convs = (unpinnedByFolder[folder.id] ?: emptyList()).sortedByDescending { it.updatedAt }
    val isWorktree = FolderVisibility.isWorktree(folder)
    val workspaceName = if (isWorktree) FolderVisibility.displayName(folder, folders) else null
    val children = SessionGrouping.sortedFolders(worktreesByParent[folder.id] ?: emptyList()).mapNotNull { child ->
        folderEntry(
            folder = child,
            folders = folders,
            unpinnedByFolder = unpinnedByFolder,
            worktreesByParent = worktreesByParent,
            childrenByParent = childrenByParent,
            depth = depth + 1,
            query = query,
        )
    }
    val nameHit = query.isEmpty() ||
        folder.name.contains(query, ignoreCase = true) ||
        folder.path.contains(query, ignoreCase = true) ||
        (folder.gitBranch?.contains(query, ignoreCase = true) == true) ||
        (workspaceName?.contains(query, ignoreCase = true) == true)
    if (query.isNotEmpty() && !nameHit && convs.isEmpty() && children.isEmpty()) return null
    return FolderEntry(
        folder = folder,
        depth = depth,
        breadcrumb = if (isWorktree) FolderVisibility.breadcrumb(folder, folders) else folder.name,
        conversations = convs.map { attachChildren(it, childrenByParent, folderName = null, depth = depth + 1) },
        children = children,
    )
}

private fun attachChildren(
    conversation: ConversationSummary,
    childrenByParent: Map<Int, List<ConversationSummary>>,
    folderName: String?,
    depth: Int,
): SessionRowItem {
    val children = (childrenByParent[conversation.id] ?: emptyList())
        .sortedByDescending { it.createdAt }
        .map { child -> attachChildren(child, childrenByParent, folderName = null, depth = depth + 1) }
    return SessionRowItem(
        conversation = conversation,
        folderName = folderName,
        depth = depth,
        children = children,
    )
}

/** LazyColumn key for a conversation row, scoped so Chats/Recent/folder copies never collide. */
fun sessionRowKey(sectionId: String, folderId: Int?, conversationId: Int): String =
    "row-$sectionId-${folderId ?: "root"}-$conversationId"

fun sessionFolderKey(folderId: Int): String = "folder-$folderId"

fun sessionHeaderKey(sectionId: String): String = "h-$sectionId"

/**
 * Every LazyColumn key the session list will emit for this fold/expand state.
 * Used to lock uniqueness: Compose crashes if Chats and Recent reuse `row-{id}`.
 */
fun sessionListKeys(
    sections: List<SessionSection>,
    collapsed: Set<String> = emptySet(),
    collapsedChildren: Set<Int> = emptySet(),
    expandedFolders: Set<Int> = emptySet(),
    includeRefreshError: Boolean = false,
): List<String> {
    val keys = ArrayList<String>()
    if (includeRefreshError) keys += "refresh-error"
    for (section in sections) {
        keys += sessionHeaderKey(section.id)
        if (section.id in collapsed) continue
        if (section.folders.isNotEmpty()) {
            appendFolderKeys(keys, section.id, section.folders, collapsedChildren, expandedFolders)
        } else {
            appendRowKeys(keys, section.id, folderId = null, section.rows, collapsedChildren)
        }
    }
    return keys
}

private fun appendFolderKeys(
    keys: MutableList<String>,
    sectionId: String,
    folders: List<FolderEntry>,
    collapsedChildren: Set<Int>,
    expandedFolders: Set<Int>,
) {
    for (entry in folders) {
        keys += sessionFolderKey(entry.folder.id)
        if (entry.folder.id in expandedFolders) {
            appendRowKeys(keys, sectionId, entry.folder.id, entry.conversations, collapsedChildren)
        }
        appendFolderKeys(keys, sectionId, entry.children, collapsedChildren, expandedFolders)
    }
}

private fun appendRowKeys(
    keys: MutableList<String>,
    sectionId: String,
    folderId: Int?,
    rows: List<SessionRowItem>,
    collapsedChildren: Set<Int>,
) {
    for (row in rows) {
        keys += sessionRowKey(sectionId, folderId, row.conversation.id)
        val expanded = row.children.isNotEmpty() && row.conversation.id !in collapsedChildren
        if (expanded) {
            appendRowKeys(keys, sectionId, folderId, row.children, collapsedChildren)
        }
    }
}
