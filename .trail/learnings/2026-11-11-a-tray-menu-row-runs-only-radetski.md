---
title: "A tray menu row runs only on a full left click, not on any mouse button"
author: Radetski
date: 2026-11-11
branch: main
cost: { minutes: 3, tokens: 45697 }
id: l-84825481
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopTrayMenu.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopTray.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
---

## Decided
- Rows of the Compose tray menu react to a left press and release that both happen on the row. The menu opens under the cursor while the right button of the tray click may still be down, and Compose's clickable accepts any button on desktop, so a right-button event could activate Exit.

## Tried and ruled out
- Keeping clickable: it does not tell buttons apart.

## Watch out
- Not reproduced in a running app; the cause is inferred. Tray click coordinates are AWT pixels but the window is placed in dp, so at 125-150% scaling the menu can land with Exit under the cursor. If Exit still fires without a left click, look at that conversion first.
In the code: DesktopTrayMenuRow in DesktopTrayMenu.kt, DesktopTrayMenuWindow in DesktopTray.kt.
