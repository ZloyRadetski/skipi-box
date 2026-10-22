# P04 global and profile-sourced group list

Status: Android source baseline verified; shared projection tests and missing-API gate captured, implementation underway. This continues the profile Groups UI checkpoint `864129e` and the separate Desktop chain-hop correction `70afc82`. P04/R01 remain open.

## Source contract

Android `TrafficConfigPage.kt` supplies global rows and profile-sourced rows to the existing shared `SkipiTrafficConfigScreen`. Global rows retain catalog order and the literal predicate `groupId == AutoBalancerGroupId && (server as? StrategyGroup)?.sourceTrafficConfigId == null`. Because the safe cast returns null for another server type, the existing expression also includes non-StrategyGroup records stored under group -2. This behavior is deliberately pinned; adding a stricter type filter would change the source.

Global rows use key `global:<id>`, remarks falling back to protocol, address falling back to the literal `Auto-Balancer`, and badge `StrategyGroup`. Sourced rows flatten every profile in its input order and every parsed group in parser order, using key `source:<profileId>:<lineNumber>`, group name, profile name as subtitle and parsed group type as badge. There is no active-profile filter. Empty inputs produce empty lists.

The two projections are kept separate: sourced parsing retains the existing `remember(appState.trafficConfigs)` dependency, while the global helper is called directly at the previous UI expression site without a new remember. This avoids unnecessarily reparsing every profile on catalog changes and preserves the source's mutable-data observation behavior.

## Android oracle and extraction

Four Android oracle cases were authored before extraction. The first compile attempt included an unrelated harness import error (`kotlin.test.Test` is unavailable in this Android module), alongside the intended missing helpers: `outputs/p04-config-group-list-android-helper-api-red-20261021.log`, **24 seconds**, no behavioral execution. The import alone was corrected to the module's established `org.junit.Test`; expectations remained unchanged.

The clean rerun stopped solely at missing `projectTrafficConfigSourcedProxyGroups` / `projectTrafficConfigGlobalProxyGroups` and their type-inference cascades: expected **API RED in 4 seconds**, `outputs/p04-config-group-list-android-helper-api-red-final-20261022.log`. Only then were the two existing Android projection blocks extracted into internal helpers and connected to the page; the old intermediate sourced-row holder became unused and was removed.

The same oracle passed on that actual Android implementation **4/4 in 23 seconds**, zero failures/errors/skips, with `:app:assembleDebug`: `outputs/p04-config-group-list-android-baseline-green-20261022.log`. Immutable XML timestamp `2026-10-22T04:40:50.054Z`: `outputs/p04-config-group-list-android-baseline-reports-20261022/`. Independent Luna source review found no semantic discrepancy in filtering, order, fields, keys or remember boundaries. Baseline/extraction commit: `8ad6a15`.

Four corresponding common UI tests now specify the same behavior using shared `TrafficConfigState`, `ProxyServerRecord`, `DefaultAutoBalancerGroupId` and the existing row DTO. Before any shared production change, they stopped only at the two missing projection functions and inferred-type cascades: expected **shared API RED in 21 seconds**, `outputs/p04-config-group-list-shared-api-red-20261022.log`, no executions. Root reviewed the fixtures and compiler diagnostics before opening the transfer gate. Shared implementation and Desktop collection integration have not yet been verified at this point; no completion is claimed for those paths.
