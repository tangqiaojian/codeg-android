package app.codeg.android.feature.terminal

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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.designsystem.component.CodegTextField
import app.codeg.android.core.designsystem.component.EmptyState
import app.codeg.android.core.designsystem.component.GlassCard
import app.codeg.android.core.designsystem.component.GlassRow
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.component.PrimaryButton
import app.codeg.android.core.designsystem.theme.CodegTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = CodegTheme.colors
    var showEditor by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.terminal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !ui.isBusy) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.terminal_refresh)) }
                    IconButton(onClick = { showEditor = true }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.terminal_new)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = colors.textPrimary),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ui.isLoading && ui.terminals.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingView(stringResource(R.string.common_loading)) }
                ui.terminals.isEmpty() && ui.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InlineError(
                        icon = Icons.Rounded.Terminal,
                        title = stringResource(R.string.terminal_title),
                        message = ui.error!!,
                        onRetry = viewModel::refresh,
                        retryLabel = stringResource(R.string.common_retry),
                    )
                }
                ui.terminals.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Terminal,
                    title = stringResource(R.string.terminal_empty_title),
                    message = stringResource(R.string.terminal_empty_message),
                    actionLabel = stringResource(R.string.terminal_new),
                    onAction = { showEditor = true },
                )
                else -> TerminalContent(ui, viewModel::select, viewModel::kill, viewModel::write, viewModel::clearOutput)
            }
        }
    }
    if (showEditor) {
        TerminalEditorSheet(
            busy = ui.isBusy,
            error = ui.error,
            onDismiss = { showEditor = false },
            defaultWorkingDir = ui.defaultWorkingDir,
            onSpawn = { workingDir, command -> viewModel.spawn(workingDir, command) { if (it == null) showEditor = false } },
        )
    }
}

@Composable
private fun TerminalContent(
    ui: TerminalUiState,
    onSelect: (String) -> Unit,
    onKill: (String) -> Unit,
    onWrite: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = CodegTheme.colors
    var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ui.terminals, key = { it.id }) { terminal ->
                GlassRow(Modifier.fillMaxWidth().clickable { onSelect(terminal.id) }, selected = terminal.id == ui.selectedId) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Text(terminal.title, color = colors.textPrimary, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        IconButton(onClick = { onKill(terminal.id) }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.terminal_kill)) }
                    }
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth().weight(1f), padding = 10.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ui.terminals.firstOrNull { it.id == ui.selectedId }?.title ?: stringResource(R.string.terminal_title), color = colors.textTertiary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClear) { Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.terminal_clear), tint = colors.textTertiary) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text(ui.outputs[ui.selectedId].orEmpty(), color = Color(0xFFE8F5E9), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CodegTextField(input, { input = it }, stringResource(R.string.terminal_input), modifier = Modifier.weight(1f))
            IconButton(onClick = { onWrite(input + "\n"); input = "" }, enabled = input.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.terminal_send), tint = colors.accent) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalEditorSheet(
    busy: Boolean,
    error: String?,
    defaultWorkingDir: String,
    onDismiss: () -> Unit,
    onSpawn: (String, String?) -> Unit,
) {
    val colors = CodegTheme.colors
    var workingDir by rememberSaveable(defaultWorkingDir) { mutableStateOf(defaultWorkingDir) }
    var command by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgElevated) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.terminal_new), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            CodegTextField(workingDir, { workingDir = it }, stringResource(R.string.terminal_working_dir), mono = true)
            CodegTextField(command, { command = it }, stringResource(R.string.terminal_initial_command), mono = true)
            error?.let { Text(it, color = colors.danger, fontSize = 12.sp) }
            PrimaryButton(stringResource(R.string.terminal_spawn), { onSpawn(workingDir, command.ifBlank { null }) }, enabled = workingDir.isNotBlank(), loading = busy, icon = Icons.Rounded.Terminal, modifier = Modifier.padding(bottom = 14.dp))
        }
    }
}
