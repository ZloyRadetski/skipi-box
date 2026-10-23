# R01 — Desktop global routing runtime verification

23 October 2026. This report records the tests-first behavior RED, the shared rendering-seam API RED/GREEN, and the focused Desktop runtime GREEN for applying persisted global routing to the generated no-profile selected-server path. This closes the focused runtime verification slice only; it does not close all R01 work or establish geo-resource parity.

## Source and behavior boundary

`DesktopAppSettings` persists `routeDomainStrategy`, `defaultRouteOutboundTag`, and `routeRules`. `prepareDesktopTunnelStartPlan` captures those settings and delegates configuration generation to `buildDesktopSelectedServerConfig`. That entrypoint preserves the existing precedence: selected Raw Custom configuration first, then an active traffic profile, then the generated selected-server fallback. This R01 slice applies saved global routing only to that fallback. It does not change profile routing or Raw Custom behavior.

The shared routing planner is the source contract for strategy mapping, enabled-rule order, target resolution, and final default routing. The no-profile behavior tests assert that `2` produces `IPOnDemand`, enabled rules retain their order, disabled rules do not become routes or add server outbounds, and the chosen default is the final TCP/UDP rule. A missing rule target is omitted and a missing default target falls back to the selected `proxy` target. The local Desktop fallback must not add its legacy `geoip:private` rule ahead of persisted rules.

The common [`LocalProxyXrayConfigFactory.buildFromPlan`](../../shared/core/src/commonMain/kotlin/platform/LocalProxyXrayConfig.kt) renderer now has an explicit optional `XrayRoutingPlan` seam. Its tests establish that a supplied plan replaces local routing and that the prior local fallback remains available when no plan is supplied. The Desktop generated no-profile branch builds and supplies that plan; profile and Raw Custom branches retain their earlier precedence.

The tests use ordinary domain/IP rules and isolated temporary profile resource paths. They do not prove geo-tag validation or full Android/Desktop resource parity. Android VPN startup prepares a real data directory even when no profile is active; Desktop no-profile startup initializes Core assets from `DesktopCoreRuntime.directory`. A separate runtime resource-validation follow-up is described below. A new global Routing UI is not required for this runtime slice.

## Tests-first behavior RED

The focused run completed in **28 seconds**: **8 tests, 4 failures, 0 errors, 0 skipped**. Three of five `DesktopGlobalRoutingRuntimeTest` cases failed because the generated no-profile config had no `routing` object. The two profile and Raw Custom precedence cases passed. In the native suite, the new no-profile loopback-routing case failed because rule relay `R` received no request; the other two FFM cases passed.

| Suite | Tests | Failures | Observed result |
| --- | ---: | ---: | --- |
| `DesktopGlobalRoutingRuntimeTest` | 5 | 3 | Missing `routing` object in the three no-profile cases; profile and Raw Custom precedence cases passed. |
| `DesktopSelectedServerCoreFfmIntegrationTest` | 3 | 1 | Real Core started, but relay `R` received no matching loopback request. Two existing native cases passed. |
| **Total** | **8** | **4** | **0 errors, 0 skipped** |

The baseline provenance records production HEAD `42a3bedab9a55931556ce3cf3527fd9c4d19d7cc`, the native runtime directory `desktopApp/build/generated/skipi-core-resources/common`, and these test-source SHA-256 values:

- `DesktopGlobalRoutingRuntimeTest.kt`: `8c7e152796d5e55ea6fd5fa5daab576cc43fdbe9c7db60e7bec45a83cf9531fa`
- `DesktopSelectedServerCoreFfmIntegrationTest.kt`: `e89207797134669bf84309682a853797dbd77d84ff90739eb150ce14d244c46e`

Evidence: [counts](../../outputs/r01-global-routing-runtime-behavior-red-reports-20261023/counts.json), [provenance](../../outputs/r01-global-routing-runtime-behavior-red-reports-20261023/test-source-provenance.json), [full Gradle output](../../outputs/r01-global-routing-runtime-behavior-red-20261023.log), [Desktop behavior XML](../../outputs/r01-global-routing-runtime-behavior-red-reports-20261023/TEST-app.skipi.desktop.DesktopGlobalRoutingRuntimeTest.xml), and [native FFM XML](../../outputs/r01-global-routing-runtime-behavior-red-reports-20261023/TEST-app.skipi.desktop.DesktopSelectedServerCoreFfmIntegrationTest.xml).

## Shared rendering-seam API RED and GREEN

After the Desktop behavior RED was recorded, [`LocalProxyExplicitRoutingPlanTest.kt`](../../shared/core/src/commonTest/kotlin/platform/LocalProxyExplicitRoutingPlanTest.kt) was installed with SHA-256 `45dcfc61b795f3c2f9ede8ab7415c6b45f81bb74a4ed58ca3c0ff51f20c2390a`. The API RED stopped after **1 second** during compilation with one diagnostic: line 134 passed `routingPlan`, but `buildFromPlan` had no such parameter. **0 tests executed.** This is a compile-time API RED, separate from the earlier runtime behavior RED.

Evidence: [API RED result](../../outputs/r01-explicit-routing-rendering-api-red-reports-20261023/result.json), [compiler diagnostics](../../outputs/r01-explicit-routing-rendering-api-red-reports-20261023/compiler-diagnostics.txt), [source provenance](../../outputs/r01-explicit-routing-rendering-api-red-reports-20261023/test-source-provenance.json), and [full Gradle output](../../outputs/r01-explicit-routing-rendering-api-red-reports-20261023/gradle-output.txt).

The shared implementation then added the explicit routing-plan seam in a seven-line production diff. Its focused verification completed in **7 seconds**: **374 Desktop core tests plus 318 Android host tests, 692 total, with 0 failures, 0 errors, and 0 skipped**. Both platform reports include the two `LocalProxyExplicitRoutingPlanTest` cases. The source provenance records `LocalProxyXrayConfig.kt` SHA-256 `dd9ca65adcbc05765e4cf475bb51ff879bc1fc2baab93f21c27a5d0cb1a530ac` and the installed test SHA-256 above.

Evidence: [focused counts](../../outputs/r01-explicit-routing-rendering-core-green-reports-20261023/counts.json), [source provenance](../../outputs/r01-explicit-routing-rendering-core-green-reports-20261023/test-source-provenance.json), [Desktop explicit-routing XML](../../outputs/r01-explicit-routing-rendering-core-green-reports-20261023/core-desktop/TEST-platform.LocalProxyExplicitRoutingPlanTest.xml), [Android explicit-routing XML](../../outputs/r01-explicit-routing-rendering-core-green-reports-20261023/core-android/TEST-platform.LocalProxyExplicitRoutingPlanTest.xml), and [full Gradle output](../../outputs/r01-explicit-routing-rendering-core-green-reports-20261023/gradle-output.txt).

## Desktop runtime focused GREEN

The focused Desktop verification completed in **26 seconds** with `:desktopApp:compileKotlin` green and **25 tests across five suites, 0 failures, 0 errors, and 0 skipped**. The existing three-case native FFM suite ran against the integration Core runtime; its new global-routing case now sent matching traffic through the saved rule relay and unmatched traffic through the saved default relay. The other suites cover declarative global-route cases, selected-config precedence, generated outbound rendering, and start-plan asset behavior. Two independent reviews of this production slice reported no issues.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `DesktopGlobalRoutingRuntimeTest` | 5 | 0 | 0 | 0 |
| `DesktopSelectedServerConfigEntryPointTest` | 4 | 0 | 0 | 0 |
| `DesktopSelectedServerCoreFfmIntegrationTest` | 3 | 0 | 0 | 0 |
| `DesktopSelectedServerXrayConfigFactoryTest` | 9 | 0 | 0 | 0 |
| `DesktopTunnelStartAssetsPlannerTest` | 4 | 0 | 0 | 0 |
| **Total** | **25** | **0** | **0** | **0** |

The production source snapshot and behavioral expectations did not change after the behavior RED. `DesktopGlobalRoutingRuntimeTest.kt` retained its RED hash `8c7e152796d5e55ea6fd5fa5daab576cc43fdbe9c7db60e7bec45a83cf9531fa`. The native test source was adjusted only for the current production API (the explicit routing import and existing helper argument); removing those API-only adjustments reproduces the original RED source hash `e89207797134669bf84309682a853797dbd77d84ff90739eb150ce14d244c46e`. Focused-build provenance records the current native test SHA-256 `48c078368c29b73c79829e5ba34144365d9373600557f602f0c792592bd90122`, the shared seam test SHA-256 above, and `expectations_changed: false`.

Evidence: [counts](../../outputs/r01-global-routing-runtime-focused-green-reports-20261023/counts.json), [source provenance](../../outputs/r01-global-routing-runtime-focused-green-reports-20261023/test-source-provenance.json), [full Gradle output](../../outputs/r01-global-routing-runtime-focused-green-20261023.log), [global-routing XML](../../outputs/r01-global-routing-runtime-focused-green-reports-20261023/TEST-app.skipi.desktop.DesktopGlobalRoutingRuntimeTest.xml), [entrypoint XML](../../outputs/r01-global-routing-runtime-focused-green-reports-20261023/TEST-app.skipi.desktop.DesktopSelectedServerConfigEntryPointTest.xml), [native FFM XML](../../outputs/r01-global-routing-runtime-focused-green-reports-20261023/TEST-app.skipi.desktop.DesktopSelectedServerCoreFfmIntegrationTest.xml), [outbound factory XML](../../outputs/r01-global-routing-runtime-focused-green-reports-20261023/TEST-app.skipi.desktop.DesktopSelectedServerXrayConfigFactoryTest.xml), and [start-plan XML](../../outputs/r01-global-routing-runtime-focused-green-reports-20261023/TEST-app.skipi.desktop.DesktopTunnelStartAssetsPlannerTest.xml).

## Reproduction command

From the repository root, with the generated native runtime directory available at the absolute provenance path:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  SKIPI_CORE_INTEGRATION_DIR=/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/build/generated/skipi-core-resources/common \
  ./gradlew --offline \
  :desktopApp:test \
  --tests 'app.skipi.desktop.DesktopGlobalRoutingRuntimeTest' \
  --tests 'app.skipi.desktop.DesktopSelectedServerCoreFfmIntegrationTest' \
  -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  -x :hevtun:buildHevTun \
  -x :hevtun:syncHevSocks5TunnelVersion \
  > outputs/r01-global-routing-runtime-behavior-red-20261023.log 2>&1
```

The run used no `--rerun-tasks` flag. The full Gradle log is saved at the linked output path and copied into the behavior RED evidence bundle.

The shared core GREEN command used the same JDK, Android SDK, `-PskipiAndroidTestJavaHome`, and two HevTun exclusions, without `SKIPI_CORE_INTEGRATION_DIR` and without `--rerun-tasks`:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  ./gradlew --offline \
  :shared:core:desktopTest \
  :shared:core:testAndroidHostTest \
  -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  -x :hevtun:buildHevTun \
  -x :hevtun:syncHevSocks5TunnelVersion
```

The API RED, shared core GREEN, and Desktop behavior RED evidence bundles now each contain saved full Gradle output.

The focused Desktop GREEN command used the same Java/Android SDK environment, Core integration directory, Android test JDK property, and HevTun exclusions as the behavior RED run; it added the four existing regression suites and `:desktopApp:compileKotlin`. It also ran without `--rerun-tasks`:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  SKIPI_CORE_INTEGRATION_DIR=/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/build/generated/skipi-core-resources/common \
  ./gradlew --offline \
  :desktopApp:test \
  --tests 'app.skipi.desktop.DesktopGlobalRoutingRuntimeTest' \
  --tests 'app.skipi.desktop.DesktopSelectedServerCoreFfmIntegrationTest' \
  --tests 'app.skipi.desktop.DesktopSelectedServerConfigEntryPointTest' \
  --tests 'app.skipi.desktop.DesktopSelectedServerXrayConfigFactoryTest' \
  --tests 'app.skipi.desktop.DesktopTunnelStartAssetsPlannerTest' \
  :desktopApp:compileKotlin \
  -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  -x :hevtun:buildHevTun \
  -x :hevtun:syncHevSocks5TunnelVersion \
  > outputs/r01-global-routing-runtime-focused-green-20261023.log 2>&1
```

## Remaining resource-validation follow-up

The focused routing runtime behavior is GREEN. Geo-resource validation remains open: no-profile Desktop start plans still need the same global asset directory that Core will initialize so the generated routing plan can filter GeoSite/GeoIP references against actual files. A tests-first seam should pass the resolved `DesktopCoreRuntimes.discover().getOrThrow().directory` into `prepareDesktopTunnelStartPlan` as `globalAssetsDirectory: Path?`; for no-profile plans, that exact directory should also be the plan's `assetsDirectory`, while an active profile keeps its own prepared profile directory. The generated selected-server factory can accept an optional `routingDataDirectory: Path?`, retaining null-dataDir behavior for preview/no-resource callers and using a portable resource-aware validator for actual starts. Enabled `ext:` domain rules also need the Android-style preflight: reject malformed references or missing external files before Core start. Keep global rules global; do not introduce a profile `ResourceFileScope`, a global custom-resource catalog, or new UI. Android I/O should delegate to the portable policy rather than duplicate routing semantics.

This report does not claim that resource-validation follow-up is implemented or tested. It records the current runtime result only; the Android global Routing screen and its navigation are outside this gate.

## Completed follow-up

The resource-validation follow-up described above was subsequently completed in `7a213bd`. The [resource verification](r01-routing-resources-verification.md) records its Android baseline, API RED, linkage correction, unchanged test expectations, and final fresh 2859/2859 GREEN gate (Core9/9, compile/debug APK). [R01 acceptance](r01-acceptance-verification.md) closes the source-backed card; work is paused after R01. Earlier scoped counts in this report remain historical evidence and are not added to the final inventory.
