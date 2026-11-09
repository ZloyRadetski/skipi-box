---
title: "Shared dialogs are checked by rendering their stateless content offscreen at phone size, and a button on the host accent is coloured by contrast"
author: Radetski
date: 2026-11-09
branch: main
cost: { minutes: 18, tokens: 128835 }
id: l-33b108c7
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
  - shared/ui/src/commonMain/composeResources/values/strings.xml
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/CustomThemesSection.kt
  - app/src/main/kotlin/app/MainActivity.kt
  - app/src/main/kotlin/data/AppSettingsPreferences.kt
  - app/src/main/kotlin/features/settings/SettingsAppearancePage.kt
  - app/src/main/res/values-ru/strings.xml
  - app/src/main/res/values/strings.xml
  - app/src/test/kotlin/data/backup/AppBackupMapperTest.kt
  - app/src/test/kotlin/data/repository/AndroidAppRepositoryMappingTest.kt
---

## Decided
Dialog bodies in shared UI are stateless composables, so a throwaway test in the desktop app can draw them with ImageComposeScene at 390x844 (density 2) inside the app theme, write a PNG, and the layout is judged from the picture. That caught what unit tests could not: three buttons in one row squeezed the last label into a column of single letters on a phone.
Buttons that sit on the primary colour take black or white by contrast, not the host's on-accent colour, because the default light palette pairs a dark on-accent with a slate accent.

## Tried and ruled out
Judging dialogs only by compiling and unit tests: the first version of the theme dialogs passed both and was unusable on a phone.

## Watch out
Content composables are internal to the shared UI module, so the throwaway render test needs them made public for a moment; restore internal and delete the test before committing. Wide button rows must share width with weights or stack.
In the code: shared/ui settings/CustomThemesSection.kt; the render pattern is the same as desktopApp/src/test config/DesktopTrafficConfigProxyGroupsResponsiveRenderTest.kt.
