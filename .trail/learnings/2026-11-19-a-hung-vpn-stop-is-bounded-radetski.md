---
title: "A hung VPN stop is bounded and detached, and the emergency stop kills the process instead of calling the stuck native code again"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 27, tokens: 153813 }
id: l-f048ae99
scope: repo
status: active
src: agent
touches:
  - app/src/test/kotlin/engine/vpn/NativeRuntimeStopTest.kt
  - app/src/main/kotlin/engine/vpn/EmergencyStop.kt
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
---

## Decided
- hev (JNI) and SKIPI Core (gomobile) live inside the app process on Android, so a stuck native stop cannot be killed on its own. Each native stop step runs on its own thread with a deadline (3 s normal, 1 s emergency); a step that misses it marks the process "dirty". While dirty, no start touches the native runtimes and the user is offered the emergency stop, which kills the process (ProcessKiller) after persisting "not connected".
- Core's StopLoop now gives up after 5 s, detaches the instance and counts it in AbandonedInstances(); ForceStop() works in every state, including a start or stop already hung. The controller lock is held only for short swaps, never across LoadJSONConfig/core.New/Start/Close.
- After an emergency stop the relaunch is an AlarmManager activity start on API 24-28 and a "restarted" notification on 29+, where background activity starts are blocked.

## Tried and ruled out
- A registry of every outbound connection, by replacing Xray's system dialer (UseAlternativeSystemDialer): it disables RegisterDialerController and the default dialer's DNS/outbound wiring. Not done; detaching the instance plus the process kill covers the stuck case.

## Watch out
- When the runtime is already dirty, the emergency path must not call hev/Core stop again: a second native stop racing the stuck one can crash instead of dying cleanly.
- A ForceStop of a start that is merely slow also counts as abandoned until that start returns, so the emergency path may kill the process for it.

In the code: app engine/vpn/NativeRuntimeHealth.kt (BoundedNativeSteps), EmergencyStop.kt (EmergencyStopCoordinator, StopWatchdog 8 s, EmergencyStopMarker), SkipiVpnService.handleEmergencyStop; SkipiCoreRuntime.forceStopAbandonedCoreInstances is the place to wire Core's ForceStop/AbandonedInstances once the AAR has them. skipi-core skipicore.go StopLoopWithTimeout/ForceStop/AbandonedInstances, amneziawg.go StopAmneziaWgWithTimeout, C API SkipiCoreDesktopStopWithTimeout/ForceStop/AbandonedInstances.
