---
title: Desktop Home visuals are checked by running a throwaway preview main against the built distribution with the system JDK
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 14, tokens: 150579 }
id: l-3ca5e8cd
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyConnectionHero.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyHeroVisuals.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiHeroInfoTiles.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyServerCard.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyGroupPicker.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyServerCompactListCard.kt
---

## Decided
- The real tunnel does not come up in a plain local run, so the connected and connecting hero states are looked at with a temporary main that renders the hero with fake state inside the desktop theme. The file is deleted afterwards.

## Tried and ruled out
- Clicking the power button in the running app: it stays disconnected here.
- Launching the preview with the jlink runtime shipped in the distribution: it has no java executable.

## Watch out
- Build with createDistributable first, then run the system java with the distribution's app jars on the class path and skiko.library.path pointing at the app folder.
- The theme wrapper is in package app.skipi.desktop, not a nested app package.

In the code: desktopApp/src/main (temporary preview file, not kept), desktopApp/build/compose/binaries/main/app/SKIPI/app
