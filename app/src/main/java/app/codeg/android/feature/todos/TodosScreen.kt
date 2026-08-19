package app.codeg.android.feature.todos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.codeg.android.core.designsystem.component.AgentIcon
import app.codeg.android.core.designsystem.component.EmptyState
import app.codeg.android.core.designsystem.component.GlassCard
import app.codeg.android.core.designsystem.component.GlassRow
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.component.PrimaryButton
import app.codeg.android.core.designsystem.component.SettingsIconBadge
import app.codeg.android.core.designsystem.component.CodegTextField
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.WorkTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosScreen(
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onOpenTools: () -> Unit,
    viewModel: TodosViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = CodegTheme.colors
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.todos_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTools) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.todos_tools))
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !ui.isBusy) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.todos_refresh))
                    }
                    IconButton(onClick = { createError = null; showEditor = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.todos_add))
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
                            icon = Icons.Rounded.ErrorOutline,
                            title = stringResource(R.string.todos_title),
                            message = ui.error!!,
                            onRetry = viewModel::refresh,
                            retryLabel = stringResource(R.string.common_retry),
                        )
                    }
                }
                ui.hasLoaded && ui.tasks.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Rounded.Schedule,
                            title = stringResource(R.string.todos_empty_title),
                            message = stringResource(R.string.todos_empty_message),
                            actionLabel = stringResource(R.string.todos_add),
                            onAction = { createError = null; showEditor = true },
                        )
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = ui.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(ui.tasks, key = { it.id }) { task ->
                                TodoRow(
                                    task = task,
                                    folderName = ui.folders.firstOrNull { it.id == task.folderId }?.name,
                                    onClick = { onOpenTask(task.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        TodoEditorSheet(
            folders = ui.folders,
            busy = ui.isBusy,
            error = createError,
            onDismiss = { showEditor = false },
            onCreate = { folderId, title, prompt, agentType ->
                viewModel.createTask(folderId, title, prompt, agentType) { error ->
                    createError = error
                    if (error == null) showEditor = false
                }
            },
        )
    }
}

@Composable
private fun TodoRow(task: WorkTask, folderName: String?, onClick: () -> Unit) {
    val colors = CodegTheme.colors
    GlassRow(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIconBadge(
                icon = statusIcon(task.status),
                tint = taskStatusColor(task.status),
                size = 38.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    task.title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    folderName?.let {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(13.dp))
                        Text(it, color = colors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    task.agentType?.let { AgentLabel(it) }
                }
                task.latestProgress?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TaskStatusPill(task.status)
        }
    }
}

@Composable
fun AgentLabel(wire: String) {
    val colors = CodegTheme.colors
    val agent = AgentType.knownFromWire(wire)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        agent?.let { AgentIcon(it, 13.dp) }
        Text(agent?.shortName ?: wire, color = colors.textTertiary, fontSize = 11.sp)
    }
}

@Composable
fun TaskStatusPill(status: String) {
    val color = taskStatusColor(status)
    Text(
        text = taskStatusLabel(status),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun taskStatusLabel(status: String): String = when (status) {
    "todo" -> stringResource(R.string.todos_status_todo)
    "queued" -> stringResource(R.string.todos_status_queued)
    "preparing" -> stringResource(R.string.todos_status_preparing)
    "running" -> stringResource(R.string.todos_status_running)
    "awaiting_input" -> stringResource(R.string.todos_status_awaiting_input)
    "review" -> stringResource(R.string.todos_status_review)
    "merging" -> stringResource(R.string.todos_status_merging)
    "done" -> stringResource(R.string.todos_status_done)
    "failed" -> stringResource(R.string.todos_status_failed)
    "canceled" -> stringResource(R.string.todos_status_canceled)
    else -> stringResource(R.string.todos_status_unknown)
}

@Composable
private fun taskStatusColor(status: String): Color = when (status) {
    "running", "preparing", "awaiting_input", "merging" -> Color(0xFF3F8CFF)
    "review" -> Color(0xFF9B6BFF)
    "queued" -> Color(0xFFE49A39)
    "done" -> Color(0xFF39B77A)
    "failed", "canceled" -> CodegTheme.colors.danger
    else -> CodegTheme.colors.textTertiary
}

private fun statusIcon(status: String): ImageVector = when (status) {
    "done" -> Icons.Rounded.CheckCircle
    "running", "preparing", "awaiting_input", "merging" -> Icons.Rounded.PlayArrow
    "failed", "canceled" -> Icons.Rounded.ErrorOutline
    else -> Icons.Rounded.Schedule
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoEditorSheet(
    folders: List<FolderDetail>,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (Int, String, String, String?) -> Unit,
) {
    val colors = CodegTheme.colors
    var folderId by rememberSaveable { mutableStateOf<Int?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var agentType by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(folders) {
        if (folderId == null) folderId = folders.firstOrNull()?.id
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgElevated) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.todos_new_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            CodegTextField(title, { title = it }, label = stringResource(R.string.todos_title_field))
            CodegTextField(prompt, { prompt = it }, label = stringResource(R.string.todos_prompt_field), singleLine = false)
            TodoFolderField(folders, folderId, onSelect = { folderId = it })
            TodoAgentField(agentType, onSelect = { agentType = it })
            error?.let { Text(it, color = colors.danger, fontSize = 12.sp) }
            PrimaryButton(
                text = stringResource(R.string.todos_create),
                onClick = { folderId?.let { onCreate(it, title.trim(), prompt.trim(), agentType) } },
                enabled = folderId != null && title.isNotBlank() && prompt.isNotBlank(),
                loading = busy,
                icon = Icons.Rounded.Add,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoFolderField(folders: List<FolderDetail>, selectedId: Int?, onSelect: (Int) -> Unit) {
    val colors = CodegTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val selected = folders.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.todos_folder_field)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = dropdownColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            folders.forEach { folder ->
                DropdownMenuItem(text = { Text(folder.name) }, onClick = { onSelect(folder.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoAgentField(selectedWire: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = AgentType.knownFromWire(selectedWire.orEmpty())
    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.todos_inherit_agent)) + AgentType.entries.map { it.wire to it.displayName }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.displayName ?: stringResource(R.string.todos_inherit_agent),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.todos_agent_field)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = dropdownColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (wire, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(wire); expanded = false })
            }
        }
    }
}

@Composable
private fun dropdownColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CodegTheme.colors.accent,
    unfocusedBorderColor = CodegTheme.colors.surfaceStroke,
    focusedLabelColor = CodegTheme.colors.accent,
    unfocusedLabelColor = CodegTheme.colors.textTertiary,
    focusedTextColor = CodegTheme.colors.textPrimary,
    unfocusedTextColor = CodegTheme.colors.textPrimary,
)
