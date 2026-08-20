package app.codeg.android.core.update

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val cache = File(System.getProperty("java.io.tmpdir"), "codeg-update-test-${System.nanoTime()}").apply { mkdirs() }

    private fun manager(
        remote: FakeRemote,
        prefs: MemoryPrefs = MemoryPrefs(),
        version: String = "1.2.3",
        now: Long = 1_000_000L,
    ) = AppUpdateManager(
        currentVersionName = { version },
        remote = remote,
        prefs = prefs,
        cacheDir = cache,
        clock = { now },
        io = dispatcher,
        latestApiUrl = "https://api.github.com/repos/tangqiaojian/codeg-android/releases/latest",
    )

    @Test
    fun `check surfaces a newer apk`() = runTest(dispatcher) {
        val remote = FakeRemote(json = releaseJson(apkUrl = "https://example.com/app.apk"))
        val mgr = manager(remote)
        mgr.check(force = true)
        val ui = mgr.ui.value as AppUpdateUi.Available
        assertEquals("1.2.4", ui.update.version)
        assertEquals("https://example.com/app.apk", ui.update.apkUrl)
    }

    @Test
    fun `skips the network when the interval has not elapsed`() = runTest(dispatcher) {
        val remote = FakeRemote(json = releaseJson())
        val prefs = MemoryPrefs(lastCheck = 999_000L)
        val mgr = manager(remote, prefs = prefs, now = 1_000_000L)
        mgr.check(force = false)
        assertTrue(mgr.ui.value is AppUpdateUi.Idle)
        assertEquals(0, remote.fetchCount)
    }

    @Test
    fun `download uses a reachable mirror when origin cannot be probed`() = runTest(dispatcher) {
        val origin = "https://github.com/tangqiaojian/codeg-android/releases/download/v1.2.4/app.apk"
        val mirror = "https://ghproxy.net/$origin"
        val apk = "apk-bytes".toByteArray()
        val remote = FakeRemote(
            json = releaseJson(apkUrl = origin),
            files = mapOf(mirror to apk),
        )
        val mgr = manager(remote)
        mgr.check(force = true)
        mgr.download()
        val ready = mgr.ui.value as AppUpdateUi.ReadyToInstall
        assertEquals(apk.toList(), ready.file.readBytes().toList())
    }

    @Test
    fun `download verifies sha256 and lands a file`() = runTest(dispatcher) {
        val apk = "apk-bytes".toByteArray()
        val hex = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        val remote = FakeRemote(
            json = releaseJson(
                apkUrl = "https://example.com/app.apk",
                shaUrl = "https://example.com/app.apk.sha256",
            ),
            files = mapOf(
                "https://example.com/app.apk" to apk,
                "https://example.com/app.apk.sha256" to "$hex  app.apk\n".toByteArray(),
            ),
        )
        val mgr = manager(remote)
        mgr.check(force = true)
        mgr.download()
        val ready = mgr.ui.value as AppUpdateUi.ReadyToInstall
        assertTrue(ready.file.exists())
        assertEquals(apk.toList(), ready.file.readBytes().toList())
    }

    @Test
    fun `checksum mismatch is an error and deletes the file`() = runTest(dispatcher) {
        val apk = "apk-bytes".toByteArray()
        val remote = FakeRemote(
            json = releaseJson(
                apkUrl = "https://example.com/app.apk",
                shaUrl = "https://example.com/app.apk.sha256",
            ),
            files = mapOf(
                "https://example.com/app.apk" to apk,
                "https://example.com/app.apk.sha256" to ("0".repeat(64) + "  app.apk\n").toByteArray(),
            ),
        )
        val mgr = manager(remote)
        mgr.check(force = true)
        mgr.download()
        val err = mgr.ui.value as AppUpdateUi.Error
        assertEquals(AppUpdateError.CHECKSUM, err.kind)
        assertTrue(cache.walkTopDown().filter { it.extension == "apk" }.none { it.exists() && it.length() > 0 })
    }

    @Test
    fun `later snoozes the launch prompt`() = runTest(dispatcher) {
        val remote = FakeRemote(json = releaseJson())
        val prefs = MemoryPrefs()
        val mgr = manager(remote, prefs)
        mgr.check(force = true)
        mgr.dismissPrompt()
        assertEquals("v1.2.4", prefs.dismissedTag())
        assertTrue(mgr.ui.value is AppUpdateUi.Available)
        assertFalse(mgr.shouldShowLaunchPrompt())
    }

    private fun assertFalse(condition: Boolean) = org.junit.Assert.assertFalse(condition)

    private fun releaseJson(
        apkUrl: String = "https://example.com/app.apk",
        shaUrl: String? = null,
    ): String {
        val sha = if (shaUrl == null) "" else """
            ,{"name":"codeg-android-v1.2.4.apk.sha256","browser_download_url":"$shaUrl","size":91}
        """.trimIndent()
        return """
            {"tag_name":"v1.2.4","body":"notes","draft":false,"prerelease":false,"assets":[
              {"name":"codeg-android-v1.2.4.apk","browser_download_url":"$apkUrl","size":9}$sha
            ]}
        """.trimIndent()
    }
}

class MemoryPrefs(
    var lastCheck: Long = 0L,
    var dismissed: String? = null,
) : AppUpdatePrefs {
    override suspend fun lastCheckEpochMs(): Long = lastCheck
    override suspend fun setLastCheckEpochMs(value: Long) { lastCheck = value }
    override suspend fun dismissedTag(): String? = dismissed
    override suspend fun setDismissedTag(tag: String?) { dismissed = tag }
}

class FakeRemote(
    private val json: String,
    private val files: Map<String, ByteArray> = emptyMap(),
) : UpdateRemote {
    var fetchCount = 0
    override suspend fun getText(url: String): String {
        fetchCount += 1
        files[url]?.let { return it.toString(Charsets.UTF_8) }
        if (url.contains("releases/latest") || url.contains("api.github.com")) return json
        error("unexpected getText $url")
    }

    override suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val bytes = files[url] ?: error("unexpected download $url")
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }

    override suspend fun probe(url: String): Boolean {
        if (files.containsKey(url)) return true
        return url.contains("releases/latest") || url.contains("api.github.com")
    }
}
