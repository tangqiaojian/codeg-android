# Android in-app updates

**Date:** 2026-08-19

**Status:** Approved; implement in 1.2.4.

## Goal

The sideloaded Android client discovers a newer GitHub Release for
`tangqiaojian/codeg-android`, downloads the APK inside the app, verifies it, and
hands it to the system installer. Users do not open a browser or the GitHub
website. China-reachable GitHub mirrors are first-class fallbacks.

## Out of scope

- Play Store / Play Core.
- Changing Settings → System → Server Update (`check_app_update` on the Codeg
  server). That remains a server-version check.
- Silent background APK downloads.
- Updating from a different signing key (the system installer already rejects
  that; we surface a clear error if install cannot start).

## User experience

- **Settings → About:** current app version, Check for updates, release notes
  when a newer APK exists, download progress, Cancel, Install. English + zh-rCN.
- **Launch:** after the splash, if a check is due (12 hours since last network
  check) fetch *metadata only*. If a newer APK exists and that tag was not
  dismissed, show a dialog: notes, Download and install, Later.
- Later snoozes that tag until a still-newer tag appears, or until the user
  checks manually in About (About always shows an available update).
- Download starts only after an explicit tap. Progress is visible. Cancel
  deletes the partial file.

## Source of truth

Latest GitHub Release JSON:

`https://api.github.com/repos/tangqiaojian/codeg-android/releases/latest`

Try in order: direct URL, `https://ghproxy.net/<url>`, `https://ghfast.top/<url>`.
Same fallback for asset download URLs.

- Ignore `draft` and `prerelease`.
- Prefer asset `codeg-android-vX.Y.Z.apk`; otherwise the first `*.apk` that is
  not a `.sha256` file.
- Companion `*.apk.sha256` is optional. If present, the hex digest must match
  before install. If absent, HTTPS + mirror is accepted.
- Compare parsed `tag_name` (`v1.2.4`) with `PackageInfo.versionName`. Offer
  the update only when remote > current.

## Install

- Permission `REQUEST_INSTALL_PACKAGES`.
- APK stored under `cacheDir/updates/`.
- `FileProvider` authority `${applicationId}.fileprovider`.
- If `canRequestPackageInstalls()` is false, open the per-app unknown-sources
  screen, then retry after the user returns.
- Debug builds (`applicationIdSuffix .debug`) update other debug builds signed
  with the same debug keystore.

## Errors

Network / no APK asset / checksum mismatch / disk / install permission each have
a dedicated string. Never crash the settings tab or the main shell.

## Testing

JVM unit tests cover version compare, mirror URL order, sha256 file parse,
digest verify, GitHub JSON → candidate, check-due policy, and dismiss policy.
HTTP and the system installer are not unit-tested.

## Rollout

1.2.4 is the first build that contains the updater; installing it is still a
manual download. Subsequent releases update in-app.
