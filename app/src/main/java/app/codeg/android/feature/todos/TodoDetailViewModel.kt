package app.codeg.android.feature.todos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.codeg.android.core.data.ServerRepository
import app.codeg.android.core.model.WorkTaskChangedFile
import app.codeg.android.core.model.WorkTaskEvent
import app.codeg.android.core.model.WorkTask
import app.codeg.android.core.network.CodegClient
import app.codeg.android.core.network.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServerRepository,
) : ViewModel() {
    private val taskId: Int = checkNotNull(savedStateHandle.get<Int>("taskId"))
    private val _ui = MutableStateFlow(TodoDetailUiState())
    val ui: StateFlow<TodoDetailUiState> = _ui.asStateFlow()

    private var client: CodegClient? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedProfile.collectLatest { profile ->
                client = profile?.let { repository.client(it) }
                loadTask(initial = true)
                loadMetadata()
            }
        }
    }

    fun refresh() {
        if (_ui.value.isLoading || _ui.value.isBusy) return
        viewModelScope.launch {
            loadTask(initial = false)
            loadMetadata()
        }
    }

    fun start() {
        val active = client ?: return
        if (_ui.value.isBusy) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            try {
                active.workTaskStart(taskId)
                loadTask(initial = false)
                loadMetadata()
                for (attempt in 0 until 10) {
                    delay(1_000)
                    loadTask(initial = false)
                    if (_ui.value.task?.status in setOf("review", "done", "failed", "canceled")) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.displayMessage()) }
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
        }
    }

    fun retry(note: String?) = mutate { workTaskRetry(taskId, note?.trim()?.takeIf { it.isNotEmpty() }) }

    fun requeue(note: String?) = mutate { workTaskRequeue(taskId, note?.trim()?.takeIf { it.isNotEmpty() }) }

    fun schedule(scheduledAt: String?) = mutate { workTaskSchedule(taskId, scheduledAt?.trim()?.takeIf { it.isNotEmpty() }) }

    fun returnForRevision(feedback: String) = mutate {
        workTaskReturn(taskId, feedback.trim(), intent = "revise")
    }

    fun cancel(reason: String?) = mutate { workTaskCancel(taskId, reason?.trim()?.takeIf { it.isNotEmpty() }) }

    fun merge(message: String?, deleteWorktree: Boolean = false) = mutate {
        workTaskMerge(taskId, message?.trim()?.takeIf { it.isNotEmpty() }, deleteWorktree)
    }

    fun complete(deleteWorktree: Boolean = false) = mutate { workTaskComplete(taskId, deleteWorktree) }

    fun archive(archived: Boolean) = mutate { workTaskArchive(taskId, archived) }

    fun cleanup() = mutate { workTaskCleanup(taskId) }

    fun mergeUnqueue() = mutate { workTaskMergeUnqueue(taskId) }

    fun delete(deleteWorktree: Boolean = false, onResult: (String?) -> Unit) {
        val active = client ?: return onResult("No server selected")
        if (_ui.value.isBusy) return
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null, notice = null) }
            val error = try {
                active.workTaskDelete(taskId, deleteWorktree)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.displayMessage()
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
            if (error != null) _ui.update { it.copy(error = error) }
            onResult(error)
        }
    }

    fun loadEvents() {
        val active = client ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isMetadataLoading = true, error = null) }
            try {
                _ui.update { it.copy(events = active.workTaskEvents(taskId), isMetadataLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isMetadataLoading = false, error = e.displayMessage()) }
            }
        }
    }

    fun loadChangedFiles() {
        val active = client ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isMetadataLoading = true, error = null) }
            try {
                _ui.update { it.copy(changedFiles = active.workTaskChangedFiles(taskId), isMetadataLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isMetadataLoading = false, error = e.displayMessage()) }
            }
        }
    }

    fun loadDiff() {
        val active = client ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isMetadataLoading = true, error = null) }
            try {
                _ui.update { it.copy(diff = active.workTaskDiff(taskId), isMetadataLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isMetadataLoading = false, error = e.displayMessage()) }
            }
        }
    }

    private fun mutate(operation: suspend CodegClient.() -> Unit) {
        val active = client ?: return
        if (_ui.value.isBusy) return
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null, notice = null) }
            try {
                active.operation()
                loadTask(initial = false)
                loadMetadata()
                _ui.update { it.copy(notice = "Task updated") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.displayMessage()) }
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
        }
    }

    fun loadFileDiff(path: String) {
        val active = client ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isMetadataLoading = true, error = null, selectedDiffPath = path) }
            try {
                _ui.update { it.copy(diff = active.workTaskDiff(taskId, path), isMetadataLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isMetadataLoading = false, error = e.displayMessage()) }
            }
        }
    }

    private suspend fun loadMetadata() {
        val active = client ?: return
        _ui.update { it.copy(isMetadataLoading = true) }
        try {
            val events = active.workTaskEvents(taskId)
            val files = runCatching { active.workTaskChangedFiles(taskId) }.getOrDefault(emptyList())
            _ui.update { it.copy(events = events, changedFiles = files, isMetadataLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _ui.update { it.copy(isMetadataLoading = false) }
        }
    }

    private suspend fun loadTask(initial: Boolean) {
        val active = client ?: return
        _ui.update {
            if (initial) it.copy(isLoading = true, error = null)
            else it.copy(error = null)
        }
        try {
            val task = active.workTaskGet(taskId)
            _ui.update { it.copy(task = task, isLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(isLoading = false, error = e.displayMessage()) }
        }
    }
}

data class TodoDetailUiState(
    val task: WorkTask? = null,
    val events: List<WorkTaskEvent> = emptyList(),
    val changedFiles: List<WorkTaskChangedFile> = emptyList(),
    val diff: String? = null,
    val selectedDiffPath: String? = null,
    val isLoading: Boolean = false,
    val isMetadataLoading: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)
