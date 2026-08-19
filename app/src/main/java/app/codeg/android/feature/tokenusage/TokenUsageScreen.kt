package app.codeg.android.feature.tokenusage

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.designsystem.component.EmptyState
import app.codeg.android.core.designsystem.component.GlassCard
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.component.PrimaryButton
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.TokenUsageBreakdownItem
import app.codeg.android.core.model.TokenUsageReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageScreen(
    onBack: () -> Unit,
    onOpenConversation: (Int) -> Unit,
    viewModel: TokenUsageViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = CodegTheme.colors
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.token_usage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.sync() }, enabled = !ui.isBusy) {
                        Icon(Icons.Rounded.Sync, contentDescription = stringResource(R.string.token_usage_sync))
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !ui.isBusy) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.token_usage_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = colors.textPrimary),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ui.isLoading && ui.report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingView(stringResource(R.string.common_loading))
                }
                ui.report == null && ui.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InlineError(
                        icon = Icons.Rounded.ErrorOutline,
                        title = stringResource(R.string.token_usage_title),
                        message = ui.error!!,
                        onRetry = viewModel::refresh,
                        retryLabel = stringResource(R.string.common_retry),
                    )
                }
                ui.report != null -> PullToRefreshBox(
                    isRefreshing = ui.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TokenUsageContent(
                        report = ui.report!!,
                        bucket = ui.bucket,
                        rangeDays = ui.rangeDays,
                        error = ui.error,
                        syncing = ui.isSyncing,
                        onBucket = viewModel::setBucket,
                        onRange = viewModel::setRangeDays,
                        onSync = { viewModel.sync() },
                        onOpenConversation = onOpenConversation,
                    )
                }
                else -> EmptyState(
                    icon = Icons.Rounded.BarChart,
                    title = stringResource(R.string.token_usage_empty_title),
                    message = stringResource(R.string.token_usage_empty_message),
                    actionLabel = stringResource(R.string.token_usage_sync),
                    onAction = { viewModel.sync() },
                )
            }
        }
    }
}

@Composable
private fun TokenUsageContent(
    report: TokenUsageReport,
    bucket: String,
    rangeDays: Int,
    error: String?,
    syncing: Boolean,
    onBucket: (String) -> Unit,
    onRange: (Int) -> Unit,
    onSync: () -> Unit,
    onOpenConversation: (Int) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FilterRow(
                labels = listOf("day" to stringResource(R.string.token_usage_day), "week" to stringResource(R.string.token_usage_week), "month" to stringResource(R.string.token_usage_month)),
                selected = bucket,
                onSelect = onBucket,
            )
        }
        item {
            FilterRow(
                labels = listOf(7 to "7d", 30 to "30d", 90 to "90d"),
                selected = rangeDays,
                onSelect = onRange,
            )
        }
        item { TotalsCard(report) }
        item { SeriesCard(report) }
        item { BreakdownCard(stringResource(R.string.token_usage_by_agent), report.byAgent) }
        item { BreakdownCard(stringResource(R.string.token_usage_by_folder), report.byFolder) }
        item { BreakdownCard(stringResource(R.string.token_usage_by_model), report.byModel) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.token_usage_streak), color = CodegTheme.colors.textTertiary, fontSize = 12.sp)
                Text(
                    stringResource(R.string.token_usage_streak_value, report.streak.currentDays, report.streak.longestDays),
                    color = CodegTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (report.truncated) Text(stringResource(R.string.token_usage_truncated), color = CodegTheme.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.token_usage_top_sessions), color = CodegTheme.colors.textTertiary, fontSize = 12.sp)
                report.topConversations.take(8).forEach { conversation ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenConversation(conversation.conversationId) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(conversation.title ?: stringResource(R.string.session_untitled), color = CodegTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(conversation.folderLabel ?: conversation.agentType, color = CodegTheme.colors.textTertiary, fontSize = 11.sp)
                        }
                        Text(formatTokens(conversation.totalTokens), color = CodegTheme.colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        if (error != null) item { Text(error, color = CodegTheme.colors.danger, fontSize = 12.sp) }
        item {
            PrimaryButton(
                text = stringResource(R.string.token_usage_sync),
                onClick = onSync,
                loading = syncing,
                icon = Icons.Rounded.Sync,
            )
        }
    }
}

@Composable
private fun <T> FilterRow(labels: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CodegTheme.colors.accentDim,
                    selectedLabelColor = CodegTheme.colors.accent,
                    labelColor = CodegTheme.colors.textSecondary,
                ),
            )
        }
    }
}

@Composable
private fun TotalsCard(report: TokenUsageReport) {
    val total = report.totals
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.token_usage_total), color = CodegTheme.colors.textTertiary, fontSize = 12.sp)
        Text(formatTokens(total.totalTokens), color = CodegTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(label = stringResource(R.string.token_usage_input), value = formatTokens(total.inputTokens))
            Metric(label = stringResource(R.string.token_usage_output), value = formatTokens(total.outputTokens))
            Metric(label = stringResource(R.string.token_usage_turns), value = total.turnCount.toString())
            Metric(label = stringResource(R.string.token_usage_days), value = total.activeDays.toString())
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, color = CodegTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(label, color = CodegTheme.colors.textTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun SeriesCard(report: TokenUsageReport) {
    val max = report.series.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1) ?: 1
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.token_usage_trend), color = CodegTheme.colors.textTertiary, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            report.series.takeLast(30).forEach { point ->
                val height = (point.totalTokens.toFloat() / max.toFloat() * 118f).coerceAtLeast(if (point.totalTokens > 0) 4f else 1f)
                Box(Modifier.weight(1f).height(height.dp).background(CodegTheme.colors.accent, RoundedCornerShape(3.dp)))
            }
        }
        Text(stringResource(R.string.token_usage_series_points, report.series.size), color = CodegTheme.colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BreakdownCard(title: String, items: List<TokenUsageBreakdownItem>) {
    if (items.isEmpty()) return
    val max = items.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1) ?: 1
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, color = CodegTheme.colors.textTertiary, fontSize = 12.sp)
        items.take(8).forEach { item ->
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, color = CodegTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatTokens(item.totalTokens), color = CodegTheme.colors.textSecondary, fontSize = 12.sp)
                }
                Box(
                    Modifier.fillMaxWidth().padding(top = 5.dp).height(5.dp).background(CodegTheme.colors.surfaceStroke, RoundedCornerShape(50)),
                ) {
                    Box(Modifier.fillMaxWidth(item.totalTokens.toFloat() / max.toFloat()).height(5.dp).background(CodegTheme.colors.accent, RoundedCornerShape(50)))
                }
            }
        }
    }
}

private fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}
