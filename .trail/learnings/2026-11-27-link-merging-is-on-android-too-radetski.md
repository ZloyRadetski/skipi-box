---
title: "Link merging is on Android too: Core is told the networks by the app, binds sockets by network handle, and the tunnel service (not the page) applies the settin…"
author: Radetski
date: 2026-11-27
branch: feat/net-merge
cost: { minutes: 29, tokens: 314171 }
id: l-c7e8625b
scope: repo
status: active
replaces: l-194962e7
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowChrome.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopWindowChromeTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiMultilinkSettingsScreen.kt
  - app/src/main/kotlin/engine/network/AndroidLinkMonitor.kt
  - app/src/main/kotlin/engine/vpn/AndroidMultilink.kt
  - app/src/main/kotlin/features/settings/SettingsMultilinkPage.kt
---

## Decided
Link merging now runs on Desktop and Android and the page is the same shared screen. Core cannot list Android's networks, so the app (AndroidLinkMonitor) reports them as JSON with the system's network handle and Core binds each socket to its network through android_setsocknetwork. The monitor also keeps Wi-Fi and mobile data connected with requestNetwork, so it runs only while merging is on. Link ids are the kind of network (wifi, mobile, ethernet), not the Network object, so limits and exclusions survive a reconnect. Mobile data and metered Wi-Fi are eased like a phone hotspot.

The tunnel service (AndroidMultilink) applies the saved settings to Core while the tunnel runs and switches merging off when it stops, because the page is not alive in the background. The page only reads Core's status (rememberMultilinkCoreStatus); Desktop's page also applies (rememberMultilinkStatus). MultilinkSync, the status effect and the link kinds live in shared code.

Search and the settings overview list the Multilink page on every host by default; the Android settings are kept in preferences as one JSON string and are not part of backups (links are this device's).

## Tried and ruled out
Binding by interface name (Xray's sockopt interface) on Android: SO_BINDTODEVICE is not allowed there and the system routes by network, not by device.

## Watch out
kotlin.math.round rounds a half to even (0.5 -> 0); speeds shown to people use floor(x + 0.5).

LinkProperties.getAllLinkAddresses is hidden API, so an IPv6-only (NAT64) mobile network takes part over IPv6 only.

In the code: engine/network/AndroidLinkMonitor.kt, engine/vpn/AndroidMultilink.kt, SkipiVpnService.startMultilink, features/settings/SettingsMultilinkPage.kt, shared/core features/multilink/MultilinkSync.kt, shared/ui settings/MultilinkStatusEffect.kt. The local core build needs skipicore.aar.version to say the required version (it is gitignored).
