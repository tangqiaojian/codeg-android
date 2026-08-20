package app.codeg.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseResolveTest {

    private val apk = GithubAsset(
        name = "codeg-android-v1.2.4.apk",
        browserDownloadUrl = "https://github.com/tangqiaojian/codeg-android/releases/download/v1.2.4/codeg-android-v1.2.4.apk",
        size = 70_000_000,
    )
    private val sha = GithubAsset(
        name = "codeg-android-v1.2.4.apk.sha256",
        browserDownloadUrl = "https://github.com/tangqiaojian/codeg-android/releases/download/v1.2.4/codeg-android-v1.2.4.apk.sha256",
        size = 91,
    )

    @Test
    fun `offers a newer release with apk and checksum assets`() {
        val release = GithubRelease(
            tagName = "v1.2.4",
            body = "Copy fixes",
            assets = listOf(sha, apk),
        )
        val update = resolveAvailableUpdate(currentVersionName = "1.2.3", release = release)!!
        assertEquals("1.2.4", update.version)
        assertEquals("v1.2.4", update.tag)
        assertEquals("Copy fixes", update.notes)
        assertEquals(apk.browserDownloadUrl, update.apkUrl)
        assertEquals(apk.size, update.apkSize)
        assertEquals(sha.browserDownloadUrl, update.checksumUrl)
    }

    @Test
    fun `skips equal, older, draft, prerelease, and apk-less releases`() {
        val ready = GithubRelease("v1.2.3", assets = listOf(apk))
        assertNull(resolveAvailableUpdate("1.2.3", ready))
        assertNull(resolveAvailableUpdate("1.2.4", ready.copy(tagName = "v1.2.3")))
        assertNull(resolveAvailableUpdate("1.2.3", ready.copy(tagName = "v1.2.4", draft = true)))
        assertNull(resolveAvailableUpdate("1.2.3", ready.copy(tagName = "v1.2.4", prerelease = true)))
        assertNull(resolveAvailableUpdate("1.2.3", GithubRelease("v1.2.4", assets = listOf(sha))))
    }
}
