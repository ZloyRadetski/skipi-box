---
title: "The home search bar is an Appearance setting again, off by default since 2026-11-04"
author: Radetski
date: 2026-11-03
branch: main
cost: { minutes: 15, tokens: 68570 }
id: l-80624445
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/features/proxy/server/list/ProxyHomeAndroidInput.kt
  - app/src/test/kotlin/data/repository/AndroidPortableSettingsContractTest.kt
  - app/src/test/kotlin/features/proxy/server/list/ProxyHomeAndroidInputTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/home/DesktopProxyHome.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsLibrary.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/home/DesktopProxyHomeLatencyFabTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/settings/DesktopSettingsLibraryTest.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/home/ProxyHomeStore.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/model/PersistedModels.kt
  - shared/core/src/commonMain/kotlin/app/AppState.kt
---

## Decided
The user wants search switched on and off in Appearance, not from the three-dot menu. The stored server-search flag drives the search bar on both Android and desktop, and the bar is shown whenever the flag is on. It defaulted to on so installs that never stored the flag kept their search; on 2026-11-04 the user asked for off by default, so those installs now lose the bar until they turn it on.

## Tried and ruled out
An earlier session had made search always available and left the flag only for backups; this reverses that on purpose. Keeping a menu toggle on top of the setting was ruled out: it duplicated the setting.

## Watch out
The default must match in every place that holds it: the app state, the backup defaults, the persisted home settings and the desktop settings. One left at false made a backup restore and a desktop test fail until the backup default was flipped. Installs that stored the flag as off from an older version lose their search bar until they turn it on. The desktop click test uses fixed pixel positions, so it turns search off to keep its layout.

In the code:
- shared/core/.../features/backup/AppBackupModels.kt: BackupDefaults.showServerSearch
- shared/core/.../app/AppState.kt: showServerSearch
- shared/ui/.../settings/SkipiAppearanceSettingsScreen.kt: the switch
