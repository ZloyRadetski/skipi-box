# R01 explicit routing-plan rendering — test-first contract

**Status:** reviewed test draft; not installed or executed. Install it after the separate Desktop behavior RED is recorded and before the shared renderer API RED. This contract covers the shared config-rendering seam, not global-routing UI, resource-file validation, TUN behavior, or Android changes.

The current shared `LocalProxyXrayConfigFactory.buildFromPlan` always derives routing from local-proxy options and the outbound plan. Its local routing builder uses a fixed `IPIfNonMatch` strategy, defaults to the selected proxy target, and adds a `geoip:private` direct rule when `includeDefaultRouting` is true. That behavior remains the fallback for callers which do not supply an explicit plan.

The generated Desktop no-active-profile path needs to render the persisted global routing configuration through the existing shared `XrayRoutingPlan`. The shared renderer should therefore accept an optional `routingPlan: XrayRoutingPlan? = null`. When supplied, it must serialize that plan and use its `primaryOutboundTag` when ordering outbounds; it must not rebuild or merge local routing rules. When omitted, existing local routing behavior remains byte/behavior-compatible with the existing `LocalProxyXrayConfigFactoryBuildFromPlanTest` and `LocalProxyXrayConfigTest` coverage.

The draft at `outputs/drafts/LocalProxyExplicitRoutingPlanTest.kt.draft` specifies two outcomes:

1. A supplied plan with `AsIs`, one explicit `direct` domain rule, and `direct` as primary serializes exactly when `includeDefaultRouting` is both false and true. In particular, the supplied rules replace the local generated rules; the legacy `geoip:private` rule is not prepended. Its primary outbound is first in the rendered list.
2. A supplied plan whose default target is a balancer selects `XrayTags.DEFAULT_ROUTE_LOOPBACK` as primary, renders that loopback outbound first, and preserves the default-route inbound rule, final TCP/UDP balancer route, and balancer definition.

The explicit input is the shared plan, not `RoutingConfigRecord`, so `shared/core` remains independent of `shared/app`. Desktop owns mapping persisted `RoutingConfigRecord` values into the common `XrayRoutingRequest` after its outbound planner has created the needed route targets. That Desktop integration test is tracked separately. The initial rendering contract uses ordinary literal routes and supplied plan objects only; GeoIP/geosite tag validation needs a later resource-aware adapter because no-profile preview/export has no profile-scoped resource directory. No profile resource scope or UI behavior is invented here.

No test command has been run for this draft, and no source file or production API was changed.
