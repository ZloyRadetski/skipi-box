---
title: "The Windows installer's version needs a growing build number or a new MSI never upgrades the old one"
author: Radetski
date: 2026-11-09
branch: main
cost: { minutes: 37, tokens: 216762 }
id: l-207d4b80
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
  - buildSrc/src/main/kotlin/BuildConfig.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/proxy/XrayOutboundPlanner.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/proxy/XrayOutboundPlannerTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt
  - app/src/main/kotlin/features/updater/runtime/AppUpdateCoordinator.kt
  - shared/core/src/commonMain/kotlin/features/updater/AppUpdateState.kt
  - shared/core/src/commonTest/kotlin/features/updater/AppUpdateStateTest.kt
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
  - shared/ui/src/commonMain/composeResources/values/strings.xml
---

## Decided
The MSI ProductVersion is major.minor.commitCount, not the plain app version name. Windows Installer compares only three numbers, and every build of one x.y.z (0.5.0-rc.8, rc.9, 0.5.0) had the same ProductVersion 0.5.0, so a new MSI looked like the installed version and did not upgrade it.

## Tried and ruled out
Changing the upgrade code: it is fixed on purpose, it is what ties all installers to one product. Checked by reading the built MSI's Upgrade table: the new package replaces every installed version up to and including its own.

## Watch out
The commit count must stay within 65535 (the build field's limit) and the CI checkout needs full history (it has fetch-depth 0). A real upgrade over an installed copy was not run on a machine; only the MSI tables were read.

In the code: desktopApp/build.gradle.kts, windowsPackageVersion.
