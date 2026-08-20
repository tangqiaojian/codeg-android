package app.codeg.android.feature.tokenusage

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.codeg.android.core.designsystem.component.EmptyState
import app.codeg.android.core.designsystem.component.GlassCard
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.TokenUsageBreakdownItem
import app.codeg.android.core.model.TokenUsageConversationItem
import app.codeg.android.core.model.TokenUsageHeatCell
import app.codeg.android.core.model.TokenUsagePoint
import app.codeg.android.core.model.TokenUsageReport
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
                        selectedKey = ui.selectedKey,
                        dayReport = ui.dayReport,
                        dayLoading = ui.dayLoading,
                        error = ui.error,
                        lastSynced = ui.syncStatus?.lastSyncedAt,
                        onBucket = viewModel::setBucket,
                        onRange = viewModel::setRangeDays,
                        onSelectPoint = viewModel::selectPoint,
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
    selectedKey: String?,
    dayReport: TokenUsageReport?,
    dayLoading: Boolean,
    error: String?,
    lastSynced: String?,
    onBucket: (String) -> Unit,
    onRange: (Int) -> Unit,
    onSelectPoint: (TokenUsagePoint) -> Unit,
    onOpenConversation: (Int) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val days = report.series.asReversed()
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FilterRow(
                labels = listOf(
                    7 to stringResource(R.string.token_usage_range_7),
                    30 to stringResource(R.string.token_usage_range_30),
                    90 to stringResource(R.string.token_usage_range_90),
                ),
                selected = rangeDays,
                onSelect = onRange,
            )
        }
        item {
            FilterRow(
                labels = listOf(
                    "day" to stringResource(R.string.token_usage_day),
                    "week" to stringResource(R.string.token_usage_week),
                    "month" to stringResource(R.string.token_usage_month),
                ),
                selected = bucket,
                onSelect = onBucket,
            )
        }
        lastSynced?.takeIf { it.isNotBlank() }?.let { synced ->
            item {
                Text(
                    stringResource(R.string.token_usage_synced, formatSynced(synced, zone, locale)),
                    color = CodegTheme.colors.textTertiary,
                    fontSize = 12.sp,
                )
            }
        }
        item { TotalsCard(report) }
        if (report.heatmap.isNotEmpty()) {
            item { HeatmapCard(report.heatmap) }
        }
        item { SparklineCard(report) }
        if (days.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.token_usage_daily),
                    color = CodegTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(days, key = { it.bucketKey.ifBlank { it.start } }) { point ->
                val key = point.bucketKey.ifBlank { point.start }
                DayRow(
                    point = point,
                    bucket = bucket,
                    selected = selectedKey == key,
                    dayReport = if (selectedKey == key) dayReport else null,
                    dayLoading = selectedKey == key && dayLoading,
                    zone = zone,
                    locale = locale,
                    onClick = { onSelectPoint(point) },
                    onOpenConversation = onOpenConversation,
                )
            }
        }
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
                if (report.truncated) {
                    Text(
                        stringResource(R.string.token_usage_truncated),
                        color = CodegTheme.colors.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (report.topConversations.isNotEmpty()) {
            item { SessionListCard(stringResource(R.string.token_usage_top_sessions), report.topConversations, onOpenConversation) }
        }
        if (error != null) item { Text(error, color = CodegTheme.colors.danger, fontSize = 12.sp) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun <T> FilterRow(labels: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
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
    val delta = TokenUsageFormat.percentChange(total.totalTokens, report.previousTotals?.totalTokens)
    val colors = CodegTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.token_usage_total), color = colors.textTertiary, fontSize = 12.sp)
        Text(
            TokenUsageFormat.compact(total.totalTokens),
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (delta != null) {
            val up = delta >= 0
            Text(
                stringResource(R.string.token_usage_vs_previous, String.format(Locale.getDefault(), "%+.0f%%", delta)),
                color = if (up) colors.accent else colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(stringResource(R.string.token_usage_input), TokenUsageFormat.compact(total.inputTokens), Modifier.weight(1f))
                Metric(stringResource(R.string.token_usage_output), TokenUsageFormat.compact(total.outputTokens), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(stringResource(R.string.token_usage_cache_read), TokenUsageFormat.compact(total.cacheReadTokens), Modifier.weight(1f))
                Metric(stringResource(R.string.token_usage_cache_write), TokenUsageFormat.compact(total.cacheCreationTokens), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(stringResource(R.string.token_usage_turns), total.turnCount.toString(), Modifier.weight(1f))
                Metric(stringResource(R.string.token_usage_conversations), total.conversationCount.toString(), Modifier.weight(1f))
                Metric(stringResource(R.string.token_usage_days), total.activeDays.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = CodegTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(label, color = CodegTheme.colors.textTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun SparklineCard(report: TokenUsageReport) {
    if (report.series.isEmpty()) return
    val max = report.series.maxOf { it.totalTokens }.coerceAtLeast(1)
    val colors = CodegTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.token_usage_trend), color = colors.textTertiary, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            report.series.forEach { point ->
                val h = (point.totalTokens.toFloat() / max.toFloat() * 64f).coerceAtLeast(if (point.totalTokens > 0) 3f else 1f)
                Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent.copy(alpha = 0.85f)))
            }
        }
    }
}

@Composable
private fun DayRow(
    point: TokenUsagePoint,
    bucket: String,
    selected: Boolean,
    dayReport: TokenUsageReport?,
    dayLoading: Boolean,
    zone: ZoneId,
    locale: Locale,
    onClick: () -> Unit,
    onOpenConversation: (Int) -> Unit,
) {
    val colors = CodegTheme.colors
    val maxShare = (point.inputTokens + point.outputTokens).coerceAtLeast(1)
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    TokenUsageFormat.bucketLabel(point, bucket, zone, locale),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    "${point.turnCount} · ${point.conversationCount}",
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(TokenUsageFormat.compact(point.totalTokens), color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Icon(
                if (selected) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(50)).background(colors.surfaceStroke)) {
            if (point.inputTokens > 0) {
                Box(Modifier.fillMaxWidth(point.inputTokens.toFloat() / maxShare).height(6.dp).background(colors.accent))
            }
        }
        AnimatedVisibility(visible = selected) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailGrid(point)
                if (dayLoading) {
                    Text(stringResource(R.string.common_loading), color = colors.textTertiary, fontSize = 12.sp)
                }
                val sessions = dayReport?.topConversations.orEmpty()
                if (sessions.isNotEmpty()) {
                    Text(stringResource(R.string.token_usage_day_sessions), color = colors.textTertiary, fontSize = 12.sp)
                    sessions.take(8).forEach { conversation ->
                        SessionLine(conversation, onOpenConversation)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailGrid(point: TokenUsagePoint) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(stringResource(R.string.token_usage_input), TokenUsageFormat.compact(point.inputTokens), Modifier.weight(1f))
            Metric(stringResource(R.string.token_usage_output), TokenUsageFormat.compact(point.outputTokens), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(stringResource(R.string.token_usage_cache_read), TokenUsageFormat.compact(point.cacheReadTokens), Modifier.weight(1f))
            Metric(stringResource(R.string.token_usage_cache_write), TokenUsageFormat.compact(point.cacheCreationTokens), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(stringResource(R.string.token_usage_turns), point.turnCount.toString(), Modifier.weight(1f))
            Metric(stringResource(R.string.token_usage_conversations), point.conversationCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeatmapCard(cells: List<TokenUsageHeatCell>) {
    val colors = CodegTheme.colors
    val max = cells.maxOf { it.totalTokens }.coerceAtLeast(1)
    val byKey = cells.associate { (it.weekday.coerceIn(0, 6) to it.hour.coerceIn(0, 23)) to it.totalTokens }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.token_usage_heatmap), color = colors.textTertiary, fontSize = 12.sp)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            (0..6).forEach { day ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (0..23).forEach { hour ->
                        val value = byKey[day to hour] ?: 0L
                        val alpha = if (value <= 0L) 0.08f else (0.18f + 0.82f * (value.toFloat() / max.toFloat())).coerceIn(0.18f, 1f)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(colors.accent.copy(alpha = alpha)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownCard(title: String, items: List<TokenUsageBreakdownItem>) {
    if (items.isEmpty()) return
    val max = items.maxOf { it.totalTokens }.coerceAtLeast(1)
    val colors = CodegTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, color = colors.textTertiary, fontSize = 12.sp)
        items.take(8).forEach { item ->
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(TokenUsageFormat.compact(item.totalTokens), color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(5.dp).clip(RoundedCornerShape(50)).background(colors.surfaceStroke)) {
                    Box(
                        Modifier
                            .fillMaxWidth((item.totalTokens.toFloat() / max.toFloat()).coerceIn(0f, 1f))
                            .height(5.dp)
                            .background(colors.accent, RoundedCornerShape(50)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionListCard(
    title: String,
    conversations: List<TokenUsageConversationItem>,
    onOpenConversation: (Int) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, color = CodegTheme.colors.textTertiary, fontSize = 12.sp)
        conversations.take(10).forEach { conversation ->
            SessionLine(conversation, onOpenConversation)
        }
    }
}

@Composable
private fun SessionLine(conversation: TokenUsageConversationItem, onOpenConversation: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenConversation(conversation.conversationId) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                conversation.title ?: stringResource(R.string.session_untitled),
                color = CodegTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                conversation.folderLabel ?: conversation.agentType,
                color = CodegTheme.colors.textTertiary,
                fontSize = 11.sp,
            )
        }
        Text(TokenUsageFormat.compact(conversation.totalTokens), color = CodegTheme.colors.textSecondary, fontSize = 12.sp)
    }
}

private fun formatSynced(raw: String, zone: ZoneId, locale: Locale): String {
    return runCatching {
        java.time.Instant.parse(raw).atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale))
    }.getOrDefault(raw)
}
