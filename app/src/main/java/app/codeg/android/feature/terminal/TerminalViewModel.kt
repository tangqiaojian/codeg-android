package app.codeg.android.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.codeg.android.core.data.ServerRepository
import app.codeg.android.core.model.TerminalInfo
import app.codeg.android.core.network.CodegClient
import app.codeg.android.core.network.StreamFrame
import app.codeg.android.core.network.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val repository: ServerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TerminalUiState())
    val ui: StateFlow<TerminalUiState> = _ui.asStateFlow()

    private var client: CodegClient? = null
    private var eventJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedProfile.collectLatest { profile ->
                eventJob?.cancel()
                client = profile?.let { repository.client(it) }
                _ui.value = TerminalUiState()
                if (profile != null) {
                    refresh()
                    eventJob = launch {
                        repository.eventStream(profile)?.frames()?.collect { frame ->
                            if (frame is StreamFrame.Global) consumeGlobal(frame.channel, frame.payload)
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        val active = client ?: return
        if (_ui.value.isBusy) return
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val terminals = active.terminalList()
                val defaultWorkingDir = runCatching { active.listOpenFolders().firstOrNull()?.path }.getOrNull()
                _ui.update { it.copy(terminals = terminals, selectedId = it.selectedId ?: terminals.firstOrNull()?.id, defaultWorkingDir = defaultWorkingDir ?: it.defaultWorkingDir, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isLoading = false, error = e.displayMessage()) }
            }
        }
    }

    fun select(id: String) {
        _ui.update { it.copy(selectedId = id) }
    }

    fun spawn(workingDir: String, initialCommand: String?, onResult: (String?) -> Unit) {
        val active = client ?: return onResult("No server selected")
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, error = null) }
            val error = try {
                val id = active.terminalSpawn(workingDir.trim().ifBlank { "/" }, initialCommand = initialCommand?.trim()?.takeIf { it.isNotEmpty() })
                val terminals = runCatching { active.terminalList() }.getOrDefault(_ui.value.terminals)
                _ui.update { it.copy(terminals = terminals, selectedId = id, isBusy = false) }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, error = e.displayMessage()) }
                e.displayMessage()
            }
            onResult(error)
        }
    }

    fun write(data: String) {
        val active = client ?: return
        val id = _ui.value.selectedId ?: return
        if (data.isEmpty()) return
        viewModelScope.launch {
            runCatching { active.terminalWrite(id, data) }
                .onFailure { error -> _ui.update { state -> state.copy(error = state.error ?: error.displayMessage()) } }
        }
    }

    fun kill(id: String) {
        val active = client ?: return
        viewModelScope.launch {
            runCatching { active.terminalKill(id) }
                .onFailure { error -> _ui.update { state -> state.copy(error = state.error ?: error.displayMessage()) } }
            _ui.update { state ->
                state.copy(
                    terminals = state.terminals.filterNot { it.id == id },
                    selectedId = state.terminals.firstOrNull { it.id != id }?.id,
                    outputs = state.outputs - id,
                )
            }
        }
    }

    fun clearOutput() {
        val id = _ui.value.selectedId ?: return
        _ui.update { it.copy(outputs = it.outputs - id) }
    }

    private fun consumeGlobal(channel: String, payload: kotlinx.serialization.json.JsonElement) {
        val prefix = "terminal://output/"
        val exitPrefix = "terminal://exit/"
        val id = when {
            channel.startsWith(prefix) -> channel.removePrefix(prefix)
            channel.startsWith(exitPrefix) -> channel.removePrefix(exitPrefix)
            else -> return
        }
        val data = payload.jsonObject["data"]?.jsonPrimitive?.content.orEmpty()
        if (channel.startsWith(exitPrefix)) {
            _ui.update { it.copy(terminals = it.terminals.filterNot { terminal -> terminal.id == id }) }
            return
        }
        if (data.isEmpty()) return
        _ui.update { state ->
            val next = (state.outputs[id].orEmpty() + data).takeLast(100_000)
            state.copy(outputs = state.outputs + (id to next))
        }
    }
}

data class TerminalUiState(
    val terminals: List<TerminalInfo> = emptyList(),
    val selectedId: String? = null,
    val outputs: Map<String, String> = emptyMap(),
    val defaultWorkingDir: String = "/",
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
) 
