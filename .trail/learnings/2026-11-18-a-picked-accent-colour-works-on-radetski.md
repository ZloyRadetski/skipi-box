---
title: A picked accent colour works on its own; the Custom colours switch gates only the other colours
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 6, tokens: 224080 }
id: l-5f492256
scope: repo
status: active
src: agent
---

## Decided
The accent the user picked applies whether "Custom colours" is on or off, on Android and Desktop. The switch still gates background, surfaces and text. The one exception is "dynamic colours" on a theme without a palette: the system accent wins there. A picked accent also wins over a named theme's accent.

## Tried and ruled out
The earlier rule (accent follows the switch, so with it off the theme's own accent shows) was reversed on the owner's request: it looked like a bug that a picked accent did nothing. A visual-polish branch that changed Home and the on-accent glyph colour was thrown away on request; nothing from it was kept.

## Watch out
Older tests pinned the old rule and were rewritten first. Other picked colours (status, ping, protocol, icon glyph) still go through customColorIfEnabled and still need the switch.

In the code:
- shared/ui/.../theme/AppearanceColorOverrides.kt: applyAppearanceSettings
- app/.../features/onboarding/OnboardingPage.kt: onboardingAccentIndex no longer checks the switch
- tests: AppearanceColorOverridesTest
