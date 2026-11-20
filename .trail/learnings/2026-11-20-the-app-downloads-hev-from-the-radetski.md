---
title: The app downloads hev from the SKIPI Core release; only the Core builds or packages it
author: Radetski
date: 2026-11-20
branch: main
cost: { minutes: 33, tokens: 108298 }
id: l-88e4f3ff
scope: repo
status: active
replaces: l-267316fd
src: agent
touches:
  - buildSrc/src/main/kotlin/PrepareDesktopHevRuntimeTask.kt
  - desktopApp/build.gradle.kts
---

## Decided
- hev for Windows and macOS is built (macOS) or packaged (Windows, pinned official archive plus the vendored gcc DLL) by the Core CI and published with every Core release. The app only downloads the asset of the pinned Core version and checks it against sha256sums-desktop.txt. Reason: one repository owns the hev version, patches and hashes, and the app needs no hev sources or toolchain.
- Linux and Android get hev linked into the Core library, so they download nothing.

## Watch out
- A release is usable only when both Core workflows finished: the Desktop one publishes the hev archives and the DLL, the AAR one the Android library. v1.1.0 had no AAR; use v1.1.1 or later.
- The macOS asset is built for the runner's architecture only (arm64); an Intel build needs a matching asset.
- HEV_SOCKS5_TUNNEL_VERSION in the app is only shown in About; the real version is pinned in the Core's scripts/package-hev-windows.sh and its submodule.

In the code: buildSrc PrepareDesktopHevRuntimeTask.kt, desktopApp/build.gradle.kts (prepareDesktopHevRuntime), skipi-core scripts/build-hev-mac.sh and scripts/package-hev-windows.sh.
