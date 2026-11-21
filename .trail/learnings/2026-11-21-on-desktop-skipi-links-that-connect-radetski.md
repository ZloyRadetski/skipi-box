---
title: "On desktop, skipi:// links that connect, disconnect, toggle or close need an external-control switch that is off by default"
author: Radetski
date: 2026-11-21
branch: main
cost: { minutes: 10, tokens: 129320 }
id: l-a39cef32
scope: repo
status: active
src: agent
touches:
  - buildSrc/src/main/kotlin/BuildConfig.kt
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopGraphicsAcceleration.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopGraphicsAccelerationTest.kt
  - shared/ui/src/commonMain/composeResources/values-fa/strings.xml
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
  - shared/ui/src/commonMain/composeResources/values-zh-rCN/strings.xml
  - shared/ui/src/commonMain/composeResources/values-zh/strings.xml
---

## Decided
Desktop ignores skipi://connect, disconnect, toggle and close unless the user turned on Settings > General > External control. Any web page can fire a skipi:// link, and a silent disconnect shows the real address. Android already gated these (DeepLinkGate.NeedsAutomationEnabled); desktop skipped the gate. The switch is a per-machine desktop setting, off by default, not carried by a backup, so a restored backup cannot open it. A blocked link logs and shows a notification saying external control is off.

## Tried and ruled out
Telling a browser link from a shortcut: both arrive as the same argv (skipi://toggle or --toggle), so one switch covers both. Reusing Android's broadcast-control strings: they talk about broadcasts, so desktop got its own texts.

## Watch out
A keyboard shortcut running `SKIPI skipi://toggle` stops working until the switch is on; the notification says why. Open and theme links are unaffected (theme is approved on screen).

In the code: desktopLinkAllowed in DesktopLaunchRequest.kt, the link loop in Main.kt, DesktopAppSettings.allowExternalControl.
