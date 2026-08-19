package app.codeg.android.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.codeg.android.core.data.ServerRepository
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.FolderVisibility
import app.codeg.android.core.model.PromptInputBlock
import app.codeg.android.core.model.WorkTask
import app.codeg.android.core.model.WorkTaskConfig
import app.codeg.android.core.model.WorkTaskDraft
import app.codeg.android.core.network.CodegClient
import app.codeg.android.core.network.StreamFrame
import app.codeg.android.core.network.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodosViewModel @Inject constructor(
    private val repository: ServerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TodosUiState())
    val ui: StateFlow<TodosUiState> = _ui.asStateFlow()

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
                    _ui.value = TodosUiState()
                } else {
                    val nextEndpoint = "${profile.id}|${profile.baseUrl}"
                    val changed = nextEndpoint != endpoint
                    endpoint = nextEndpoint
                    client = repository.client(profile)
                    eventJob?.cancel()
                    eventJob = launch {
                        repository.eventStream(profile)?.frames()?.collect { frame ->
                            if (frame is StreamFrame.Global && frame.channel.startsWith("task://")) refresh()
                        }
                    }
                    if (changed) _ui.value = TodosUiState()
                    fetch(initial = true)
                }
            }
        }
    }

    fun refresh() {
        if (_ui.value.isBusy) return
        viewModelScope.launch { fetch(initial = false) }
    }

    fun createTask(
        folderId: Int,
        title: String,
        prompt: String,
        agentType: String?,
        onResult: (String?) -> Unit,
    ) {
        val active = client ?: return onResult("No server selected")
        viewModelScope.launch {
            val error = try {
                active.workTaskCreate(
                    WorkTaskDraft(
                        folderId = folderId,
                        title = title,
                        config = WorkTaskConfig(
                            promptBlocks = listOf(PromptInputBlock.Text(prompt)),
                            displayText = prompt,
                            agentType = agentType,
                        ),
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

    private suspend fun fetch(initial: Boolean) {
        val active = client ?: return
        _ui.update {
            if (initial) it.copy(isLoading = true, error = null)
            else it.copy(isRefreshing = true, error = null)
        }
        try {
            val (tasks, folders) = coroutineScope {
                val taskRequest = async { active.workTaskList() }
                val folderRequest = async { active.listFolders() }
                taskRequest.await() to folderRequest.await()
            }
            _ui.value = TodosUiState(
                tasks = tasks.sortedWith(compareBy({ statusRank(it.status) }, { it.sortOrder }, { it.title.lowercase() })),
                folders = FolderVisibility.filterProjectFolders(folders).sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() })),
                hasLoaded = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(isLoading = false, isRefreshing = false, error = e.displayMessage()) }
        }
    }

    private fun statusRank(status: String): Int = when (status) {
        "running", "preparing", "awaiting_input", "merging" -> 0
        "review" -> 1
        "queued" -> 2
        "todo" -> 3
        "failed", "canceled" -> 4
        "done" -> 5
        else -> 6
    }
}

data class TodosUiState(
    val tasks: List<WorkTask> = emptyList(),
    val folders: List<FolderDetail> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    val isBusy: Boolean get() = isLoading || isRefreshing
}
