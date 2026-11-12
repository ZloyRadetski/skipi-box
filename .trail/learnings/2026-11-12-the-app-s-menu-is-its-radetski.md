---
title: "The app's menu is its own popup, not a Material3 DropdownMenu: position, shadow, motion and rows are written here"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 29, tokens: 72593 }
id: l-a0b07a04
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/features/proxy/server/list/ProxyServerListPage.kt
  - app/src/main/kotlin/features/resources/runtime/AndroidResourceFileDownloadNotifier.kt
  - app/src/main/kotlin/features/subscription/SubscriptionGroupListPage.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/config/DesktopConfigsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/config/SkipiRoutingTableTest.kt
  - shared/ui/src/commonMain/composeResources/values-fa/strings.xml
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
  - shared/ui/src/commonMain/composeResources/values-zh-rCN/strings.xml
  - shared/ui/src/commonMain/composeResources/values-zh/strings.xml
---

## Decided
SkipiMenu is drawn by SkipiMenuPopup, a Compose Popup with its own position rule (under the anchor, flipped to the end or above when it would leave the window, kept inside otherwise), shadow, fade-and-scale motion and rows; Material3's DropdownMenu, DropdownMenuItem and HorizontalDivider are no longer used for menus anywhere. A tap outside, Back or Escape asks to dismiss it.

## Tried and ruled out
Keeping the Material3 popup under a restyled surface (the first attempt): it still looked and behaved like a Material menu, which the owner rejected twice.

## Watch out
The shadow needs an 8 dp margin inside the popup window or the window cuts it; the position rule subtracts that margin so the visible surface sits exactly under the anchor. A menu composed in a window shorter than itself is pushed to the top edge. Looked at only in an offscreen render, not in the running apps.

In the code:
- shared/ui/.../components/SkipiMenuPopup.kt (skipiMenuPosition, SkipiMenuPopup, SkipiMenuRowItem), SkipiMenu.kt
- test: shared/ui/src/commonTest/.../SkipiMenuPositionTest.kt
