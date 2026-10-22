# P03 automatic profile refresh execution

Date: 22 October 2026. P03 remains open: this checkpoint starts with the Android execution extraction; shared execution and the real Desktop periodic profile host follow separately.

## Android source contract and tests first

Source: `TrafficConfigAutoUpdateWorker.doWork` at collection checkpoint `542d059`. Its input ID `-1` guard precedes the `SkipiApplication` context guard and profile lookup. Missing, disabled, locked, or blank-URL profiles complete successfully. Eligible work captures the profile, trims its URL, fetches with that profile's normalized UA and freshly read runtime fetch options, normalizes `trimEnd() + "\n"`, rejects Error diagnostics with Retry, commits to the latest same-ID row, reconciles generated groups, then succeeds. The original `runCatching` maps all caught errors, including cancellation, to Retry.

Seven tests were prepared and reviewed before production changed. They cover missing/ineligible snapshots without callbacks; captured profile identity, trimmed URL, normalization and callback order; invalid-document Retry; no-op commit for an already-removed row followed by reconciliation; and errors/cancellation at fetch, commit and reconciliation. Assertions and fixtures were not changed to fit the implementation.

- API RED: `outputs/p03-profile-auto-refresh-android-helper-api-red-20261022.log`, **25 seconds**, failure at `:app:compileDebugUnitTestKotlin` for missing execution helper/result and dependent lambda type inference. No tests executed; stale XML was not treated as new evidence.
- Android source extraction: internal `features.config.runtime.executeTrafficConfigAutoUpdate` is now called by the actual worker. WorkManager ID/context handling and result mapping, transport/UA/options, `stateStore.update`, timestamp creation inside its update lambda, shared Automatic commit policy and group reconciliation stay in the host.
- GREEN: `outputs/p03-profile-auto-refresh-android-helper-green-20261022.log`, **15 seconds**, `:app:testDebugUnitTest` filtered to `TrafficConfigAutoUpdateExecutionAndroidTest`, `TrafficConfigProfileRefreshAndroidBaselineTest` and `TrafficConfigProfileRefreshAndroidCommitAdapterTest`, followed by `:app:assembleDebug`. **12/12 tests**, no failures/errors/skips; APK build successful.
- Preserved XML and counts: `outputs/p03-profile-auto-refresh-android-helper-reports-20261022/`. Timestamps: `2026-10-22T11:46:15.446Z` through `11:46:15.500Z`.

All commands use the repository wrapper offline, JDK 26, the existing Android SDK and JDK 21 test runtime, excluding `:hevtun:buildHevTun` and `:hevtun:syncHevSocks5TunnelVersion`. Existing native artifacts were used; the foreign native submodule change is preserved.

## Scope and next gate

The seven tests call the actual extracted execution helper; they do not instantiate WorkManager or prove application-context/input handling, Android UA/options integration, or real network/storage behavior. Those adapter lines were checked against the original worker diff; existing commit tests supplement the helper baseline. Existing four policy baseline tests contain copied policy lambdas and are not worker integration tests.

No revision/CAS fence, post-fetch eligibility check, new default, or retry policy was added. A Unit commit that finds no latest row still returns normally and is followed by reconciliation and Success. Profile-document refresh remains separate from geo-resource refresh.

Next: the same seven source-derived cases in shared/app commonTest, missing-common-API RED, then move the portable executor into shared/app and make Android import it. Only after that gate should tests and implementation connect the real Desktop profile schedule/fetch/persistence host. This source extraction alone does not complete P03 or establish Desktop periodic refresh.
