---
title: "Two audit findings were left as they are because tests pin them as Android parity, and Android's state store now loads Room after the first frame"
author: Radetski
date: 2026-11-13
branch: main
cost: { minutes: 21, tokens: 387299 }
id: l-682eb82f
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopBackgroundPhotoStore.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopDeviceIdentity.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopLogger.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopPeriodicRefreshScheduler.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopProcessRunner.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopSystemAccent.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowsAutostart.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWorkingSetTrim.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/config/DesktopTrafficConfigAutoUpdater.kt
---

## Decided
- A cancelled bulk ping still publishes every in-flight probe as failed, and a subscription refresh that reassigns the selected server's id still falls back to the old selection rule. Existing tests state both as deliberate Android parity, so they stay until someone changes the tests on purpose.
- Android's state store starts from preferences and loads the Room lists in the background. Saving waits for that load, otherwise an empty list would replace the database. Every entry that reads servers at process start (tile, widgets, boot and broadcast receivers, workers, auto-connect) waits for the load off the main thread.

## Tried and ruled out
- Rethrowing cancellation in the latency runner and remapping the selected id in the reconciler: both broke parity tests, so both were reverted.
- One background writer for the persisted app log: a test needs a write readable from another instance right after append, so writes are batched instead.

## Watch out
- The resource download cancel flag is still reset by begin(); closing that needs an end() or a generation token in callers outside the core module.
- Start timing and the Room-load ordering were not measured on a device.

In the code:
- app/src/main/kotlin/data/AndroidAppStateStore.kt, data/InitialLoadGate.kt
- shared/app/.../home/ProxyHomeLatencyRunner.kt, proxy/SubscriptionServerCollectionReconciler.kt
