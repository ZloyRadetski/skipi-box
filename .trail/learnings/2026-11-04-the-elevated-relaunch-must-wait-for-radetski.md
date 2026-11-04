---
title: The elevated relaunch must wait for the old instance to exit before it claims the single-instance channel
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 17, tokens: 72266 }
id: l-f93d5bfe
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopLaunchRequest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopLaunchRequestTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopHevProcessTunnel.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunnelControllerFactory.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopWindowsTunDevice.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopWindowsTunRoutes.kt
---

## Decided
The elevated copy is started with --wait-for-pid <old pid> and waits for that process to end before it claims the single-instance channel. The old instance exits right after the UAC prompt is accepted.

## Tried and ruled out
A sleep before Start-Process in the PowerShell script: the old instance waits for that script to finish, so it is still alive when the new one starts, and the sleep changes nothing.

## Watch out
Symptom was the app just closing with no admin window: the new instance forwarded its start to the dying old one and exited. A declined UAC prompt still has to come back as an error, so the old instance cannot simply exit before launching.

The hev start wait on Windows is 0.4 s and the stale-adapter clean-up runs beside Core start; TUN stage timings go to the log as "TUN prepare/start took N ms" (check them first on Linux and macOS, where no fixed wait was found).

In the code: relaunchElevated in DesktopWindowsElevation.kt, main() in Main.kt, DesktopLaunchRequest.kt.
