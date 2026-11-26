---
title: Settings search uses a catalog plus row anchors instead of passing the row through navigation
author: Radetski
date: 2026-11-26
branch: main
cost: { minutes: 30, tokens: 302575 }
id: l-68c78f0e
scope: repo
status: active
src: agent
touches:
  - shared/app/src/commonTest/kotlin/app/skipi/app/settings/search/SettingsSearchTest.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/settings/search/SettingsSearch.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/settings/search/SettingsSearchCatalogTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/search/SettingsSearchModel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/search/SettingsSearchCatalog.kt
  - shared/ui/src/desktopTest/kotlin/app/skipi/ui/settings/search/SettingsSearchUiTest.kt
  - shared/ui/src/desktopTest/kotlin/app/skipi/ui/settings/search/SettingsSearchSourceTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/search/SettingsSearchController.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/search/SettingsSearchAnchors.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/search/SettingsSearchResults.kt
---

## Decided
Settings search keeps a hand-written catalog beside the pages, and a shared in-memory holder tells the opened page which row to show. Routes and desktop destinations carry no row argument, so only the overview and the two hosts know about navigation. Every new settings row needs a catalog entry and an anchor under the same id; source tests fail otherwise.

## Tried and ruled out
- Passing the row id through Android routes and desktop destinations: touches every screen signature for state that is fine to lose on process death.
- Describing all pages declaratively so the catalog is derived: a rewrite of every page.
- Mapping rows to lazy list indices: breaks with conditional rows; the page scrolls down until the anchor composes, then falls back to the setting that shows it.

## Watch out
Calling scrollToItem from an effect that starts with a composition crashed Compose UI tests with "pending composition has not been applied". Use requestScrollToItem, or wait a frame first.

In the code: shared/ui settings/search (catalog, anchors, controller), shared/app settings/search (matching), SettingsSearchSourceTest.
