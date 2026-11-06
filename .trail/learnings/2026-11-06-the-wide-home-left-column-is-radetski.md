---
title: The wide Home left column is resized by a divider and the width is not saved yet
author: Radetski
date: 2026-11-06
branch: main
cost: { minutes: 3, tokens: 15313 }
id: l-c109d971
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiPaneDivider.kt
---

## Decided
The left column (power button and log) starts at the width the button needs and can be widened by dragging a divider, up to 60% of the window. It never goes narrower than the button. The width lives only in memory for the window's life; saving it would need a new setting.

## Tried and ruled out
A resize cursor on hover: common Compose code has no resize pointer icon, so the divider shows a thin line on hover and drag instead.

## Watch out
- Inside the Home screen content, `maxWidth` is already a local Modifier; use `this@BoxWithConstraints.maxWidth` for the real width.
- The wide branch is shared code, so the divider also appears on tablets.

In the code: shared/ui ProxyHomeScreen.kt (wide branch), SkipiPaneDivider.kt
