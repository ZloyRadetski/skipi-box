---
title: Changing the page background must never move the app content in the composition
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 17, tokens: 121297 }
id: l-ac8e7c07
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/app/AppContent.kt
---

## Decided
The background composable draws its style layers first and calls the page content once, after the style switch, in a fixed place.

## Tried and ruled out
Removing the background style from the navigation entry key in AppContent: it was a real but minor rebuild, and the user saw no change. The first "defensive" photo fixes were also off target.

## Watch out
Calling content() inside each branch of when(style) makes the whole app (back stack, open settings page, a pending gallery picker) leave and re-enter the composition on every style change. That sent the user to Home and dropped the picked photo, because the page waiting for the picker result was gone. Always reproduce with a test before claiming a UI fix.

In the code: SkipiAppBackground in shared/ui; regression test changingTheStyleKeepsTheContentAliveSoThePageIsNotRebuilt in SkipiAppBackgroundIsolationTest.
