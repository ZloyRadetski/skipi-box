---
title: "The tunnel memory tile shows what the Go runtime holds from the OS, not its live heap objects"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 6, tokens: 47698 }
id: l-6a39d780
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopTunnelMemoryTest.kt
  - shared/core/src/commonMain/kotlin/features/settings/CoreMemoryStats.kt
  - shared/core/src/commonTest/kotlin/features/settings/CoreMemoryKilobytesTest.kt
  - app/src/main/kotlin/app/MainActivity.kt
  - app/src/main/kotlin/engine/vpn/EmergencyStop.kt
  - app/src/main/kotlin/engine/vpn/EmergencyStopAndroid.kt
  - app/src/main/kotlin/engine/vpn/NativeRuntimeHealth.kt
  - app/src/main/kotlin/engine/vpn/SkipiCoreRuntime.kt
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
  - app/src/main/kotlin/engine/vpn/SkipiVpnServiceIntent.kt
---

## Decided
- The figure is sysBytes minus heapReleased from Core's ReadMemoryStats: everything the Go runtime mapped, minus heap it gave back. The live heap (allocBytes) left out free heap not yet returned, goroutine stacks and runtime metadata, so it showed a fraction of the core and jumped with every GC. It falls back to allocBytes only when the total is missing or not larger.
- Still the core's own memory, not the whole app (the October choice stands); no Core release needed, the fields already exist.

## Watch out
- On Android hev runs inside the app process and its native memory cannot be separated, so it is not in the figure.

In the code: shared/core features/settings/CoreMemoryStats.kt coreMemoryKilobytes; used by Android TunnelMemorySection.currentTunnelMemoryPssKb and Desktop readDesktopTunnelMemoryKb.
