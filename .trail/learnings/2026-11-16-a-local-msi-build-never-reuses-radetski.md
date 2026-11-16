---
title: "A local MSI build never reuses a build number, and the real TUN is tested from an isolated elevated run"
author: Radetski
date: 2026-11-16
branch: main
cost: { minutes: 1, tokens: 1623 }
id: l-9135f37e
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/proxy/DesktopLocalProxyReadiness.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/proxy/DesktopSystemProxy.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/proxy/DesktopWindowsSystemProxy.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopHevProcessTunnel.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunnelController.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunnelControllerFactory.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopWindowsTunDevice.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopWindowsTunRoutes.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/proxy/DesktopLocalHttpProxyTest.kt
---

## Decided
Windows Installer refuses a package with the same version and product code as the installed one ('another version of this product is already installed'). The MSI build number was the commit count, so a second build without a new commit got the installed number. A local packageMsi now takes max(commit count, last local number + 1) and keeps the last one in local.properties (not in git); CI starts from a clean checkout and still uses the commit count.
The Windows TUN is checked for real by starting the packaged app elevated (UAC is off on this machine, so a RunAs start is silent) with APPDATA, LOCALAPPDATA and XDG_RUNTIME_DIR pointed at a throwaway folder, then reading its logs, the adapter's DNS and curl with --noproxy.

## Tried and ruled out
Seeding the counter automatically from the installed version: not needed once the file exists, the first local build after the change must be run once to record the number. A time-based build number: it would exceed the commit count of later CI builds, and the installer would then refuse those as downgrades.

## Watch out
A local number equal to a later CI build's commit count gives the same error when the CI MSI is installed over it; a new commit fixes it. Without XDG_RUNTIME_DIR the test app hands its start to the running instance and exits. A killed test app leaves hev-socks5-tunnel.exe running; stop it from an elevated shell.

In the code: desktopApp/build.gradle.kts (windowsBuildNumber), local.properties key skipi.lastMsiBuildNumber.
