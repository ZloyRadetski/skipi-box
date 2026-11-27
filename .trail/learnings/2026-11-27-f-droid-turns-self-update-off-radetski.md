---
title: "F-Droid turns self-update off with a Gradle property, not a product flavor"
author: Radetski
date: 2026-11-27
branch: main
cost: { minutes: 16, tokens: 147894 }
id: l-7db4de9d
scope: repo
status: active
src: agent
touches:
  - app/src/test/kotlin/features/updater/runtime/SelfUpdateDisabledTest.kt
---

## Decided
Builds for a store pass a Gradle property that switches self-update off at compile time and removes the install permission. The default build keeps updating itself. Every entry point is gated (checks, downloads, banner, About section, settings search), and a build without self-update cancels queued update work and forgets a saved offer, because an APK with the same signature can install over a GitHub build.

In the code: `-PskipiSelfUpdate=false` -> `BuildConfig.SELF_UPDATE` -> `features.updater.SelfUpdateEnabled`; overlay `app/src/noSelfUpdate/AndroidManifest.xml`; `retireSelfUpdate`.

## Tried and ruled out
Product flavors: they rename Gradle tasks and APK outputs (testDebugUnitTest and friends), which CI and release steps depend on, for one boolean.

## Watch out
A new place that starts an update check or shows update UI has to respect the flag. The updater classes stay partly inside the APK (WorkManager workers are kept by R8); they are inert.
