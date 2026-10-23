# R01 — routing source and scope audit

23 October 2026. Read-only Android navigation, history, persistence, and runtime audit following the Desktop `RoutingRepository` slice. **R01 remains open.** The repository foundation is verified; no global routing UI is approved by this source review.

## Finding: global Android routing UI was already unreachable

`RoutingPage` contains global list/default/clipboard behavior, and `RouteRuleEditorPage` contains global rule form behavior, but the current app does not mount `RoutingPage`. Android’s top-level destinations are only Proxy, Configs, and Settings. `Route.RoutingRuleEditor` is registered in `AppContent`, but the only pushes to it are inside the unmounted `RoutingPage`, so the editor has no ordinary app entrypoint. Desktop has the same three top-level sections; its Configs → Routing flow edits profile `.conf` rules and is a separate feature.

This is not a route removed by the recent shared migration. The source/history checks were:

```sh
git log --all --oneline -S 'RoutingPage(' -- app
git grep -n 'RoutingPage(' c842879 -- app
git grep -n 'RoutingPage(' cde715f -- app
git grep -n 'RoutingPage(' ad3389c -- app
git grep -n 'RoutingPage(' 77e4973 -- app
git grep -n 'RoutingPage(' e4c438d -- app
git grep -n 'RoutingPage(' ff5f5d8 -- app
rg -n 'RoutingPage\(|Route\.Routing|RoutingRuleEditor' app shared desktopApp
```

The `git log -S` result is only `c842879 Initial commit`; each historical `git grep` found only the `RoutingPage` declaration. The current-tree search found the declaration, its editor push sites, and the editor route registration, with no page mount or `Route.Routing` key.

The component code remains an authoritative record of what that dormant Android screen does if invoked. It is not an active user flow or authorization to add a new screen. The current migration therefore does not add global navigation, connect the registered editor route to a reachable page, or port global clipboard UI. Those would constitute new user-facing behavior and need a separate product request.

## Persisted data and runtime boundary

The legacy global data is still persisted and exported: `AppState.routeRules` defaults to empty with `nextRouteRuleId = 10`; Room stores the rule rows, preferences store the counter/defaults, and AppBackup serializes/restores the rules. That makes the component semantics and data contract worth recording even though the screen is unreachable.

At normal Android VPN start, `withActiveTrafficConfigApplied()` projects the active profile’s Shadowrocket rules, default route, and route-domain strategy onto `AppState` before Xray config generation. The default profile has no non-final rules and uses Proxy, matching today’s global defaults. If no profile is available, that projection returns the original state. The Desktop selected-server generated path currently ignores saved global values, passing empty rules and Proxy; its profile and custom-Xray paths already have their own routing sources and precedence.

The next bounded R01 slice is the **no-active-profile generated Desktop runtime**: consume saved global rules/default/domain strategy there, while preserving the existing active-profile and custom-Xray precedence. `nextRouteRuleId` remains allocation-only and must not affect generated Xray output. This work does not depend on adding global UI first. R01 stays open until the remaining runtime behavior is implemented and verified.

## Evidence and related drafts

- [Global routing source-contract draft](../../outputs/drafts/r01-global-routing-source-contract.md) — component semantics, unreachable navigation, and revised R01 slices.
- [Runtime routing source-contract draft](../../outputs/drafts/r01-runtime-routing-source-contract.md) — Android profile projection, Desktop no-profile gap, and counter boundary.
- [Desktop repository research](r01-routing-repository-research.md) and [focused repository verification](r01-desktop-routing-repository-verification.md) — completed repository slice and its limits.

No production or test files were changed for this audit. No build or tests were run.
