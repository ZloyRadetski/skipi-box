---
title: "Each update channel keeps its own check cache, and switching channel drops the offer found on the old one"
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 12, tokens: 166464 }
id: l-cf110718
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/app/SkipiApplication.kt
  - app/src/main/kotlin/data/AppSettingsPreferences.kt
  - app/src/main/kotlin/features/about/AboutComponents.kt
  - app/src/main/kotlin/features/updater/GitHubReleaseChecker.kt
  - app/src/main/kotlin/features/updater/runtime/AppUpdateCheckStore.kt
  - app/src/main/kotlin/features/updater/runtime/AppUpdateCoordinator.kt
  - app/src/main/kotlin/features/updater/runtime/AppUpdateScheduleGateway.kt
  - app/src/main/kotlin/features/updater/runtime/AppUpdateWorker.kt
  - app/src/test/kotlin/data/AppSettingsPreferencesCompatibilityTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
---

## Decided
- Stable and Nightly each have their own ETag and check TTL, so a 304 or a recent check on one channel never hides a release of the other.
- Switching channel clears the discovered update and invalidates the new channel's cache, so the next check asks GitHub at once. Otherwise a pre-release found on Nightly could still be installed after leaving it.
- A response of the wrong shape for the channel (object vs list) is a failed check, never "up to date".

## Tried and ruled out
- One shared ETag key for all channels: a 304 on one channel would hide the other's release.

## Watch out
- Releases must be GitHub pre-releases with "Version code: N" in the notes, or Nightly cannot order them.
- Version names used to be compared by dropping non-numeric parts, so 0.5.0-rc.6 became 0.5.6; now a pre-release sorts before its release.
- The Stable cache file keeps its original name so installs from before channels carry their cache over.

In the code:
- shared/core/.../features/updater/AppUpdateChannel.kt, GitHubReleaseParser.kt, AppReleaseVersions.kt
- app/.../features/updater/runtime/AppUpdateCheckStore.kt, AppUpdateCoordinator.kt (changeChannel)
