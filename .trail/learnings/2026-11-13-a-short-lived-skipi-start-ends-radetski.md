---
title: "A short-lived SKIPI start ends through the operating system's exit, because Runtime.halt still writes the class archive and a tiny archive would stick"
author: Radetski
date: 2026-11-13
branch: main
cost: { minutes: 25, tokens: 178346 }
id: l-62f17aab
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/ExitWithoutArchiveProbe.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopExitWatchdogTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopStartupQuietTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/resources/DesktopResourceRefreshTargetTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopExitWatchdog.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopStartupQuiet.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopClassArchive.kt
---

## Decided
- A second start that only hands its link over (and the elevated relaunch) ends with kernel32 ExitProcess via FFM. Both Runtime.halt and a normal return write the class-data archive at exit; ExitProcess and TerminateProcess do not (checked with a probe JVM on JDK 26). A tiny archive from such a start is valid, so the next real start would load it, switch AutoCreateSharedArchive off, and stay slow until the next update.
- Resource auto-update anchors a new schedule on the older modification time of the profile's geoip/geosite files, and a start deletes `<name>.<digits>.tmp` leftovers before any download. The schedule lives only in memory, so without an anchor every start fetched every profile's files again.
- The resource runner waits for its download threads inside runInterruptible and drops their connections when the caller is cancelled; exit also starts a 10 s watchdog that calls Runtime.exit, so a coroutine stuck in blocking I/O no longer keeps the process alive after the window is gone.
- The update check and the three automatic refresh loops wait 8 s after the first frame.

## Tried and ruled out
- A test whose cancelled caller is a child of runBlocking: runBlocking waits for the stuck child forever, so the red run hung the whole Gradle test task instead of failing; the caller gets its own scope.
- Rethrowing the batch's failure straight through runInterruptible: with assertions on (Gradle tests) coroutines' stacktrace recovery copies an exception that crosses a dispatcher, and the feedback test compares the mapped cancellation error by identity. Failures travel as a Result value.

## Watch out
- The JVM writes a class archive only for classes loaded from jars; a probe started with class directories on its class path fails with "non-empty directory". The test packs the two classes it needs into a jar.
- Anchoring on file dates means a profile whose geo files were just copied or restored from the bundle counts as fresh for one interval.

In the code:
- desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopClassArchive.kt exitWithoutClassArchive; DesktopExitWatchdog.kt; DesktopStartupQuiet.kt.
- desktopApp/.../resources/DesktopProfileResourceFiles.kt lastGeoFetchAtMillis, deleteInterruptedDownloads; DesktopResourceFileUpdateRunner.kt execute.
- shared/core/.../GeoResourceAutoRefreshScheduling.kt newSchedule; TrafficConfigRefreshTarget.resourceLastUpdatedAtMillis.
