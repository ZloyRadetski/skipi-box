---
title: A named theme shows its own accent unless Custom colours are on; elsewhere a picked accent works without the switch
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 9, tokens: 109644 }
id: l-491b393e
scope: repo
status: active
replaces: l-5f492256
src: agent
touches:
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/theme/AppearanceColorOverridesTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/AppearanceSettingsState.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopVisualThemeContractTest.kt
  - app/src/main/kotlin/ui/Theme.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopVisualTheme.kt
---

## Decided
Aurora, Sakura, Forest and Sunset show their own accent while "Custom colours" is off, even if an accent was picked earlier for another theme. With the switch on, a picked accent paints a named theme too. On the other themes a picked accent still works with the switch off, and "dynamic colours" there still shows the system accent. Dynamic colours do nothing on a named theme.
The accent row follows the same rule: on a named theme it shows only with Custom colours on; on other themes it hides while dynamic colours are on.

## Tried and ruled out
The Nov 18 rule (a picked accent wins over a named theme's accent with the switch off) made choosing Aurora and the rest change nothing about the accent. The owner reported it as a bug. The accent row was also hidden on named themes, so the user could neither see nor change the colour that was painting them.

## Watch out
Picking a theme turns Custom colours off (withColorMode), and that is what lets the named accent show. Do not clear the saved accent instead: it comes back on the plain themes.

In the code:
- shared/ui/.../theme/AppearanceColorOverrides.kt: applyAppearanceSettings
- shared/ui/.../settings/AppearanceSettingsState.kt: showsAccentPicker, used by SkipiAppearanceSections
- tests: AppearanceColorOverridesTest, AppearanceAccentRowTest, DesktopVisualThemeContractTest.everyNamedDesktopThemeShowsItsOwnAccentOverOnePickedEarlier
