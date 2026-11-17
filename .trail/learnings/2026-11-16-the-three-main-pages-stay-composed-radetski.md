---
title: The three main pages stay composed so a tab switch only slides ready pages
author: Radetski
date: 2026-11-16
branch: main
cost: { minutes: 20, tokens: 54932 }
id: l-86f1b530
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/app/App.kt
  - app/src/main/kotlin/features/config/TrafficConfigEditorPages.kt
  - app/src/main/kotlin/features/config/TrafficConfigPage.kt
  - app/src/main/kotlin/features/proxy/server/list/ProxyHomeAndroidInput.kt
  - app/src/main/kotlin/features/routing/ui/RoutingSuggestionComponents.kt
  - app/src/main/kotlin/features/settings/SettingsPage.kt
  - app/src/main/kotlin/features/settings/sheets/AppIconSelectionBottomSheet.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopVisualTheme.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/config/DesktopConfigsScreen.kt
---

## Decided
The top-level pager keeps all three pages (Home, Configs, Settings) composed. Going from Settings to Home used to compose Home from scratch inside the 280 ms page animation, which dropped frames. This is a hypothesis from the code, not a measurement: no profiler run was available.

## Tried and ruled out
Jumping to the page next to the target before animating: it flashes the middle page. Keeping only neighbours composed: Home is not a neighbour of Settings, so the first composition still lands in the animation.

## Watch out
Hidden pages keep recomposing when their state changes, so battery and a slower first start are the cost; if Settings itself gets laggy, look here first. If the lag stays, get a profiler trace and look at what is heavy inside Home itself.

In the code: SkipiMainPager (beyondViewportPageCount); test MainPagerKeepsPagesTest in the desktop tests.
