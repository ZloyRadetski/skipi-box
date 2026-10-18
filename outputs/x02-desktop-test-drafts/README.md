# X02 Desktop plan-composition test drafts

These are review drafts only. The `.kt.draft` suffix keeps every file outside Gradle source sets. Do not run them or promote them until the parent task opens the next test gate.

The frozen common API is:

```kotlin
LocalProxyXrayConfigFactory.buildFromPlan(
    plan: XrayOutboundPlan,
    options: LocalProxyXrayConfigOptions,
    renderingOptions: XrayOutboundRenderingOptions,
): String
```

The Desktop selected-row adapter draft assumes:

```kotlin
DesktopSelectedServerXrayConfigFactory.build(
    serverLibrary: DesktopServerLibrary,
    trafficConfigContentById: Map<Int, String>,
    options: LocalProxyXrayConfigOptions,
    subscriptionPingUrl: String,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
): Result<String>?
```

The adapter is only for generated no-active-profile configs. `Main` must continue to select `DesktopTrafficProfileXrayConfigFactory` for an active `.conf`, and must continue routing a raw selected `Custom` through `buildDesktopCustomXrayConfig`. The adapter must find the selected *stored row first* and decode that row; it must preserve a selected row's decode failure instead of mapping valid rows and accidentally falling through to a later duplicate ID.

The common draft checks listener and DNS settings, generated outbound order, chain dialers, and the final route for a balancer target even when `includeDefaultRouting` is false. The Desktop draft checks the persisted group mapping (`subscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId`, where null maps to 1 and -2 stays -2), current latency snapshot, subscription ping fallback, config-origin group content, and opaque selected-row behavior.

The FFM draft is gated by `SKIPI_CORE_INTEGRATION_DIR`, matching `DesktopCoreFfmIntegrationTest`. Its chain case uses two local SOCKS relays and a loopback HTTP marker to prove that the planner's reversed outbound chain reaches each hop in order. Its automatic-group case starts the rendered balancer configuration with loopback-only members and checks the route/listener. Neither case accesses the internet or changes a system proxy. The test does not claim that a green FFM run covers every platform.

Native bridge support remains a separate host-runtime question. Android's `app/src/main/kotlin/engine/xray/XrayConfig.kt` replaces matching OlcRtc/AmneziaWg outbounds with authenticated or unauthenticated loopback SOCKS outbounds only when `SkipiCoreRuntime` reports the active bridge. `app/src/main/kotlin/engine/vpn/SkipiCoreRuntime.kt` invokes `startOlcRtc` / `startAmneziaWg` and waits for the corresponding SOCKS endpoint. Desktop's `DesktopCoreController` controls the Xray FFM session and has no corresponding start/readiness bridge API. Therefore the drafts do not treat a protocol model's `toXrayOutbound` output as evidence that Desktop can run it, and they do not prescribe a new failure policy or use a fake localhost endpoint.

After parent approval, promote the common draft to `shared/core/src/commonTest/kotlin/platform/LocalProxyXrayConfigFactoryBuildFromPlanTest.kt`, the selected-row draft to `desktopApp/src/test/kotlin/app/skipi/desktop/DesktopSelectedServerXrayConfigFactoryTest.kt`, and merge the FFM cases into `desktopApp/src/test/kotlin/app/skipi/desktop/DesktopCoreFfmIntegrationTest.kt` (or a separately named test in the same package).
