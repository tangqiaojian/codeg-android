package app.codeg.android.core.model

/**
 * Pure folder-visibility + branch-switch routing, ported 1:1 from iOS
 * `FolderVisibility` (itself a port of the codeg web client's
 * `folder-display.ts` / `branch-switch.ts`).
 *
 * Worktree folders carry a [FolderDetail.parentId] (their root repo). These
 * helpers are deterministic (no I/O), so the session-detail view model can share
 * them and they're trivially unit-testable.
 */
object FolderVisibility {

    /** A folder is a worktree of a root repo when it has a parent. */
    fun isWorktree(folder: FolderDetail): Boolean = folder.parentId != null

    /**
     * Top-level folders only ([FolderDetail.parentId] null) — the new-session
     * folder picker must not target a worktree directly.
     */
    fun filterTopLevel(folders: List<FolderDetail>): List<FolderDetail> =
        folders.filter { it.parentId == null }

    /**
     * Chat containers the server auto-creates (often named "Chat"). They are
     * not imported workspaces and must not appear in the Folders bucket.
     */
    fun isChatFolder(folder: FolderDetail): Boolean =
        folder.isChat || folder.name.equals("Chat", ignoreCase = true)

    /**
     * Desktop sidebar Folders: imported workspaces only. Drops chats, nested
     * worktrees, hex session-id dirs, and `{repo}-agent-*` worktrees that the
     * server registers as top-level without [FolderDetail.parentId].
     *
     * [openFolderIds] is `list_open_folder_details` when the caller has it;
     * null means "don't intersect" (tests / fallbacks).
     */
    fun filterProjectFolders(
        folders: List<FolderDetail>,
        openFolderIds: Set<Int>? = null,
    ): List<FolderDetail> =
        folders.filter { isSidebarWorkspace(it, folders, openFolderIds) }

    fun isSidebarWorkspace(
        folder: FolderDetail,
        all: List<FolderDetail>,
        openFolderIds: Set<Int>? = null,
    ): Boolean {
        if (folder.parentId != null) return false
        if (isChatFolder(folder)) return false
        if (openFolderIds != null && folder.id !in openFolderIds) return false
        if (isSessionIdName(folder.name)) return false
        if (isDerivedAgentWorkspace(folder, all)) return false
        return true
    }

    /** Agent/session worktrees named with a 16–32 char hex id (no dashes). */
    fun isSessionIdName(name: String): Boolean =
        SESSION_ID_NAME.matches(name.trim())

    /**
     * `codeg-android-agent-mentions` is a worktree of `codeg-android`, not an
     * imported project. Match `{existingWorkspace}-agent…` so a real repo
     * named `my-agent-tools` still shows.
     */
    fun isDerivedAgentWorkspace(folder: FolderDetail, all: List<FolderDetail>): Boolean {
        val lower = folder.name.lowercase()
        return all.any { other ->
            other.id != folder.id &&
                other.parentId == null &&
                !isChatFolder(other) &&
                !isSessionIdName(other.name) &&
                lower.startsWith(other.name.lowercase() + "-agent")
        }
    }

    private val SESSION_ID_NAME = Regex("^[0-9a-fA-F]{16,}$")

    /**
     * The root repo folder for [folder] (itself when top-level, or when its
     * parent isn't in [all]).
     */
    fun resolveRoot(folder: FolderDetail, all: List<FolderDetail>): FolderDetail {
        val pid = folder.parentId ?: return folder
        return all.firstOrNull { it.id == pid } ?: folder
    }

    /**
     * Display name for [folder]: the root repo's name when [folder] is a
     * worktree, else its own name.
     */
    fun displayName(folder: FolderDetail, all: List<FolderDetail>): String {
        val pid = folder.parentId ?: return folder.name
        return all.firstOrNull { it.id == pid }?.name ?: folder.name
    }

    /**
     * Workspace → folder breadcrumb. Worktrees show `root / branch` (or the
     * worktree name when the branch is missing) so the session list can present
     * hierarchy without flattening every folder to the same header.
     */
    fun breadcrumb(folder: FolderDetail, all: List<FolderDetail>): String {
        if (!isWorktree(folder)) return folder.name
        val root = resolveRoot(folder, all)
        if (root.id == folder.id) return folder.name
        val leaf = folder.gitBranch?.takeIf { it.isNotBlank() } ?: folder.name
        return "${root.name} / $leaf"
    }

    // region Branch-switch routing

    /** Where a branch switch should land. Port of web `BranchSwitchPlan`. */
    sealed interface BranchSwitchPlan {
        /** Already on this branch in this folder — nothing to do. */
        data object Noop : BranchSwitchPlan
        /** Branch is checked out in an already-registered folder → open a session there. */
        data class NavigateRegistered(val folderId: Int) : BranchSwitchPlan
        /**
         * Branch is in an unregistered worktree dir → register it (parented to
         * [rootId]), then open a session there.
         */
        data class NavigateExternal(val path: String, val rootId: Int) : BranchSwitchPlan
        /**
         * Branch isn't checked out in any worktree (or it's a remote pick) →
         * `git checkout` in the repo root.
         */
        data class CheckoutInRoot(val root: FolderDetail) : BranchSwitchPlan
    }

    /**
     * Decide how to switch [active] to a branch, given the worktree [resolution]
     * (null for a remote pick or when resolution failed) and the full folder set.
     * Port of `planBranchSwitch`.
     */
    fun planBranchSwitch(
        active: FolderDetail,
        resolution: WorktreeResolution?,
        allFolders: List<FolderDetail>,
        isRemote: Boolean,
    ): BranchSwitchPlan {
        val root = resolveRoot(active, allFolders)
        // Remote selection or "not checked out anywhere" → checkout in the root.
        val path = resolution?.path
        if (isRemote || resolution == null || path == null) {
            return BranchSwitchPlan.CheckoutInRoot(root)
        }
        if (resolution.folderId == active.id) return BranchSwitchPlan.Noop
        resolution.folderId?.let { return BranchSwitchPlan.NavigateRegistered(it) }
        return BranchSwitchPlan.NavigateExternal(path, root.id)
    }

    // endregion
}
