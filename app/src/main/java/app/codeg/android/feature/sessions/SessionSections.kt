package app.codeg.android.feature.sessions

import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.FolderVisibility

/**
 * One rendered group in the Chats list, fully sorted and filtered.
 *
 * Built off the main thread by [buildSessionSections] (exposed as
 * [SessionListViewModel.sections]) so the O(n·log n) grouping/sorting never runs
 * during composition — the `LazyColumn` just iterates these pre-sorted rows, which
 * is what keeps a very large session list scrolling smoothly. Mirrors the iOS
 * `SessionListView` sectioning (Pinned / per-folder / Other), realized on Android
 * as native collapsible sections rather than zoom-drill cards.
 */
data class SessionSection(
    /** Stable id used for the collapse set and the header's LazyColumn key. */
    val id: String,
    val kind: SectionKind,
    val rows: List<SessionRowItem>,
    /** Worktree (or other child-folder) groups nested under a workspace. */
    val nested: List<SessionSection> = emptyList(),
    val depth: Int = 0,
    val breadcrumb: String? = null,
) {
    val count: Int get() = rows.size + nested.sumOf { it.count }
}

/** What a [SessionSection] represents — drives its header icon, label, and tint. */
sealed interface SectionKind {
    /** Pinned conversations across all folders. */
    data object Pinned : SectionKind

    /**
     * One folder's non-pinned conversations. Workspace (top-level) folders use
     * [depth] 0; worktrees nest under their parent with [depth] 1 and a
     * [workspaceName] breadcrumb.
     */
    data class Folder(
        val name: String,
        val colorHex: String,
        val folderId: Int = 0,
        val workspaceName: String? = null,
        val isWorktree: Boolean = false,
        val gitBranch: String? = null,
        val path: String = "",
        val depth: Int = 0,
    ) : SectionKind

    /** Non-pinned conversations whose folder isn't in the folder list (orphans). */
    data object Other : SectionKind
}

/** One conversation row, with its cross-folder tag pre-resolved. */
data class SessionRowItem(
    val conversation: ConversationSummary,
    /** Dim folder tag shown on cross-folder rows (Pinned/Other); `null` inside a
     * folder group, where the folder is already the header. */
    val folderName: String?,
    val depth: Int = 0,
    val children: List<SessionRowItem> = emptyList(),
)

/** Which folder kind the Chats list is currently showing. */
enum class SessionListScope {
    ALL,
    WORKSPACES,
    CHATS,
    ;

    fun includes(folder: FolderDetail): Boolean = when (this) {
        ALL -> true
        WORKSPACES -> !folder.isChat
        CHATS -> folder.isChat
    }
}

/**
 * Compose [SessionGrouping]'s pure accessors into the ordered section list shown by
 * the Chats screen: Pinned (if any) → chat folders (so they are not buried under
 * workspaces) → workspace folders → Other (if any). [scope] hides the other kind
 * so the list can switch without scrolling.
 *
 * `folderName` is resolved here, once, for the cross-folder sections only, so each
 * row needs no map lookup at compose time. Pure and allocation-light; the view model
 * runs it on `Dispatchers.Default`.
 */
fun buildSessionSections(
    folders: List<FolderDetail>,
    conversations: List<ConversationSummary>,
    search: String = "",
    scope: SessionListScope = SessionListScope.ALL,
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
    val out = ArrayList<SessionSection>()

    fun inScope(conversation: ConversationSummary): Boolean {
        val folder = folderById[conversation.folderId] ?: return scope == SessionListScope.ALL
        return scope.includes(folder)
    }

    val pinned = SessionGrouping.pinned(topLevel).filter(::inScope)
    if (pinned.isNotEmpty()) {
        out += SessionSection(
            id = "pinned",
            kind = SectionKind.Pinned,
            rows = pinned.map { attachChildren(it, childrenByParent, folderNames[it.folderId], depth = 0) },
        )
    }

    val unpinned = topLevel.filter { !it.isPinned }
    val unpinnedByFolder = unpinned.groupBy { it.folderId }
    val worktreesByParent = folders.filter { it.parentId != null }.groupBy { it.parentId!! }
    val knownFolderIds = folders.map { it.id }.toSet()

    val roots = folders.filter { it.parentId == null && scope.includes(it) }
    val orderedRoots = when (scope) {
        SessionListScope.ALL ->
            SessionGrouping.sortedFolders(roots.filter { it.isChat }) +
                SessionGrouping.sortedFolders(roots.filter { !it.isChat })
        else -> SessionGrouping.sortedFolders(roots)
    }
    for (folder in orderedRoots) {
        val section = folderSection(
            folder = folder,
            folders = folders,
            unpinnedByFolder = unpinnedByFolder,
            worktreesByParent = worktreesByParent,
            childrenByParent = childrenByParent,
            depth = 0,
            searching = searching,
        )
        if (section != null) out += section
    }

    val orphanWorktrees = folders.filter {
        it.parentId != null && it.parentId !in knownFolderIds && scope.includes(it)
    }
    for (folder in SessionGrouping.sortedFolders(orphanWorktrees)) {
        val section = folderSection(
            folder = folder,
            folders = folders,
            unpinnedByFolder = unpinnedByFolder,
            worktreesByParent = worktreesByParent,
            childrenByParent = childrenByParent,
            depth = 0,
            searching = searching,
        )
        if (section != null) out += section
    }

    val other = if (scope == SessionListScope.ALL) {
        SessionGrouping.ungrouped(folders, unpinned)
    } else {
        emptyList()
    }
    if (other.isNotEmpty()) {
        out += SessionSection(
            id = "other",
            kind = SectionKind.Other,
            rows = other.map { attachChildren(it, childrenByParent, folderNames[it.folderId], depth = 0) },
        )
    }

    return out
}

private fun folderSection(
    folder: FolderDetail,
    folders: List<FolderDetail>,
    unpinnedByFolder: Map<Int, List<ConversationSummary>>,
    worktreesByParent: Map<Int, List<FolderDetail>>,
    childrenByParent: Map<Int, List<ConversationSummary>>,
    depth: Int,
    searching: Boolean,
): SessionSection? {
    val convs = (unpinnedByFolder[folder.id] ?: emptyList()).sortedByDescending { it.updatedAt }
    val isWorktree = FolderVisibility.isWorktree(folder)
    val workspaceName = if (isWorktree) FolderVisibility.displayName(folder, folders) else null
    val nested = SessionGrouping.sortedFolders(worktreesByParent[folder.id] ?: emptyList()).mapNotNull { child ->
        folderSection(
            folder = child,
            folders = folders,
            unpinnedByFolder = unpinnedByFolder,
            worktreesByParent = worktreesByParent,
            childrenByParent = childrenByParent,
            depth = depth + 1,
            searching = searching,
        )
    }
    if (searching && convs.isEmpty() && nested.isEmpty()) return null
    return SessionSection(
        id = "folder-${folder.id}",
        kind = SectionKind.Folder(
            name = folder.name,
            colorHex = folder.color,
            folderId = folder.id,
            workspaceName = workspaceName,
            isWorktree = isWorktree,
            gitBranch = folder.gitBranch,
            path = folder.path,
            depth = depth,
        ),
        rows = convs.map { attachChildren(it, childrenByParent, folderName = null, depth = depth) },
        nested = nested,
        depth = depth,
        breadcrumb = if (isWorktree) FolderVisibility.breadcrumb(folder, folders) else folder.name,
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
