---
title: "Settings rows get their icon through the Miuix startAction slot, colored by the user's icon color"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 15, tokens: 112400 }
id: l-17f93952
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/app/App.kt
  - app/src/main/kotlin/features/settings/LocalProxySettingsPage.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopVpnSettings.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/networkautomation/SkipiNetworkAutomationScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiAboutScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiAppearanceSections.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiBackupResetSettingsScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiGeneralSettingsScreen.kt
---

## Decided
Each settings row shows a small 32 dp icon tile in the user's "icon color", because plain rows looked monotonous and the home page of settings already uses colored tiles. The icon goes into the Miuix preference's own startAction slot (settingIcon(icon)), so the row, switch and click handling stay Miuix and follow the font and theme settings.
The color reaches every page through one composition local that Android (App.kt) and Desktop (Main.kt around the settings screen) provide; no page gets the color passed down.

## Tried and ruled out
Writing our own icon row wrappers for switch, arrow and info rows: not needed, every Miuix preference already has startAction, so the wrapper would only copy its signature.

## Watch out
- Desktop used a fixed color on the settings home and ignored the user's setting; both now use the setting with the old color as the default.
- The app module has no icon set, so hosts take icons from SkipiSettingRowIcons or pass them as the icon parameter of the platform row helpers.
- Seen only in an offscreen render (light and dark, VPN page), not on a device.

In the code: SkipiSettingIcon.kt (settingIcon, ProvideSettingsIconColor, LocalSettingsIconColor).
