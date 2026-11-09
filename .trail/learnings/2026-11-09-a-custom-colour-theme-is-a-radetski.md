---
title: "A custom colour theme is a saved set of the existing colour overrides, stored and shared as one short code"
author: Radetski
date: 2026-11-09
branch: main
cost: { minutes: 30, tokens: 232465 }
id: l-91387c1b
scope: repo
status: active
src: agent
touches:
  - docs/custom-themes-plan.md
  - shared/core/src/commonMain/kotlin/features/config/CustomThemeShare.kt
  - shared/core/src/commonMain/kotlin/features/config/SkipiDeepLink.kt
  - shared/core/src/commonMain/kotlin/features/config/SkipiDeepLinkPolicy.kt
  - shared/core/src/commonTest/kotlin/features/config/CustomThemeShareTest.kt
  - shared/core/src/commonTest/kotlin/features/config/SkipiDeepLinkPolicyTest.kt
  - shared/core/src/commonTest/kotlin/features/config/SkipiDeepLinkTest.kt
  - shared/core/src/commonTest/kotlin/features/config/CustomThemeLibraryTest.kt
  - shared/core/src/commonMain/kotlin/features/config/CustomThemeLibrary.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/settings/CustomThemeAppearanceTest.kt
---

## Decided
A user theme is the six colours of Appearance's custom colour overrides (accent, background, surface, surface variant, text, secondary text) plus a light or dark base and a name. Applying it writes those override fields, so Android and Desktop draw it with no new theme mode. The library is one string with a theme code per line, one new field in each store. A code is a version byte, a flag byte, six RGB colours, a name length and the UTF-8 name, in URL-safe Base64: about 40-60 characters, and the same code is the skipi://theme/ link.
A theme link adds to the library only after the user approves it, and never applies by itself.

## Tried and ruled out
A new ThemeMode or DesktopThemeMode value per user theme: both enums are persisted and mapped in many places, and the override layer already does the job.
Customising the Home layout (blocks, drag and drop, skins): judged far too big for the gain.

## Watch out
The same field has to be added in the shared AppState and AppearanceSettings, the record mappers, the Desktop settings and repositories, the Android preferences, the settings page adapter and both backup paths; a test for each store catches a missed one.
In the code: shared/core features/config/CustomThemeShare.kt and CustomThemeLibrary.kt, shared/ui settings/CustomThemesSection.kt and CustomThemeAppearance.kt.
