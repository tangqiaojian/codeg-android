package app.codeg.android.core.model

import java.time.Instant

/** One row in the new-session workspace picker. */
data class WorkspaceFolderChoice(
    val folder: FolderDetail,
    val title: String,
    val subtitle: String,
    val isWorktree: Boolean,
)

/**
 * Builds the searchable, worktree-nested folder list used when picking a
 * working directory for a new session.
 */
object WorkspaceFolders {

    fun choices(folders: List<FolderDetail>, query: String = ""): List<WorkspaceFolderChoice> {
        val q = query.trim()
        val byParent = folders.filter { it.parentId != null }.groupBy { it.parentId!! }
        val knownIds = folders.map { it.id }.toSet()
        val top = FolderVisibility.filterTopLevel(folders)
            .sortedWith(
                compareByDescending<FolderDetail> { it.lastOpenedAt ?: Instant.MIN }
                    .thenBy { it.name.lowercase() },
            )
        val out = ArrayList<WorkspaceFolderChoice>(folders.size)
        fun add(folder: FolderDetail) {
            val worktree = FolderVisibility.isWorktree(folder)
            val title = if (worktree) FolderVisibility.breadcrumb(folder, folders) else folder.name
            val row = WorkspaceFolderChoice(
                folder = folder,
                title = title,
                subtitle = folder.path,
                isWorktree = worktree,
            )
            if (q.isEmpty() ||
                row.title.contains(q, ignoreCase = true) ||
                row.subtitle.contains(q, ignoreCase = true) ||
                folder.name.contains(q, ignoreCase = true)
            ) {
                out += row
            }
        }
        for (root in top) {
            add(root)
            val nested = (byParent[root.id] ?: emptyList()).sortedWith(
                compareBy<FolderDetail> { it.gitBranch?.lowercase() ?: it.name.lowercase() },
            )
            nested.forEach(::add)
        }
        folders.filter { it.parentId != null && it.parentId !in knownIds }
            .sortedBy { it.name.lowercase() }
            .forEach(::add)
        return out
    }
}
