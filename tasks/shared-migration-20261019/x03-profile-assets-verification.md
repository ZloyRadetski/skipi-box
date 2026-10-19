# X03 Desktop profile-scoped assets at Core startup — 19 October 2026

This checkpoint records the tests-first Desktop startup slice that carries a selected traffic profile's prepared resource directory into SKIPI Core initialization. For a selected profile, the generated Core configuration and resource directory come from one captured `DesktopConfigLibrary` snapshot. Dynamic-port retries rebuild only the configuration while reusing the captured profile assets path. If there is no selected stored traffic profile, Core keeps using the packaged runtime assets directory. The Core library path remains resolved from the runtime library as before.

This completes only the profile-assets-to-Core-startup connection. It does not complete the wider X03/X02 or R02 acceptance: Desktop resource settings/source/download UI and its queue/download host are still absent. P04, R01, R02, and X03 remain open.

The Desktop Core start boundary is commit `1a8c4b5`; the Desktop start-plan factory, request path, and Main wiring are commit `e101a52`. Evidence is commit `d676dc0`.

## Source contract preserved

`prepareDesktopTunnelStartPlan` snapshots the `DesktopConfigLibrary`, resolves its selected stored profile once, and materializes that same stored record as `TrafficConfigState`. The profile's `id` and parsed `[SKIPI]` resource source select the profile-scoped file store; resource scope does not come from `TunnelConnectRequest.profileId`. The selected profile's content from the same captured library is used by the configuration builder.

Desktop calls `DesktopProfileResourceFiles.prepare` with package timestamp `0L` and `restoreAfterPackageUpdate = false`, then captures the resulting `dataDir` path. The captured start plan rebuilds configuration for a failed listener port without resolving another profile or preparing a different resource path. Every Core attempt made from this captured profile plan receives its rebuilt configuration and the same captured asset path together.

When no selected stored traffic profile exists, the start-plan asset override is `null`; the legacy raw server/custom configuration path still starts Core with the packaged runtime assets directory. `DesktopCoreController` changes only the asset directory passed to `initializeAssets`; native library discovery/loading continues to use `runtime.library`. The packaged global geo files are not substituted for a selected profile's files.

## Test-first evidence

| Gate | Result | Evidence |
|---|---|---|
| Core start API | Expected pre-production compile **RED** because the optional asset-directory argument was missing. | [API RED log](../../outputs/x03-core-profile-assets-api-red-20261018.log). |
| Core focused verification | Six existing native-boundary and six Core-runtime/lifecycle tests passed. Three new fake-Core tests initially did not execute because a JUnit helper returned `DesktopCoreState`; changing only that test helper's return type to `Unit` let JUnit run the three tests. The combined focused gate passed **16/16 GREEN**. The fake-Core tests pin the library-loader path; the separate real-FFM test checks that Core reads from the profile directory when the global assets directory is empty. | [Focused counts](../../outputs/x03-core-profile-assets-focused-green-20261018/counts.json), [profile-assets FFM XML](../../outputs/x03-core-profile-assets-focused-green-20261018/TEST-app.skipi.desktop.DesktopProfileAssetsFfmIntegrationTest.xml). |
| Start-plan API | Expected pre-production compile **RED** for four new planner tests and three copied callback tests before adding the start-plan API. | [Start-plan API RED log](../../outputs/x03-start-plan-api-red-20261019.log). |
| Start-plan focused behavior | The first 32-test run had 30 passes and two fixture failures because the new tests lacked a selected working server required by the unchanged common planner. The fixtures were given a valid SOCKS server with ID 91 and port 1080; production and assertions were unchanged. The four affected tests then passed, and the combined gate is **32/32 GREEN**. | [Focused counts](../../outputs/x03-start-plan-focused-green-20261019/counts.json), [focused XML reports](../../outputs/x03-start-plan-focused-green-20261019/). |
| Full regression, Desktop compile, Android APK, native integration | **2,333 executions, failures/errors/skips 0** in 35 seconds: core 295 + 267, app 309 + 309, UI 94 + 94, Android 521, Desktop 444. Desktop compilation and Android debug APK assembly passed. Seven real native tests ran with explicit `SKIPI_CORE_INTEGRATION_DIR`: six existing cases (4 Core, 2 selected-server) and one new profile-assets alias case. The alias reads from only the profile directory while the global assets directory is empty; the fake-Core tests separately pin the library-loader path. | [Full regression log](../../outputs/x03-profile-assets-full-green-20261019.log), [suite counts](../../outputs/x03-profile-assets-final-reports-20261019/suite-counts.json), [XML reports](../../outputs/x03-profile-assets-final-reports-20261019/). |

The real profile-assets FFM check starts with only the profile-scoped asset directory available while the global runtime asset directory is empty, then verifies Core reads the profile asset. The fake-Core tests verify the native library path remains unchanged. Native artifacts were not rebuilt.

## Source map

| Concern | Current source |
|---|---|
| Capture selected profile, resolved resource settings, and one asset path for configuration retries | [DesktopTunnelStartPlanFactory.kt](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTunnelStartPlanFactory.kt#L10) |
| Carry configuration and optional path through every controller start attempt | [DesktopTunnelController.kt](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTunnelController.kt#L20); [Main.kt](../../desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt#L424) |
| Use profile files when supplied; otherwise preserve runtime assets and library loading | [DesktopCoreController.kt](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopCoreController.kt#L57) |
| Prepare profile-scoped files from packaged assets | [DesktopProfileResourceFiles.kt](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProfileResourceFiles.kt#L16) |

## Review scope and limits

The source review confirmed that the selected profile's ID and resource source come from the captured `DesktopConfigLibrary`, the JSON and asset path use that same snapshot, and retries reuse the captured asset path. No selected stored profile preserves the old global `runtime.directory` fallback. No asset content or provider defaults were invented.

Physical Android/Desktop UI QA was not performed. The foreign native submodule was left untouched.

## Remaining work

Since this X03 checkpoint, the common resource-source resolver has been committed as `0e2978d`; its separate 2,341-execution regression and evidence are recorded in [the R02 source resolver checkpoint](r02-resource-source-verification.md) and evidence commit `d14f303`. The historical X03 gate above remains 2,333 executions. The next R02 tests-first slice is the shared resource-update queue: carry the Android eight-case behavior baseline into common tests and capture the expected API RED before production. The Android queue baseline is commit `9fa9a2b`; global target deduplication across profiles and the downstream reserved-name filter are preserved contract details. Desktop resource management UI, source/download queue production, and host adapters remain future work. Do not mark R02, P04, R01, or X03 complete. Prior resource policy, snapshot-store, and bundled-restore evidence remains in [the 18 October R02 checkpoint](../shared-migration-20261018/r02-verification.md).
