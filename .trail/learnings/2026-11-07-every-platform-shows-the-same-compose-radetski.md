---
title: "Every platform shows the same Compose tray menu: the Linux StatusNotifier item exports no menu and the panel asks the app to draw it"
author: Radetski
date: 2026-11-07
branch: main
cost: { minutes: 3, tokens: 35500 }
id: l-53dd2b1b
scope: repo
status: active
replaces: l-255bc5a6
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopStatusNotifierTest.kt
---

## Decided
The tray menu is one Compose window on every platform. The Linux StatusNotifier item exports no dbusmenu (Menu is /NO_DBUSMENU), so the panel calls ContextMenu(x, y) on the item and the app opens the same Compose menu it opens for the AWT icon on Windows and macOS.

## Tried and ruled out
Exporting a dbusmenu with the connect and exit entries (the first Linux version): every panel draws that menu in its own style, so it never matched the app theme.

## Watch out
The x, y of ContextMenu come from the panel and are used as the AWT window position. On Wayland the compositor may ignore a client-chosen position, and focus-loss dismissal was not tried on a real Wayland panel. Check both there.

In the code: desktopApp/.../app/DesktopStatusNotifier.kt (ContextMenu), DesktopTray.kt (DesktopTrayIcon, menuClick), DesktopTrayMenu.kt.
