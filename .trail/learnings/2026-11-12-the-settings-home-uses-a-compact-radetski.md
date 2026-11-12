---
title: "The settings home uses a compact top bar: the large collapsing title leaves a tall empty band above it"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 2, tokens: 35631 }
id: l-9790dac4
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingsHomeScreen.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/subscription/SubscriptionRefreshUseCase.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/subscription/SubscriptionRefreshUseCaseTest.kt
---

## Decided
The settings home screen uses a compact top bar (title plus version line) that sits below the status bar on a phone. The large collapsing Miuix bar has a tall empty zone above its title, which looked like a strange top gap.

## Tried and ruled out
The page-background change (the commit that moved app content into one place) was suspected first; it does not touch padding and is not the cause. The gap appeared when the settings bar was switched from the M3 bar to the standard page bar.

## Watch out
Not checked on a device or screenshot, only compiled. Other pages using the large bar (subscriptions, diagnostics, editors) may show the same band; the user has not asked to change them.

In the code:
- shared/ui/.../settings/SkipiSettingsHomeScreen.kt: SmallTopAppBar with defaultWindowInsetsPadding = !isWideScreen
- shared/ui/.../layout/PageLayout.kt: AdaptiveTopAppBar (large bar on narrow screens)
