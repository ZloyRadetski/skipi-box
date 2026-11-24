---
title: "A raw JSON server with the automatic rule override takes the profile's routing and also gets the profile's other servers and groups as real outbounds"
author: Radetski
date: 2026-11-24
branch: main
cost: { minutes: 15, tokens: 119633 }
id: l-902f1270
scope: repo
status: active
replaces: l-e5a3cae2
src: agent
touches:
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
  - app/src/main/kotlin/engine/vpn/VpnXrayConfig.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopConnectionLog.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/home/ConnectionLog.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/home/ConnectionLogReader.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/home/OutboundLabels.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/home/ConnectionLogReaderTest.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/home/OutboundLabelsTest.kt
  - shared/app/src/jvmMain/kotlin/features/logs/ConnectionAccessLogFeed.kt
---

## Decided
With the subscription option "Automatic rule override", the active profile's rules replace the routing of a raw JSON server on Desktop. A policy that names another local server or a proxy group is no longer collapsed into the JSON's one outbound: TrafficProfileXrayPlanner.plan(rawProxyTag = <the JSON's proxy tag>) builds those servers (skipi-server-<id>) and balancers (skipi-group-N with skipi-group-member-N-<id> members) and CustomSkipiRouting.extras adds them to the raw config: outbounds after the JSON's own (its proxy stays the default), balancers merged into routing, the observatory merged with the JSON's. PROXY, the raw server named by its remarks, a group with no matching local server and any unknown policy still go to the JSON's own proxy outbound. DIRECT goes to direct, REJECT to block. With the flag off the JSON keeps its routing.

## Tried and ruled out
- Routing a raw server through the generated-config path (lifting its proxy outbound into SKIPI's config): it dropped the tag and DNS contracts that the existing override tests pin.
- Adding AmneziaWG servers as extras: their runner is started by the generated path only, so they are left out and a policy naming one goes to the raw proxy.

## Watch out
- Global routing rules (no profile) still collapse to the raw proxy; only profile rules get the extras.
- The Android rewriter still keeps the JSON routing; only Desktop applies the override.
- The log names skipi-group-member tags "Balancer - Server" because the group's own name is not in the tag.
- DesktopMihomoPayloadImporterTest.mihomoInlineStringPayloadIsNotRecursivelyTreatedAsAProxyUrl fails without this change too.

In the code: customServerSkipiRouting in DesktopSelectedServerConfig.kt, TrafficProfileXrayPlanner.plan(rawProxyTag), CustomSkipiExtras in CustomXrayDnsRewriter.kt, OutboundLabels.kt
