---
title: "Dynamic local proxy port must move the system-proxy HTTP inbound too, not only SOCKS"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 10, tokens: 33048 }
id: l-acafd67f
scope: repo
status: active
src: agent
touches:
  - desktopApp/packaging/tests/verify-listing-test.sh
  - desktopApp/packaging/verify-listing.sh
  - desktopApp/src/main/kotlin/app/skipi/desktop/proxy/DesktopLocalProxyRuntime.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunHost.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/proxy/DesktopLocalProxyRuntimeLifecycleTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/tunnel/DesktopTunHostTest.kt
  - buildSrc/src/main/kotlin/PrepareDesktopHevWindowsRuntimeTask.kt
  - desktopApp/build.gradle.kts
  - desktopApp/packaging/windows/msys-gcc_s-seh-1.dll
---

## Decided
With dynamic port on, Desktop allocates both the SOCKS and the HTTP inbound port (HTTP kept off the SOCKS one). The system proxy and the readiness check read the HTTP port from the running options, so they follow it.

## Watch out
Before this only SOCKS moved. v2rayN uses the same defaults (10808/10809), so a busy 10809 failed the start every time and the single shared retry (MaxAttempts = 2, also used by Android) did not help.

In the code: DesktopLocalProxyRuntime.prepareStart, DesktopLocalProxyRuntimeLifecycleTest.
