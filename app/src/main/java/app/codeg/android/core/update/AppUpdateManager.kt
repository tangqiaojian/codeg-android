package app.codeg.android.core.update

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class AppUpdateManager(
    private val currentVersionName: () -> String,
    private val remote: UpdateRemote,
    private val prefs: AppUpdatePrefs,
    private val cacheDir: File,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher,
    private val latestApiUrl: String = AppUpdateConfig.LATEST_API,
) {

    private val mutex = Mutex()
    private val _ui = MutableStateFlow<AppUpdateUi>(AppUpdateUi.Idle)
    val ui: StateFlow<AppUpdateUi> = _ui.asStateFlow()
    @Volatile private var dismissed: String? = null

    fun shouldShowLaunchPrompt(): Boolean {
        val available = (_ui.value as? AppUpdateUi.Available)?.update ?: return false
        return UpdatePolicy.shouldPrompt(available.tag, dismissed)
    }

    suspend fun check(force: Boolean) = mutex.withLock {
        when (_ui.value) {
            is AppUpdateUi.Downloading, is AppUpdateUi.ReadyToInstall -> return@withLock
            else -> Unit
        }
        withContext(io) {
            val last = prefs.lastCheckEpochMs()
            dismissed = prefs.dismissedTag()
            if (!UpdatePolicy.shouldNetworkCheck(last, clock(), force)) return@withContext
            _ui.value = AppUpdateUi.Checking
            val current = currentVersionName()
            val json = try {
                val url = UpdateUrlPicker.pick(UpdateMirrors.candidates(latestApiUrl)) { remote.probe(it) }
                remote.getText(url)
            } catch (_: Exception) {
                _ui.value = AppUpdateUi.Error(AppUpdateError.NETWORK)
                return@withContext
            }
            prefs.setLastCheckEpochMs(clock())
            val release = try {
                parseGithubRelease(json)
            } catch (_: Exception) {
                _ui.value = AppUpdateUi.Error(AppUpdateError.NETWORK)
                return@withContext
            }
            val update = resolveAvailableUpdate(current, release)
            val remoteVer = AppVersion.parse(release.tagName)
            val currentVer = AppVersion.parse(current)
            _ui.value = when {
                update != null -> AppUpdateUi.Available(update)
                remoteVer != null && currentVer != null && !remoteVer.isNewerThan(currentVer) ->
                    AppUpdateUi.UpToDate(current)
                else -> AppUpdateUi.Error(AppUpdateError.NO_APK)
            }
        }
    }

    suspend fun download() {
        val available = mutex.withLock {
            when (val s = _ui.value) {
                is AppUpdateUi.Downloading, is AppUpdateUi.ReadyToInstall -> return
                is AppUpdateUi.Available -> s.update
                is AppUpdateUi.Error -> s.update
                else -> null
            }
        } ?: return
        val dest = File(File(cacheDir, "updates"), "codeg-${available.version}.apk")
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        _ui.value = AppUpdateUi.Downloading(available, 0L, available.apkSize)
        try {
            withContext(io) {
                val url = UpdateUrlPicker.pick(UpdateMirrors.candidates(available.apkUrl)) { remote.probe(it) }
                remote.download(url, dest) { received, total ->
                    _ui.value = AppUpdateUi.Downloading(available, received, total.coerceAtLeast(0L))
                }
                if (!dest.exists() || dest.length() == 0L) {
                    dest.delete()
                    _ui.value = AppUpdateUi.Error(AppUpdateError.SAVE_FAILED, available)
                    return@withContext
                }
                val checksumUrl = available.checksumUrl
                if (checksumUrl != null) {
                    val body = try {
                        val shaUrl = UpdateUrlPicker.pick(UpdateMirrors.candidates(checksumUrl)) { remote.probe(it) }
                        remote.getText(shaUrl)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        dest.delete()
                        _ui.value = AppUpdateUi.Error(AppUpdateError.CHECKSUM, available)
                        return@withContext
                    }
                    val expected = Checksum.parseSha256File(body)
                    if (expected == null || !Checksum.matches(dest, expected)) {
                        dest.delete()
                        _ui.value = AppUpdateUi.Error(AppUpdateError.CHECKSUM, available)
                        return@withContext
                    }
                }
                _ui.value = AppUpdateUi.ReadyToInstall(available, dest)
            }
        } catch (e: CancellationException) {
            dest.delete()
            _ui.value = AppUpdateUi.Available(available)
            throw e
        } catch (_: Exception) {
            dest.delete()
            _ui.value = AppUpdateUi.Error(AppUpdateError.NETWORK, available)
        }
    }

    suspend fun dismissPrompt() {
        val tag = (_ui.value as? AppUpdateUi.Available)?.update?.tag ?: return
        dismissed = tag
        withContext(io) { prefs.setDismissedTag(tag) }
    }

    suspend fun cancelDownload() {
        val s = _ui.value
        val update = when (s) {
            is AppUpdateUi.Downloading -> s.update
            is AppUpdateUi.ReadyToInstall -> s.update
            else -> return
        }
        File(File(cacheDir, "updates"), "codeg-${update.version}.apk").delete()
        _ui.value = AppUpdateUi.Available(update)
    }
}
