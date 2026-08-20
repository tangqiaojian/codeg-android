package app.codeg.android.feature.update

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.codeg.android.R
import app.codeg.android.core.designsystem.component.PrimaryButton
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.update.ApkInstaller
import app.codeg.android.core.update.AppUpdateError
import app.codeg.android.core.update.AppUpdateUi
import app.codeg.android.core.update.AvailableUpdate
import java.io.File

@Composable
fun AppUpdatePanel(
    ui: AppUpdateUi,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodegTheme.colors
    val context = LocalContext.current
    val canInstall = rememberCanInstall(context)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bgElevated.copy(alpha = 0.55f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.app_update_section),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )
        when (val s = ui) {
            AppUpdateUi.Idle -> {
                PrimaryButton(stringResource(R.string.app_update_check), onCheck)
            }
            AppUpdateUi.Checking -> {
                PrimaryButton(stringResource(R.string.app_update_checking), onClick = {}, loading = true)
            }
            is AppUpdateUi.UpToDate -> {
                Text(stringResource(R.string.app_update_up_to_date), fontSize = 13.sp, color = colors.textTertiary)
                PrimaryButton(stringResource(R.string.app_update_check), onCheck)
            }
            is AppUpdateUi.Available -> AvailableBody(s.update, onDownload)
            is AppUpdateUi.Downloading -> DownloadingBody(s, onCancel)
            is AppUpdateUi.ReadyToInstall -> ReadyBody(s, canInstall, onInstall)
            is AppUpdateUi.Error -> ErrorBody(s, onCheck, onDownload)
        }
    }
}

@Composable
private fun AvailableBody(update: AvailableUpdate, onDownload: () -> Unit) {
    val colors = CodegTheme.colors
    Text(
        stringResource(R.string.app_update_available, update.version),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.accent,
    )
    if (update.apkSize > 0) {
        Text(formatBytes(update.apkSize), fontSize = 12.sp, color = colors.textTertiary)
    }
    Notes(update.notes)
    PrimaryButton(stringResource(R.string.app_update_download), onDownload)
}

@Composable
private fun DownloadingBody(state: AppUpdateUi.Downloading, onCancel: () -> Unit) {
    val colors = CodegTheme.colors
    val total = state.total.coerceAtLeast(0L)
    val fraction = if (total > 0L) (state.received.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    Text(
        if (state.received <= 0L) {
            stringResource(R.string.app_update_connecting)
        } else {
            stringResource(R.string.app_update_downloading, formatBytes(state.received), formatBytes(total))
        },
        fontSize = 13.sp,
        color = colors.textSecondary,
    )
    if (total > 0L) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.hairline)
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.hairline)
    }
    PrimaryButton(stringResource(R.string.app_update_cancel), onCancel)
}

@Composable
private fun ReadyBody(state: AppUpdateUi.ReadyToInstall, canInstall: Boolean, onInstall: (File) -> Unit) {
    val colors = CodegTheme.colors
    Text(
        stringResource(R.string.app_update_available, state.update.version),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.accent,
    )
    if (!canInstall) {
        Text(stringResource(R.string.app_update_allow_unknown), fontSize = 13.sp, color = colors.textSecondary)
    }
    PrimaryButton(
        if (canInstall) stringResource(R.string.app_update_install) else stringResource(R.string.app_update_allow_unknown_action),
        onClick = { onInstall(state.file) },
    )
}

@Composable
private fun ErrorBody(state: AppUpdateUi.Error, onCheck: () -> Unit, onDownload: () -> Unit) {
    val colors = CodegTheme.colors
    Text(stringResource(state.kind.messageRes()), fontSize = 13.sp, color = colors.danger)
    if (state.update != null) {
        PrimaryButton(stringResource(R.string.app_update_retry), onDownload)
    } else {
        PrimaryButton(stringResource(R.string.app_update_retry), onCheck)
    }
}

@Composable
private fun Notes(notes: String) {
    if (notes.isBlank()) return
    Text(
        notes,
        fontSize = 12.sp,
        color = CodegTheme.colors.textTertiary,
        modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun rememberCanInstall(context: Context): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return remember(tick) { ApkInstaller.canInstall(context) }
}

internal fun AppUpdateError.messageRes(): Int = when (this) {
    AppUpdateError.NETWORK -> R.string.app_update_error_network
    AppUpdateError.NO_APK -> R.string.app_update_error_no_apk
    AppUpdateError.CHECKSUM -> R.string.app_update_error_checksum
    AppUpdateError.SAVE_FAILED -> R.string.app_update_error_save
    AppUpdateError.INSTALL_BLOCKED -> R.string.app_update_error_install_blocked
    AppUpdateError.UNKNOWN -> R.string.app_update_error_unknown
}

internal fun formatBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${n / 1024} KB"
    else -> String.format("%.1f MB", n / (1024.0 * 1024.0))
}
