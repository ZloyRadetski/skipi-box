---
title: "The desktop tray menu is drawn by Compose, because the native AWT popup always looks like the OS toolkit"
author: Radetski
date: 2026-11-07
branch: main
cost: { minutes: 6, tokens: 70306 }
id: l-cb8e93e8
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopTray.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopTrayMenu.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopTrayMenuTest.kt
  - app/src/main/kotlin/features/tools/AndroidDiagnostics.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopDiagnostics.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/tools/DiagnosticsControllers.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/tools/DnsLeakTestRunner.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/tools/SystemPathDnsTxtResolver.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/tools/DiagnosticsControllersTest.kt
---

## Decided
A right click on the tray icon opens an undecorated, transparent Compose dialog window with the menu, themed like the app. The tray icon itself stays the AWT one with the same tooltip and left-click action. The menu keeps the same items: connect/disconnect, separator, exit.

## Tried and ruled out
Compose's own Tray with a menu block: it builds an AWT PopupMenu, which Windows draws with the Win32 toolkit and which cannot be styled.

## Watch out
The window is a DialogWindow, not a Window, so it has no taskbar button. It closes on focus loss and on Escape; if the OS never gives it focus it stays until Escape or another tray click. Tray click coordinates on Windows with 125-150% scaling and on Linux/macOS trays were not checked in a running app, only the placement maths is unit tested.
In the code: DesktopTrayIcon, DesktopTrayMenuWindow, desktopTrayMenuOrigin.
