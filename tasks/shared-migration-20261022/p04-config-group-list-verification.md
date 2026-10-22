# P04 global and profile-sourced group list

Status: Android source baseline and shared projection transfer verified. Desktop collection UI integration is the next tests-first gate. This continues the profile Groups UI checkpoint `864129e` and the separate Desktop chain-hop correction `70afc82`. P04/R01 remain open.

## Source contract

Android `TrafficConfigPage.kt` supplies global rows and profile-sourced rows to the existing shared `SkipiTrafficConfigScreen`. Global rows retain catalog order and the literal predicate `groupId == AutoBalancerGroupId && (server as? StrategyGroup)?.sourceTrafficConfigId == null`. Because the safe cast returns null for another server type, the existing expression also includes non-StrategyGroup records stored under group -2. This behavior is deliberately pinned; adding a stricter type filter would change the source.

Global rows use key `global:<id>`, remarks falling back to protocol, address falling back to the literal `Auto-Balancer`, and badge `StrategyGroup`. Sourced rows flatten every profile in its input order and every parsed group in parser order, using key `source:<profileId>:<lineNumber>`, group name, profile name as subtitle and parsed group type as badge. There is no active-profile filter. Empty inputs produce empty lists.

The two projections are kept separate: sourced parsing retains the existing `remember(appState.trafficConfigs)` dependency, while the global helper is called directly at the previous UI expression site without a new remember. This avoids unnecessarily reparsing every profile on catalog changes and preserves the source's mutable-data observation behavior.

## Android oracle and extraction

Four Android oracle cases were authored before extraction. The first compile attempt included an unrelated harness import error (`kotlin.test.Test` is unavailable in this Android module), alongside the intended missing helpers: `outputs/p04-config-group-list-android-helper-api-red-20261021.log`, **24 seconds**, no behavioral execution. The import alone was corrected to the module's established `org.junit.Test`; expectations remained unchanged.

The clean rerun stopped solely at missing `projectTrafficConfigSourcedProxyGroups` / `projectTrafficConfigGlobalProxyGroups` and their type-inference cascades: expected **API RED in 4 seconds**, `outputs/p04-config-group-list-android-helper-api-red-final-20261022.log`. Only then were the two existing Android projection blocks extracted into internal helpers and connected to the page; the old intermediate sourced-row holder became unused and was removed.

The same oracle passed on that actual Android implementation **4/4 in 23 seconds**, zero failures/errors/skips, with `:app:assembleDebug`: `outputs/p04-config-group-list-android-baseline-green-20261022.log`. Immutable XML timestamp `2026-10-22T04:40:50.054Z`: `outputs/p04-config-group-list-android-baseline-reports-20261022/`. Independent Luna source review found no semantic discrepancy in filtering, order, fields, keys or remember boundaries. Baseline/extraction commit: `8ad6a15`.

Four corresponding common UI tests now specify the same behavior using shared `TrafficConfigState`, `ProxyServerRecord`, `DefaultAutoBalancerGroupId` and the existing row DTO. Before any shared production change, they stopped only at the two missing projection functions and inferred-type cascades: expected **shared API RED in 21 seconds**, `outputs/p04-config-group-list-shared-api-red-20261022.log`, no executions. Root reviewed the fixtures and compiler diagnostics before opening the transfer gate. Shared implementation and Desktop collection integration have not yet been verified at this point; no completion is claimed for those paths.

## Shared transfer result

The two pure functions now live in shared UI beside the existing list row DTO. Android's sourced adapter delegates directly; the global adapter maps the original ID, group ID, server and latency fields into the existing shared proxy record and delegates. The old Android parsing/filter/mapping algorithms are removed, and the composition's remember boundaries remain unchanged. Root compared both functions and the adapter against the accepted baseline before verification.

The post-transfer command passed **234/234 in 31 seconds**, zero failures/errors/skips: shared UI Desktop **115**, Android **115**, and the unchanged Android oracle **4**. `:app:assembleDebug` also passed using existing HevTun artifacts. Log: `outputs/p04-config-group-list-shared-focused-green-20261022.log`; immutable XML/counts: `outputs/p04-config-group-list-shared-focused-reports-20261022/`. Implementation/tests/Android adapter are committed as `149ebec`. No device installation or user app restart occurred. This is a scoped projection gate; the preceding 2634 inventory remains the latest full checkpoint.

Desktop collection tests are being prepared against the actual existing screen before production changes. They will cover visible global/sourced rows, the displayed non-StrategyGroup row's safe no-op click, and source-row navigation into the correct inactive profile. A direct source-row entry must return to the catalog on Back; the normal Basics → Routing → Groups entry must continue to return to Routing. Those origins must not be collapsed into the current hardwired Groups → Routing return.

Global Add/Edit/Duplicate/Delete require a separate explicit host callback/API gate before UI implementation; no temporary no-op actions are permitted. Source audit also found that Android's long-press menu Edit uses an unsafe StrategyGroup cast for a malformed non-StrategyGroup auto-group row, whereas its normal row click and Duplicate use safe casts. Any correction of that crash must be described as a separate tests-first fix, not claimed as unchanged source parity. The shared list projection deliberately retains the existing row eligibility.
