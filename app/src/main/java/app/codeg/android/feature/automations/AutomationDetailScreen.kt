package app.codeg.android.feature.automations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.codeg.android.core.designsystem.component.GlassCard
import app.codeg.android.core.designsystem.component.GlassRow
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.component.PrimaryButton
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.Automation
import app.codeg.android.core.model.AutomationRun
import app.codeg.android.core.model.wire.Rfc3339
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDetailScreen(
    onBack: () -> Unit,
    onOpenConversation: (Int) -> Unit,
    onDeleted: () -> Unit = onBack,
    viewModel: AutomationDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = CodegTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onDeleted()
    }

    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automations_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !ui.isLoading && !ui.isBusy) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.automations_refresh))
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = !ui.isBusy && ui.automation != null) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = colors.textPrimary),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ui.isLoading && ui.automation == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingView(message = stringResource(R.string.common_loading))
                    }
                }
                ui.automation == null && ui.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        InlineError(
                            icon = Icons.Rounded.ErrorOutline,
                            title = stringResource(R.string.automations_detail),
                            message = ui.error!!,
                            onRetry = viewModel::refresh,
                            retryLabel = stringResource(R.string.common_retry),
                        )
                    }
                }
                ui.automation != null -> {
                    AutomationDetailContent(
                        automation = ui.automation!!,
                        runs = ui.runs,
                        folderName = ui.folders.firstOrNull { it.id == ui.automation!!.rootFolderId }?.name,
                        busy = ui.isBusy,
                        busyOp = ui.busyOp,
                        error = ui.error,
                        onToggle = viewModel::setEnabled,
                        onRunNow = viewModel::runNow,
                        onCancelRun = viewModel::cancelRun,
                        onOpenConversation = onOpenConversation,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.automations_delete_title)) },
            text = { Text(stringResource(R.string.automations_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.common_delete), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun AutomationDetailContent(
    automation: Automation,
    runs: List<AutomationRun>,
    folderName: String?,
    busy: Boolean,
    busyOp: String?,
    error: String?,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onCancelRun: (Int) -> Unit,
    onOpenConversation: (Int) -> Unit,
) {
    val colors = CodegTheme.colors
    val prompt = automation.displayPrompt()
    val scheduled = automation.triggerKind == Automation.TRIGGER_SCHEDULE && !automation.cron.isNullOrBlank()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    automation.name,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                AutomationStatusPill(automation.lastRunStatus)
            }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AutomationAgentLabel(automation.agentType)
                Text(stringResource(if (automation.enabled) R.string.automations_enabled else R.string.automations_disabled), color = colors.textTertiary, fontSize = 12.sp)
                Switch(
                    checked = automation.enabled,
                    onCheckedChange = onToggle,
                    enabled = !busy,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                )
            }
            prompt?.let {
                Text(it, color = colors.textSecondary, modifier = Modifier.padding(top = 18.dp))
            } ?: Text(stringResource(R.string.automations_prompt_empty), color = colors.textTertiary, modifier = Modifier.padding(top = 18.dp))
        }

        GlassCard(Modifier.fillMaxWidth()) {
            MetaRow(
                stringResource(R.string.automations_meta_trigger),
                if (scheduled) {
                    val res = automationScheduleCaptionRes(automation.cron)
                    if (res != null && automationCronPresets().any { it.cron == automation.cron?.trim() }) stringResource(res)
                    else automation.cron ?: stringResource(R.string.automations_trigger_manual)
                } else {
                    stringResource(R.string.automations_trigger_manual)
                },
            )
            if (scheduled) {
                automation.cron?.let { MetaRow(stringResource(R.string.automations_meta_cron), it) }
                MetaRow(stringResource(R.string.automations_meta_timezone), automation.timezone)
                MetaRow(stringResource(R.string.automations_meta_next_run), formatAbsolute(automation.nextRunAt))
            }
            MetaRow(stringResource(R.string.automations_meta_action), stringResource(automationActionLabelRes(automation.action())))
            MetaRow(stringResource(R.string.automations_field_folder), folderName ?: automation.rootFolderId?.let { "#$it" } ?: "—")
            if (automation.action() != Automation.ACTION_ENQUEUE_TASK) {
                MetaRow(stringResource(R.string.automations_isolation), stringResource(automationIsolationLabelRes(automation.isolation)))
                automation.branch?.takeIf { it.isNotBlank() }?.let { MetaRow(stringResource(R.string.automations_field_branch), it) }
            }
        }

        error?.let {
            Text(it, color = colors.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }

        PrimaryButton(
            text = stringResource(R.string.automations_run_now),
            onClick = onRunNow,
            loading = busy && busyOp == "run",
            enabled = !busy,
            icon = Icons.Rounded.PlayArrow,
        )

        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.automations_run_history), color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            if (runs.isEmpty()) {
                Text(stringResource(R.string.automations_runs_empty), color = colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
            } else {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    runs.forEach { run ->
                        AutomationRunRow(
                            run = run,
                            busy = busy,
                            onCancel = { onCancelRun(run.id) },
                            onOpenConversation = onOpenConversation,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    val colors = CodegTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = colors.textTertiary, fontSize = 12.sp, modifier = Modifier.weight(0.38f))
        Text(value, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(0.62f))
    }
}

@Composable
private fun AutomationRunRow(
    run: AutomationRun,
    busy: Boolean,
    onCancel: () -> Unit,
    onOpenConversation: (Int) -> Unit,
) {
    val colors = CodegTheme.colors
    GlassRow(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AutomationStatusPill(run.status)
                    Text(stringResource(if (run.trigger == "manual") R.string.automations_trigger_manual else R.string.automations_trigger_schedule), color = colors.textTertiary, fontSize = 11.sp)
                    Text(formatAbsolute(run.startedAt ?: run.createdAt), color = colors.textTertiary, fontSize = 11.sp)
                }
                run.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.textSecondary, fontSize = 12.sp)
                }
                run.error?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.danger, fontSize = 12.sp)
                }
            }
            if (run.status == "running") {
                IconButton(onClick = onCancel, enabled = !busy) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.automations_cancel_run), tint = colors.danger)
                }
            }
            run.conversationId?.let { id ->
                IconButton(onClick = { onOpenConversation(id) }, enabled = !busy) {
                    Icon(Icons.Rounded.Forum, contentDescription = stringResource(R.string.automations_open_conversation), tint = colors.accent)
                }
            }
        }
    }
}

private fun formatAbsolute(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val instant = Rfc3339.parse(iso) ?: return iso
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
