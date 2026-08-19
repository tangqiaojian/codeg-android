package app.codeg.android.feature.automations

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.codeg.android.R
import app.codeg.android.core.data.ServerRepository
import app.codeg.android.core.model.Automation
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.FolderVisibility
import app.codeg.android.core.network.CodegClient
import app.codeg.android.core.network.StreamFrame
import app.codeg.android.core.network.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val repository: ServerRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _ui = MutableStateFlow(AutomationsUiState())
    val ui: StateFlow<AutomationsUiState> = _ui.asStateFlow()

    private var client: CodegClient? = null
    private var endpoint: String? = null
    private var eventJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedProfile.collectLatest { profile ->
                if (profile == null) {
                    eventJob?.cancel()
                    eventJob = null
                    client = null
                    endpoint = null
                    _ui.value = AutomationsUiState()
                } else {
                    val nextEndpoint = "${profile.id}|${profile.baseUrl}"
                    val changed = nextEndpoint != endpoint
                    endpoint = nextEndpoint
                    client = repository.client(profile)
                    eventJob?.cancel()
                    eventJob = launch {
                        repository.eventStream(profile)?.frames()?.collect { frame ->
                            if (frame is StreamFrame.Global && frame.channel.startsWith("automation://")) refresh()
                        }
                    }
                    if (changed) _ui.value = AutomationsUiState()
                    fetch(initial = true)
                }
            }
        }
    }

    fun refresh() {
        if (_ui.value.isBusy) return
        viewModelScope.launch { fetch(initial = false) }
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        val active = client ?: return
        if (_ui.value.mutatingIds.contains(id)) return
        viewModelScope.launch {
            _ui.update { it.copy(mutatingIds = it.mutatingIds + id, error = null) }
            try {
                val updated = active.automationSetEnabled(id, enabled)
                _ui.update { state ->
                    state.copy(
                        automations = sortAutomations(state.automations.map { if (it.id == id) updated else it }),
                        mutatingIds = state.mutatingIds - id,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(mutatingIds = it.mutatingIds - id, error = e.displayMessage()) }
            }
        }
    }

    fun create(
        name: String,
        prompt: String,
        folderId: Int?,
        agentType: String,
        triggerKind: String,
        cron: String?,
        action: String,
        isolation: String,
        branch: String?,
        onResult: (String?) -> Unit,
    ) {
        val active = client ?: return onResult(appContext.getString(R.string.automations_error_no_server))
        val validation = AutomationDrafts.validate(name, prompt, folderId, triggerKind, cron)
        if (validation != null) return onResult(appContext.getString(validation.messageRes))
        viewModelScope.launch {
            val error = try {
                active.automationCreate(
                    AutomationDrafts.create(
                        name = name,
                        prompt = prompt,
                        folderId = folderId,
                        agentType = agentType,
                        triggerKind = triggerKind,
                        cron = cron,
                        timezone = AutomationDrafts.defaultTimezone(),
                        action = action,
                        isolation = isolation,
                        branch = branch,
                    ),
                )
                fetch(initial = false)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.displayMessage()
            }
            onResult(error)
        }
    }

    fun previewNextRun(cron: String, timezone: String, onResult: (String?) -> Unit) {
        val active = client ?: return onResult(null)
        if (cron.trim().isEmpty()) return onResult(null)
        viewModelScope.launch {
            val preview = try {
                active.automationComputeNextRun(cron.trim(), timezone)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            onResult(preview)
        }
    }

    private suspend fun fetch(initial: Boolean) {
        val active = client ?: return
        _ui.update {
            if (initial) it.copy(isLoading = true, error = null)
            else it.copy(isRefreshing = true, error = null)
        }
        try {
            val (automations, folders) = coroutineScope {
                val automationRequest = async { active.automationList() }
                val folderRequest = async { active.listFolders() }
                automationRequest.await() to folderRequest.await()
            }
            runCatching { active.automationMarkSeen() }
            _ui.value = AutomationsUiState(
                automations = sortAutomations(automations),
                folders = FolderVisibility.filterProjectFolders(folders).sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() })),
                hasLoaded = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(isLoading = false, isRefreshing = false, error = e.displayMessage()) }
        }
    }
}

data class AutomationsUiState(
    val automations: List<Automation> = emptyList(),
    val folders: List<FolderDetail> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val mutatingIds: Set<Int> = emptySet(),
) {
    val isBusy: Boolean get() = isLoading || isRefreshing
}
