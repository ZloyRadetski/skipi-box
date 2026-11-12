---
title: HTTP proxy has one switch on Android; the VPN advertises the inbound whenever it is on
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 13, tokens: 52883 }
id: l-238340d0
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingIcon.kt
---

## Decided
The user wants one HTTP proxy switch, so Android's second row "Add HTTP proxy" is gone. The single "HTTP proxy (inbound)" switch now also makes the VPN advertise that inbound as its system HTTP proxy (on Android 10 and later). This replaces the earlier choice of keeping the VPN proxy as a second row inside the HTTP card.
The legacy field enableVpnAppendHttpProxy is still stored and read (httpProxyInboundEnabled is an OR), so a user whose older version had only that switch on keeps the inbound running and sees the one switch on; switching off clears both.

## Tried and ruled out
Removing the field from the state and backups: old backups and preferences hold it, and the released-backup guard would complain.
Keeping a flag for "advertise to the VPN" next to the inbound: it only existed for the second switch, so it was removed (appliesToVpn).

## Watch out
- Behaviour change: someone who had the HTTP inbound on but the VPN proxy off now gets the proxy advertised to apps too.
- Desktop keeps its separate system proxy row in the HTTP card; that sets the OS proxy and is not a second inbound switch.

In the code: toVpnAppendHttpProxyOptions (VpnLocalProxyConfig.kt), SkipiVpnService.applyAppendHttpProxy, AppStateLocalProxy.kt.
