package app.codeg.android.feature.automations

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.common.RelativeTime
import app.codeg.android.core.designsystem.component.AgentIcon
import app.codeg.android.core.designsystem.component.CodegSegmented
import app.codeg.android.core.designsystem.component.CodegTextField
import app.codeg.android.core.designsystem.component.EmptyState
import app.codeg.android.core.designsystem.component.GlassRow
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.component.PrimaryButton
import app.codeg.android.core.designsystem.component.SettingsIconBadge
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.Automation
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.wire.Rfc3339
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    onBack: () -> Unit,
    onOpenAutomation: (Int) -> Unit,
    viewModel: AutomationsViewModel = hiltViewModel(),
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
                title = { Text(stringResource(R.string.automations_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !ui.isBusy) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.automations_refresh))
                    }
                    IconButton(onClick = { createError = null; showEditor = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.automations_new))
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
                            title = stringResource(R.string.automations_title),
                            message = ui.error!!,
                            onRetry = viewModel::refresh,
                            retryLabel = stringResource(R.string.common_retry),
                        )
                    }
                }
                ui.hasLoaded && ui.automations.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Rounded.Schedule,
                            title = stringResource(R.string.automations_empty_title),
                            message = stringResource(R.string.automations_empty_message),
                            actionLabel = stringResource(R.string.automations_new),
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
                            if (ui.error != null) {
                                item("list-error") {
                                    Text(ui.error!!, color = colors.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                            items(ui.automations, key = { it.id }) { automation ->
                                AutomationRow(
                                    automation = automation,
                                    folderName = ui.folders.firstOrNull { it.id == automation.rootFolderId }?.name,
                                    toggling = ui.mutatingIds.contains(automation.id),
                                    onClick = { onOpenAutomation(automation.id) },
                                    onToggle = { viewModel.setEnabled(automation.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AutomationEditorSheet(
            folders = ui.folders,
            busy = ui.isBusy,
            error = createError,
            onPreviewNextRun = viewModel::previewNextRun,
            onDismiss = { showEditor = false },
            onCreate = { name, prompt, folderId, agentType, triggerKind, cron, action, isolation, branch ->
                viewModel.create(name, prompt, folderId, agentType, triggerKind, cron, action, isolation, branch) { error ->
                    createError = error
                    if (error == null) showEditor = false
                }
            },
        )
    }
}

@Composable
private fun AutomationRow(
    automation: Automation,
    folderName: String?,
    toggling: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = CodegTheme.colors
    GlassRow(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIconBadge(
                icon = if (automation.triggerKind == Automation.TRIGGER_SCHEDULE) Icons.Rounded.Schedule else Icons.Rounded.TouchApp,
                tint = automationStatusColor(automation.lastRunStatus, automation.enabled),
                size = 38.dp,
            )
            Column(
                Modifier.weight(1f).clickable(onClick = onClick),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    automation.name,
                    color = if (automation.enabled) colors.textPrimary else colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    folderName?.let {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(13.dp))
                        Text(it, color = colors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    AutomationAgentLabel(automation.agentType)
                }
                Text(
                    listOfNotNull(
                        if (automation.triggerKind == Automation.TRIGGER_SCHEDULE) {
                            automationScheduleText(automation.cron)
                        } else {
                            stringResource(R.string.automations_trigger_manual)
                        },
                        automationTimeCaption(automation),
                    ).joinToString(" · "),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AutomationStatusPill(automation.lastRunStatus)
                Switch(
                    checked = automation.enabled,
                    onCheckedChange = onToggle,
                    enabled = !toggling,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                )
            }
        }
    }
}

@Composable
fun AutomationAgentLabel(wire: String) {
    val colors = CodegTheme.colors
    val agent = AgentType.knownFromWire(wire)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        agent?.let { AgentIcon(it, 13.dp) }
        Text(agent?.shortName ?: wire, color = colors.textTertiary, fontSize = 11.sp)
    }
}

@Composable
fun AutomationStatusPill(status: String?) {
    val color = automationStatusColor(status, enabled = true)
    Text(
        text = stringResource(automationRunStatusLabelRes(status)),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun automationStatusColor(status: String?, enabled: Boolean): Color = when {
    !enabled -> CodegTheme.colors.textTertiary
    status == "running" -> Color(0xFFE49A39)
    status == "succeeded" -> Color(0xFF39B77A)
    status == "failed" -> CodegTheme.colors.danger
    status == "cancelled" || status == "skipped" -> CodegTheme.colors.textTertiary
    else -> Color(0xFF39B77A)
}

@Composable
private fun automationTimeCaption(automation: Automation): String? {
    val next = automation.nextRunAt.takeIf { automation.enabled && automation.triggerKind == Automation.TRIGGER_SCHEDULE }
    if (next != null) {
        val future = formatFuture(next)
        return if (future == null) stringResource(R.string.automations_now)
        else stringResource(R.string.automations_next_run_relative, future)
    }
    return automation.lastRunAt?.let { formatPast(it) }
}

@Composable
private fun automationScheduleText(cron: String?): String {
    val res = automationScheduleCaptionRes(cron)
    val raw = cron?.trim().orEmpty()
    return if (res != null && (raw.isEmpty() || automationCronPresets().any { it.cron == raw })) {
        stringResource(res)
    } else if (raw.isNotEmpty()) {
        raw
    } else {
        stringResource(R.string.automations_trigger_manual)
    }
}

private fun formatPast(iso: String): String? {
    val instant = Rfc3339.parse(iso) ?: return null
    return RelativeTime.compact(instant)
}

@Composable
private fun formatFuture(iso: String): String? {
    val instant = Rfc3339.parse(iso) ?: return null
    val seconds = instant.epochSecond - Instant.now().epochSecond
    if (seconds <= 0) return null
    val minutes = (seconds / 60).coerceAtLeast(1)
    if (minutes < 60) return stringResource(R.string.automations_duration_minutes, minutes)
    val hours = minutes / 60
    if (hours < 24) return stringResource(R.string.automations_duration_hours, hours)
    return stringResource(R.string.automations_duration_days, hours / 24)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationEditorSheet(
    folders: List<FolderDetail>,
    busy: Boolean,
    error: String?,
    onPreviewNextRun: (String, String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int?, String, String, String?, String, String, String?) -> Unit,
) {
    val colors = CodegTheme.colors
    val timezone = remember { AutomationDrafts.defaultTimezone() }
    var folderId by rememberSaveable { mutableStateOf<Int?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var agentType by rememberSaveable { mutableStateOf(AgentType.CLAUDE_CODE.wire) }
    var triggerKind by rememberSaveable { mutableStateOf(Automation.TRIGGER_SCHEDULE) }
    var cron by rememberSaveable { mutableStateOf("0 9 * * 1-5") }
    var action by rememberSaveable { mutableStateOf(Automation.ACTION_LAUNCH_SESSION) }
    var isolation by rememberSaveable { mutableStateOf(Automation.ISOLATION_WORKTREE) }
    var branch by rememberSaveable { mutableStateOf("") }
    var nextRun by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(folders) {
        if (folderId == null) {
            val first = folders.firstOrNull()
            folderId = first?.id
            first?.defaultAgentType?.let { agentType = it.wire }
        }
    }
    LaunchedEffect(triggerKind, cron, timezone) {
        if (triggerKind != Automation.TRIGGER_SCHEDULE || cron.isBlank()) {
            nextRun = null
        } else {
            onPreviewNextRun(cron, timezone) { nextRun = it }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgElevated) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.automations_new), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            CodegTextField(name, { name = it }, label = stringResource(R.string.automations_field_name))
            CodegTextField(prompt, { prompt = it }, label = stringResource(R.string.automations_field_prompt), singleLine = false)
            AutomationFolderField(folders, folderId, onSelect = { folderId = it })
            AutomationAgentField(agentType, onSelect = { agentType = it })
            FieldLabel(stringResource(R.string.automations_when_it_fires))
            CodegSegmented(
                options = listOf(stringResource(R.string.automations_trigger_schedule), stringResource(R.string.automations_trigger_manual)),
                selectedIndex = if (triggerKind == Automation.TRIGGER_SCHEDULE) 0 else 1,
                onSelect = { triggerKind = if (it == 0) Automation.TRIGGER_SCHEDULE else Automation.TRIGGER_MANUAL },
            )
            if (triggerKind == Automation.TRIGGER_SCHEDULE) {
                AutomationCronField(cron, onSelect = { cron = it })
                Text(stringResource(R.string.automations_timezone, timezone), color = colors.textTertiary, fontSize = 12.sp)
                nextRun?.let { Text(stringResource(R.string.automations_next_run_absolute, formatInstant(it)), color = colors.textSecondary, fontSize = 12.sp) }
            }
            FieldLabel(stringResource(R.string.automations_what_it_does))
            CodegSegmented(
                options = listOf(stringResource(R.string.automations_action_launch_session), stringResource(R.string.automations_action_enqueue_task)),
                selectedIndex = if (action == Automation.ACTION_ENQUEUE_TASK) 1 else 0,
                onSelect = { action = if (it == 1) Automation.ACTION_ENQUEUE_TASK else Automation.ACTION_LAUNCH_SESSION },
            )
            if (action != Automation.ACTION_ENQUEUE_TASK) {
                FieldLabel(stringResource(R.string.automations_isolation))
                CodegSegmented(
                    options = listOf(stringResource(R.string.automations_isolation_worktree_short), stringResource(R.string.automations_isolation_shared_short)),
                    selectedIndex = if (isolation == Automation.ISOLATION_SHARED) 1 else 0,
                    onSelect = { isolation = if (it == 1) Automation.ISOLATION_SHARED else Automation.ISOLATION_WORKTREE },
                )
                if (isolation == Automation.ISOLATION_SHARED) {
                    CodegTextField(branch, { branch = it }, label = stringResource(R.string.automations_field_branch))
                }
            }
            error?.let { Text(it, color = colors.danger, fontSize = 12.sp) }
            PrimaryButton(
                text = stringResource(R.string.automations_create),
                onClick = {
                    onCreate(name.trim(), prompt.trim(), folderId, agentType, triggerKind, cron, action, isolation, branch)
                },
                enabled = folderId != null && name.isNotBlank() && prompt.isNotBlank(),
                loading = busy,
                icon = Icons.Rounded.Add,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = CodegTheme.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationFolderField(folders: List<FolderDetail>, selectedId: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = folders.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.automations_field_folder)) },
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
private fun AutomationAgentField(selectedWire: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = AgentType.knownFromWire(selectedWire)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.displayName ?: selectedWire,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.automations_field_agent)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = dropdownColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AgentType.entries.forEach { agent ->
                DropdownMenuItem(text = { Text(agent.displayName) }, onClick = { onSelect(agent.wire); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationCronField(selectedCron: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val presets = automationCronPresets()
    var custom by rememberSaveable { mutableStateOf(presets.none { it.cron == selectedCron }) }
    val selectedPreset = presets.firstOrNull { it.cron == selectedCron }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (custom) stringResource(R.string.automations_cron_custom) else selectedPreset?.let { stringResource(it.labelRes) } ?: stringResource(R.string.automations_cron_custom),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.automations_field_cadence)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = dropdownColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                DropdownMenuItem(text = { Text(stringResource(preset.labelRes)) }, onClick = {
                    custom = false
                    onSelect(preset.cron)
                    expanded = false
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.automations_cron_custom)) }, onClick = {
                custom = true
                expanded = false
            })
        }
    }
    if (custom) {
        CodegTextField(selectedCron, onSelect, label = stringResource(R.string.automations_field_cron), mono = true)
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

private fun formatInstant(iso: String): String {
    val instant = Rfc3339.parse(iso) ?: return iso
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
