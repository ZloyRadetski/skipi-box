---
title: "A raw JSON server with the automatic rule override takes SKIPI's routing, not the routing written in its JSON"
author: Radetski
date: 2026-11-07
branch: main
cost: { minutes: 30, tokens: 246715 }
id: l-e5a3cae2
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/navigation/SkipiNavigationReselect.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingsHomeScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/home/DesktopProxyHome.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/tools/DiagnosticsControllers.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/tools/DiagnosticsControllersTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiAppearanceSettingsScreen.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/home/SpeedGaugeSnapshotTest.kt
---

## Decided
The subscription option "Automatic rule override" (stored as the server's override-inbound-and-DNS flag) promises that SKIPI's routing rules apply to every config in the subscription. On Desktop a raw (Custom) server used to keep its own JSON routing and only took SKIPI's inbounds and DNS, so profile rules such as "Discord direct" never applied. Now, with the flag on, the active profile's rules replace the JSON's rules (global routing rules do when there is no profile and they are not empty). PROXY and every group or server policy go to the JSON's own proxy outbound, DIRECT to direct, REJECT to block (a block outbound is added when the JSON has none). With the flag off the JSON keeps its routing.

## Tried and ruled out
Routing a raw server through the generated-config path (lifting its proxy outbound into SKIPI's config): it dropped the tag and DNS contracts that the existing override tests pin and changed more than needed.

## Watch out
- The symptom was Discord going to the proxy while a profile had geosite:discord direct; access.log showed the raw JSON's tag proxy instead of skipi-proxy, which is how to tell which path ran.
- The Android rewriter still keeps the JSON routing; only Desktop applies the override so far.
- Policies that name proxy groups collapse to the one outbound a raw server has.

In the code: customServerSkipiRouting in DesktopSelectedServerConfig.kt, trafficProfileRulesForRawServer in TrafficProfileXrayPlanner.kt, CustomSkipiRouting in CustomXrayDnsRewriter.kt
