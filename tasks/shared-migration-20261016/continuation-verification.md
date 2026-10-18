# Continuation: Android to shared, 16 October 2026

The user resumed the full migration. Luna agents are available again; the credit error in the earlier checkpoint is historical. Root coordinates and runs verification; GPT-6 Luna with reasoning max writes production and test code. Existing dirty work is preserved.

## P04 Tunnel and Network: tests-first gate

Android source: `TrafficConfigEditorPages.kt`, `withTunnelOptions`, `withNetworkActivation`, and the existing AppState profile updater. Tunnel retains the complete settings draft captured by profile ID, including fields not visible in this section; only the separate mux concurrency text is trimmed. Network transport is bounded to Wi-Fi/cellular. Back and Save update the latest same-ID row before navigation. Missing rows are not recreated.

Before production changes, root ran `outputs/shared-migration-20261016-tunnel-network-api-red.log` with `--continue`:

- Android `TrafficConfigTunnelNetworkAndroidOracleTest`: **2/2 GREEN**, zero failures/errors/skips. The baseline XML is preserved in `outputs/baseline-continuation-20261016/tunnel-network-android-oracle.xml`.
- Shared application and Desktop tests: expected compile **RED**, limited to missing `saveTunnelThenNavigate`, `saveNetworkActivation`, and `saveNetworkActivationThenNavigate` controller methods.

The common tests specify the full settings snapshot, latest-row updates, transport bounds, commit-before-navigation and missing-profile behavior. Desktop tests use the real profile repository/store and check document serialization, library selection/counter/siblings, metadata and save/publish/navigation ordering. Known SKIPI normalization is retained; arbitrary unknown SKIPI keys are not claimed as preserved.

The production gate opened only after these results. The slice does not by itself add Desktop OS network detection or apply Tunnel fields to Desktop runtime JSON. Those remain A01/X03 work until independently connected and verified.

## X02 outbound planning

Read-only audit confirmed Android's pure outbound selection can use typed records in shared/app without an AppState dependency. The shared/core plan model and shared/app group resolvers already exist. Native runtime bridging stays a host operation.

Before extraction, root ran the current Android planner: the new `XrayOutboundPlannerAndroidOracleTest` **10/10**, existing `XrayConfigTest` **17/17**, and existing `XrayOutboundPlannerBalancerTest` **7/7**, all GREEN. XML reports are preserved under `outputs/baseline-continuation-20261016/`. These fixtures pin order, disabled-route handling, optional fixed targets, reversed chains and dialer links, member filtering/errors, custom proxy/host extraction, select/fallback/latency/probe precedence and regular concurrent Observatory behavior.

The first combined run (`outputs/shared-migration-20261016-x02-api-red.log`) also exposed a missing JSON dependency in the new common tests. Luna added the existing `libs.kotlinx.serialization.json` dependency to shared/app. The common-only rerun (`outputs/shared-migration-20261016-x02-api-only-red.log`) then failed on the intended missing `XrayOutboundPlanner`/`XrayOutboundPlanningInput` APIs and their dependent inference errors. Production extraction was authorized only after the Android baseline and this corrected API RED.

After extraction, root ran the complete Android unit suite: **472 tests, zero failures/errors/skips**. The shared application and Desktop test compilation remained blocked only by the next T02 APIs intentionally absent at that gate. Android reports from this run are preserved under `outputs/continuation-reports-20261016/android-post-x02/`.

Source review found an additional parity edge: a backup restore may temporarily expose duplicate server IDs before persistent primary-key collapse. The old membership adapters rebind every resolved member to the first source row with that ID. A new focused fixture was added before correcting the extracted planner. `outputs/shared-migration-20261016-x02-duplicate-id-behavior-red.log` ran two Android cases: unchanged membership adapter baseline GREEN, extracted planner behavior RED on the expected first-record result. Its XML is `outputs/baseline-continuation-20261016/x02-duplicate-id-red.xml`. Only after that result was the first-ID rebinding correction authorized for both shared member-resolution paths.

## T02 dynamic bind retry: tests-first gate

Android's current engine permits two starts only when dynamic ports are enabled and the failed start has a matching bind-error message in its cause chain. Static ports, non-bind failures, preparation failures and a successful result with `running=false` do not retry. The next candidate excludes the failed port; Android native cleanup remains in its existing service/engine adapters.

Before implementation, the current Android classifier oracle passed as part of the full **472/472** run. `outputs/shared-migration-20261016-t02-retry-api-red.log` captured expected missing shared retry-policy and Desktop immutable start-plan APIs. Desktop lifecycle fixtures specify full-config rebuild from one captured settings/auth snapshot, the two-attempt limit, failed-port exclusion, endpoint publication, system-proxy ordering, prepared-config behavior and error stage.

An additional opt-in real Core test was written before implementation. Its first compile exposed a suspend-call setup error; Luna corrected only that test coroutine wrapper. The rerun `outputs/shared-migration-20261016-t02-retry-native-api-only-red.log` then contained only the intended missing policy/start-plan/seam APIs and inference errors caused by them. The production gate opened after this corrected RED. The native test is intended to hold the first SOCKS port, observe a real Core bind failure, retry on another port and verify authentication; it has not yet executed at this checkpoint.

## First integrated continuation run

`outputs/shared-migration-20261016-continuation-green.log` completed shared/app **293 + 293**, shared/ui **82 + 82**, and Android **474** tests without failures/errors/skips; Android APK assembly also completed. Desktop executed **406** tests with two fixture failures: its Windows default enabled the existing HTTP system-proxy listener, while the new SOCKS-only allocator assertions expected no reserved HTTP port. Luna corrected only the test settings to explicitly disable system proxy; expectations and production reservation behavior were unchanged. The full Desktop rerun (`outputs/shared-migration-20261016-continuation-desktop-green.log`) is GREEN, **406 tests**, including the four retry lifecycle cases.

Full unit reports are preserved under `outputs/continuation-reports-20261016/first-wave/`. The first real native run executed four tests: the previous three passed; the new retry fixture encountered the same missing explicit SOCKS-only setting before Core start. It is recorded in `outputs/shared-migration-20261016-continuation-real-core.log` and `outputs/continuation-reports-20261016/native-retry-red.xml`; its corrected native rerun is pending. These fixture failures do not establish a Core/runtime defect.

The corrected native run (`outputs/shared-migration-20261016-continuation-real-core-green.log`) is **4/4 GREEN**, zero failures/errors/skips, with `SKIPI_CORE_INTEGRATION_DIR` explicitly set to the existing generated runtime directory. The new case executed a real Core start against an occupied loopback port, verified failure/cleanup/no published endpoint, retried excluding that port, then completed an authenticated SOCKS handshake and stop. XML: `outputs/continuation-reports-20261016/native-retry-green.xml`. No native rebuild or Windows proxy setting mutation occurred. Physical Android UI/VPN and rendered Desktop QA remain unperformed.

## Next P04 Rules gate

Rules fixtures were authored outside source sets while the first-wave verification ran. Review corrected draft mistakes against the existing source before publication: reorder retains each raw rule's option, profile metadata matches the fixture, and the Android writer may append SKIPI after a newly added Rule section. After publication, `outputs/shared-migration-20261016-rules-api-red.log` captured the intended missing common/Desktop rule controller APIs, alongside two Android oracle import errors. Luna corrected only those imports. The subsequent Android-only run (`outputs/shared-migration-20261016-rules-android-baseline.log`) is **6/6 GREEN** against the existing Rules source; baseline XML is `outputs/baseline-continuation-20261016/rules-android-oracle.xml`.

The production gate then opened for the common rule controller and shared Android rule state/UI coordination. The contract deliberately distinguishes list snapshot writes from Add/Edit operations applied to the latest raw document. Blank drafts do not write but Back still leaves; editing drops the parsed option as the existing Android editor does. No new merge/conflict policy is introduced. Desktop controller/store behavior is under test, while its complete visual rule flow remains gated on a real policy selector and scoped resource adapters; no empty Rules tile is added.

The focused common rule controller **8/8** and Desktop store **3/3** are GREEN in `outputs/shared-migration-20261016-renderer-suggestions-api-red-rules-green.log`. That combined run exposed an Android wrapper import error (`LocalHapticFeedback`); Luna restored its original package. The subsequent Android run compiled the new shared screens/wrapper and confirmed the Rules oracle **6/6 GREEN**. Reports for the common controller and Desktop store are preserved under `outputs/continuation-reports-20261016/`.

## Outbound renderer and routing suggestions: next source gates

Before moving these implementations, the current Android `XrayOutboundJsonAndroidOracleTest` **4/4** and `RoutingSuggestionsProviderAndroidOracleTest` **4/4** passed in `outputs/shared-migration-20261016-renderer-suggestions-android-baseline.log`. The first uses literal JSON outputs for transport/WireGuard/Mux/Fragment/DNS/ordering; the second uses real temporary scoped `.dat` files and the current Android provider, plus the exact filename sanitizer contract. Their XML reports are preserved under `outputs/baseline-continuation-20261016/`.

The preceding combined run captured missing common renderer/resolver/picker APIs in core/UI tests before production. The picker copy/type-inference diagnostics originate from the missing filter-state API, not an approved new opt-in dependency. Pure picker tests check filtering/group order and select-before-dismiss; no Compose click/screenshot tests are claimed. Only after these baselines/RED was production extraction authorized. Renderer and suggestion code use explicit host facts/callbacks; native I/O and runtime bridge creation remain host adapters. Desktop scoped custom-resource files/download/assets and the complete policy selector remain pending connections.

## Second integrated continuation run: GREEN

`outputs/shared-migration-20261016-rules-renderer-suggestions-green.log` completed **all eight suites** and APK assembly successfully. XML totals: core Desktop/Android **257/256**, shared/app **301/301**, shared/ui **87/87**, Android app **488**, Desktop **409**: **2,186 executions**, zero failures/errors/skips. Reports are preserved under `outputs/continuation-reports-20261016/second-wave/`.

Android keeps the outbound wrapper and its original defaults, with typed state-derived options delegated to the shared renderer. Both routing suggestion caller modes use the same common resolver with host-scoped file reads. The geo picker body was removed from Android in favor of a facade calling shared UI; Android process/app picking remains outside this slice. Rule draft restoration/list order/save and Back coordination are common; Android policy navigation/resource input remain adapters. The full Desktop profile rule UI and actual selected composite generated-config connection are not claimed by this wave. The separate native 4/4 run predates these pure renderer/suggestion moves; current Desktop runtime has not yet been connected to the new renderer.
