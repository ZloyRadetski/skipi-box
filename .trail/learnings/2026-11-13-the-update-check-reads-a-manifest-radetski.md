---
title: "The update check reads a manifest file at a fixed address that the release workflow writes last, and falls back to the release list"
author: Radetski
date: 2026-11-13
branch: main
cost: { minutes: 24, tokens: 106514 }
id: l-78514211
scope: repo
status: active
src: agent
touches:
  - ".github/workflows/skipi-version-platform.yml"
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopAutomaticRefreshLoop.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopClassArchive.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopExitWatchdog.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopStartupQuiet.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/resources/DesktopProfileResourceFiles.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/resources/DesktopResourceFileUpdateRunner.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopClassArchiveTest.kt
---

## Decided
- Every full release or pre-release gets an `update-manifests` job that runs after the APK and all three desktop jobs and uploads `nightly.json` (and `stable.json` for a full release) to one fixed pre-release called `update-manifests`. The apps fetch `https://github.com/<repo>/releases/download/update-manifests/<channel>.json` first. It carries versionName, versionCode, the tag, the release page, a short changelog, and each asset's name, URL, size and SHA-256 taken from what GitHub says the release really has.
- The build number is a JSON field (the commit count), and the app compares by it; it no longer greps "Version code: N" out of the release notes. An offer is only made for a release whose installers all exist, because the job fails (and the manifest is not written) when any required file or digest is missing. A re-run of an older release does not move a channel backwards.
- The old path (GitHub API: /releases/latest for Stable, the 20 newest for Nightly) stays as the fallback when the manifest cannot be fetched or parsed, and the "Version code:" line stays in the release notes for versions that only know that path.

## Tried and ruled out
- Putting the manifest in the repository (main branch): the workflow would need to push to a protected branch.
- The GitHub API alone with better parsing: it is rate limited to 60 requests an hour per address without a token, it announces a release the moment it is created (before the desktop installers are uploaded), and it needed text in the notes to carry the build number. A release body rewrite (new notes layout) already broke the check once.

## Watch out
- The manifests release must stay a pre-release and never "latest": old apps read /releases/latest, and its release has no APK so the Nightly list of old apps skips it.
- A manifest older than the current channel is kept on purpose (skipped with a notice), so deleting a bad release does not republish an old manifest; delete or edit the manifest asset by hand to withdraw an offer.
- The release job now outputs version_code for the manifest job; keep it in step with the collect step.

In the code:
- shared/core/.../updater/UpdateManifest.kt (parser, android and desktop offers), AppUpdateChannel.manifestUrl.
- desktopApp/.../DesktopAppUpdateChecker.kt and app/.../GitHubReleaseChecker.kt try the manifest first.
- .github/workflows/skipi-version-platform.yml job update-manifest.
