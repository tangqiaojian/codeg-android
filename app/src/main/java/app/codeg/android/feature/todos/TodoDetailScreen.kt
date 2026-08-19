package app.codeg.android.feature.todos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.designsystem.component.CodegTextField
import app.codeg.android.core.designsystem.component.GlassCard
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.component.PrimaryButton
import app.codeg.android.core.designsystem.diff.DiffView
import app.codeg.android.core.designsystem.diff.UnifiedDiff
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.WorkTask
import app.codeg.android.core.model.WorkTaskChangedFile
import app.codeg.android.core.model.WorkTaskEvent
import androidx.compose.foundation.clickable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(
    onBack: () -> Unit,
    onOpenConversation: (Int) -> Unit,
    onDeleted: () -> Unit,
    viewModel: TodoDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = CodegTheme.colors
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.todos_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !ui.isLoading && !ui.isBusy) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.todos_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = colors.textPrimary),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ui.isLoading && ui.task == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingView(message = stringResource(R.string.common_loading))
                    }
                }
                ui.task == null && ui.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        InlineError(
                            icon = Icons.Rounded.ErrorOutline,
                            title = stringResource(R.string.todos_detail),
                            message = ui.error!!,
                            onRetry = viewModel::refresh,
                            retryLabel = stringResource(R.string.common_retry),
                        )
                    }
                }
                ui.task != null -> {
                    TodoDetailContent(
                        task = ui.task!!,
                        busy = ui.isBusy,
                        error = ui.error,
                        onStart = viewModel::start,
                        onOpenConversation = { id -> onOpenConversation(id) },
                        events = ui.events,
                        changedFiles = ui.changedFiles,
                        diff = ui.diff,
                        metadataLoading = ui.isMetadataLoading,
                        notice = ui.notice,
                        onAction = { action = it },
                        onLoadEvents = viewModel::loadEvents,
                        onLoadChangedFiles = viewModel::loadChangedFiles,
                        onLoadDiff = viewModel::loadDiff,
                        onOpenFileDiff = viewModel::loadFileDiff,
                    )
                }
            }
        }
    }
    action?.let { selectedAction ->
        TodoActionSheet(
            action = selectedAction,
            busy = ui.isBusy,
            error = ui.error,
            onDismiss = { if (!ui.isBusy) action = null },
            onSubmit = { value ->
                when (selectedAction) {
                    ACTION_RETRY -> viewModel.retry(value)
                    ACTION_REQUEUE -> viewModel.requeue(value)
                    ACTION_SCHEDULE -> viewModel.schedule(value)
                    ACTION_RETURN -> viewModel.returnForRevision(value)
                    ACTION_CANCEL -> viewModel.cancel(value)
                    ACTION_MERGE -> viewModel.merge(value)
                    ACTION_COMPLETE -> viewModel.complete()
                    ACTION_ARCHIVE -> viewModel.archive(true)
                    ACTION_UNARCHIVE -> viewModel.archive(false)
                    ACTION_CLEANUP -> viewModel.cleanup()
                    ACTION_UNQUEUE -> viewModel.mergeUnqueue()
                    ACTION_DELETE -> viewModel.delete { if (it == null) onDeleted() }
                }
                action = null
            },
        )
    }
}

@Composable
private fun TodoDetailContent(
    task: WorkTask,
    busy: Boolean,
    error: String?,
    onStart: () -> Unit,
    onOpenConversation: (Int) -> Unit,
    events: List<WorkTaskEvent>,
    changedFiles: List<WorkTaskChangedFile>,
    diff: String?,
    metadataLoading: Boolean,
    notice: String?,
    onAction: (String) -> Unit,
    onLoadEvents: () -> Unit,
    onLoadChangedFiles: () -> Unit,
    onLoadDiff: () -> Unit,
    onOpenFileDiff: (String) -> Unit,
) {
    val colors = CodegTheme.colors
    val prompt = task.config?.get("display_text")?.jsonPrimitive?.contentOrNull
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(task.title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskStatusPillForDetail(task.status)
                task.agentType?.let { AgentLabelForDetail(it) }
            }
            prompt?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = colors.textSecondary, modifier = Modifier.padding(top = 18.dp))
            } ?: Text(stringResource(R.string.todos_prompt_empty), color = colors.textTertiary, modifier = Modifier.padding(top = 18.dp))
        }

        task.latestProgress?.takeIf { it.isNotBlank() }?.let {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.todos_status_running), color = colors.textTertiary, fontSize = 12.sp)
                Text(it, color = colors.textPrimary, modifier = Modifier.padding(top = 6.dp))
            }
        }

        error?.let {
            Text(it, color = colors.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
        notice?.let {
            Text(it, color = colors.accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }

        if (task.status == "todo") {
            PrimaryButton(
                text = stringResource(R.string.todos_start),
                onClick = onStart,
                loading = busy,
                icon = Icons.Rounded.PlayArrow,
            )
        }
        if (task.status == "failed") {
            TextButton(onClick = { onAction(ACTION_RETRY) }, enabled = !busy) {
                Text(stringResource(R.string.todos_retry))
            }
        }
        if (task.status == "canceled") {
            TextButton(onClick = { onAction(ACTION_REQUEUE) }, enabled = !busy) {
                Text(stringResource(R.string.todos_requeue))
            }
        }
        if (task.status == "todo") {
            TextButton(onClick = { onAction(ACTION_SCHEDULE) }, enabled = !busy) {
                Text(stringResource(R.string.todos_schedule))
            }
        }
        if (task.status in setOf("queued", "preparing", "running", "awaiting_input")) {
            TextButton(onClick = { onAction(ACTION_CANCEL) }, enabled = !busy) {
                Text(stringResource(R.string.todos_cancel))
            }
        }
        if (task.status == "review") {
            TextButton(onClick = { onAction(ACTION_RETURN) }, enabled = !busy) {
                Text(stringResource(R.string.todos_return))
            }
            if (task.filesChanged == 0 || task.worktreeFolderId == null || task.worktreeMissing) {
                TextButton(onClick = { onAction(ACTION_COMPLETE) }, enabled = !busy) {
                    Text(stringResource(R.string.todos_complete))
                }
            } else {
                PrimaryButton(
                    text = stringResource(R.string.todos_merge),
                    onClick = { onAction(ACTION_MERGE) },
                    loading = busy,
                )
            }
        }
        if (task.mergeQueued != null) {
            TextButton(onClick = { onAction(ACTION_UNQUEUE) }, enabled = !busy) {
                Text(stringResource(R.string.todos_unqueue_merge))
            }
        }
        if (task.archivedAt == null && task.status in setOf("done", "failed", "canceled")) {
            TextButton(onClick = { onAction(ACTION_ARCHIVE) }, enabled = !busy) {
                Text(stringResource(R.string.todos_archive))
            }
        } else if (task.archivedAt != null) {
            TextButton(onClick = { onAction(ACTION_UNARCHIVE) }, enabled = !busy) {
                Text(stringResource(R.string.todos_unarchive))
            }
        }
        if (task.worktreeFolderId != null && !task.worktreeMissing) {
            TextButton(onClick = { onAction(ACTION_CLEANUP) }, enabled = !busy) {
                Text(stringResource(R.string.todos_cleanup))
            }
        }
        TextButton(onClick = { onAction(ACTION_DELETE) }, enabled = !busy) {
            Text(stringResource(R.string.todos_delete))
        }
        task.conversationId?.let { id ->
            PrimaryButton(
                text = stringResource(R.string.todos_open_conversation),
                onClick = { onOpenConversation(id) },
                enabled = !busy,
                icon = Icons.Rounded.Forum,
            )
        }
        TextButton(onClick = onLoadEvents, enabled = !metadataLoading) {
            Text(stringResource(R.string.todos_timeline))
        }
        if (events.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.todos_timeline), color = colors.textTertiary, fontSize = 12.sp)
                events.takeLast(20).asReversed().forEach { event ->
                    Text(
                        text = "${event.kind} · ${event.actor} · ${event.createdAt.orEmpty()}",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        TextButton(onClick = onLoadChangedFiles, enabled = !metadataLoading) {
            Text(stringResource(R.string.todos_changed_files))
        }
        if (changedFiles.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                val additions = changedFiles.sumOf { it.additions }
                val deletions = changedFiles.sumOf { it.deletions }
                Text(
                    stringResource(R.string.session_changes_file_count, changedFiles.size) + "  +$additions  −$deletions",
                    color = colors.textTertiary,
                    fontSize = 12.sp,
                )
                changedFiles.forEach { file ->
                    Text(
                        text = "${file.file} (+${file.additions}/−${file.deletions})",
                        color = colors.accent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFileDiff(file.file) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
        TextButton(onClick = onLoadDiff, enabled = !metadataLoading) {
            Text(stringResource(R.string.todos_diff))
        }
        diff?.let {
            GlassCard(Modifier.fillMaxWidth()) {
                val parsed = UnifiedDiff.parse(it)
                if (parsed != null) {
                    DiffView(parsed)
                } else {
                    Text(
                        it.ifBlank { stringResource(R.string.todos_diff_empty) },
                        color = colors.textSecondary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoActionSheet(
    action: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val colors = CodegTheme.colors
    var value by rememberSaveable(action) { mutableStateOf("") }
    val label = when (action) {
        ACTION_SCHEDULE -> stringResource(R.string.todos_schedule_hint)
        ACTION_RETURN -> stringResource(R.string.todos_feedback)
        ACTION_MERGE -> stringResource(R.string.todos_merge_message)
        ACTION_CANCEL -> stringResource(R.string.todos_cancel_reason)
        else -> stringResource(R.string.todos_note_optional)
    }
    val title = when (action) {
        ACTION_RETRY -> stringResource(R.string.todos_retry)
        ACTION_REQUEUE -> stringResource(R.string.todos_requeue)
        ACTION_SCHEDULE -> stringResource(R.string.todos_schedule)
        ACTION_RETURN -> stringResource(R.string.todos_return)
        ACTION_CANCEL -> stringResource(R.string.todos_cancel)
        ACTION_MERGE -> stringResource(R.string.todos_merge)
        ACTION_ARCHIVE -> stringResource(R.string.todos_archive)
        ACTION_UNARCHIVE -> stringResource(R.string.todos_unarchive)
        ACTION_CLEANUP -> stringResource(R.string.todos_cleanup)
        ACTION_UNQUEUE -> stringResource(R.string.todos_unqueue_merge)
        ACTION_DELETE -> stringResource(R.string.todos_delete)
        ACTION_COMPLETE -> stringResource(R.string.todos_complete)
        else -> action
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgElevated) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            if (action !in setOf(ACTION_ARCHIVE, ACTION_UNARCHIVE, ACTION_CLEANUP, ACTION_COMPLETE, ACTION_UNQUEUE, ACTION_DELETE)) {
                CodegTextField(value, { value = it }, label = label, singleLine = action == ACTION_SCHEDULE)
            }
            error?.let { Text(it, color = colors.danger, fontSize = 12.sp) }
            PrimaryButton(
                text = title,
                onClick = { onSubmit(value) },
                enabled = action != ACTION_RETURN || value.isNotBlank(),
                loading = busy,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}

private const val ACTION_RETRY = "retry"
private const val ACTION_REQUEUE = "requeue"
private const val ACTION_SCHEDULE = "schedule"
private const val ACTION_RETURN = "return"
private const val ACTION_CANCEL = "cancel"
private const val ACTION_MERGE = "merge"
private const val ACTION_COMPLETE = "complete"
private const val ACTION_ARCHIVE = "archive"
private const val ACTION_UNARCHIVE = "unarchive"
private const val ACTION_CLEANUP = "cleanup"
private const val ACTION_UNQUEUE = "unqueue"
private const val ACTION_DELETE = "delete"

@Composable
private fun TaskStatusPillForDetail(status: String) {
    TaskStatusPill(status)
}

@Composable
private fun AgentLabelForDetail(wire: String) {
    AgentLabel(wire)
}
