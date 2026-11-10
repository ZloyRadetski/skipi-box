---
title: "Home lists manual servers, balancers and the user's proxy groups before subscriptions, and the server list is one column"
author: Radetski
date: 2026-11-10
branch: main
cost: { minutes: 23, tokens: 329660 }
id: l-3ddfca1f
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/app/AppContent.kt
  - app/src/main/kotlin/app/navigation/Route.kt
  - app/src/main/kotlin/features/settings/LocalProxySettingsPage.kt
  - app/src/main/kotlin/features/settings/SettingsPage.kt
  - app/src/main/kotlin/features/settings/SettingsSubscriptionsPage.kt
  - app/src/main/kotlin/features/settings/SettingsVpnPage.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopApplicationRepositories.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunSettings.kt
---

## Decided
The group selector order is fixed: All, Manual servers, Auto balancers, the user's own proxy groups, then subscriptions (each kind in stored order). The user wants what they keep by hand never to sink below providers. A group moves up or down only among groups of its own kind, so every step is visible. The first group in the list is now also the fallback selection when the saved one is missing.
The "Server list columns" setting is removed everywhere (Android, Desktop, backups, strings in all locales): the list is always one column. Old backups and settings files that still hold the key load fine and the key is dropped on the next export.
Manual groups and balancers get a header like a subscription's, with a ping button and an in-progress state but no refresh or settings.

## Tried and ruled out
Keeping the old fallback selection (first subscription, manual last) while only reordering the list: Desktop's default group comes from the list order, so the two would have disagreed.
A new header design for manual groups: Home redesigns were rejected before, so the subscription card is reused with parts hidden.

## Watch out
The released-backup guard test fails when a setting that old backups hold disappears. A removed setting must be added to its retired list, with the reason.
Subscription kind is not in the stored group's id: grouping needs the new `remote` flag, set from a non-blank URL by both hosts. A new host that forgets it sorts its subscriptions among the proxy groups.

In the code:
- Order: visibleGroups in shared/app ProxyServerGrouping.kt; section-aware moveSubscriptionGroup (sectionOf); move-up/down neighbour check in ProxyHomePanels.kt.
- Retired setting: retiredSettings in BackupFixtureCompatibilityTest.kt.
