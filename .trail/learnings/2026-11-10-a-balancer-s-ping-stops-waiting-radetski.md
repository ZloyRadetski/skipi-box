---
title: "A balancer's ping stops waiting for members that can no longer beat its best answer"
author: Radetski
date: 2026-11-10
branch: main
cost: { minutes: 11, tokens: 68135 }
id: l-c915166a
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopAppUpdateChecker.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopAppUpdater.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopUpdateDownloader.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopUpdateInstaller.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopUpdateLabels.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopUpdatePackageDetection.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopAppUpdaterTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopUpdateDownloaderTest.kt
---

## Decided
A balancer's latency is its best member's, so once one member has answered, a member still silent after that answer plus a grace (the answer or 200 ms, whichever is larger) cannot improve it and is given up on. Dead members used to hold the whole group until their own timeout, which is what made balancer pings take minutes on big subscriptions. The same shared function now serves Desktop and Android.
Desktop decodes the server library once per ping run, and only when a balancer is pinged, instead of once per balancer.

## Tried and ruled out
Cancelling and then waiting for the probe: a blocking socket or native core call cannot be interrupted, so the wait would last until its own timeout. Probes run in their own scope and are no longer awaited once outrun; the scope is cancelled when the group is decided.
Skipping members that have not started yet: the exact best needs all of them, so only the wait on each is shortened.

## Watch out
Android's probe time includes DNS and Desktop's does not, which is why the cutoff has a grace instead of cutting exactly at the best answer.
A cutoff probe that cannot be interrupted still finishes in the background until its own timeout; only the group no longer waits for it.

In the code: shared/app home/StrategyGroupLatency.kt and its two test files; AndroidProxyLatencyTester.testStrategyGroupLatency; Main.kt libraryRecords.
