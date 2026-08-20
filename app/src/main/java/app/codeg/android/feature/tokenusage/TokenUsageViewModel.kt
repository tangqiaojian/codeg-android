package app.codeg.android.feature.tokenusage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.codeg.android.core.data.ServerRepository
import app.codeg.android.core.model.TokenUsageFacets
import app.codeg.android.core.model.TokenUsageFilter
import app.codeg.android.core.model.TokenUsagePoint
import app.codeg.android.core.model.TokenUsageReport
import app.codeg.android.core.model.TokenUsageSyncStatus
import app.codeg.android.core.network.CodegClient
import app.codeg.android.core.network.displayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TokenUsageViewModel @Inject constructor(
    private val repository: ServerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TokenUsageUiState())
    val ui: StateFlow<TokenUsageUiState> = _ui.asStateFlow()

    private var client: CodegClient? = null

    init {
        viewModelScope.launch {
            repository.selectedProfile.collectLatest { profile ->
                client = profile?.let { repository.client(it) }
                _ui.value = TokenUsageUiState()
                if (profile != null) load(initial = true)
            }
        }
    }

    fun setBucket(bucket: String) {
        if (_ui.value.bucket == bucket) return
        _ui.update { it.copy(bucket = bucket, selectedKey = null, dayReport = null) }
        refresh()
    }

    fun setRangeDays(days: Int) {
        if (_ui.value.rangeDays == days) return
        _ui.update { it.copy(rangeDays = days, selectedKey = null, dayReport = null) }
        refresh()
    }

    fun selectPoint(point: TokenUsagePoint) {
        val key = point.bucketKey.ifBlank { point.start }
        if (_ui.value.selectedKey == key) {
            _ui.update { it.copy(selectedKey = null, dayReport = null, dayLoading = false) }
            return
        }
        _ui.update { it.copy(selectedKey = key) }
        viewModelScope.launch { loadDay(point) }
    }

    fun refresh() {
        if (_ui.value.isBusy) return
        viewModelScope.launch { load(initial = false) }
    }

    fun sync(full: Boolean = false) {
        val active = client ?: return
        if (_ui.value.isBusy) return
        viewModelScope.launch {
            _ui.update { it.copy(isSyncing = true, error = null) }
            try {
                active.tokenUsageSync(if (full) "full" else "incremental")
                load(initial = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isSyncing = false, error = e.displayMessage()) }
            }
        }
    }

    private suspend fun load(initial: Boolean) {
        val active = client ?: return
        _ui.update {
            if (initial) it.copy(isLoading = true, error = null)
            else it.copy(isRefreshing = true, error = null)
        }
        try {
            val now = Instant.now()
            val start = now.minus(Duration.ofDays(_ui.value.rangeDays.toLong()))
            val offsetMinutes = ZoneId.systemDefault().rules.getOffset(now).totalSeconds / 60
            val report = active.tokenUsageReport(
                TokenUsageFilter(
                    start = start.toString(),
                    end = now.toString(),
                    bucket = _ui.value.bucket,
                    tzOffsetMinutes = offsetMinutes,
                    comparePrevious = true,
                ),
            )
            val facets = runCatching { active.tokenUsageFacets() }.getOrNull()
            val status = runCatching { active.tokenUsageStatus() }.getOrNull()
            _ui.update {
                it.copy(
                    report = report,
                    facets = facets ?: it.facets,
                    syncStatus = status ?: it.syncStatus,
                    isLoading = false,
                    isRefreshing = false,
                    isSyncing = false,
                    hasLoaded = true,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(isLoading = false, isRefreshing = false, isSyncing = false, error = e.displayMessage()) }
        }
    }

    private suspend fun loadDay(point: TokenUsagePoint) {
        val active = client ?: return
        val bounds = TokenUsageFormat.dayBounds(point, ZoneId.systemDefault()) ?: return
        val key = point.bucketKey.ifBlank { point.start }
        _ui.update { it.copy(dayLoading = true) }
        val offsetMinutes = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 60
        val report = runCatching {
            active.tokenUsageReport(
                TokenUsageFilter(
                    start = bounds.first,
                    end = bounds.second,
                    bucket = "day",
                    tzOffsetMinutes = offsetMinutes,
                    comparePrevious = false,
                ),
            )
        }.getOrNull()
        _ui.update {
            if (it.selectedKey != key) it
            else it.copy(dayReport = report, dayLoading = false)
        }
    }
}

data class TokenUsageUiState(
    val report: TokenUsageReport? = null,
    val facets: TokenUsageFacets? = null,
    val syncStatus: TokenUsageSyncStatus? = null,
    val bucket: String = "day",
    val rangeDays: Int = 30,
    val selectedKey: String? = null,
    val dayReport: TokenUsageReport? = null,
    val dayLoading: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    val isBusy: Boolean get() = isLoading || isRefreshing || isSyncing
}
