# Shared migration verification — 12 October 2026

This is an interim verification record for the approved whole-plan continuation. It supplements the historical journal in [tasks/todo.md](../todo.md); it does not close cards or replace earlier evidence. The plan is still tests-first. Sections below retain the first checkpoint's chronology; the latest checkpoint is recorded separately at the end.

## Starting point and preserved status

- User approved the audited whole plan with «Делаем!» on 12 October; the earlier pause was revoked. Starting source point was `d8b3e0a96c48c3f4eebd8415293377c4a36381e0`; the checklist remains at 9/39 closed cards. Existing `tasks/shared-migration-20261012/initial-baseline` result snapshots were preserved.
- Root reports the original focused baseline as **GREEN42**. The core suite checkpoint remains **461 GREEN**. The earlier shared/app checkpoint was 388 executions; S03 cancellation and worker tests were added after that run.
- For H01, the captured first pass had common grouping 5/5 GREEN and Android grouping compatibility 1/1 GREEN. Android Home-input passed 8/8 in the initial H01 run and 9/9 in the later baseline snapshot. The first Desktop grouping run had 2 failures in 5 tests; later Desktop all-groups/projection, persisted-sort, and install-adapter checks are reported GREEN. The old RED is retained as initial-baseline evidence, not an outstanding behavior gap. H01 remains open until the planned Q2 checkpoint and full acceptance, despite these GREEN slices.

## S03 install, expiry, batch, and worker

- Android install and expiry baselines are frozen in `install-expiry-android-green`: `SubscriptionInstallConfigUseCaseTest` **2/2 GREEN** and `SubscriptionExpirySchedulerTest` **1/1 GREEN**. The common install/expiry API tests first produced the expected compile RED before their implementation gate. Android behavior remains canonical for generated IDs, URI merge, persisted defaults, duplicate URL handling, and failure state.
- Batch cancellation parity was demonstrated as behavioral RED in both captured XMLs under `s03-cancellation-red`. The Android observer cancelled after the catalog write, while the original Android path still proceeds to metadata and embedded-profile writes. The shared path initially stopped between those writes. Root reports the common cancellation fix subsequently GREEN; Android production semantics were not changed.
- The worker API first produced compile RED in `s03-worker-api-red.log`. The common policy and Android WorkManager adapter then passed `shared:app:allTests` (**197 Desktop + 197 Android**) and the Android worker suite (**5 tests**); `:app:assembleDebug` also passed. Full output is in `s03-worker-common-green.log`. The policy preserves Android outcomes: missing/disabled/blank subscriptions and no failures return success; any transient fetch failure returns retry; otherwise failures return failure. Exception classification and WorkManager result mapping remain host-side.
- S03's refresh-all runner API RED was captured for both host wiring slices in `s03-android-runner-api-red.log` and `s03-desktop-callback-api-red.log`. Root/UI agent reports actual Desktop and Android Home wiring is still underway; Android H02 bridge baseline is also waiting for the Android runner source to land so that the app test source set compiles. S03 is not complete.

## Android-source corrections carried into the adapters

- Install preparation uses Android's normalized catalog and `SubscriptionCatalogOperations.allocateNextId`, which chooses `max(nextSubscriptionId, maxExistingId + 1)`. The frozen fixture has `nextSubscriptionId = 21` above existing ID 15 and confirms allocation at 21; it does not establish semantics for invalid or stale persisted counters beyond Android's own normalization. A created group is persisted before fetch, so a failed fetch leaves that group row. An exact URL duplicate remains fully unchanged. Android's new-row mapping uses `lastUpdatedAtMillis = null`; do not substitute another timestamp default.
- The batch cancellation contract is based on the original Android synchronous order, with server catalog write followed by metadata/profile writes and no cancellation check inserted between them.
- H02 duplicate cleanup retains the selected duplicate and deletes directly. Invalid cleanup calls Android's `deleteServersByIds`: when running and the selected ID is removed, it uses stop-before-delete; stopped or unselected paths delete directly. Earlier pure cleanup tests establish catalog transformations only, not this full runtime decision. Single-server and subscription-group paths have different dispatch and stop-state ordering and remain separate until their own Android-grounded tests are approved.

## H02/H03 test drafts

The common coordinator and Desktop callback tests remain drafts outside all module source sets; no H02 production changes have been made. Root approved activating the Android bridge tests that use existing APIs; they are in the app test source set, awaiting the runner prerequisite before baseline execution:

- `h02-h03/test-drafts/ProxyHomeDeletionCleanupOperationTest.kt` covers group-scoped duplicate selection and composite-reference pruning.
- `h02-h03/test-drafts/ProxyServerBulkDeleteAfterStopTest.kt` covers the existing bulk stop result order, missing-service behavior, dispatch outcomes, and cancellation propagation.
- `h02-h03/test-drafts/single-group-deletion-source-contract.md` records the source-history contract for single-server and subscription-group deletion; no shared coordinator or implementation is presumed for these different orderings.
- `app/src/test/kotlin/features/proxy/server/list/ProxyHomeAndroidDeletionBridgeTest.kt` exercises a real Room-backed catalog through Android cleanup and bulk helper APIs, including stop failure, missing service, latest-snapshot preservation, cancellation, selected-duplicate retention, and composite-reference pruning.
- `h02-h03/test-drafts/DesktopProxyHomeDeletionBridgeTest.kt` specifies explicit Desktop cleanup callbacks against `servers.json`, including latest-catalog duplicate retention, group scope, selected/counter/order/reference preservation, invalid rows, and unsupported behavior when a callback is absent.

After the Android bridge baseline is GREEN, root will release the common bulk-coordinator tests for a separate compile/behavior RED. The Desktop callback tests remain independently gated. The final verification record still needs the next integrated H01/S03 checkpoint and its exact consolidated counts. Physical Android UI QA and Desktop visual QA have not been performed in this interim record.

## Latest integrated checkpoint and remaining gates

The earlier pending statements above describe the first checkpoint. Subsequent gates and host wiring have now advanced as follows:

- S03 Android and Desktop retained batch runners are connected to actual Home callbacks, including progress and commit. Android install preserves the established single refresh path for disabled existing rows; bulk eligibility is separate. Desktop install persists common preparation through the actual repository before fetching. Worker and expiry decisions are common, with native scheduling adapters retained.
- H01 common grouping, projection, headlines and sort are used by both hosts. Desktop visible-neighbor reorder now applies the canonical whole-catalog move under the latest repository transform. A further initial-group behavior test is RED: Desktop seeds an earlier subscription instead of the selected server's group. That seed slice remains open; the catalog's independent default-group policy must not be changed to conceal the initializer discrepancy.
- H02's bulk stop/delete coordinator is common and the existing Android entry point delegates to it. Desktop duplicate/invalid cleanup tools are reachable and use the current unfiltered group scope. A further source review confirmed the Desktop invalid-cleanup callback still lacks Android's running-selected stop decision; that branch is not covered by the earlier catalog-only cleanup tests and remains open. Single/group deletion and selection/runtime aftermath also remain open and have distinct Android ordering contracts.
- P02.1 profile high-water allocation, last-profile protection, selection, clicked-snapshot duplicate and typed creation are implemented. P02.2 document import baseline is GREEN; latest-catalog Desktop import and explicit URL creation wiring remain open. Matching follows Android's first matching row in list order, not a global URL-over-name priority.

Executed checks, without failures, errors or skips in the recorded successful reports:

| Evidence log | Executed result |
|---|---|
| `p02-h02-shared-checkpoint.log` | shared/core: 232 Desktop + 231 Android; shared/app: 213 Desktop + 213 Android |
| `h01-s03-h02-p02-host-checkpoint.log` | shared/ui: 67 Desktop + 67 Android; app: 437 Android tests; Desktop compilation and debug APK assembly. The command initially failed at a Desktop test-fixture compile gate; Desktop tests were subsequently fixed and rerun separately. |
| `desktop-full-import-baseline-final-green.log` | Full Desktop suite: 315 tests; original shared document-import characterization: 4 Desktop tests |
| `desktop-core-ffm-integration-green.log` | Actual Core 26.9.9 FFM integration: 1 test, two native starts/stops, traffic and geosite checks. `SKIPI_CORE_INTEGRATION_DIR` points to existing generated Core resources; native sources were not rebuilt. |
| `h01-initial-group-desktop-behavior-red.log` | Initializer discrepancy reproduced before the new shared seed implementation: 1 behavioral failure |

These counts are checkpoint results, not a claim that later targeted runs executed the whole suite. Common tests are counted once per target. XML snapshots for the shared checkpoint are retained under `p02-h02-shared-green/`; targeted runs can overwrite the live Gradle result directories. Physical Android UI/device QA and Desktop visual QA are still not performed. A fresh read-only ADB inventory now finds a Pixel 10 Pro; availability does not itself establish device parity. The original native submodule modification and untracked `shared/yaml-jvm/bin/` remain outside the migration.

## Continuation on 13 October

The user requested larger connected blocks. The next acceptance groups are Home operations/runtime/tools (H02/H03), profile lifecycle/refresh (P02/P03), and portable settings (T01/T02). Root continues orchestration/review and Gradle verification; all implementation and tests are assigned to GPT-6 Luna agents. A completed helper is not an acceptance group.

The initial-group API gate was RED on all three modules before production (`h01-initial-group-api-red.log`). The new Android and Desktop host suites then passed 2 and 3 tests respectively in `h01-initial-group-green.log`; Desktop now seeds only once through `remember`, preserving the existing saveable presentation. The common seed suites did not run in that command: the independently activated profile-import tests reached their expected missing-`importDocument` API compile RED first. That is a separate gate, not a seed assertion failure. After the import facade was implemented, the common seed suites passed 3 Desktop + 3 Android tests in `p02-host-import-api-red-common-seed-check.log`; durable seed XMLs are in `h01-seed-green/`. The common profile-import facade passed 2 Desktop tests in that command. Desktop compilation reached the expected missing `importDocument`/`transformLatest` and Home runtime callback APIs before their implementation gates.

Read-only device inventory found Pixel 10 Pro, installed SKIPI 0.4.1/versionCode 402, and another foreground app. No APK was installed, no app data/reset was performed, and no runtime/device parity is claimed by that inventory.

### Larger-block test gates

- `home-runtime-common-host-api-red.log` captures missing common bulk-deletion decision and Desktop runtime tool/callback APIs. The later `home-file-latency-api-red-profile-compile-check.log` confirms the missing file-backed invalid-cleanup adapter and retained latency-job owner after Main's profile callback compiled. Earlier `home-invalid-file-adapter-api-red*.log` attempts were blocked by profile callback/import integration errors; those failures are not evidence of the intended cleanup RED.
- Android's invalid/all deletion calls the stop helper only when running and the selected row is removed; an unrelated running service operation does not block unselected/direct deletion. A null selection also follows direct deletion. The full Android tool handler binds a concrete final VPN engine, so existing Room-backed stop-helper tests and source inspection are distinguished from an unexecuted full-handler baseline.
- Profile Screen writes now use the existing repository's synchronized latest transform. Main compares exact previous/updated active-profile snapshots for its existing reconnect decision. Separate document import and explicit URL creation paths are connected. These changes still await the larger host GREEN checkpoint; concurrent missing Home/settings test APIs currently prevent Desktop test execution.
- `settings-inbound-profile-source-baseline.log` executed the original Android SOCKS builder: 2 tests GREEN before the common builder change. Its separate profile-refresh oracle executed 3 tests, 2 initially failing due to invalid hand-written custom-file fixture encoding. The agent corrected the fixture using the unchanged Android writer; the profile oracle rerun is pending. That oracle exercises real collection/parser/writer APIs with policy callbacks copied from the pinned source, not the actual composable/WorkManager entry point.
- Desktop auth/dynamic-port limitations were traced to current hard-coded inbounds/settings; FFM already accepts the complete JSON configuration. The existing Android ephemeral TCP allocator has 10 attempts and excluded-port handling; its JVM implementation can be shared by both targets. No alternative allocator or native ABI capability is being invented.
