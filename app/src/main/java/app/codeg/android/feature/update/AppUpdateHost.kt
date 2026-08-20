package app.codeg.android.feature.update

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.update.AppUpdateUi

/** Cold-start metadata check + prompt. Download starts only after the user confirms. */
@Composable
fun AppUpdateHost(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.check(force = false) }
    LaunchedEffect(ui) {
        if (ui is AppUpdateUi.Available && viewModel.shouldShowLaunchPrompt()) open = true
    }
    if (!open) return

    val colors = CodegTheme.colors
    when (val s = ui) {
        is AppUpdateUi.Available -> AlertDialog(
            onDismissRequest = { viewModel.dismiss(); open = false },
            containerColor = colors.bgElevated,
            title = { Text(stringResource(R.string.app_update_available, s.update.version), color = colors.textPrimary) },
            text = {
                if (s.update.notes.isNotBlank()) {
                    Text(
                        s.update.notes,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::download) {
                    Text(stringResource(R.string.app_update_download), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismiss(); open = false }) {
                    Text(stringResource(R.string.app_update_later), color = colors.textSecondary)
                }
            },
        )
        is AppUpdateUi.Downloading -> AlertDialog(
            onDismissRequest = { },
            containerColor = colors.bgElevated,
            title = { Text(stringResource(R.string.app_update_available, s.update.version), color = colors.textPrimary) },
            text = {
                val total = s.total.coerceAtLeast(0L)
                val fraction = if (total > 0L) (s.received.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                androidx.compose.foundation.layout.Column {
                    Text(
                        stringResource(R.string.app_update_downloading, formatBytes(s.received), formatBytes(total)),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    if (total > 0L) LinearProgressIndicator(progress = { fraction }, color = colors.accent)
                    else LinearProgressIndicator(color = colors.accent)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::cancel) {
                    Text(stringResource(R.string.app_update_cancel), color = colors.textSecondary)
                }
            },
        )
        is AppUpdateUi.ReadyToInstall -> AlertDialog(
            onDismissRequest = { open = false },
            containerColor = colors.bgElevated,
            title = { Text(stringResource(R.string.app_update_available, s.update.version), color = colors.textPrimary) },
            text = { Text(stringResource(R.string.app_update_ready), color = colors.textSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.install(context, s.file) }) {
                    Text(stringResource(R.string.app_update_install), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.app_update_later), color = colors.textSecondary)
                }
            },
        )
        is AppUpdateUi.Error -> AlertDialog(
            onDismissRequest = { open = false },
            containerColor = colors.bgElevated,
            title = { Text(stringResource(R.string.app_update_section), color = colors.textPrimary) },
            text = { Text(stringResource(s.kind.messageRes()), color = colors.danger, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { if (s.update != null) viewModel.download() else viewModel.check(true) }) {
                    Text(stringResource(R.string.app_update_retry), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.common_ok), color = colors.textSecondary)
                }
            },
        )
        else -> Unit
    }
}
