---
title: Nested home menu animates its width per section because the Material3 menu snaps to the widest content
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 3, tokens: 23125 }
id: l-f4762ee5
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiHomeMenuWidth.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyHomeHeader.kt
---

## Decided
- The header menu pins its surface width to the natural width of the visible section and animates between sections (220 ms), so wider or narrower sections no longer make the menu jump.
- Each section reports its own intrinsic width; the first known width is applied without animation.

## Tried and ruled out
- Fixing it with only the size transform on the animated content: the menu itself sizes to the widest of the outgoing and incoming sections, which clamps that animation.
- A fixed menu width equal to the widest section: it would change how the top level looks.

## Watch out
- The cause is an assumption from how the menu sizes itself; it was not confirmed visually, only compiled and unit tests run.
- Other nested menus, if added, should reuse the same helpers instead of their own size transform.

In the code: shared/ui/.../home/SkipiHomeMenuWidth.kt, SkipiProxyHomeHeader.kt (SkipiProxyHomeHeaderMenu)
