---
title: Settings are shared through PersistedSettings up to the backup writer; the restore stays per client on purpose
author: Radetski
date: 2026-11-17
branch: refactor/shared-persisted-settings
cost: { minutes: 30, tokens: 56385 }
id: l-8feb5757
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/backup/DesktopBackup.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/backup/DesktopBackupRangesTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopApplicationRepositories.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/backup/DesktopEverySettingBackupTest.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/model/LanguageTags.kt
  - shared/app/src/commonMain/kotlin/data/backup/AppBackupMapper.kt
  - shared/app/src/commonMain/kotlin/data/backup/PersistedBackupMapping.kt
  - shared/app/src/commonMain/kotlin/data/repository/AppStateRecordMappers.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiGeneralSettingsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopThemeColorMode.kt
---

## Decided
The appearance screen, the backup writer, language tags and the theme choice are shared (one mapper each, used by Android and Desktop). The restore side of the backup stays per client: Android and Desktop validate differently on purpose (Android regenerates empty proxy credentials and user agents, Desktop keeps its own port, retention and timeout rules plus normalized()), so moving only the pass-through lines would add a layer without removing the real code. Nesting CustomColors into every model was dropped for the same reason: the mappers no longer repeat the colors.

## Tried and ruled out
Routing the General, VPN, Local proxy, Subscription and Logs screens through PersistedSettings: 3 to 8 lines each, mostly host-owned values, and on Android it would rebuild the whole shared model on every recomposition.

## Watch out
Two real bugs surfaced while comparing the clients: the Desktop backup never carried the theme, and the Desktop restore applied sizes no screen offers. Both now have tests. A backup writes the nearest font-size step, so a test that changes fontSizeMode needs a real step (120).

In the code: shared/app data/backup/PersistedBackupMapping.kt, shared/ui AppearanceStateMapping.kt, desktopApp DesktopBackup.kt, SettingsFieldInventoryTest.
