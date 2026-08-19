package app.codeg.android.feature.sessiondetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.codeg.android.R
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.SessionFailureAction
import app.codeg.android.core.model.SessionFailureRecord
import app.codeg.android.core.model.SessionFailures

@Composable
fun SessionFailureBanner(
    failures: List<SessionFailureRecord>,
    canOpenNewSession: Boolean,
    canOpenSettings: Boolean = false,
    onAction: (SessionFailureAction, SessionFailureRecord) -> Unit,
    onDismiss: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = SessionFailures.activeView(failures)
    val recovered = SessionFailures.mostRecentRecoveredWarning(failures)
    if (view.errors.isEmpty() && view.warning == null && recovered == null) return
    Column(modifier.fillMaxWidth()) {
        view.errors.forEach { record ->
            FailureStrip(
                record = record,
                error = true,
                extra = null,
                canOpenNewSession = canOpenNewSession,
                canOpenSettings = canOpenSettings,
                onAction = onAction,
                onDismiss = { onDismiss(listOf(record.id)) },
            )
        }
        view.warning?.let { warning ->
            FailureStrip(
                record = warning,
                error = false,
                extra = view.hiddenWarnings.takeIf { it > 0 }?.let { stringResource(R.string.session_failure_more, it) },
                canOpenNewSession = canOpenNewSession,
                canOpenSettings = canOpenSettings,
                onAction = onAction,
                onDismiss = { onDismiss(view.warningIds) },
            )
        }
        recovered?.let { record ->
            FailureStrip(
                record = record,
                error = false,
                extra = stringResource(R.string.session_failure_recovered),
                canOpenNewSession = false,
                canOpenSettings = false,
                recovered = true,
                onAction = onAction,
                onDismiss = { onDismiss(listOf(record.id)) },
            )
        }
    }
}

@Composable
private fun FailureStrip(
    record: SessionFailureRecord,
    error: Boolean,
    extra: String?,
    canOpenNewSession: Boolean,
    canOpenSettings: Boolean,
    onAction: (SessionFailureAction, SessionFailureRecord) -> Unit,
    onDismiss: () -> Unit,
    recovered: Boolean = false,
) {
    val colors = CodegTheme.colors
    val background = when {
        recovered -> colors.textTertiary.copy(alpha = 0.10f)
        error -> colors.danger.copy(alpha = 0.14f)
        else -> Color(0xFFE49A39).copy(alpha = 0.16f)
    }
    val title = record.title.ifBlank { categoryLabel(record.category) }
    val actions = SessionFailures.knownActions(record).filter { action ->
        when (action) {
            SessionFailureAction.LOGIN -> canOpenSettings && !recovered
            SessionFailureAction.NEW_SESSION -> canOpenNewSession && !recovered && error
            SessionFailureAction.RETRY -> !recovered && error
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                extra?.let { Text(it, color = colors.textSecondary, fontSize = 12.sp) }
                record.details?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_dismiss), tint = colors.textSecondary)
            }
        }
        if (actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                actions.forEach { action ->
                    TextButton(onClick = { onAction(action, record) }) {
                        Text(actionLabel(action), color = colors.accent, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: String): String = stringResource(
    when (category) {
        "connection" -> R.string.session_failure_category_connection
        "access" -> R.string.session_failure_category_access
        "limit" -> R.string.session_failure_category_limit
        "request" -> R.string.session_failure_category_request
        "service" -> R.string.session_failure_category_service
        else -> R.string.session_failure_category_unknown
    },
)

@Composable
private fun actionLabel(action: SessionFailureAction): String = stringResource(
    when (action) {
        SessionFailureAction.RETRY -> R.string.session_failure_action_retry
        SessionFailureAction.LOGIN -> R.string.session_failure_action_login
        SessionFailureAction.NEW_SESSION -> R.string.session_failure_action_new_session
    },
)
