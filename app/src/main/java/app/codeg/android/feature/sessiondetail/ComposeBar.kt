package app.codeg.android.feature.sessiondetail

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.codeg.android.R
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.AcpAgentInfo
import app.codeg.android.core.model.AgentMention
import app.codeg.android.core.model.AgentMentionQuery
import app.codeg.android.core.model.findActiveAgentMentionQuery

/**
 * Session composer laid out like a messaging bar: circular + on the left, a
 * pill field in the middle, and a circular trailing action on the right
 * (voice when empty, send when there is a draft, stop while the turn is live).
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
    onVoiceCommit: ((text: String, sendNow: Boolean) -> Unit)? = null,
) {
    val colors = CodegTheme.colors
    val context = LocalContext.current
    var extrasOpen by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val voiceSession = remember { object { var prefix: String = "" } }
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val onVoiceCommitState = rememberUpdatedState(onVoiceCommit)
    val valueState = rememberUpdatedState(value)
    val applySpoken = rememberUpdatedState { spoken: String, isFinal: Boolean ->
        val prefix = voiceSession.prefix
        val merged = VoiceDraft.merge(prefix, spoken)
        onValueChangeState.value(TextFieldValue(merged, TextRange(merged.length)))
        if (isFinal) {
            listening = false
            val commit = onVoiceCommitState.value
            if (commit != null && VoiceDraft.shouldAutoSend(prefix) && merged.isNotBlank()) {
                commit(merged, true)
            }
        }
    }
    val dictation = remember {
        VoiceDictation(
            context = context,
            onSpoken = { spoken, isFinal -> applySpoken.value(spoken, isFinal) },
            onError = {
                listening = false
                Toast.makeText(context, R.string.compose_voice_error, Toast.LENGTH_SHORT).show()
            },
            onListening = { listening = it },
        )
    }
    DisposableEffect(dictation) { onDispose { dictation.release() } }
    val speechActivity = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (spoken.isBlank()) {
            Toast.makeText(context, R.string.compose_voice_error, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        applySpoken.value(spoken, true)
    }
    fun beginVoice() {
        voiceSession.prefix = valueState.value.text
        if (dictation.inlineAvailable) {
            dictation.start()
            return
        }
        val intent = VoiceDictation.recognitionIntent()
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, R.string.compose_voice_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        listening = true
        runCatching { speechActivity.launch(intent) }.onFailure {
            listening = false
            Toast.makeText(context, R.string.compose_voice_unavailable, Toast.LENGTH_LONG).show()
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginVoice() else {
            Toast.makeText(context, R.string.compose_voice_permission, Toast.LENGTH_LONG).show()
        }
    }
    fun toggleVoice() {
        if (listening) {
            dictation.stop()
            return
        }
        if (!VoiceDictation.canLaunchRecognizer(context)) {
            Toast.makeText(context, R.string.compose_voice_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) beginVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
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
    val chrome = colors.textPrimary.copy(alpha = if (colors.isDark) 0.10f else 0.07f)
    val trailing = when {
        listening -> ComposerTrailing.StopListen
        hasDraft -> if (isInFlight) ComposerTrailing.Queue else ComposerTrailing.Send
        isInFlight -> ComposerTrailing.StopRun
        else -> ComposerTrailing.Voice
    }
    val trailingContainer by animateColorAsState(
        targetValue = when (trailing) {
            ComposerTrailing.StopListen -> colors.accent.copy(alpha = 0.22f)
            ComposerTrailing.Send, ComposerTrailing.Queue -> colors.accent
            ComposerTrailing.StopRun -> colors.danger
            ComposerTrailing.Voice -> chrome
        },
        animationSpec = tween(220),
        label = "composer-trailing-container",
    )
    val trailingContent by animateColorAsState(
        targetValue = when (trailing) {
            ComposerTrailing.StopListen -> colors.accent
            ComposerTrailing.Send, ComposerTrailing.Queue, ComposerTrailing.StopRun -> colors.onAccent
            ComposerTrailing.Voice -> colors.textSecondary
        },
        animationSpec = tween(220),
        label = "composer-trailing-content",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val hasExtras = onPlus != null || mentionAgents.isNotEmpty() || onAttach != null || isInFlight
            if (hasExtras) {
                Box {
                    ComposerCircleButton(
                        onClick = { extrasOpen = true },
                        container = chrome,
                        content = colors.textSecondary,
                        image = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.compose_more_actions),
                    )
                    DropdownMenu(expanded = extrasOpen, onDismissRequest = { extrasOpen = false }) {
                        if (isInFlight) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.compose_stop)) },
                                leadingIcon = { Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    extrasOpen = false
                                    onStop()
                                },
                            )
                        }
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
            val fieldShape = RoundedCornerShape(percent = 50)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(fieldShape)
                    .background(chrome)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        stringResource(if (listening) R.string.compose_voice_listening else R.string.compose_message_placeholder),
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
                    minLines = 1,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = if (hasDraft) ImeAction.Send else ImeAction.Default),
                    keyboardActions = KeyboardActions(onSend = { if (hasDraft) onSend() }),
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

            ComposerCircleButton(
                onClick = {
                    when (trailing) {
                        ComposerTrailing.StopListen, ComposerTrailing.Voice -> toggleVoice()
                        ComposerTrailing.Send, ComposerTrailing.Queue -> onSend()
                        ComposerTrailing.StopRun -> onStop()
                    }
                },
                container = trailingContainer,
                content = trailingContent,
                image = when (trailing) {
                    ComposerTrailing.StopListen, ComposerTrailing.StopRun -> Icons.Rounded.Stop
                    ComposerTrailing.Send, ComposerTrailing.Queue -> Icons.Rounded.ArrowUpward
                    ComposerTrailing.Voice -> Icons.Rounded.GraphicEq
                },
                contentDescription = stringResource(
                    when (trailing) {
                        ComposerTrailing.StopListen -> R.string.compose_voice_stop
                        ComposerTrailing.StopRun -> R.string.compose_stop
                        ComposerTrailing.Send -> R.string.compose_send
                        ComposerTrailing.Queue -> R.string.compose_queue
                        ComposerTrailing.Voice -> R.string.compose_voice
                    },
                ),
            ) {
                AnimatedContent(
                    targetState = trailing,
                    transitionSpec = {
                        (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.6f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f))
                    },
                    label = "composer-trailing-icon",
                ) { action ->
                    Icon(
                        imageVector = when (action) {
                            ComposerTrailing.StopListen, ComposerTrailing.StopRun -> Icons.Rounded.Stop
                            ComposerTrailing.Send, ComposerTrailing.Queue -> Icons.Rounded.ArrowUpward
                            ComposerTrailing.Voice -> Icons.Rounded.GraphicEq
                        },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private enum class ComposerTrailing { Voice, StopListen, Send, Queue, StopRun }

@Composable
private fun ComposerCircleButton(
    onClick: () -> Unit,
    container: Color,
    content: Color,
    image: ImageVector,
    contentDescription: String,
    icon: (@Composable () -> Unit)? = null,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        if (icon != null) {
            icon()
        } else {
            Icon(image, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}
