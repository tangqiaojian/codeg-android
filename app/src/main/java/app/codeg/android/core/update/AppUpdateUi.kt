package app.codeg.android.core.update

import java.io.File

sealed interface AppUpdateUi {
    data object Idle : AppUpdateUi
    data object Checking : AppUpdateUi
    data class UpToDate(val current: String) : AppUpdateUi
    data class Available(val update: AvailableUpdate) : AppUpdateUi
    data class Downloading(val update: AvailableUpdate, val received: Long, val total: Long) : AppUpdateUi
    data class ReadyToInstall(val update: AvailableUpdate, val file: File) : AppUpdateUi
    data class Error(val kind: AppUpdateError, val update: AvailableUpdate? = null) : AppUpdateUi
}

enum class AppUpdateError {
    NETWORK,
    NO_APK,
    CHECKSUM,
    SAVE_FAILED,
    INSTALL_BLOCKED,
    UNKNOWN,
}
