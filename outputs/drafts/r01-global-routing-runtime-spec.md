# R01: persisted global routing in the Desktop selected-server start plan

## Source contract

`DesktopAppSettings` already persists `routeDomainStrategy`, `defaultRouteOutboundTag`, ordered `routeRules`, and `nextRouteRuleId` in `desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsLibrary.kt`. `prepareDesktopTunnelStartPlan` captures the settings and calls `buildDesktopSelectedServerConfig`, but the current no-profile selected-server factory supplies an empty rule list and `proxy` as the default route target (`DesktopSelectedServerXrayConfigFactory.kt`). The existing route values are therefore ignored on this path.

The generated selected-server branch is the fallback after the entrypoint's existing Raw Custom and active-profile branches (`DesktopSelectedServerConfig.kt`). R01 must apply global settings only to that generated fallback. Active profiles continue to use their profile rules, and Raw Custom continues to retain its raw config and established precedence.

The shared planner contract is in `shared/core/src/commonMain/kotlin/engine/xray/XrayRoutingPlan.kt`: integer strategy `2` renders as `IPOnDemand`; enabled rules retain source order, disabled rules are skipped, target tags resolve through the route-target map, and the chosen default target is emitted as the final TCP/UDP route. An unavailable default target falls back to the selected `proxy` route target. `XrayOutboundPlanner` adds server outbounds referenced by enabled rules or the default target. The local Desktop fallback must not prepend its legacy `geoip:private` rule to the persisted global rules.

## Behavioral cases

All tests exercise the existing `prepareDesktopTunnelStartPlan` entrypoint with an isolated temporary resource root and deterministic port allocation. They do not call the proposed future routing-parameter APIs.

1. A no-profile plan renders strategy `2` as `IPOnDemand`; preserves enabled rule order; omits a disabled rule; resolves an enabled rule to a second stored server; contains the selected server's final default route; emits no local `geoip:private` rule; and leaves settings/server-library inputs unchanged.
2. A no-profile plan with a second stored server as its default renders that server's outbound and uses it as the final route target.
3. Missing default/rule targets do not create unresolved references: the missing rule is skipped and the missing default target falls back to the selected `proxy` outbound.
4. With an active profile, conflicting global route settings do not replace the profile's route strategy or rules.
5. With a selected Raw Custom server and a conflicting active profile, the custom JSON routing and Raw Custom precedence remain intact; global settings are not injected.

## Verification boundary

This draft records tests-first expectations only. The test has not been built or executed by this author; root owns Gradle and Git. The native FFM integration path and geo-resource validation are outside these cases. Android VPN startup prepares a real resource directory even without an active profile, while Desktop no-profile Core startup uses `DesktopCoreRuntime.directory`; these tests use ordinary domain/IP literals and claim no geo-tag parity.
