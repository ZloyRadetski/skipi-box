---
title: "hev-socks5-tunnel sources live only in the Core repo, and every CI clone of the Core must be recursive"
author: Radetski
date: 2026-11-20
branch: main
cost: { minutes: 1, tokens: 980 }
id: l-267316fd
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
  - ".github/workflows/skipi-version-platform.yml"
---

## Decided
- The app has no hev copy of its own, because two submodules could drift apart in version and patches. The macOS executable and the Wintun licence are read from the Core checkout's hev submodule.

## Watch out
- A Core clone without submodules leaves that folder empty, so the macOS build fails with "sources are unavailable" and the Windows licence step fails too. All three CI clones therefore use --recurse-submodules.
- The Core release named by SKIPI_CORE_VERSION must contain the hev submodule, otherwise the same failure occurs.

In the code: desktopApp/build.gradle.kts (desktopHevSourceDirectory), .github/workflows/skipi-version-platform.yml (git clone lines).
