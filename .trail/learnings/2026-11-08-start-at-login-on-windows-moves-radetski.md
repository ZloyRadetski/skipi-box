---
title: Start at login on Windows moves to a highest-rights logon task once the app runs elevated with TUN on
author: Radetski
date: 2026-11-08
branch: main
cost: { minutes: 9, tokens: 35659 }
id: l-c71867ba
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopVpnSettings.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopWindowsElevation.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/tunnel/DesktopTunSwitchTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopWindowsAutostartTest.kt
---

## Decided
The Run registry value stays the autostart for a non-elevated app. When the app runs elevated with TUN saved as on and start at login wanted, it registers a Task Scheduler logon task with RunLevel Highest and drops the Run value (or a second, unelevated copy would start). The next login then starts elevated with no UAC prompt. If the task cannot be registered, the Run value is the fallback. A non-elevated app leaves an existing task alone, since only an elevated process can change it.
The task settings allow starting on battery and set the execution time limit to zero: the defaults skip the start on a laptop on battery and kill the app after 72 hours.

## Tried and ruled out
schtasks /create with /sc onlogon: it triggers for every user and cannot set the battery and time-limit settings, so the task is registered through PowerShell Register-ScheduledTask.

## Watch out
Turning start at login off from a non-elevated app can fail to delete the task (access denied); that is reported instead of saving the setting as off.
In the code: DesktopWindowsAutostart.kt (registerTaskCommand, shouldRegisterElevatedAutostart), Main.kt moveAutostartToElevatedTask.
