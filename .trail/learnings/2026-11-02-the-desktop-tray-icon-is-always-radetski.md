---
title: "The desktop tray icon is always shown, with only connect/disconnect and exit in its menu"
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 11, tokens: 41216 }
id: l-d1fda54b
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopTray.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopTrayTest.kt
  - shared/ui/src/commonMain/composeResources/values-fa/strings.xml
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
  - shared/ui/src/commonMain/composeResources/values-zh-rCN/strings.xml
  - shared/ui/src/commonMain/composeResources/values-zh/strings.xml
  - shared/ui/src/commonMain/composeResources/values/strings.xml
---

## Decided
- The tray icon lives as long as the app runs, independent of the close-to-background setting.
- The menu has only Connect/Disconnect (opposite of tunnel state) and Exit, as the user chose.
- Connected = colour logo, disconnected = the same logo desaturated, so no extra icon assets.
- A click on the icon shows a hidden or minimized window, otherwise hides it like the close button would.

## Tried and ruled out
- Icon only while close-to-background is on, and a separate "show tray icon" setting: the user picked "always".
- Server picker submenu and a "Show window" item: not chosen.

## Watch out
- Without a "Show window" item, a window hidden to the background can only be restored by clicking the icon; some Linux tray hosts do not deliver that click.
- Hosts without tray support get no icon at all (isTraySupported is false).

In the code:
- desktopApp/.../app/DesktopTray.kt (decisions and the Tray composable), wired in Main.kt after desktopToggleTunnel
