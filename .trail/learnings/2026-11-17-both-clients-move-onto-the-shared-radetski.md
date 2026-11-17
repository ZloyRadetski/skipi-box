---
title: Both clients move onto the shared PersistedSettings; Desktop is not moved onto AppState
author: Radetski
date: 2026-11-17
branch: refactor/shared-persisted-settings
cost: { minutes: 10, tokens: 63514 }
id: l-695ac5e1
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/settings/SettingsFieldInventoryTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingIcon.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingsHomeScreen.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/settings/SettingIconColorsTest.kt
  - app/src/main/kotlin/app/AppStateSlices.kt
  - app/src/main/kotlin/features/settings/SettingsAppearancePage.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/backup/DesktopBackup.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopApplicationRepositories.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopCustomColors.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/settings/CustomColorsConversionTest.kt
---

## Decided
The duplicated settings mapping is removed by making PersistedSettings the one model both clients map to, with one shared mapper from it to the settings-screen states. DesktopAppSettings stays the storage format (no settings.json change) and AppState stays Android's runtime model. Work runs in stages on branch refactor/shared-persisted-settings: classify every field, add sections for the 27 settings both clients have but PersistedSettings lacks, one shared state mapper, switch the screens, one backup mapper, nest CustomColors, then a guard for host-only fields.

## Tried and ruled out
Moving Desktop onto AppState: it touches 30 main and 58 test files that mention DesktopAppSettings and needs a settings.json migration, for a gain PersistedSettings already gives.

## Watch out
The 27 settings still to share: VPN (strict full tunnel, local DNS, tun mtu/dns/cidrs/keepalive), network automation, sniffing, mux, fragment, log level/access log/retention, update check, auto connect, next route rule id. SettingsFieldInventoryTest fails on a new unclassified field in either model; when a stage moves a field into PersistedSettings, move its name in that test.

In the code: shared/app PersistedModels.kt, desktopApp SettingsFieldInventoryTest.
