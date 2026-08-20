package app.codeg.android.core.update

import java.io.File

interface UpdateRemote {
    suspend fun getText(url: String): String
    suspend fun download(url: String, dest: File, onProgress: (received: Long, total: Long) -> Unit)
}

interface AppUpdatePrefs {
    suspend fun lastCheckEpochMs(): Long
    suspend fun setLastCheckEpochMs(value: Long)
    suspend fun dismissedTag(): String?
    suspend fun setDismissedTag(tag: String?)
}

object AppUpdateConfig {
    const val OWNER = "tangqiaojian"
    const val REPO = "codeg-android"
    const val LATEST_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
}
