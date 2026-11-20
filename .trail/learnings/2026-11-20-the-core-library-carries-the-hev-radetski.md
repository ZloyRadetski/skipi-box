---
title: The Core library carries the hev runtime on Windows and macOS; the app ships and passes no hev
author: Radetski
date: 2026-11-20
branch: main
cost: { minutes: 23, tokens: 154395 }
id: l-ef84b71b
scope: repo
status: active
replaces: l-88e4f3ff
src: agent
touches:
  - ".github/workflows/skipi-version-platform.yml"
  - buildSrc/src/main/kotlin/BuildConfig.kt
  - desktopApp/build.gradle.kts
---

## Decided
- On Windows and macOS hev runs as a process of its own, so the runtime (hev, Wintun, MSYS DLLs / the macOS executable) is embedded into the Core library with go:embed. The Core release build stages it first (scripts/stage-hev-payload.sh). The app downloads, ships and names nothing: the session plan has no hev executable. Reason: Core already owns hev's lifecycle and security checks, and now also the exact bytes it runs, so the app can neither supply a wrong file nor go out of sync with the Core's hev version.
- macOS: Core writes the carried hev into the run directory and gives the root supervisor the SHA-256 of the embedded bytes (not of the written file), so replacing the written file gains nothing. Windows: Core installs the runtime into the hev directory beside the Core DLL before every start and rewrites any file that differs.
- The macOS library is now built and published by the Core CI (libskipicore-macos-arm64.dylib), so the app's macOS build downloads it like the Windows DLL and no longer compiles the Core.

## Watch out
- A Core build made without staging the payload works but reports that it carries no HEV TUN runtime at the first tunnel start. Local source builds stage it through the Core script, which needs bash, curl and python (macOS also make and a compiler).
- The app must pin a Core release that no longer requires hev.executable (v1.2.0 or later); with v1.1.x the Windows and macOS tunnels fail at start.
- Windows installs hev files into the application's install directory at the first TUN start (it runs elevated then). An uninstall does not remove them; this leaves files behind.

In the code: skipi-core internal/hevpayload, internal/hevtun (hevtun_windows.go prepareExecutable, macruntime.go), scripts/stage-hev-payload.sh, .github/workflows/build-desktop.yml; skipi-box BuildDesktopSkipiCoreTask.kt stageHevRuntime, skipi-version-platform.yml macOS job.
