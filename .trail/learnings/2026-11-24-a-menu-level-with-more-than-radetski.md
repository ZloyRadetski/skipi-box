---
title: A menu level with more than 40 rows is a lazy list at a fixed width; shorter menus keep the intrinsic width
author: Radetski
date: 2026-11-24
branch: main
cost: { minutes: 6, tokens: 47786 }
id: l-3e255958
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SkipiMenu.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SkipiMenuModel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SkipiMenuPopup.kt
  - shared/ui/src/desktopTest/kotlin/app/skipi/ui/components/SkipiMenuLongListTest.kt
---

## Decided
The slow "long list" was the dropdown menus (both platforms), not the server list or logs. A menu composed and measured every row, twice for the intrinsic width. Any level over 40 rows, or a menu with a submenu over 40, is drawn as a LazyColumn at the full menu width (280 dp); the popup then has no scroll wrapper and no intrinsic width. Menus up to 40 rows are unchanged, so their width still follows the widest row.

## Tried and ruled out
- Keeping the Column and measuring the widest rows by text: a lazy list cannot answer intrinsic width, and a Column nested in a verticalScroll cannot hold a lazy list (infinite height).
- Estimating the width from the longest titles: more code for little gain; long menus are wide anyway.

## Watch out
- A long menu does not shrink to its content width, and the width glide between levels does not apply to it.
- Not timed on a real list, only a Compose UI test (5000 rows: only the visible ones exist) that fails with the threshold raised.

In the code: SkipiMenu and LazyMenuLevel, SkipiMenuPopup (lazyRows), hasLongLevel and LazyMenuRowThreshold in SkipiMenuModel.kt; test SkipiMenuLongListTest in shared/ui desktopTest.
