---
title: "New PersistedSettings sections are nullable and the core log level travels as Android's index"
author: Radetski
date: 2026-11-17
branch: refactor/shared-persisted-settings
cost: { minutes: 10, tokens: 64541 }
id: l-04ff1b49
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopApplicationRepositories.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/settings/DesktopRuntimeSectionsParityTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/settings/SettingsFieldInventoryTest.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/model/PersistedModels.kt
  - shared/app/src/commonMain/kotlin/data/repository/AppStateRecordMappers.kt
  - shared/app/src/commonTest/kotlin/data/RuntimeSectionsPersistenceTest.kt
---

## Decided
The VPN, engine, log and network-automation settings are new nullable sections of PersistedSettings; a null section leaves the host's own values, like the older local proxy and subscription sections. The core log level is shared as Android's index (0 debug, 1 info, 2 warning, 3 error, 4 none), the same number the backup stores; Desktop converts to and from its level names.

## Tried and ruled out
Sharing the level as a name: Android and the backup already store the index, so a name would have added a second conversion on the Android side.

## Watch out
autoCheckAppUpdates and autoConnectOnAppOpen sit in ApplicationPreferences with plain defaults (true, false). A PersistedSettings built from scratch, not with copy from the current one, resets them. A level index Desktop has no name for keeps the current level. The routing rule counter is the only setting still unshared.

In the code: shared/app PersistedModels.kt, AppStateRecordMappers.kt; desktopApp DesktopApplicationRepositories.kt (withLogPreferences).
