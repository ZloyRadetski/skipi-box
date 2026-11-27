---
title: "A Desktop-only settings page is left out of the default search pages, and Android maps it to nothing"
author: Radetski
date: 2026-11-27
branch: feat/net-merge
cost: { minutes: 27, tokens: 112853 }
id: l-194962e7
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/settings/DesktopMultilinkSettingsRenderTest.kt
  - shared/core/src/commonMain/kotlin/features/multilink/MultilinkOverview.kt
  - shared/core/src/commonTest/kotlin/features/multilink/MultilinkOverviewTest.kt
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
  - shared/ui/src/commonMain/composeResources/values/strings.xml
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiMultilinkSettingsScreen.kt
---

## Decided
Link merging is the first settings page that exists only on Desktop. The search host's default pages (Android's) leave it out, the Desktop host and the test hosts list every page, and Android's overview and search mappings return no route for it instead of a stand-in page. The page's rows are in the search catalog with anchors; its sheets live in a *Sheet.kt file, which the source test does not scan.

The page's look is checked from images a render test writes, not by eye in the running app.

## Watch out
kotlin.math.round rounds a half to even (0.5 -> 0); speeds shown to people use floor(x + 0.5).

In the code: SettingsSearchModel.kt (default pages minus Multilink), SettingsSearchCatalogTest and SettingsSearchSourceTest hosts, app SettingsPage.kt (Multilink -> null), DesktopMultilinkSettingsRenderTest writes desktopApp/build/reports/multilink-settings/*.png, formatMbps in MultilinkSettings.kt.
