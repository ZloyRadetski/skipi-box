---
title: "Where the start time goes: Android's first frame waits for every server to be decoded from Room, and Desktop decodes the server library again at every use"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 11, tokens: 337805 }
id: l-651a863c
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/data/AndroidAppStateStore.kt
  - app/src/main/kotlin/app/App.kt
  - app/src/main/kotlin/app/MainActivity.kt
  - app/src/main/kotlin/data/ListEntities.kt
  - app/src/main/kotlin/data/AppSettingsPreferences.kt
  - app/src/main/kotlin/engine/proxy/latency/ProxyPingResultCache.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/home/DesktopServerLibrary.kt
  - shared/app/src/jvmMain/kotlin/features/logs/BoundedLogFileStore.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - app/src/main/kotlin/app/SkipiApplication.kt
---

## Decided
- Nothing changed yet; this is the audit's finding, so the fix starts here instead of from measuring again.
- Android: the first frame cannot be drawn until the whole state store is built. The store's constructor loads Room synchronously (runBlocking) and decodes the JSON of every server, and the main thread reaches the store during the first composition and in the activity's auto-connect check. Whichever thread starts the store first, the main thread waits on its lock. The activity also builds the whole AppState from SharedPreferences once more in attachBaseContext just to read the language and theme.
- Android: every persisted update re-encodes all servers to JSON twice to find out whether the list changed, and rewrites all preferences including the full traffic config contents. The ping cache writes its whole map to preferences after every single probe.
- Desktop: a stored server has no decode cache, so the library is decoded from JSON at least three times on the UI thread at start (home list, groups, repository records) and again on every commit; the settings section decodes it on every recomposition because the automation server list is built without remember.
- Both: every AppLog line is a synchronous open/append/close of the log file on the calling thread, under one lock, plus a copy of the up-to-8000-entry in-memory list.

## Tried and ruled out
- Looking for an unbounded in-memory log: the viewers and the Home feed are bounded (8000 entries, 500 rows, 2 MB app log file). The log system's cost is per-line I/O and list copies, not growth.
- Looking for a leaking photo background: both platforms downscale to 2560 px on import; Desktop decodes twice (BufferedImage plus ImageBitmap) but it is bounded.

## Watch out
- The Desktop JVM memory work (serial GC, heap trim, working-set trim, class-data archive) is already done and recorded in the earlier learnings; what remains there is CPU and allocation churn, not resident size.
- The Desktop refresh schedulers wake every second while any entry is overdue (running or waiting for the network), because the next wake-up time is the minimum over all entries.
- Desktop process helpers read stdout before waitFor, so their timeouts never fire; the network automation loop spawns netsh every 5 s when a rule needs the SSID.

In the code:
- app/src/main/kotlin/data/AndroidAppStateStore.kt:136-157 (runBlocking load), app/src/main/kotlin/app/App.kt:139, app/src/main/kotlin/app/MainActivity.kt:167-172 and 269
- app/src/main/kotlin/data/ListEntities.kt:214-220 (hasSamePersistedContent), app/src/main/kotlin/data/AppSettingsPreferences.kt:464-466 and 555
- app/src/main/kotlin/engine/proxy/latency/ProxyPingResultCache.kt (record -> persistLocked)
- desktopApp/src/main/kotlin/app/skipi/desktop/home/DesktopServerLibrary.kt:36 (decode), DesktopProxyHome.kt:261, DesktopProxyGroups.kt:136, DesktopProxyServerRepository.kt:163-252, Main.kt:1974 and 650-669
- shared/app/src/jvmMain/kotlin/features/logs/BoundedLogFileStore.kt:18-32, CoreLogRepository.kt:74-85
- desktopApp/.../app/DesktopPeriodicRefreshScheduler.kt:83-85, DesktopAutomaticRefreshLoop.kt:52-54, network/DesktopNetworkObserver.kt:100-105
