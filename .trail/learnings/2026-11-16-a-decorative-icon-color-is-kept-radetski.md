---
title: "A decorative icon color is kept as a fallback, never dropped: a Material icon with no color draws black on dark themes"
author: Radetski
date: 2026-11-16
branch: main
cost: { minutes: 27, tokens: 84883 }
id: l-c8616082
scope: repo
status: active
src: agent
---

## Decided
When icons were moved onto the shared wrapper, the colors they already had for looks (text color, muted text, primary) are kept as a fallback tint, not removed. The picked "Icon color" still wins over them; with no pick the icon keeps its old color.

## Tried and ruled out
Deleting those tints so the wrapper's default would take over: a Material icon with no tint falls back to the library's default content color, which is black, so on dark themes and OLED it was black on black (three dots, refresh). It looked fine on light themes, so it slipped through.

## Watch out
A render test that only checks the picked color cannot catch this; check the no-pick case on a dark theme too. If the icons look dim on dark themes, look for an icon that lost its fallback tint.

In the code: SkipiIcon / SkipiContentIcon take fallbackTint; the render test is IconColorRenderTest in desktopApp tests.
