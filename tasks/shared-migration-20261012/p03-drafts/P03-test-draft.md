# P03 behavior-test draft (not an active source set)

Status: test design only. This file deliberately lives under `tasks/`; it is not compiled and makes no claim that the current test run is green. The Android source-derived baseline remains pending rerun after T model/app test compilation is available. Its earlier fixture failures were corrected using `TrafficConfigState.withSkipiSettingsInRawConfig()` so custom resource files use the format the Android writer actually emits.

## Shared profile refresh policy

Proposed test target: `TrafficConfigProfileRefreshOperations.apply(current, normalizedDocument, capturedUrl, timestamp, mode)` in `shared/app/commonMain`. Keep the parser/network fetch and persistence outside this pure operation.

- Manual mode with `unlock = false`: parse the fetched document over the current same-ID profile; take name, auto-update and both intervals, resource settings, and fetched update lock from the document; write the captured source URL and response timestamp; keep ID. Raw `[SKIPI]` fields must agree with typed fields after a read/write round trip.
- Manual mode with `unlock = true`: same as above, except force `updateLocked = false` in both typed model and raw `[SKIPI]` output.
- Automatic mode: import the fetched portable settings/custom files and use the captured URL/time, while preserving the current profile's `updateLocked`, `autoUpdate`, and `updateInterval`, plus current resource `autoUpdate` and `updateInterval`. The response updates the current same-ID record even if it changed while the request was in flight; do not introduce a revision/CAS check.
- Late response: transform the latest collection row by ID. If the fetched document omits `profile-name`, retain the latest row's name; preserve unrelated rows, active selection, and the next-ID high-water.

The source-derived Android oracle is `app/src/test/kotlin/features/config/TrafficConfigProfileRefreshAndroidBaselineTest.kt`. It invokes the existing collection updater/parser/writer, but its Manual/Automatic policy lambdas are explicitly copied source references, not tests of the composable or WorkManager wiring.

## Shared generated-group behavior

Proposed common test target: `reconcileConfigProxyGroups` in `shared/app`. Existing Android wrapper coverage is in `TrafficConfigProxyGroupIsolationTest` and `TrafficConfigProxyGroupPersistenceTest`; move/extend useful portable cases in `shared/app/commonTest` rather than duplicating the wrapper.

Use a profile with a `skipi-display=always` group named `Stable` and members `Node A, Node B`. Reconcile once and record generated `StrategyGroup.id`, `sourceTrafficConfigId`, `sourcePolicyGroupName`, and selected member. Update the same profile ID with `Stable` members `Node B, Node C`, and provide latest regular-server records containing B and C. Assert:

- the generated group retains its ID because its source profile ID and policy-group name are unchanged;
- member IDs reflect B/C from the new profile, not the stale A/B set;
- selected member remains B while B remains in the new membership; if a later response removes B, use the existing first-valid-member fallback;
- generated IDs advance `nextServerId` only when a new source group is materialized;
- active-profile visibility follows `ALWAYS`/`ACTIVE_CONFIG`/`NEVER` semantics already implemented by the shared policy.

## Desktop host integration test for the approved adapter seam

Use the narrow `DesktopTrafficConfigProxyGroupReconciler` adapter. Its `reconcile()` operation must call `DesktopProxyServerRepository.updateCatalog`, read the latest `DesktopConfigLibrary` inside that catalog transform, convert profiles through the existing shared library converter, then call `reconcileConfigProxyGroups`. It must not cache a second profile catalog or introduce a cross-file transaction/CAS policy.

Current `DesktopApplicationRepositoriesTest.trafficConfigTransformLatestReadsFreshCatalogAndPersistsLateResponseById` already proves that profile refresh reads the late `configs.json` snapshot and preserves unrelated profiles, selection, and `nextConfigId`. Reuse that fixture/result for the profile-side assertions rather than adding another generic latest-transform test. `DesktopProxyServerRepository` already owns latest `servers.json` writes and its opaque-row preservation.

The adapter test should create a `Stable` profile group over Node A/B, materialize it, then update the latest profile content to Node B/C while updating the server catalog with C and preserving the existing generated `StrategyGroup`. After `reconcile()` assert the same generated group ID, source profile/group identity, B/C membership, valid selected-member continuity, profile `nextConfigId`, server `nextServerId`, selected server ID, and unrelated typed rows. Use one opaque persisted server row as selected; extend the shared reconciler with `retainedServerIds: Set<Int> = emptySet()` only behind tests-first coverage so the selected opaque ID remains valid and the Desktop adapter preserves its raw serialized row. A failing server save must not publish the transformed catalog. Do not assert rollback across configs.json and servers.json.

Current Desktop wiring does not call `reconcileConfigProxyGroups`: `DesktopProxyGroups.create` only projects existing server rows. The profile URL path is in `DesktopConfigsScreen`, and the proxy catalog adapter is `DesktopProxyServerRepository`; the named reconciler is the minimal host seam under review.

## Existing scheduler contract

Reuse `TrafficConfigScheduler` and its existing tests; do not copy its interval parser into Desktop. When Desktop profile scheduling is wired, adapt the profile record to `TrafficConfigRefreshTarget` and verify the same eligibility: profile work requires auto-update, unlocked state, nonblank source URL, and enabled interval; geo work uses the independent resource-auto-update/interval fields. WorkManager remains Android I/O; Desktop's timer remains a host trigger.

