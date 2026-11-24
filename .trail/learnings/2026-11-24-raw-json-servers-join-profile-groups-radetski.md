---
title: Raw JSON servers join profile groups and rule targets through their primary outbound; a group of them used to collapse into the selected server
author: Radetski
date: 2026-11-24
branch: main
cost: { minutes: 13, tokens: 38107 }
id: l-c536aa68
scope: repo
status: active
replaces: l-540dea50
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopSelectedServerConfig.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTrafficProfileXrayConfig.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/tunnel/DesktopCustomOverrideProfileTest.kt
  - shared/core/src/commonMain/kotlin/engine/xray/TrafficProfileXrayPlanner.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/ProxyServerEndpoint.kt
  - app/src/main/kotlin/engine/xray/CustomXrayConfigRewriter.kt
  - app/src/main/kotlin/engine/xray/XrayOutboundPlanner.kt
  - app/src/main/kotlin/engine/xray/XrayRoutingConfig.kt
  - app/src/test/kotlin/engine/xray/RawServerRoutingConfigTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopSelectedServerXrayConfigFactory.kt
---

## Decided
The profile planner now accepts a raw JSON server (Custom) as a group member or rule target when its JSON has a primary proxy outbound: that outbound is copied under the planner's tag. The raw server that is itself selected is excluded, so a rule naming it routes to its own outbound. Their hosts are resolved before the tunnel through ProxyServer.bootstrapHosts. Everything else about raw-server routing stays as in the earlier learning (extras added next to the JSON's proxy outbound, Desktop and Android, profile or global rules).

## Tried and ruled out
Trusting tests that use plain VLESS members: they passed while a real library, where every member of a group was itself a raw JSON server, still showed the group as "proxy". A group with no usable members falls back to the raw proxy on purpose, which hid the cause in the log.

## Watch out
- In a subscription of raw JSON servers, check the members' type before blaming the labels or the routing.
- Verify with the user's real servers.json and profile, not only fixtures.

In the code: isTrafficProfileUsable and addOutbound in TrafficProfileXrayPlanner.kt, bootstrapHosts in ProxyServerEndpoint.kt, aGroupOfRawJsonServersBecomesABalancerOfTheirOwnOutbounds in DesktopCustomOverrideProfileTest.kt
