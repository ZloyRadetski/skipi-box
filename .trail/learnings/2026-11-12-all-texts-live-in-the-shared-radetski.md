---
title: "All texts live in the shared module; Android's string resources are generated from them at build time"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 4, tokens: 36014 }
id: l-00c0e5ab
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SkipiMenu.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SkipiMenuModel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeEmptyState.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeServerListItem.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyGroupPicker.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyGroupSelector.kt
  - app/build.gradle.kts
---

## Decided
The only place to edit texts is shared/ui composeResources (values, values-ru, values-fa, values-zh, values-zh-rCN). The Android app has no strings.xml of its own: a Gradle copy task generates its R.string resources from the shared files, so services, notifications, widgets and the manifest still read R.string and nothing else changed. The 23 strings that existed only on Android were moved into shared first. Where the two copies differed (settings_connection_display_mode_summary, tools_dns_failed, one fa/ru icon name) the shared text won.

## Tried and ruled out
Converting every R.string use in the 53 Android files to the shared Res accessors: services and notifications cannot read Compose resources without suspending, so generation is the simple single source.

## Watch out
The two formats share the same escaping (\' and so on), so the files are copied as they are; if a shared string ever needs something Android resources reject, the copy fails at resource processing. Desktop still has about 300 hard-coded Russian literals that are not in any language file yet.

In the code:
- app/build.gradle.kts: syncSharedStrings (preBuild), source dir build/generated/sharedStringsRes
- edit texts only in shared/ui/src/commonMain/composeResources/values*/strings.xml
