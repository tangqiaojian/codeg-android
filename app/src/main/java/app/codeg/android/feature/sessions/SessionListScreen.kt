package app.codeg.android.feature.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.datastore.ServerProfile
import app.codeg.android.core.designsystem.component.CodegSegmented
import app.codeg.android.core.designsystem.component.EmptyState
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.designsystem.theme.colorFromHex
import app.codeg.android.core.designsystem.component.FolderBadge
import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.FolderDetail


/**
 * The Chats tab: the grouped session list for the selected server (Pinned /
 * per-folder / Other), a server switcher in the title, and a "New Task" action.
 *
 * The iOS card-zoom interaction is adapted to native **collapsible sections**: tap a
 * section header to fold/unfold its rows. The grouping itself is computed off the main
 * thread ([SessionListViewModel.sections]) and the list is virtualized with per-type
 * recycling, so a very large session list stays smooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    servers: List<ServerProfile>,
    selectedId: String?,
    onSelectServer: (String) -> Unit,
    onManageServers: () -> Unit,
    onOpenConversation: (Int) -> Unit,
    onOpenFolder: (Int) -> Unit,
    onNewTask: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenTokenUsage: () -> Unit,
    onOpenTerminal: () -> Unit,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val grouped by viewModel.grouped.collectAsStateWithLifecycle()
    val sectionsReady = grouped.scope == ui.scope && grouped.search == ui.search
    val sections = grouped.sections
    // Collapsed section ids. In the composable (not the VM) so collapsing never
    // re-runs the grouping flow; rememberSaveable keeps the fold state across
    // rotation / process death.
    var collapsed by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var collapsedChildren by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var expandedFolders by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var pendingDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    var pendingCloseFolder by remember { mutableStateOf<FolderDetail?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }
    val colors = CodegTheme.colors
    val selectedName = servers.firstOrNull { it.id == selectedId }?.name
        ?: stringResource(R.string.app_name)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    ServerSwitcher(
                        name = selectedName,
                        servers = servers,
                        selectedId = selectedId,
                        onSelect = onSelectServer,
                        onManageServers = onManageServers,
                    )
                },
                actions = {
                    IconButton(onClick = onNewTask) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.session_new_task),
                            tint = colors.accent,
                        )
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(
                                Icons.Rounded.MoreHoriz,
                                contentDescription = stringResource(R.string.common_more),
                                tint = colors.accent,
                            )
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.todos_title)) },
                                leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                                onClick = { overflowOpen = false; onOpenTodos() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.automations_title)) },
                                leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                                onClick = { overflowOpen = false; onOpenAutomations() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.token_usage_title)) },
                                leadingIcon = { Icon(Icons.Rounded.Analytics, contentDescription = null) },
                                onClick = { overflowOpen = false; onOpenTokenUsage() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_title)) },
                                leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                                onClick = { overflowOpen = false; onOpenTerminal() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_manage_servers)) },
                                leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                                onClick = { overflowOpen = false; onManageServers() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ui.isLoading && !ui.hasLoaded -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingView(message = stringResource(R.string.common_loading))
                    }
                }

                !ui.hasLoaded && ui.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        InlineError(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = stringResource(R.string.sessions_load_failed),
                            message = ui.error!!,
                            onRetry = viewModel::refresh,
                            retryLabel = stringResource(R.string.common_retry),
                        )
                    }
                }

                ui.hasLoaded && ui.isEmpty -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = stringResource(R.string.sessions_empty_title),
                            message = stringResource(R.string.sessions_empty_message),
                            actionLabel = stringResource(R.string.session_new_task),
                            onAction = onNewTask,
                        )
                    }
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        SessionScopeFilter(
                            scope = ui.scope,
                            onChange = viewModel::onScopeChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        SessionListSearchField(
                            value = ui.search,
                            onValueChange = viewModel::onSearchChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        if (sectionsReady && sections.isEmpty() && ui.search.isNotBlank()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                EmptyState(
                                    icon = Icons.Rounded.Search,
                                    title = stringResource(R.string.search_no_results),
                                    message = stringResource(R.string.search_no_results_for, ui.search.trim()),
                                )
                            }
                        } else if (sectionsReady && sections.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                EmptyState(
                                    icon = if (ui.scope == SessionListScope.CHATS) Icons.Outlined.ChatBubbleOutline else Icons.Rounded.Folder,
                                    title = stringResource(
                                        when (ui.scope) {
                                            SessionListScope.CHATS -> R.string.sessions_filter_chats_empty
                                            SessionListScope.WORKSPACES -> R.string.sessions_filter_folders_empty
                                            SessionListScope.ALL -> R.string.sessions_empty_title
                                        },
                                    ),
                                    message = stringResource(R.string.sessions_empty_message),
                                )
                            }
                        } else {
                            PullToRefreshBox(
                                isRefreshing = ui.isRefreshing,
                                onRefresh = viewModel::refresh,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                SessionList(
                                    sections = sections,
                                    collapsed = collapsed,
                                    collapsedChildren = collapsedChildren,
                                    expandedFolders = expandedFolders,
                                    onToggleSection = { id ->
                                        collapsed = if (id in collapsed) collapsed - id else collapsed + id
                                    },
                                    onToggleChildren = { id ->
                                        collapsedChildren = if (id in collapsedChildren) collapsedChildren - id else collapsedChildren + id
                                    },
                                    onToggleFolder = { id ->
                                        expandedFolders = if (id in expandedFolders) expandedFolders - id else expandedFolders + id
                                    },
                                    onOpenConversation = onOpenConversation,
                                    onOpenFolder = onOpenFolder,
                                    onTogglePin = { conv -> viewModel.setPinned(conv, !conv.isPinned) },
                                    onDeleteConversation = { pendingDelete = it },
                                    onCloseFolder = { pendingCloseFolder = it },
                                    refreshError = ui.error,
                                    onRetry = viewModel::refresh,
                                    onDismissError = viewModel::dismissError,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.bgElevated,
            title = { Text(stringResource(R.string.sessionactions_delete_session_confirm), color = colors.textPrimary) },
            text = { Text(stringResource(R.string.sessionactions_delete_session_message), color = colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteConversation(conversation)
                    },
                ) { Text(stringResource(R.string.common_delete), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
        )
    }
    pendingCloseFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingCloseFolder = null },
            containerColor = colors.bgElevated,
            title = { Text(stringResource(R.string.sessions_close_folder_confirm, folder.name), color = colors.textPrimary) },
            text = { Text(stringResource(R.string.sessions_close_folder_message), color = colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCloseFolder = null
                        viewModel.closeFolder(folder)
                    },
                ) { Text(stringResource(R.string.common_remove), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCloseFolder = null }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun SessionScopeFilter(
    scope: SessionListScope,
    onChange: (SessionListScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(R.string.sessions_filter_all),
        stringResource(R.string.sessions_filter_folders),
        stringResource(R.string.sessions_filter_chats),
    )
    CodegSegmented(
        options = labels,
        selectedIndex = SessionListScope.entries.indexOf(scope).coerceAtLeast(0),
        onSelect = { onChange(SessionListScope.entries[it]) },
        modifier = modifier,
    )
}

@Composable
private fun SessionListSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = CodegTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        placeholder = { Text(stringResource(R.string.sessions_search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.codeSurface,
            unfocusedContainerColor = colors.codeSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = colors.accent,
            focusedLeadingIconColor = colors.textSecondary,
            unfocusedLeadingIconColor = colors.textTertiary,
            focusedTrailingIconColor = colors.textSecondary,
            unfocusedTrailingIconColor = colors.textTertiary,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedPlaceholderColor = colors.textTertiary,
            unfocusedPlaceholderColor = colors.textTertiary,
        ),
    )
}

@Composable
private fun SessionList(
    sections: List<SessionSection>,
    collapsed: Set<String>,
    collapsedChildren: Set<Int>,
    expandedFolders: Set<Int>,
    onToggleSection: (String) -> Unit,
    onToggleChildren: (Int) -> Unit,
    onToggleFolder: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit,
    onOpenFolder: (Int) -> Unit,
    onTogglePin: (ConversationSummary) -> Unit,
    onDeleteConversation: (ConversationSummary) -> Unit,
    onCloseFolder: (FolderDetail) -> Unit,
    refreshError: String?,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = 24.dp,
        ),
    ) {
        if (refreshError != null) {
            item(key = "refresh-error", contentType = "banner") {
                RefreshErrorBanner(
                    message = refreshError,
                    onRetry = onRetry,
                    onDismiss = onDismissError,
                    modifier = Modifier.animateItem().padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }

        sections.forEach { section ->
            sessionSectionItems(
                section = section,
                collapsed = collapsed,
                collapsedChildren = collapsedChildren,
                expandedFolders = expandedFolders,
                onToggleSection = onToggleSection,
                onToggleChildren = onToggleChildren,
                onToggleFolder = onToggleFolder,
                onOpenConversation = onOpenConversation,
                onOpenFolder = onOpenFolder,
                onTogglePin = onTogglePin,
                onDeleteConversation = onDeleteConversation,
                onCloseFolder = onCloseFolder,
            )
        }
    }
}

private fun LazyListScope.sessionSectionItems(
    section: SessionSection,
    collapsed: Set<String>,
    collapsedChildren: Set<Int>,
    expandedFolders: Set<Int>,
    onToggleSection: (String) -> Unit,
    onToggleChildren: (Int) -> Unit,
    onToggleFolder: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit,
    onOpenFolder: (Int) -> Unit,
    onTogglePin: (ConversationSummary) -> Unit,
    onDeleteConversation: (ConversationSummary) -> Unit,
    onCloseFolder: (FolderDetail) -> Unit,
) {
    val isCollapsed = section.id in collapsed
    item(key = sessionHeaderKey(section.id), contentType = "header") {
        val style = sectionStyle(section.kind)
        CollapsibleSectionHeader(
            icon = style.icon,
            tint = style.tint,
            label = style.label,
            count = section.count,
            collapsed = isCollapsed,
            onToggle = { onToggleSection(section.id) },
            collapsible = section.count > 0,
            modifier = Modifier.animateItem(),
        )
    }
    if (isCollapsed) return
    if (section.folders.isNotEmpty()) {
        section.folders.forEach { entry ->
            folderEntryItems(
                entry = entry,
                section = section,
                collapsedChildren = collapsedChildren,
                expandedFolders = expandedFolders,
                onToggleChildren = onToggleChildren,
                onToggleFolder = onToggleFolder,
                onOpenConversation = onOpenConversation,
                onOpenFolder = onOpenFolder,
                onTogglePin = onTogglePin,
                onDeleteConversation = onDeleteConversation,
                onCloseFolder = onCloseFolder,
            )
        }
    } else {
        section.rows.forEach { row ->
            sessionRowItems(
                row = row,
                sectionId = section.id,
                folderId = null,
                collapsedChildren = collapsedChildren,
                onToggleChildren = onToggleChildren,
                onOpenConversation = onOpenConversation,
                onTogglePin = onTogglePin,
                onDeleteConversation = onDeleteConversation,
            )
        }
    }
}

private fun LazyListScope.folderEntryItems(
    entry: FolderEntry,
    section: SessionSection,
    collapsedChildren: Set<Int>,
    expandedFolders: Set<Int>,
    onToggleChildren: (Int) -> Unit,
    onToggleFolder: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit,
    onOpenFolder: (Int) -> Unit,
    onTogglePin: (ConversationSummary) -> Unit,
    onDeleteConversation: (ConversationSummary) -> Unit,
    onCloseFolder: (FolderDetail) -> Unit,
) {
    val expanded = entry.folder.id in expandedFolders
    item(key = sessionFolderKey(entry.folder.id), contentType = "folder") {
        SessionListFolderRow(
            folder = entry.folder,
            depth = entry.depth,
            breadcrumb = entry.breadcrumb,
            sessionCount = entry.sessionCount,
            expanded = expanded,
            onToggle = {
                if (entry.sessionCount > 0) onToggleFolder(entry.folder.id)
                else onOpenFolder(entry.folder.id)
            },
            onOpenFolder = { onOpenFolder(entry.folder.id) },
            onCloseFolder = { onCloseFolder(entry.folder) },
            modifier = Modifier.animateItem(),
        )
    }
    if (expanded) {
        entry.conversations.forEach { row ->
            sessionRowItems(
                row = row,
                sectionId = section.id,
                folderId = entry.folder.id,
                collapsedChildren = collapsedChildren,
                onToggleChildren = onToggleChildren,
                onOpenConversation = onOpenConversation,
                onTogglePin = onTogglePin,
                onDeleteConversation = onDeleteConversation,
            )
        }
    }
    // Worktrees stay listed under the workspace even when its sessions are folded.
    entry.children.forEach { child ->
        folderEntryItems(
            entry = child,
            section = section,
            collapsedChildren = collapsedChildren,
            expandedFolders = expandedFolders,
            onToggleChildren = onToggleChildren,
            onToggleFolder = onToggleFolder,
            onOpenConversation = onOpenConversation,
            onOpenFolder = onOpenFolder,
            onTogglePin = onTogglePin,
            onDeleteConversation = onDeleteConversation,
            onCloseFolder = onCloseFolder,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionListFolderRow(
    folder: FolderDetail,
    depth: Int,
    breadcrumb: String?,
    sessionCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenFolder: () -> Unit,
    onCloseFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodegTheme.colors
    val tile = colorFromHex(folder.color) ?: colors.accent
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier
                .fillMaxWidth()
                .padding(start = (depth * 16).dp)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FolderBadge(color = tile, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildList {
                    breadcrumb?.takeIf { it.isNotBlank() && it != folder.name }?.let { add(it) }
                    folder.gitBranch?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontSize = 11.sp, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (sessionCount > 0) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandMore else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(if (expanded) R.string.sessions_collapse else R.string.sessions_expand),
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = stringResource(R.string.sessions_open_folder),
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenFolder)
                    .padding(6.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sessions_open_folder)) },
                leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpenFolder()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sessions_close_folder), color = colors.danger) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = colors.danger) },
                onClick = {
                    menuOpen = false
                    onCloseFolder()
                },
            )
        }
    }
}

private fun LazyListScope.sessionRowItems(
    row: SessionRowItem,
    sectionId: String,
    folderId: Int?,
    collapsedChildren: Set<Int>,
    onToggleChildren: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit,
    onTogglePin: (ConversationSummary) -> Unit,
    onDeleteConversation: (ConversationSummary) -> Unit,
) {
    val expanded = row.children.isNotEmpty() && row.conversation.id !in collapsedChildren
    item(key = sessionRowKey(sectionId, folderId, row.conversation.id), contentType = "row") {
        SessionRow(
            conversation = row.conversation,
            onClick = { onOpenConversation(row.conversation.id) },
            modifier = Modifier.animateItem(),
            folderName = row.folderName,
            onTogglePin = { onTogglePin(row.conversation) },
            onDelete = { onDeleteConversation(row.conversation) },
            depth = row.depth,
            childCount = row.children.size,
            childrenExpanded = expanded,
            onToggleChildren = if (row.children.isNotEmpty()) {
                { onToggleChildren(row.conversation.id) }
            } else {
                null
            },
        )
    }
    if (expanded) {
        row.children.forEach { child ->
            sessionRowItems(
                row = child,
                sectionId = sectionId,
                folderId = folderId,
                collapsedChildren = collapsedChildren,
                onToggleChildren = onToggleChildren,
                onOpenConversation = onOpenConversation,
                onTogglePin = onTogglePin,
                onDeleteConversation = onDeleteConversation,
            )
        }
    }
}

/** Resolved header presentation for a [SectionKind] (icon + theme-aware tint + label). */
private data class SectionStyle(
    val icon: ImageVector,
    val tint: Color,
    val label: String,
    val subtitle: String? = null,
)

@Composable
private fun sectionStyle(kind: SectionKind): SectionStyle {
    val colors = CodegTheme.colors
    return when (kind) {
        SectionKind.Pinned -> SectionStyle(
            icon = Icons.Rounded.PushPin,
            tint = colors.accent,
            label = stringResource(R.string.sessions_pinned),
        )
        SectionKind.Folders -> SectionStyle(
            icon = Icons.Rounded.Folder,
            tint = colors.accent,
            label = stringResource(R.string.sessions_section_folders),
        )
        SectionKind.Chats -> SectionStyle(
            icon = Icons.Outlined.ChatBubbleOutline,
            tint = colors.accent,
            label = stringResource(R.string.sessions_section_chats),
        )
        SectionKind.Recent -> SectionStyle(
            icon = Icons.Rounded.History,
            tint = colors.textSecondary,
            label = stringResource(R.string.sessions_section_recent),
        )
        SectionKind.Other -> SectionStyle(
            icon = Icons.Rounded.Inbox,
            tint = colors.textSecondary,
            label = stringResource(R.string.sessions_other),
        )
    }
}

/**
 * A dismissible banner for a refresh that failed while rows are still on screen —
 * the stale rows stay visible and the error is surfaced inline with Retry / dismiss
 * (the Android analogue of iOS `RefreshErrorBanner`). The initial-load failure still
 * uses the full-screen [InlineError]; this is only for the keep-the-list case.
 */
@Composable
private fun RefreshErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodegTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.danger.copy(alpha = 0.12f))
            .border(CodegTheme.dimens.hairlineWidth, colors.danger.copy(alpha = 0.35f), shape)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onRetry,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
        ) {
            Text(stringResource(R.string.common_retry))
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.common_dismiss),
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ServerSwitcher(
    name: String,
    servers: List<ServerProfile>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onManageServers: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = CodegTheme.colors
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = { Text(server.name) },
                    onClick = {
                        open = false
                        if (server.id != selectedId) onSelect(server.id)
                    },
                    trailingIcon = {
                        if (server.id == selectedId) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_manage_servers)) },
                leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                onClick = {
                    open = false
                    onManageServers()
                },
            )
        }
    }
}
