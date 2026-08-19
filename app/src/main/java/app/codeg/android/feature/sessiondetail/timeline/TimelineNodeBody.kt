package app.codeg.android.feature.sessiondetail.timeline

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.codeg.android.R
import app.codeg.android.core.common.RelativeTime
import app.codeg.android.core.common.copyPlainText
import app.codeg.android.core.designsystem.markdown.MarkdownContent
import app.codeg.android.core.designsystem.markdown.SingleBlockView
import app.codeg.android.core.designsystem.markdown.markdownBlockPlainText
import app.codeg.android.core.model.copyableTurnText
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.ContentBlock
import app.codeg.android.core.model.MessageTurn
import app.codeg.android.feature.sessiondetail.rendering.InlineImage
import app.codeg.android.feature.sessiondetail.rendering.LivePlanView
import app.codeg.android.feature.sessiondetail.rendering.ReasoningBlock
import app.codeg.android.feature.sessiondetail.rendering.ToolCallCard
import app.codeg.android.feature.sessiondetail.rendering.ToolGroupCard
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val SuccessGreen = Color(0xFF85D18F)

/** Scrolls the timeline to a node id — used by the footer's jump-to-question button. */
val LocalTimelineScroll = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Renders a node's content to the right of the rail. A pure dispatcher that reuses
 * the existing render leaves verbatim — the timeline is only a new shell. Port of
 * the iOS `NodeBody`.
 */
@Composable
fun NodeBody(node: TimelineNode, modifier: Modifier = Modifier) {
    when (val c = node.content) {
        is NodeContent.User -> UserNodeBody(c.turn, modifier)
        is NodeContent.System -> SystemNodeBody(c.turn, modifier)
        is NodeContent.AssistantBlock -> AssistantNodeBody(c, node.agent, node.rail, modifier)
        is NodeContent.Reasoning -> ReasoningBlock(c.text, modifier, initiallyExpanded = c.streaming)
        is NodeContent.Tool -> ToolCallCard(c.vm, modifier)
        is NodeContent.ToolGroup -> ToolGroupCard(c.items, modifier, streaming = c.streaming)
        is NodeContent.Image -> InlineImage(c.image, c.caption, modifier)
        is NodeContent.Compaction -> ContextCompactionDivider(c.before, c.after, c.running, modifier)
        is NodeContent.Footer -> TurnFooter(c.turn, c.questionId, modifier)
        is NodeContent.Plan -> LivePlanView(c.entries, modifier)
        is NodeContent.Thinking -> ThinkingShimmer(modifier)
        is NodeContent.Error -> InlineTurnError(c.message, modifier)
        is NodeContent.Unsupported -> Text(
            stringResource(R.string.timeline_unsupported_block, c.type),
            fontSize = 11.sp,
            color = CodegTheme.colors.textTertiary,
            modifier = modifier,
        )
    }
}

/** The user's prompt — a right-aligned accent bubble with a You/time label. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserNodeBody(turn: MessageTurn, modifier: Modifier) {
    val colors = CodegTheme.colors
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.timeline_copy)
    val copyText = remember(turn) { copyableTurnText(turn.blocks) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    fun copyNow(fromLongPress: Boolean = false) {
        if (copyText.isEmpty()) return
        if (copyPlainText(context, copyText, copyLabel)) {
            copied = true
            if (fromLongPress) notifyCopied(context)
        }
    }
    val bubble = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.session_role_you) + " · " + RelativeTime.compact(turn.timestamp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textTertiary,
        )
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.88f)
                .clip(bubble)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { copyNow(fromLongPress = true) },
                    onLongClickLabel = copyLabel,
                )
                .background(colors.accent.copy(alpha = 0.22f))
                .border(0.5.dp, colors.accent.copy(alpha = 0.48f), bubble)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (block in turn.blocks) {
                        when (block) {
                            is ContentBlock.Text -> if (block.text.isNotBlank()) MarkdownContent(block.text)
                            is ContentBlock.Image -> InlineImage(block.image, null)
                            is ContentBlock.ImageGeneration ->
                                if (block.image != null) InlineImage(block.image, block.revisedPrompt)
                                else if (!block.revisedPrompt.isNullOrBlank()) MarkdownContent(block.revisedPrompt)
                            else -> {}
                        }
                    }
                }
            }
        }
        if (copyText.isNotEmpty()) {
            DisableSelection { CopyChip(copied = copied, onCopy = { copyNow() }) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantNodeBody(
    content: NodeContent.AssistantBlock,
    agent: AgentType,
    rail: RailStyle,
    modifier: Modifier,
) {
    val colors = CodegTheme.colors
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.timeline_copy)
    val blockText = remember(content.block) { markdownBlockPlainText(content.block) }
    val shape = when (rail) {
        RailStyle.Standalone -> RoundedCornerShape(16.dp)
        RailStyle.Head -> RoundedCornerShape(16.dp, 16.dp, 6.dp, 6.dp)
        RailStyle.Middle -> RoundedCornerShape(6.dp)
        RailStyle.Tail -> RoundedCornerShape(6.dp, 6.dp, 16.dp, 16.dp)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rail == RailStyle.Standalone || rail == RailStyle.Head) {
            Text(
                text = stringResource(R.string.session_role_assistant, agent.shortName),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textTertiary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (!content.streaming && blockText.isNotEmpty()) {
                        Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                if (copyPlainText(context, blockText, copyLabel)) notifyCopied(context)
                            },
                            onLongClickLabel = copyLabel,
                        )
                    } else {
                        Modifier
                    },
                )
                .background(colors.bgElevated.copy(alpha = if (colors.isDark) 0.55f else 0.92f))
                .border(0.5.dp, colors.hairline, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (content.streaming) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SingleBlockView(content.block)
                    BlinkingCaret()
                }
            } else {
                SelectionContainer { SingleBlockView(content.block) }
            }
        }
    }
}

/** A system note — a dim, card-less log line. */
@Composable
private fun SystemNodeBody(turn: MessageTurn, modifier: Modifier) {
    val text = remember(turn) { systemText(turn) }
    SelectionContainer {
        Text(
            text,
            fontSize = 12.sp,
            color = CodegTheme.colors.textTertiary,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/** Per-reply action + metadata row: Copy · jump-to-question · model · time/tokens/duration. */
@Composable
private fun TurnFooter(turn: MessageTurn, questionId: String?, modifier: Modifier) {
    val colors = CodegTheme.colors
    // Copy text joins every block → memoize it. Meta stays live so the relative time
    // ("47m") keeps ticking rather than freezing at whatever it was when first composed.
    val copyText = remember(turn) { copyableTurnText(turn.blocks) }
    val meta = buildList {
        add(RelativeTime.compact(turn.completedAt ?: turn.timestamp))
        val tokens = turn.usage?.total ?: 0
        if (tokens > 0) add(compactTokens(tokens))
        turn.durationMs?.let { if (it > 0) add(compactDuration(it)) }
    }.joinToString(" · ")

    Row(
        modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (copyText.isNotEmpty()) DisableSelection { CopyChip(copyText) }
        if (questionId != null) JumpToQuestionButton(questionId)
        Spacer(Modifier.weight(1f))
        turn.model?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(meta, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = colors.textTertiary, maxLines = 1)
    }
}

@Composable
private fun CopyChip(text: String) {
    val context = LocalContext.current
    val label = stringResource(R.string.timeline_copy)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    CopyChip(
        copied = copied,
        onCopy = { if (copyPlainText(context, text, label)) copied = true },
    )
}

@Composable
private fun CopyChip(copied: Boolean, onCopy: () -> Unit) {
    val colors = CodegTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onCopy)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Crossfade(copied, label = "copy-icon") { c ->
            Icon(
                if (c) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.timeline_copy),
                tint = if (c) SuccessGreen else colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            if (copied) stringResource(R.string.timeline_copied) else stringResource(R.string.timeline_copy),
            fontSize = 12.sp,
            color = colors.textTertiary,
        )
    }
}

private fun notifyCopied(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.timeline_copied), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun JumpToQuestionButton(questionId: String) {
    val scroll = LocalTimelineScroll.current
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { scroll(questionId) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            Icons.Rounded.ArrowUpward,
            contentDescription = stringResource(R.string.timeline_scroll_to_question),
            tint = CodegTheme.colors.textTertiary,
            modifier = Modifier.size(13.dp),
        )
        Text(stringResource(R.string.timeline_question), fontSize = 11.sp, color = CodegTheme.colors.textTertiary)
    }
}

/** Three pulsing dots shown before the agent's first token. Port of iOS `ThinkingShimmer`. */
@Composable
fun ThinkingShimmer(modifier: Modifier = Modifier) {
    val colors = CodegTheme.colors
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(
        modifier.padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0..2) {
            val a by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, StartOffset(i * 180)),
                label = "dot-$i",
            )
            Spacer(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.textTertiary.copy(alpha = a)),
            )
        }
    }
}

/** A blinking text cursor trailing streamed prose. */
@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "caret-alpha",
    )
    Text("▌", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = CodegTheme.colors.accent, modifier = Modifier.alpha(a))
}

/**
 * The context-compaction boundary: a hairline running the content width with a small
 * archive glyph + label centred on it. Deliberately chrome-less (no card, no border) —
 * it marks "the conversation's context was compacted here", it is not something the
 * agent *did*.
 *
 * Grok stamps the before/after token counts on its compaction; codex sends none, and a
 * no-op delta (before == after) would read as a bug, so both fall back to the plain
 * label.
 */
@Composable
fun ContextCompactionDivider(before: Int?, after: Int?, running: Boolean, modifier: Modifier = Modifier) {
    val colors = CodegTheme.colors
    val label = when {
        running -> stringResource(R.string.compaction_running)
        before != null && after != null && before != after ->
            stringResource(R.string.compaction_done_tokens, groupDigits(before), groupDigits(after))
        else -> stringResource(R.string.compaction_done)
    }
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp).alpha(if (running) 0.65f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = colors.hairline)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Rounded.Archive, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, color = colors.textTertiary)
        }
        HorizontalDivider(Modifier.weight(1f), color = colors.hairline)
    }
}

/** Thousands separators for the token counts, in the user's locale. */
private fun groupDigits(value: Int): String = String.format(java.util.Locale.getDefault(), "%,d", value)

/** A danger banner for a turn-tail error / cancellation. Port of iOS `InlineTurnError`. */
@Composable
fun InlineTurnError(message: String, modifier: Modifier = Modifier) {
    val colors = CodegTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.danger.copy(alpha = 0.10f))
            .border(0.5.dp, colors.danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = colors.danger, modifier = Modifier.size(14.dp))
        Text(message, fontSize = 12.sp, color = colors.danger, modifier = Modifier.weight(1f))
    }
}

private fun compactTokens(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> trimNum(n / 1_000.0) + "k"
    else -> trimNum(n / 1_000_000.0) + "M"
}

private fun trimNum(v: Double): String = if (v >= 100) v.roundToInt().toString() else String.format("%.1f", v)

private fun compactDuration(ms: Long): String {
    val seconds = ms / 1000.0
    if (seconds < 60) return String.format("%.1fs", seconds)
    val whole = seconds.roundToInt()
    val m = whole / 60
    val s = whole % 60
    return if (s == 0) "${m}m" else "${m}m ${s}s"
}
