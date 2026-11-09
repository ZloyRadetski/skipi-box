---
title: "Cancellation must not be caught as a failure, except where a contract test says a cancelled run reports Retry"
author: Radetski
date: 2026-11-09
branch: main
cost: { minutes: 24, tokens: 128316 }
id: l-6f352778
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/app/MainActivity.kt
  - app/src/main/kotlin/app/effects/LauncherIconSynchronizer.kt
  - app/src/main/kotlin/app/effects/ProxyStatusSynchronizer.kt
  - app/src/main/kotlin/app/effects/ResourceFileSynchronizer.kt
  - app/src/main/kotlin/data/AndroidAppStateStore.kt
  - app/src/main/kotlin/engine/proxy/AndroidProxyEngine.kt
  - app/src/main/kotlin/engine/proxy/latency/AndroidProxyLatencyTester.kt
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
  - app/src/main/kotlin/features/automation/BroadcastControlReceiver.kt
  - app/src/main/kotlin/features/config/TrafficConfigPage.kt
---

## Decided
Suspend code that needs "capture failures" uses runCatchingCancellable (utils), which rethrows CancellationException; plain runCatching also catches it, so a cancelled coroutine kept running. Found by temporarily shadowing runCatching with a non-inline function: the compiler then rejects every suspend call inside it, which lists the real sites (a name search gave mostly false hits).

## Tried and ruled out
Making every site cancellation-safe. Two are specified the other way and their tests pin it: the traffic-config auto-update returns Retry when cancelled (so a stopped worker runs again), and a Mihomo provider fetch that is cancelled falls back to its inline nodes. Both keep the plain catch, with a comment in the first.
An inline helper: the Android module compiles for JVM 11 and cannot inline a function built for JVM 21 from shared code, so the helper is a normal suspend function (no non-local return from its block).

## Watch out
Use runCatchingCancellable only where the block really suspends; a catch of Throwable around suspend code needs a CancellationException clause before it.

In the code: shared/core utils/RunCatchingCancellable.kt.
