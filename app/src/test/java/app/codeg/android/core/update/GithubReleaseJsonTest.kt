package app.codeg.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GithubReleaseJsonTest {

    @Test
    fun `decodes GitHub latest-release JSON`() {
        val json = """
            {
              "tag_name": "v1.2.4",
              "draft": false,
              "prerelease": false,
              "body": "In-app updates",
              "assets": [
                {
                  "name": "codeg-android-v1.2.4.apk",
                  "browser_download_url": "https://example.com/app.apk",
                  "size": 42
                }
              ]
            }
        """.trimIndent()
        val release = parseGithubRelease(json)
        assertEquals("v1.2.4", release.tagName)
        assertFalse(release.draft)
        assertEquals("In-app updates", release.body)
        assertEquals("codeg-android-v1.2.4.apk", release.assets.single().name)
        assertEquals(42L, release.assets.single().size)
    }

    @Test
    fun `ignores GitHub fields the client does not model`() {
        val json = """
            {
              "url": "https://api.github.com/repos/tangqiaojian/codeg-android/releases/1",
              "tag_name": "v1.2.3",
              "target_commitish": "main",
              "draft": false,
              "prerelease": false,
              "body": "",
              "assets": [
                {
                  "url": "https://api.github.com/assets/1",
                  "name": "codeg-android-v1.2.3.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "state": "uploaded",
                  "size": 69250974,
                  "browser_download_url": "https://github.com/tangqiaojian/codeg-android/releases/download/v1.2.3/codeg-android-v1.2.3.apk"
                }
              ]
            }
        """.trimIndent()
        val release = parseGithubRelease(json)
        assertEquals("v1.2.3", release.tagName)
        assertEquals(69250974L, release.assets.single().size)
        val update = resolveAvailableUpdate("1.2.2", release)!!
        assertEquals("1.2.3", update.version)
        assertEquals(69250974L, update.apkSize)
    }
}
