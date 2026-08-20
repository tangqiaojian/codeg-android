package app.codeg.android.core.update

import app.codeg.android.core.network.CodegJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
data class GithubRelease(
    val tagName: String,
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long = 0,
)

data class AvailableUpdate(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val checksumUrl: String?,
)

fun parseGithubRelease(json: String): GithubRelease =
    CodegJson.response.decodeFromString(json)

fun resolveAvailableUpdate(currentVersionName: String, release: GithubRelease): AvailableUpdate? {
    if (release.draft || release.prerelease) return null
    val remote = AppVersion.parse(release.tagName) ?: return null
    val current = AppVersion.parse(currentVersionName) ?: return null
    if (remote <= current) return null
    val apk = pickApk(release.assets) ?: return null
    val checksum = release.assets.firstOrNull { it.name == "${apk.name}.sha256" }
        ?: release.assets.firstOrNull { it.name.endsWith(".apk.sha256") }
    return AvailableUpdate(
        version = remote.toString(),
        tag = release.tagName,
        notes = release.body.trim(),
        apkUrl = apk.browserDownloadUrl,
        apkSize = apk.size,
        checksumUrl = checksum?.browserDownloadUrl,
    )
}

private fun pickApk(assets: List<GithubAsset>): GithubAsset? {
    val apks = assets.filter { it.name.endsWith(".apk") && !it.name.endsWith(".sha256") }
    return apks.firstOrNull { it.name.startsWith("codeg-android-") && it.name.endsWith(".apk") }
        ?: apks.firstOrNull()
}
