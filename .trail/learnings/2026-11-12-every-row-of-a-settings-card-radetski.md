---
title: "Every row of a settings card carries a tile, so the text edge is the same; a soft tint tile failed on dark colors"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 13, tokens: 120626 }
id: l-5a4d9761
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopVpnSettings.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SkipiDropdownPreference.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/networkautomation/SkipiNetworkAutomationScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/CustomThemesSection.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiAboutScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiAppearanceSections.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiAppearanceSettingsScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiBackupResetSettingsScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiGeneralSettingsScreen.kt
---

## Decided
In a settings card either every row has a start tile or none does: mixed rows put the text at two different left edges and looked disharmonious. So dropdowns, spinners, sliders (one shared SkipiSliderPreference), color rows (the color is the tile) and the expiry switch all use the Miuix row layout with a tile.
The tile is solid in the user's icon color with a black or white glyph picked by contrast (readableOn), and glyphs come from the rounded set. The tile adds 6 dp after itself because Miuix leaves only 8 dp between the start action and the text.

## Tried and ruled out
A soft tint tile (icon color at 16 percent, glyph in the full color): a dark icon color on a dark card made the glyphs almost invisible.
Hand-built rows for sliders and colors: their text started about 14 dp away from the Miuix rows.

## Watch out
- Rows that are inputs or segmented controls (text fields, ping mode chips, URL scheme command chips) keep no icon on purpose.
- Pages are checked with a throwaway offscreen render of the whole screen (state class plus ImageComposeScene in a desktopApp test); delete the test afterwards.

In the code: SkipiSettingIcon.kt (tile, swatch), SkipiSliderPreference.kt, SkipiDropdownPreference.kt (startAction).
