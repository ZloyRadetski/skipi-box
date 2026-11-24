---
title: "A raw JSON server that overrides routing gets the other servers and groups its rules name as real outbounds, on Desktop and Android, with profile or global rul…"
author: Radetski
date: 2026-11-24
branch: main
cost: { minutes: 17, tokens: 87725 }
id: l-540dea50
scope: repo
status: active
replaces: l-902f1270
src: agent
---

## Decided
A raw JSON server with "Automatic rule override" keeps its own proxy outbound as the default, and what the rules name besides it (other servers, proxy groups, balancers) is added to its config as real outbounds, balancers and observatory (CustomSkipiRouting.extras). Desktop profiles plan with TrafficProfileXrayPlanner(rawProxyTag); route rules (Android always, Desktop without a profile) plan with XrayOutboundPlanner(rawProxyTag) through rawServerSkipiRouting. PROXY, the raw server by id or remarks and anything unknown go to the JSON's own outbound.

## Tried and ruled out
- Treating "an active profile exists" as the Android switch: the app always carries a default profile, so every raw JSON lost its own routing. Android overrides only with an enabled rule or a default route other than proxy.
- AmneziaWG servers as extras: their runner starts in the generated path only; a rule naming one goes to the raw proxy.

## Watch out
- A group with no members makes the plan fail on rule-based routing, as in generated configs.
- The log shows skipi-group-member tags as "Balancer - Server": the group's name is not in the tag.

In the code: rawServerSkipiRouting (shared/app RawServerRouting.kt), AppState.rawServerSkipiRouting (app XrayOutboundPlanner.kt), customServerSkipiRouting (desktopApp DesktopSelectedServerConfig.kt)
