---
title: "Every menu and dropdown in the app is the app's own SkipiMenu, not a miuix popup"
author: Radetski
date: 2026-11-11
branch: main
cost: { minutes: 1, tokens: 1205 }
id: l-7d6fc9ae
scope: repo
status: active
replaces: l-121b6509
src: agent
---

## Decided
All dropdown preferences, spinner preferences, icon-button menus and context menus use one component, SkipiMenu: a Material3 dropdown in the app's colors with nested levels. The user asked for this after the miuix popups showed the text of long items cropped and rescaled on every frame of their opening animation, which the library does not let the app change. The earlier decision to keep both miuix hosts is withdrawn.

## Tried and ruled out
Keeping the miuix popups and shortening labels: it does not remove the cropping, only makes it shorter. Fixing the animation inside the library: the clip reveal and the spring are internal to it.

## Watch out
A Material3 menu opens next to the layout it is composed in. A menu for one card or button must be composed inside that card's own box (slots profileMenu, globalProxyGroupMenu, addConfigMenu); composed at the page root it opens next to the whole page. Inside the menu each level is measured at its own natural width, otherwise its text reflows on every frame while the surface glides to the next level.

In the code:
- shared/ui/.../components/SkipiMenu.kt, SkipiMenuModel.kt, SkipiDropdownPreference.kt, AppEntriesMenu.kt, IconDropdownMenu.kt
- shared/ui/.../home/SkipiHomeMenuWidth.kt: menuLevelConstraints
