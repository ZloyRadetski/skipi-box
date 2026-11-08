---
title: "A Windows start with TUN saved as on asks for administrator rights once, and never turns the setting off by itself"
author: Radetski
date: 2026-11-08
branch: main
cost: { minutes: 8, tokens: 152257 }
id: l-b3174db4
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopVpnSettings.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopWindowsElevation.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/tunnel/DesktopTunSwitchTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopAutostart.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowsAutostart.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopLaunchRequest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunnelControllerFactory.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunHost.kt
---

## Decided
The saved TUN setting is never changed by a start without administrator rights. At start, if TUN is saved as on and the process is not elevated, the app relaunches itself elevated (one UAC prompt, --hidden kept). A declined prompt keeps the setting; the app runs on and Connect reports why TUN cannot start. The settings switch shows on while only elevation is missing.
The relaunched copy carries --wait-for-pid, and that marker also means "already relaunched", so a wrong elevation check cannot loop on UAC.

## Tried and ruled out
Silent elevation from the Run registry entry: not possible, a Run start is never elevated. Avoiding the UAC prompt at each login would need a Task Scheduler task with highest privileges; not built, needs the user's decision.
Benchmarking startup by launching java.exe from the packaged runtime: the packaged runtime has no java.exe (the JVM is inside SKIPI.exe), so the numbers from that attempt were meaningless.

## Watch out
The reported "TUN turns itself off" was only the switch drawing off (checked required availability). The only code that writes enableTunMode=false is the explicit switch flow when its UAC prompt is declined.
In the code: Main.kt relaunchElevatedForSavedTunMode, DesktopWindowsElevation.kt shouldRelaunchElevatedAtStart, DesktopVpnSettings.kt tunSwitchChecked.
