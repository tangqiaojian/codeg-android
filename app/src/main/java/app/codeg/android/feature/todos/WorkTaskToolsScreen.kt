package app.codeg.android.feature.todos

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.data.ServerRepository
import app.codeg.android.core.designsystem.component.CodegTextField
import app.codeg.android.core.designsystem.component.GlassCard
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.component.PrimaryButton
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.FolderVisibility
import app.codeg.android.core.model.PromptInputBlock
import app.codeg.android.core.model.WorkTaskConfig
import app.codeg.android.core.model.WorkTaskFolderSettings
import app.codeg.android.core.model.WorkTaskTemplate
import app.codeg.android.core.model.WorkTaskTemplateDraft
import app.codeg.android.core.network.CodegClient
import app.codeg.android.core.network.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkTaskToolsViewModel @Inject constructor(
    private val repository: ServerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(WorkTaskToolsUiState())
    val ui: StateFlow<WorkTaskToolsUiState> = _ui.asStateFlow()
    private var client: CodegClient? = null

    init {
        viewModelScope.launch {
            repository.selectedProfile.collectLatest { profile ->
                client = profile?.let { repository.client(it) }
                if (profile == null) _ui.value = WorkTaskToolsUiState()
                else refresh()
            }
        }
    }

    fun refresh() {
        val active = client ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val folders = FolderVisibility.filterProjectFolders(active.listFolders()).sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
                val templates = active.workTaskTemplateList()
                val folderId = _ui.value.selectedFolderId?.takeIf { id -> folders.any { it.id == id } } ?: folders.firstOrNull()?.id
                val settings = folderId?.let { folder -> active.workTaskSettingsGetOwn(folder) ?: active.workTaskSettingsGet(0) }
                _ui.value = WorkTaskToolsUiState(
                    folders = folders,
                    selectedFolderId = folderId,
                    settings = settings,
                    templates = templates,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isLoading = false, error = e.displayMessage()) }
            }
        }
    }

    fun selectFolder(id: Int) {
        val active = client ?: return
        viewModelScope.launch {
            _ui.update { it.copy(selectedFolderId = id, isLoading = true, error = null) }
            try {
                _ui.update { it.copy(settings = active.workTaskSettingsGetOwn(id) ?: active.workTaskSettingsGet(0), isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isLoading = false, error = e.displayMessage()) }
            }
        }
    }

    fun saveSettings(settings: WorkTaskFolderSettings) {
        val active = client ?: return
        val folderId = _ui.value.selectedFolderId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            try {
                active.workTaskSettingsSet(folderId, settings)
                _ui.update { it.copy(settings = active.workTaskSettingsGet(folderId), isBusy = false, notice = "Settings saved") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, error = e.displayMessage()) }
            }
        }
    }

    fun resetSettings() {
        val active = client ?: return
        val folderId = _ui.value.selectedFolderId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            try {
                active.workTaskSettingsDelete(folderId)
                _ui.update { it.copy(settings = active.workTaskSettingsGet(0), isBusy = false, notice = "Settings reset") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, error = e.displayMessage()) }
            }
        }
    }

    fun saveTemplate(name: String, title: String, prompt: String, onResult: (String?) -> Unit) {
        val active = client ?: return onResult("No server selected")
        if (name.isBlank() || title.isBlank() || prompt.isBlank()) return onResult("Name, title and prompt are required")
        viewModelScope.launch {
            val error = try {
                active.workTaskTemplateSave(
                    WorkTaskTemplateDraft(
                        name = name.trim(),
                        title = title.trim(),
                        config = WorkTaskConfig(
                            promptBlocks = listOf(PromptInputBlock.Text(prompt.trim())),
                            displayText = prompt.trim(),
                        ),
                    ),
                )
                _ui.update { it.copy(templates = active.workTaskTemplateList()) }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.displayMessage()
            }
            onResult(error)
        }
    }

    fun deleteTemplate(id: Int) {
        val active = client ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            try {
                active.workTaskTemplateDelete(id)
                _ui.update { it.copy(templates = active.workTaskTemplateList(), isBusy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, error = e.displayMessage()) }
            }
        }
    }
}

data class WorkTaskToolsUiState(
    val folders: List<FolderDetail> = emptyList(),
    val selectedFolderId: Int? = null,
    val settings: WorkTaskFolderSettings? = null,
    val templates: List<WorkTaskTemplate> = emptyList(),
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkTaskToolsScreen(
    onBack: () -> Unit,
    viewModel: WorkTaskToolsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = CodegTheme.colors
    var showTemplateEditor by rememberSaveable { mutableStateOf(false) }
    var templateError by rememberSaveable { mutableStateOf<String?>(null) }
    var autoProcess by rememberSaveable { mutableStateOf(false) }
    var autoMerge by rememberSaveable { mutableStateOf(false) }
    var deleteWorktree by rememberSaveable { mutableStateOf(false) }
    var maxConcurrent by rememberSaveable { mutableStateOf("0") }
    var worktreeRoot by rememberSaveable { mutableStateOf("") }
    var preflight by rememberSaveable { mutableStateOf("") }
    var initCommand by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(ui.settings, ui.selectedFolderId) {
        ui.settings?.let { settings ->
            autoProcess = settings.autoProcess
            autoMerge = settings.autoMerge
            deleteWorktree = settings.deleteWorktreeDefault
            maxConcurrent = settings.maxConcurrent.toString()
            worktreeRoot = settings.worktreeRoot.orEmpty()
            preflight = settings.preflightCommand.orEmpty()
            initCommand = settings.initCommand.orEmpty()
        }
    }
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.todos_tools)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !ui.isBusy) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.todos_refresh)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = colors.textPrimary),
            )
        },
    ) { padding ->
        when {
            ui.isLoading && ui.folders.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { LoadingView(message = stringResource(R.string.common_loading)) }
            ui.folders.isEmpty() && ui.error != null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { InlineError(Icons.Rounded.Refresh, stringResource(R.string.todos_tools), ui.error!!, viewModel::refresh, retryLabel = stringResource(R.string.common_retry)) }
            else -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkTaskFolderPicker(ui.folders, ui.selectedFolderId, viewModel::selectFolder)
                val settings = ui.settings
                if (settings != null) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.todos_settings), color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        WorkTaskSwitch(stringResource(R.string.todos_auto_process), autoProcess) { autoProcess = it }
                        WorkTaskSwitch(stringResource(R.string.todos_auto_merge), autoMerge) { autoMerge = it }
                        WorkTaskSwitch(stringResource(R.string.todos_delete_worktree_default), deleteWorktree) { deleteWorktree = it }
                        CodegTextField(maxConcurrent, { maxConcurrent = it }, stringResource(R.string.todos_max_concurrent), singleLine = true)
                        CodegTextField(worktreeRoot, { worktreeRoot = it }, stringResource(R.string.todos_worktree_root), singleLine = true)
                        CodegTextField(preflight, { preflight = it }, stringResource(R.string.todos_preflight), singleLine = false)
                        CodegTextField(initCommand, { initCommand = it }, stringResource(R.string.todos_init_command), singleLine = false)
                        PrimaryButton(
                            text = stringResource(R.string.todos_save_settings),
                            onClick = {
                                viewModel.saveSettings(
                                    settings.copy(
                                        autoProcess = autoProcess,
                                        autoMerge = autoMerge,
                                        deleteWorktreeDefault = deleteWorktree,
                                        maxConcurrent = maxConcurrent.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                        worktreeRoot = worktreeRoot.trim().ifBlank { null },
                                        preflightCommand = preflight.trim().ifBlank { null },
                                        initCommand = initCommand.trim().ifBlank { null },
                                    ),
                                )
                            },
                            loading = ui.isBusy,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TextButton(onClick = viewModel::resetSettings, enabled = !ui.isBusy) {
                            Text(stringResource(R.string.todos_reset_settings))
                        }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.todos_templates), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { templateError = null; showTemplateEditor = true }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.todos_template_new)) }
                    }
                    ui.templates.forEach { template ->
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(template.name, color = colors.textPrimary)
                                Text(template.title, color = colors.textTertiary, fontSize = 12.sp)
                            }
                            IconButton(onClick = { viewModel.deleteTemplate(template.id) }, enabled = !ui.isBusy) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.todos_template_delete), tint = colors.danger) }
                        }
                    }
                    if (ui.templates.isEmpty()) Text(stringResource(R.string.todos_templates_empty), color = colors.textTertiary, modifier = Modifier.padding(top = 10.dp))
                }
                ui.notice?.let { Text(it, color = colors.accent) }
                ui.error?.let { Text(it, color = colors.danger) }
            }
        }
    }
    if (showTemplateEditor) {
        WorkTaskTemplateSheet(
            busy = ui.isBusy,
            error = templateError,
            onDismiss = { showTemplateEditor = false },
            onSave = { name, title, prompt -> viewModel.saveTemplate(name, title, prompt) { error -> templateError = error; if (error == null) showTemplateEditor = false } },
        )
    }
}

@Composable
private fun WorkTaskSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CodegTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTaskFolderPicker(folders: List<FolderDetail>, selectedId: Int?, onSelect: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = folders.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = selected?.name.orEmpty(), onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.todos_folder_field)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            folders.forEach { folder -> DropdownMenuItem(text = { Text(folder.name) }, onClick = { onSelect(folder.id); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTaskTemplateSheet(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val colors = CodegTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgElevated) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.todos_template_new), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            CodegTextField(name, { name = it }, stringResource(R.string.todos_template_name))
            CodegTextField(title, { title = it }, stringResource(R.string.todos_title_field))
            CodegTextField(prompt, { prompt = it }, stringResource(R.string.todos_prompt_field), singleLine = false)
            error?.let { Text(it, color = colors.danger) }
            PrimaryButton(stringResource(R.string.todos_template_save), { onSave(name, title, prompt) }, enabled = name.isNotBlank() && title.isNotBlank() && prompt.isNotBlank(), loading = busy, modifier = Modifier.padding(bottom = 14.dp))
        }
    }
}
