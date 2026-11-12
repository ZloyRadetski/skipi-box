---
title: "Every dropdown goes through SkipiMenu; Home menus with their own palette, a search field or a button row are SkipiMenu slots, not Material3 items"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 3, tokens: 48292 }
id: l-21cae078
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingsHomeScreen.kt
---

## Decided
All menus of the app, including the Home ones (group picker, group chip menu, server long-press menu, empty-state Add menu), are SkipiMenu. SkipiMenu takes optional surface/text/mutedText/outline colours for screens with their own palette, and header/footer slots above and below the top-level rows (the group search field, the move up/down arrow row). A row can carry its own spoken description.

## Tried and ruled out
Rewriting the popup base on Compose Popup instead of the Material3 DropdownMenu: much bigger (positioning, dismissal, keyboard) and not what was asked; SkipiMenu still sits on one Material3 popup inside the single SkipiHomeDropdownMenu helper.

## Watch out
A new menu must be a SkipiMenu; do not import DropdownMenu or DropdownMenuItem from Material3 anywhere except that one helper. Not checked visually on a device.

In the code:
- shared/ui/.../components/SkipiMenu.kt, SkipiMenuModel.kt (SkipiMenuItem.contentDescription)
- shared/ui/.../home/: ProxyHomeEmptyState, ProxyHomeServerListItem, SkipiProxyGroupPicker, SkipiProxyGroupSelector
