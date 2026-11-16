---
title: The MSI closes a running SKIPI by itself through custom actions added to the finished package
author: Radetski
date: 2026-11-16
branch: main
cost: { minutes: 16, tokens: 64252 }
id: l-4ad0ebe9
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
---

## Decided
jpackage cannot add custom actions, so after packageMsi the finished MSI is edited with the Windows Installer API (patch-msi-close-app.ps1, run from packageMsi.doLast). It adds taskkill actions for SKIPI.exe and hev-socks5-tunnel.exe: immediate ones before InstallValidate (where files in use are found) and deferred SYSTEM ones after InstallInitialize that also end an elevated SKIPI (TUN). RemoveExistingProducts moves to after InstallFinalize. Checked by installing a newer MSI over the running app: exit 0, version replaced, app closed.

## Tried and ruled out
A directory-based exe action with SystemFolder fails with error 2727 until SystemFolder is added to the Directory table. RemoveExistingProducts at 1510, between InstallInitialize and InstallFinalize, fails with 2613 'sequenced incorrectly'. A WiX CloseApplication element: needs jpackage's main.wxs overridden, and Compose clears the resource dir that jpackage reads before it runs, so the override cannot be placed.

## Watch out
The app is not started again after the install. Patching the same MSI twice fails on duplicate keys, so the script runs once per packageMsi on the file of the current version.

In the code: desktopApp/packaging/windows/patch-msi-close-app.ps1, desktopApp/build.gradle.kts (packageMsi doLast).
