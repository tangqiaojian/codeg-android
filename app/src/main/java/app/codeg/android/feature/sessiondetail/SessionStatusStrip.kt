package app.codeg.android.feature.sessiondetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.codeg.android.R
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.SessionFailureAction
import app.codeg.android.core.model.SessionFailureRecord
import app.codeg.android.core.model.SessionFailures

/**
 * Collapsible top-center strip for session action status (send/reconnect) and
 * failure banners. Permission / question / plan cards stay at the composer.
 */
@Composable
fun SessionStatusStrip(
    sendStatus: String?,
    failures: List<SessionFailureRecord>,
    notice: String?,
    canOpenNewSession: Boolean,
    canOpenSettings: Boolean,
    onFailureAction: (SessionFailureAction, SessionFailureRecord) -> Unit,
    onDismissFailures: (List<String>) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = SessionFailures.activeView(failures)
    val recovered = SessionFailures.mostRecentRecoveredWarning(failures)
    val hasFailures = view.errors.isNotEmpty() || view.warning != null || recovered != null
    val hasNotice = !notice.isNullOrBlank()
    val hasStatus = !sendStatus.isNullOrBlank()
    if (!hasFailures && !hasNotice && !hasStatus) return

    val colors = CodegTheme.colors
    val forceOpen = hasFailures || hasNotice
    var expanded by rememberSaveable { mutableStateOf(forceOpen) }
    LaunchedEffect(forceOpen) {
        if (forceOpen) expanded = true
    }

    val headline = when {
        hasFailures -> view.errors.firstOrNull()?.title
            ?: view.warning?.title
            ?: recovered?.title
            ?: sendStatus
        hasNotice -> notice
        else -> sendStatus
    }.orEmpty()

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.bgElevated.copy(alpha = 0.94f))
                .border(0.5.dp, colors.surfaceStroke, shape),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    headline,
                    color = if (hasFailures) colors.danger else colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.session_status_collapse else R.string.session_status_expand,
                        ),
                        tint = colors.textTertiary,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(160)),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (hasStatus && (hasFailures || hasNotice)) {
                        Text(
                            sendStatus.orEmpty(),
                            fontSize = 12.sp,
                            color = colors.textTertiary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                    SessionFailureBanner(
                        failures = failures,
                        canOpenNewSession = canOpenNewSession,
                        canOpenSettings = canOpenSettings,
                        onAction = onFailureAction,
                        onDismiss = onDismissFailures,
                    )
                    if (hasNotice) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.danger.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                notice.orEmpty(),
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onDismissNotice) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.common_dismiss),
                                    tint = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
