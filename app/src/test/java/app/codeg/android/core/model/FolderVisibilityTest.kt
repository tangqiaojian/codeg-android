package app.codeg.android.core.model

import app.codeg.android.core.model.FolderVisibility.BranchSwitchPlan
import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks down the worktree-aware branch-switch routing ported from iOS `FolderVisibility`. */
class FolderVisibilityTest {

    private fun folder(
        id: Int,
        name: String = "f$id",
        parentId: Int? = null,
        isChat: Boolean = false,
        path: String = "/p/$id",
    ) = FolderDetail(id = id, name = name, path = path, parentId = parentId, isChat = isChat)

    @Test
    fun `filterTopLevel keeps only parentless folders`() {
        val all = listOf(folder(1), folder(2, parentId = 1), folder(3))
        assertEquals(listOf(1, 3), FolderVisibility.filterTopLevel(all).map { it.id })
    }

    @Test
    fun `filterProjectFolders excludes chats and worktrees`() {
        val all = listOf(
            folder(1, parentId = null, isChat = false),
            folder(2, parentId = 1, isChat = false),
            folder(3, parentId = null, isChat = true),
            folder(4, name = "Chat", parentId = null, isChat = false),
        )

        assertEquals(listOf(1), FolderVisibility.filterProjectFolders(all).map { it.id })
    }

    @Test
    fun `hex session-id folders are not imported workspaces`() {
        val all = listOf(
            folder(1, name = "codeg-android"),
            folder(2, name = "e272c76627634921bd22a3da1ab2fcb7"),
            folder(3, name = "c0f18cffd34e47fa905a64ac1ad493b3"),
        )
        assertEquals(listOf("codeg-android"), FolderVisibility.filterProjectFolders(all).map { it.name })
    }

    @Test
    fun `agent worktrees named after an imported repo are not sidebar folders`() {
        val all = listOf(
            folder(1, name = "codeg-android"),
            folder(2, name = "codeg-android-agent-mentions"),
            folder(3, name = "hxzh-dev-1"),
        )
        assertEquals(listOf("codeg-android", "hxzh-dev-1"), FolderVisibility.filterProjectFolders(all).map { it.name })
    }

    @Test
    fun `open-folder intersection drops closed workspaces`() {
        val all = listOf(
            folder(1, name = "codeg-android"),
            folder(2, name = "stale-clone"),
        )
        assertEquals(
            listOf("codeg-android"),
            FolderVisibility.filterProjectFolders(all, openFolderIds = setOf(1)).map { it.name },
        )
    }

    @Test
    fun `a human-named repo is kept even if its path is under chat-sessions`() {
        val all = listOf(
            folder(
                1,
                name = "codeg-android",
                path = "/home/hxzh/.local/share/codeg/chat-sessions/2026-08-18/abc/codeg-android",
            ),
        )
        assertEquals(listOf("codeg-android"), FolderVisibility.filterProjectFolders(all).map { it.name })
    }

    @Test
    fun `a folder named Chat counts as a chat folder even without the flag`() {
        assertEquals(true, FolderVisibility.isChatFolder(folder(4, name = "Chat")))
        assertEquals(false, FolderVisibility.isChatFolder(folder(1, name = "codeg-android")))
    }

    @Test
    fun `resolveRoot returns self for a top-level folder`() {
        val root = folder(1)
        assertEquals(root, FolderVisibility.resolveRoot(root, listOf(root)))
    }

    @Test
    fun `resolveRoot returns the parent for a worktree`() {
        val root = folder(1)
        val wt = folder(2, parentId = 1)
        assertEquals(root, FolderVisibility.resolveRoot(wt, listOf(root, wt)))
    }

    @Test
    fun `resolveRoot falls back to self when the parent is absent`() {
        val wt = folder(2, parentId = 99)
        assertEquals(wt, FolderVisibility.resolveRoot(wt, listOf(wt)))
    }

    @Test
    fun `displayName uses the root repo name for a worktree`() {
        val root = folder(1, name = "repo")
        val wt = folder(2, name = "repo-wt", parentId = 1)
        assertEquals("repo", FolderVisibility.displayName(wt, listOf(root, wt)))
    }

    @Test
    fun `displayName uses own name for a top-level folder`() {
        val root = folder(1, name = "repo")
        assertEquals("repo", FolderVisibility.displayName(root, listOf(root)))
    }

    @Test
    fun `breadcrumb uses workspace slash branch for a worktree`() {
        val root = folder(1, name = "repo")
        val wt = FolderDetail(id = 2, name = "repo-feat", path = "/p/2", parentId = 1, gitBranch = "feat")
        assertEquals("repo / feat", FolderVisibility.breadcrumb(wt, listOf(root, wt)))
        assertEquals("repo", FolderVisibility.breadcrumb(root, listOf(root, wt)))
    }

    @Test
    fun `remote pick always checks out in the root`() {
        val root = folder(1)
        // Even with a resolution present, a remote pick checks out fresh in the root.
        val plan = FolderVisibility.planBranchSwitch(
            active = root,
            resolution = WorktreeResolution(path = "/p/2", folderId = 2),
            allFolders = listOf(root, folder(2)),
            isRemote = true,
        )
        assertEquals(BranchSwitchPlan.CheckoutInRoot(root), plan)
    }

    @Test
    fun `unresolved branch checks out in the root`() {
        val root = folder(1)
        assertEquals(
            BranchSwitchPlan.CheckoutInRoot(root),
            FolderVisibility.planBranchSwitch(root, resolution = null, allFolders = listOf(root), isRemote = false),
        )
        // path == null means "not checked out anywhere" → also checkout in root.
        assertEquals(
            BranchSwitchPlan.CheckoutInRoot(root),
            FolderVisibility.planBranchSwitch(root, WorktreeResolution(path = null, folderId = null), listOf(root), false),
        )
    }

    @Test
    fun `branch already in the active folder is a noop`() {
        val root = folder(1)
        val plan = FolderVisibility.planBranchSwitch(
            active = root,
            resolution = WorktreeResolution(path = "/p/1", folderId = 1),
            allFolders = listOf(root),
            isRemote = false,
        )
        assertEquals(BranchSwitchPlan.Noop, plan)
    }

    @Test
    fun `branch in another registered folder navigates there`() {
        val root = folder(1)
        val other = folder(2)
        val plan = FolderVisibility.planBranchSwitch(
            active = root,
            resolution = WorktreeResolution(path = "/p/2", folderId = 2),
            allFolders = listOf(root, other),
            isRemote = false,
        )
        assertEquals(BranchSwitchPlan.NavigateRegistered(2), plan)
    }

    @Test
    fun `branch in an unregistered worktree navigates external with the root id`() {
        val root = folder(1)
        val active = folder(2, parentId = 1) // we're inside a worktree; root is 1
        val plan = FolderVisibility.planBranchSwitch(
            active = active,
            resolution = WorktreeResolution(path = "/ext/wt", folderId = null),
            allFolders = listOf(root, active),
            isRemote = false,
        )
        assertEquals(BranchSwitchPlan.NavigateExternal("/ext/wt", rootId = 1), plan)
    }
}
