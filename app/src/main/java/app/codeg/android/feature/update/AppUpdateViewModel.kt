package app.codeg.android.feature.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.codeg.android.core.update.ApkInstaller
import app.codeg.android.core.update.AppUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val manager: AppUpdateManager,
) : ViewModel() {
    val ui = manager.ui
    private var downloadJob: Job? = null

    fun check(force: Boolean = true) {
        viewModelScope.launch { manager.check(force) }
    }

    fun download() {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch { manager.download() }
    }

    fun cancel() {
        downloadJob?.cancel()
        viewModelScope.launch { manager.cancelDownload() }
    }

    fun dismiss() {
        viewModelScope.launch { manager.dismissPrompt() }
    }

    fun shouldShowLaunchPrompt(): Boolean = manager.shouldShowLaunchPrompt()

    fun install(context: Context, file: File) {
        if (!ApkInstaller.canInstall(context)) {
            ApkInstaller.openUnknownSourcesSettings(context)
            return
        }
        ApkInstaller.install(context, file)
    }
}
