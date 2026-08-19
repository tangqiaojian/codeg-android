package app.codeg.android.feature.sessiondetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.codeg.android.R
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.AcpAgentInfo
import app.codeg.android.core.model.AgentMention
import app.codeg.android.core.model.AgentMentionQuery
import app.codeg.android.core.model.findActiveAgentMentionQuery

/**
 * The prompt composer pinned to the bottom of the session detail screen: a
 * taller multiline field, one overflow button for insert / mention / attach,
 * and send / stop. The send button morphs between Send and Stop with a Material
 * scale-and-fade transition and an accent↔danger colour tween.
 */
@Composable
fun ComposeBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    isInFlight: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onPlus: (() -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    canSendOverride: Boolean = false,
    mentionAgents: List<AcpAgentInfo> = emptyList(),
    mentionRanges: List<AgentMention> = emptyList(),
    onMentionSelected: (AgentMentionQuery, AcpAgentInfo) -> Unit = { _, _ -> },
    onMentionDeleted: (Int) -> Unit = {},
) {
    val colors = CodegTheme.colors
    var extrasOpen by remember { mutableStateOf(false) }
    var manualMentionPicker by remember { mutableStateOf(false) }
    val detectedMention = findActiveAgentMentionQuery(value.text, value.selection.start)
    val activeMention = detectedMention?.takeUnless { query ->
        mentionRanges.any { it.start == query.start && it.end == query.end }
    }
    val pickerQuery = activeMention ?: if (manualMentionPicker) {
        AgentMentionQuery(value.selection.start, value.selection.start, "")
    } else {
        null
    }
    val hasDraft = value.text.isNotBlank() || canSendOverride
    val canSend = hasDraft
    val sendContainer by animateColorAsState(
        targetValue = if (isInFlight && !hasDraft) colors.danger else colors.accent,
        animationSpec = tween(220),
        label = "send-container",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // A hairline setting the composer off from the timeline above it — the same
        // separator the bottom navigation bar uses, so every bottom-chrome surface is
        // delineated from its content the same way.
        HorizontalDivider(thickness = Dp.Hairline, color = colors.surfaceStroke)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgElevated.copy(alpha = 0.92f))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hasExtras = onPlus != null || mentionAgents.isNotEmpty() || onAttach != null
            if (hasExtras) {
                Box {
                    FilledTonalIconButton(
                        onClick = { extrasOpen = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.textPrimary.copy(alpha = 0.06f),
                            contentColor = colors.textSecondary,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.compose_more_actions),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(expanded = extrasOpen, onDismissRequest = { extrasOpen = false }) {
                        if (onPlus != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.compose_insert)) },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    extrasOpen = false
                                    onPlus()
                                },
                            )
                        }
                        if (mentionAgents.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.compose_agent_mention)) },
                                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    extrasOpen = false
                                    val start = value.selection.start.coerceIn(0, value.text.length)
                                    if (value.text.getOrNull(start - 1) != '@') {
                                        val prefix = if (start > 0 && !value.text[start - 1].isWhitespace()) " @" else "@"
                                        val newText = value.text.substring(0, start) + prefix + value.text.substring(start)
                                        onValueChange(TextFieldValue(newText, TextRange(start + prefix.length)))
                                    }
                                    manualMentionPicker = true
                                },
                            )
                        }
                        if (onAttach != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.compose_attach_image)) },
                                leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    extrasOpen = false
                                    onAttach()
                                },
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .background(colors.codeSurface, RoundedCornerShape(18.dp))
                    .border(0.5.dp, colors.surfaceStroke, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        stringResource(R.string.compose_message_placeholder),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = {
                        if (it.text != value.text) manualMentionPicker = false
                        onValueChange(it)
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    minLines = 2,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.Backspace || !value.selection.collapsed) return@onPreviewKeyEvent false
                            val cursor = value.selection.start
                            if (mentionRanges.any { it.end == cursor }) {
                                onMentionDeleted(cursor)
                                true
                            } else {
                                false
                            }
                        },
                )
                pickerQuery?.let { query ->
                    AgentMentionPopup(
                        expanded = true,
                        query = query.query,
                        agents = mentionAgents,
                        onSelect = { agent ->
                            manualMentionPicker = false
                            onMentionSelected(query, agent)
                        },
                        onDismiss = { manualMentionPicker = false },
                    )
                }
            }

            if (isInFlight && hasDraft) {
                FilledIconButton(
                    onClick = onSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.compose_queue),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            FilledIconButton(
                onClick = { if (isInFlight) onStop() else onSend() },
                enabled = canSend || isInFlight,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = sendContainer,
                    contentColor = colors.onAccent,
                    disabledContainerColor = colors.accent.copy(alpha = 0.4f),
                    disabledContentColor = colors.onAccent.copy(alpha = 0.7f),
                ),
            ) {
                AnimatedContent(
                    targetState = isInFlight,
                    transitionSpec = {
                        (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.6f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f))
                    },
                    label = "send-stop-icon",
                ) { inFlight ->
                    Icon(
                        imageVector = if (inFlight) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                        contentDescription = if (inFlight) stringResource(R.string.compose_stop) else stringResource(R.string.compose_send),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
