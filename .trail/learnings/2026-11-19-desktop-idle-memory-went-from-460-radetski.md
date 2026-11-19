---
title: "Desktop idle memory went from ~460 to ~160 MB: the heap trim never fired, the Core loaded at every start, and Windows draws with the software renderer"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 50, tokens: 232467 }
id: l-65adcc0e
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopHeapTrimTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/core/DesktopConnectionLogTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopHeapTrim.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopConnectionLog.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/subscription/DesktopSubscriptionFetcherTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopUpdateDownloaderTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/tunnel/DesktopAutoConnectTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopAutoConnect.kt
  - desktopApp/build.gradle.kts
---

## Decided
- The heap trim compares the committed heap with what was live right after the last trim (excess: max(32 MB, half the live set)), first check 12 s after start, then each minute. The old rule (committed minus used >= 64 MB) never fired after a start burst: the serial old generation holds promoted garbage that Runtime reports as used, so 190 MB stayed committed for ~40 MB live.
- The Core library is preloaded only when the app will auto-connect; otherwise the first connect or ping loads it (Go runtime ~60 MB and ~20 threads, cannot be unloaded).
- HttpClients are lazy and shared (subscription, GitHub API, release downloads); each one built at start cost a selector thread and the TLS stack.
- The Home connection log does not ask a stopped Core (it built an exception 4x a second) and polls every 2 s instead of 250 ms while the window is hidden or minimized; the tailer viewer lease and the tunnel-memory poll follow window visibility.
- Windows launcher: SOFTWARE_FAST + background erase on + Java2D D3D off. Same isolated image, idle private / 5 s drag peak / 10 s later / black corner samples: SOFTWARE_FAST 165 / 630 / 240-300 MB / 14 %; OPENGL 230 / 865 / 445-490 MB / 56 %; DIRECT3D 410 / 430 / 410 MB / 32 %. Maximize and restore repaint fully with SOFTWARE_FAST now (screenshots).

## Tried and ruled out
- -XX:TieredStopAtLevel=1: code cache only 17 -> 11 MB, and in that run the heap stayed at 196 MB; no gain.
- -XX:ArchiveRelocationMode=0 (diagnostic): no measurable change (162 vs 161 MB).
- 100 MB private is not reachable with JVM + Skia: idle now is ~31 MB heap, ~70 MB class archive, ~17 MB code, ~11 MB metaspace, rest native (Skia, AWT, fonts). The drag peak is software raster surfaces and is given back.

## Watch out
- Measure with the system java on the built jars, isolated APPDATA/LOCALAPPDATA and a SHORT XDG_RUNTIME_DIR (the instance socket path fails when long, then skipi://close cannot end the app and no class archive is written). Wait for the previous test JVM to exit first, or the next start forwards to it and exits at once.
- Make the test window topmost before the SetWindowPos loop; otherwise pixels and CPU come from whatever window covers it. The default theme is dark (1A1716), so count 000000, not "dark".
- PowerShell -File parses an -Extra value starting with -XX as a parameter name: pass it as "-Extra:...".

In the code: desktopApp/build.gradle.kts (jvmArgs), app/DesktopHeapTrim.kt, tunnel/DesktopAutoConnect.kt shouldPreloadCoreAtStart, core/DesktopConnectionLog.kt follow, Main.kt (preload, connection log, tunnel memory), DesktopSubscriptionFetcher/DesktopUpdateDownloader/DesktopAppUpdateChecker clients.
