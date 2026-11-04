---
title: The local proxy login protects the HTTP inbound as well as SOCKS
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 19, tokens: 31671 }
id: l-a0b96aae
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopSelectedServerConfig.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTrafficProfileXrayConfig.kt
  - shared/core/src/commonMain/kotlin/platform/LocalProxyXrayConfig.kt
  - shared/core/src/commonTest/kotlin/platform/LocalProxyXrayConfigTest.kt
---

## Decided
When local proxy auth is on, the HTTP inbound gets the same user and password as SOCKS. The user wants one login covering both; an open HTTP port next to a protected SOCKS port was not acceptable.

## Tried and ruled out
Switching the Windows system proxy to SOCKS5: WinINet has no credentials field for it and many apps read socks= as SOCKS4, so HTTP stays the system proxy.

## Watch out
WinINet cannot carry a login, so with auth on, browsers answer 407 with a credentials prompt and some apps that follow the system proxy cannot connect. That is the accepted cost.

In the code: LocalProxyXrayConfigFactory.buildHttpInbound is the only place that builds the HTTP inbound (profile, custom and local configs all use it). Export config stays noauth on purpose.
