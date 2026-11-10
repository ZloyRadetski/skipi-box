---
title: "A setting has one switch: features that depend on the HTTP inbound sit inside its card instead of being second switches"
author: Radetski
date: 2026-11-10
branch: main
cost: { minutes: 10, tokens: 234460 }
id: l-15bcb35f
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
Every setting is changed in exactly one place. The system proxy (Desktop) and the VPN's own HTTP proxy (Android) depend on the HTTP inbound, so they are rows inside the HTTP card, shown only while the inbound is on, not separate switches that quietly turn it on. Deletion confirmation lives only in General (it covers servers, configs and resources, not just subscriptions). Broadcast control lives only in General's integration section.
An audit of all Desktop and Android fields found no inert switches: each has a runtime reader or is hidden per platform by capabilities. The only problems were duplicates.

## Tried and ruled out
Keeping the standalone Integration page: it was hidden from the settings home by the default visible-destinations filter, so nothing could open it. It was removed with its route.

## Watch out
Removing a sealed-interface entry by deleting only the `data object` line leaves its `@Serializable` annotation behind and breaks the Android build ("annotation is not repeatable").
The inbound still runs when the VPN HTTP proxy is on even if the inbound flag is off (`httpProxyInboundEnabled` is an OR); switching the inbound off clears both.

In the code: SkipiLocalProxySettingsScreen.httpPlatformContent slot; DesktopLocalProxySettings and Android LocalProxySettingsPage fill it; AppStateLocalProxy.kt.
