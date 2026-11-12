---
title: "Balancer members are picked servers plus whole subscriptions plus a saved regex, and a config's balancer keeps its id while its config is inactive"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 30, tokens: 633789 }
id: l-1231d2a5
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonTest/kotlin/engine/xray/CustomXrayStrictTunnelTest.kt
  - shared/core/src/commonMain/kotlin/engine/xray/CustomXrayStrictTunnel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigDialogs.kt
  - app/src/main/kotlin/features/config/TrafficConfigPage.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/config/DesktopConfigsScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/AppEntriesMenu.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SkipiMenuModel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SkipiMenu.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/server/editor/ProxyServerComponents.kt
  - app/src/main/kotlin/features/proxy/server/editor/StrategyGroupMemberSelectorPage.kt
---

## Decided
- A user balancer (StrategyGroup) gets members from three sources that add up, resolved on every use: servers picked one by one, subscriptions picked whole (wholeGroupIds), and a saved name regex limited to chosen groups (filter + filterGroupIds; empty scope means every group). Because the whole-subscription and regex parts are looked up at resolution time, servers a subscription refresh brings in join the balancer without any edit. Clearing one server of a whole subscription turns the others into plain picked members and drops the whole flag.
- The old single "Subscription group" field is gone from the editor. The old field (subscriptionGroupId) is still read: with no regex it means that group whole, with a regex it narrows the regex. Opening the editor moves it into the new fields, which keeps the meaning.
- A config-owned balancer with display mode "only if the config is active" keeps its record (and id) while another config is active; only the list hides it (isProxyServerGroupEntryVisible). That is what keeps On Demand and routing rules pointing at it working. A balancer with display mode "never" is still not materialized, as before.
- Choosing a route target ("Send through") shows one "Proxy groups" group (global balancers plus the profile's own groups), then manual servers, then subscriptions. The "Configs" group (CONFIG:id targets) is no longer offered there; rules that already say CONFIG:id still work. The member picker order is proxy groups, manual servers, subscriptions.
- Strict full tunnel now works for raw JSON servers: rules to any freedom outbound are dropped and a tcp,udp catch-all to the proxy outbound closes the rule list (withStrictFullTunnelRouting); SKIPI's own direct-DNS route survives.
- The profile menu keeps UI edit, raw edit, duplicate and an Export submenu; enable, refresh and delete are the card's own buttons.

## Tried and ruled out
- Making whole-subscription selection a UI-only convenience (selecting the servers present at that moment): it cannot cover servers added later.
- Dropping the old subscriptionGroupId and migrating stored data at load: more moving parts than reading it as a fallback.

## Watch out
- Explicit picked servers used to win over the regex and source group; now all add up, so an old group with picked servers and a stale regex text gains the regex matches.
- Desktop/Android tests that opened the profile menu to refresh, delete or activate now use the card buttons; the "last profile cannot be deleted" path is no longer reachable from the menu.

In the code: resolveStrategyGroupMembers (StrategyGroupMemberResolver.kt), StrategyGroup.effectiveWholeGroupIds, StrategyMemberSelectionState, reconcileConfigProxyGroups, projectRouteOutboundPicker, CustomXrayStrictTunnel.kt
