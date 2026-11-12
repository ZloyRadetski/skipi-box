---
title: "Geo files, CIDR lists and fonts live in one shared folder that Android and Desktop both build from"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 6, tokens: 30514 }
id: l-bf4649a8
scope: repo
status: active
src: agent
touches:
  - app/build.gradle.kts
  - desktopApp/build.gradle.kts
  - build.gradle.kts
---

## Decided
The bundled binary data (geo .dat.xz sources, the direct-CIDR lists, the seven fonts) lives in shared/bundled, not in the Android module. Android copies what it needs at build time (assets for geo and CIDR, res/font for fonts, plus the logo and the strings) and Desktop packs the same folder into its resources. The updateResourceFileAssets task now writes the refreshed geo files there. The about logo exists once, in shared ui's composeResources; the Android copy is generated. The anydpi-v33 launcher icons were exact copies of the v26 ones and were removed.

## Tried and ruled out
Putting the fonts into composeResources: Desktop would then carry them twice (as Compose resources and as the font files it loads by name), about 6 MB. Pointing Android's asset source straight at the shared folder: it would also pack the fonts into the APK assets.

## Watch out
Both sync tasks must run after updateResourceFileAssets, or Gradle rejects the build for an undeclared dependency. The values-night-v31 colors look like a copy of values-v31 but cannot be removed: night has priority over the version qualifier. Flags with identical images (no/bv/sj, fr/mf, us/um) are still separate files.

In the code:
- shared/bundled/{geo,xray,font}
- app/build.gradle.kts: syncSharedResources, syncSharedBundledAssets; desktopApp/build.gradle.kts processResources; root build.gradle.kts updateResourceFileAssets
