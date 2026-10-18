# P04/R01 route-outbound selector checkpoint — 18 October 2026

This checkpoint records one completed selector slice: the Android-derived route-outbound projection, shared option presentation/state and grouped screen, and Android's adapter/wiring. Focused tests, the final regression, and two rendered Desktop sizes passed. This does not close the wider P04, R01, or R02 cards; the complete Desktop Rules/resource flow remains unwired.

The code/evidence checkpoint is branch `refactor/desktop-shared-outbound-plan`, HEAD `0471d7f`. Relevant commits: `1ceaa93` (Android behavior baseline), `b50d45c` (shared application projection), `8bf9125` (shared UI state/screen, tests, render cases), `60b67ed` (Android adapter and mechanical oracle update), `0471d7f` (saved final reports and render evidence). The documentation update is being committed separately.

## Behavior and boundaries

The Android source snapshot used before extraction is `git show b50d45c:app/src/main/kotlin/features/routing/RouteOutboundSelectorPage.kt`. In that version, the projection helper occupied lines 132–296 and the full Compose projection/presentation lived in `RouteOutboundSelectorPage` from line 298 onward. The current [Android page](../../app/src/main/kotlin/features/routing/RouteOutboundSelectorPage.kt#L35) is a thin adapter: it maps `AppState` records and localized labels to the shared projection, maps Android subscription details, passes the globe painter, and preserves navigation/result handling.

The shared [application projection](../../shared/app/src/commonMain/kotlin/app/skipi/app/routing/RouteOutboundPickerProjection.kt#L25) preserves the source rules covered by the seven-case Android oracle: a non-null profile ID selects policy mode even if the row is missing; global server tags remain numeric IDs while policy tags use trimmed remarks; `Custom` eligibility and policy-mode blank-remarks filtering are unchanged; groups retain Android order and empty subscription groups; known unrendered servers do not create synthetic targets; unknown selected tags, system-target case handling, and initial expansion retain their existing semantics. A server item carries the original `ProxyServerRecord`, avoiding a second lookup by ID.

The shared [selector state](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/routing/RouteOutboundSelectorState.kt#L16) owns the pending choice and group expansion overrides. The [grouped screen](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/routing/SkipiRouteOutboundSelectorScreen.kt#L38) owns row presentation, rendering, expansion, and confirmation. Android still supplies localized labels, current catalog/profile snapshots, subscription traffic/expiry/announcement/update details, the painter, and navigation. My read-only comparison against the `b50d45c` source found no behavior mismatch; common country-flag helpers match Android's previous `CountryFlagUtils` behavior for flag extraction and title fallback.

Platform file access remains outside the shared selector. Android's profile-scoped store handles resource paths, custom filenames, packaged files, and atomic writes in [AndroidResourceFileStore.kt](../../app/src/main/kotlin/features/resources/runtime/AndroidResourceFileStore.kt#L26). Desktop's [runtime locator](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopCoreRuntime.kt#L17) finds its bundled Core library plus bundled `geoip.dat` and `geosite.dat`; it does not supply the profile-scoped custom-resource validation, download, replace, and lifecycle behavior required by the Rules flow. The Desktop profile builder also rejects resource inputs that need files it does not bundle ([DesktopTrafficProfileXrayConfig.kt](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTrafficProfileXrayConfig.kt#L206)). Full Desktop Rules stays gated on a real R02 host adapter; global bundled geo files are not a substitute for profile-scoped resources. See [R02 acceptance](../plan.md#r02).

## Test and verification evidence

| Gate | Result | Evidence |
|---|---|---|
| Android selector characterization | **7/7 GREEN**, no failures/errors/skips, before the shared projection. | [Baseline log](../../outputs/p04-selector-android-baseline-20261018.log), [XML](../../outputs/p04-selector-android-baseline-20261018/TEST-features.routing.RouteOutboundSelectorProjectionAndroidOracleTest.xml), commit `1ceaa93`. |
| Shared projection API and behavior | Expected missing-API compile **RED**, followed by **8/8 GREEN** focused projection tests. | [API RED](../../outputs/p04-selector-api-red-20261018.log), [GREEN log](../../outputs/p04-selector-projection-green-20261018.log), [XML](../../outputs/p04-selector-projection-green-20261018/TEST-app.skipi.app.routing.RouteOutboundPickerProjectionTest.xml), commit `b50d45c`. |
| Shared UI presentation/state | Expected missing-API compile **RED** for seven authored cases; after implementation, included in the final UI suites: **94 Android + 94 Desktop**, zero failures/errors/skips. The seven cases specify static targets, flags, titles, protocol/transport, raw remarks, selection, and expansion behavior. | [UI API RED](../../outputs/p04-selector-ui-final-api-red-20261018.log), [Android presentation XML](../../outputs/p04-selector-final-reports-20261018/ui-android/TEST-app.skipi.ui.routing.RouteOutboundOptionPresentationTest.xml), [Android state XML](../../outputs/p04-selector-final-reports-20261018/ui-android/TEST-app.skipi.ui.routing.RouteOutboundSelectorStateTest.xml), [Desktop UI reports](../../outputs/p04-selector-final-reports-20261018/ui-desktop/). |
| Android wiring and full regression | **2,253 tests**, zero failures/errors/skips across eight suite groups: core 262 Desktop + 261 Android; app 309 + 309; UI 94 + 94; Android app 495; Desktop app 429. `:desktopApp:compileKotlin` and `:app:assembleDebug` succeeded. | [Final log](../../outputs/p04-selector-final-regression-20261018.log), [suite-counts.json](../../outputs/p04-selector-final-reports-20261018/suite-counts.json), [XML reports](../../outputs/p04-selector-final-reports-20261018/). |
| Render smoke | Render API was first captured as expected compile **RED** before production; then **2/2 GREEN**. Root inspected both outputs: no blank page, clipping, or overlap at 390×844 and 1100×800. | [Render API RED](../../outputs/p04-selector-render-api-red-20261018.log), [narrow render](../../outputs/p04-selector-render-20261018/narrow-selected-server.png), [wide render](../../outputs/p04-selector-render-20261018/wide-empty-provider.png). |
| Real Core integration context | Six native Core cases ran with `SKIPI_CORE_INTEGRATION_DIR` explicitly set: four existing lifecycle/inbound cases plus two selected-config chain/balancer cases. | [Existing integration XML](../../outputs/p04-selector-final-reports-20261018/desktop-app/TEST-app.skipi.desktop.DesktopCoreFfmIntegrationTest.xml), [selected-config XML](../../outputs/p04-selector-final-reports-20261018/desktop-app/TEST-app.skipi.desktop.DesktopSelectedServerCoreFfmIntegrationTest.xml). |

The final regression command ran from `/home/vqsego/TorvaldsVPN/Skipi/skipi-box` with the existing Temurin 26 Gradle JVM and the repository-configured Android JDK 21 toolchain:

```sh
env \
  JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  SKIPI_CORE_INTEGRATION_DIR=/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/build/generated/skipi-core-resources/common \
  ./gradlew --offline \
  :shared:core:allTests :shared:app:allTests :shared:ui:allTests \
  :desktopApp:test :app:testDebugUnitTest \
  :desktopApp:compileKotlin :app:assembleDebug \
  -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion \
  > outputs/p04-selector-final-regression-20261018.log 2>&1
```

No native artifacts were rebuilt. No physical Android or Desktop window/device QA was performed, and there was no interaction automation beyond the selector state tests and the rendered screenshots.

## Pause and next checkpoint

By the user's request, implementation and builds are paused after this slice; no R02 code or test draft was started. On resumption, first read this checkpoint and inspect the Android source-backed pure resource activation/restore decisions. Then define their tests/API before implementation. Preserve the existing `XrayCore` kind distinction even where the higher-level bundled-file loop normally skips it; the common resource-kind contract is not finalized. Do not substitute Desktop's global bundled geofiles for per-profile resources. Preserve the pre-existing unrelated dirty native submodule `hevtun/src/main/jni/hev-socks5-tunnel`; do not reset or stage it as part of the next slice.

P04, R01, and R02 remain open. The route-outbound selector is connected on Android; Desktop Rules/policy/resource UI is still not wired. The broader migration checkpoint for generated Desktop Xray configs remains [x02-verification.md](x02-verification.md). All agent and build work is stopped at this pause point.
