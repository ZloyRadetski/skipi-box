---
title: "HEV TUN is owned by SKIPI Core on Android, Linux and Windows; the session stops itself when hev dies"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 25, tokens: 73470 }
id: l-6036402a
scope: repo
status: active
src: agent
touches:
  - ".github/workflows/skipi-version-platform.yml"
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopCoreController.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopCoreNative.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopCoreRuntime.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopFfmTun.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/proxy/DesktopLocalProxyRuntime.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunHost.kt
---

## Decided
- The user wants hev kept as the TUN on every platform but owned by Core. Android and Linux link hev statically into the Core library (cgo, same internal/hevtun code); Windows keeps hev's MSYS executable, which cannot be linked into the MinGW Core DLL, and Core supervises that process. macOS still runs its privileged supervisor from the app.
- Hosts start everything with one startSession plan that carries the hev YAML plus the TUN descriptor (in-process targets) or the hev executable (Windows); Core starts AmneziaWG, Xray, hev and stops them in reverse, each bounded.
- When hev exits under a running session, Core stops Xray and AmneziaWG and reports Idle with "HEV TUN exited unexpectedly". This replaces the desktop's own dead-hev check, which read the in-app hev before.

## Tried and ruled out
- Switching to Xray's own tun inbound instead of moving hev: the user ruled it out; HEV TUN stays.

## Watch out
- The Linux cgo build of Core (hev linked into libskipicore.so) can only be built in CI (no gcc on the Windows dev machine); its first real build is the desktop workflow.
- Android now establishes the TUN before Core starts (hev needs the descriptor), no longer in parallel.
- On Windows QueryTunnelStats is all zeros: hev's counters live in its own process.

In the code: skipi-core internal/hevtun (hevtun_cgo.*, hevtun_windows.go, hevtun_stub.go), session_hev.go (watchHev, hevWatchInterval), scripts/build-hev-android.sh, build-hev-linux.sh; skipi-box app engine/vpn/CoreSessionPlan.kt, desktopApp core/DesktopCoreController.kt, tunnel/DesktopTunRuntime.kt.
