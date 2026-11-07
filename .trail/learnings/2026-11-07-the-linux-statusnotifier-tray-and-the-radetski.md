---
title: "The Linux StatusNotifier tray and the Compose tray menu coexist: the panel gets the D-Bus menu, the AWT fallback gets the Compose menu"
author: Radetski
date: 2026-11-07
branch: main
cost: { minutes: 3, tokens: 43677 }
id: l-255bc5a6
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopTray.kt
---

## Decided
Both tray paths stay. A StatusNotifier panel on Linux gets the icon and a D-Bus menu (connect/disconnect, exit). Where there is no such panel, the AWT tray icon is used and its right click opens the Compose-drawn menu. Windows and macOS only use the AWT path.

## Tried and ruled out
Merging the two tray menu files under one name: a friend's D-Bus menu model and the Compose menu both defined DesktopTrayMenu and DesktopTrayMenuEntry with different shapes, so they could not share a file.

## Watch out
The D-Bus menu model is DesktopStatusNotifierMenu(Entry); DesktopTrayMenu(Entry) is the Compose menu. A change to the menu items (labels, new entry) has to be made in both places.

In the code: desktopApp/.../app/DesktopTray.kt (DesktopTrayIcon), DesktopStatusNotifierMenu.kt, DesktopTrayMenu.kt.
