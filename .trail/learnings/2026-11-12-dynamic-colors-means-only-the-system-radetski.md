---
title: "Dynamic colors means only the system accent, desktop Back goes to the newest screen with a back arrow, and deletion dialogs obey the setting by themselves"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 21, tokens: 289501 }
id: l-cfeddf2f
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/app/AppContent.kt
  - app/src/main/kotlin/app/navigation/Route.kt
  - app/src/main/kotlin/engine/xray/XrayConfig.kt
  - app/src/main/kotlin/features/config/TrafficConfigPage.kt
  - app/src/main/kotlin/features/proxy/server/editor/ProxyServerPage.kt
  - app/src/main/kotlin/features/proxy/server/editor/StrategyGroupMemberSelectorPage.kt
  - app/src/main/kotlin/features/routing/RouteOutboundSelectorPage.kt
  - app/src/main/res/values-fa/strings.xml
  - app/src/main/res/values-ru/strings.xml
  - app/src/main/res/values-zh-rCN/strings.xml
---

## Decided
- The "Dynamic colors" switch only turns on the system accent (Material You on Android, the OS accent on Desktop: Windows registry, macOS defaults, GNOME gsettings). The seed-colour dropdown is gone; turning it on resets the seed to "system". While it is on, a hand-picked accent never overrides it, and the custom accent row is hidden.
- Desktop Escape and the mouse back button go to the newest screen whose top bar shows a back arrow (BackNavigationIcon registers itself), then to a page registered with the escape handler.
- Deletion dialogs obey the "Deletion confirmation" setting without each caller checking it: with it off, DeleteConfirmationDialog confirms immediately.
- Adding a theme (link or pasted code) switches the app to it; a theme already saved is applied too, a full library changes nothing.
- Menu rows use 13 sp and a menu is at most 280 dp wide, so long rows wrap instead of being cut.

## Tried and ruled out
- Finding the one deletion dialog that ignored the setting: every caller I read already checked it, so the guard sits in the dialog instead.

## Watch out
- The custom-photo and "refresh button without a link" reports could not be reproduced by reading the code; the fixes (fallback decoding, page follows the saved photo, tip on failure, http(s)-only refresh button) are unverified on a device.
- The Desktop Home still edits a profile-owned group through the catalog record, not the profile text (Android opens the group's own editor).

In the code: AppearanceColorOverrides.kt, DesktopSystemAccent.kt, NavigationIcon.kt (BackActions), DeleteConfirmationDialog.kt (LocalConfirmDeletion), CustomThemeLibrary.kt (withImportedCustomTheme), SkipiHomeMenuWidth.kt
