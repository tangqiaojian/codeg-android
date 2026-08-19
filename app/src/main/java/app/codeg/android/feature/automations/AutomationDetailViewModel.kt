package app.codeg.android.feature.automations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.codeg.android.core.data.ServerRepository
import app.codeg.android.core.model.Automation
import app.codeg.android.core.model.AutomationRun
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.network.CodegClient
import app.codeg.android.core.network.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServerRepository,
) : ViewModel() {
    private val automationId: Int = checkNotNull(savedStateHandle.get<Int>("automationId"))
    private val _ui = MutableStateFlow(AutomationDetailUiState())
    val ui: StateFlow<AutomationDetailUiState> = _ui.asStateFlow()

    private var client: CodegClient? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedProfile.collectLatest { profile ->
                client = profile?.let { repository.client(it) }
                load(initial = true)
            }
        }
    }

    fun refresh() {
        if (_ui.value.isLoading || _ui.value.isBusy) return
        viewModelScope.launch { load(initial = false) }
    }

    fun setEnabled(enabled: Boolean) {
        mutate("toggle") {
            it.automationSetEnabled(automationId, enabled)
            load(initial = false)
        }
    }

    fun runNow() {
        mutate("run") {
            it.automationRunNow(automationId)
            load(initial = false)
            for (attempt in 0 until 10) {
                delay(1_000)
                load(initial = false)
                val latest = _ui.value.runs.firstOrNull()?.status
                if (latest != null && latest != "running") break
            }
        }
    }

    fun cancelRun(runId: Int) {
        mutate("cancel") {
            it.automationCancelRun(runId)
            load(initial = false)
        }
    }

    fun delete() {
        mutate("delete") {
            it.automationDelete(automationId)
            _ui.update { state -> state.copy(deleted = true) }
        }
    }

    private fun mutate(op: String, block: suspend (CodegClient) -> Unit) {
        val active = client ?: return
        if (_ui.value.isBusy) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, busyOp = op, error = null) }
            try {
                block(active)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.displayMessage()) }
            } finally {
                _ui.update { it.copy(isBusy = false, busyOp = null) }
            }
        }
    }

    private suspend fun load(initial: Boolean) {
        val active = client ?: return
        _ui.update {
            if (initial) it.copy(isLoading = true, error = null)
            else it.copy(error = null)
        }
        try {
            val (automation, runs, folders) = coroutineScope {
                val automationRequest = async { active.automationGet(automationId) }
                val runsRequest = async { active.automationRuns(automationId) }
                val foldersRequest = async { active.listFolders() }
                Triple(automationRequest.await(), runsRequest.await(), foldersRequest.await())
            }
            _ui.update {
                it.copy(
                    automation = automation,
                    runs = runs,
                    folders = folders,
                    isLoading = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(isLoading = false, error = e.displayMessage()) }
        }
    }
}

data class AutomationDetailUiState(
    val automation: Automation? = null,
    val runs: List<AutomationRun> = emptyList(),
    val folders: List<FolderDetail> = emptyList(),
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val busyOp: String? = null,
    val error: String? = null,
    val deleted: Boolean = false,
)
