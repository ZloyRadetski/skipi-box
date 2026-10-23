# R01 — routing resource validation verification

**Status: CLOSED within the source-backed acceptance scope.** The shared validation policy, Android adapter, Desktop no-profile resource path, routing repository/runtime behavior, both host targets, and final full build gate are verified. The dormant global Routing UI, OS clipboard integration, installed-package enumeration, and a global external-resource catalog are not acceptance requirements and are not claimed as implemented.

## Source contract

The portable resource-policy contract is in the [resource validation spec](../../outputs/drafts/r01-routing-resources-spec.md) and the [Desktop resource-aware start-plan spec](../../outputs/drafts/r01-global-routing-resource-aware-spec.md). Android validates enabled external domain rules before Xray configuration and uses a data directory to filter GeoIP/GeoSite references ([`VpnXrayConfigFactory`](../../app/src/main/kotlin/engine/vpn/VpnXrayConfig.kt:115), [Android geo sanitizer](../../app/src/main/kotlin/engine/xray/XrayGeoRuleSanitizer.kt), [Android ext preflight](../../app/src/main/kotlin/engine/xray/XrayExternalRoutingResources.kt)). Shared code owns the portable validation policy while Android retains the file-backed adapter.

Desktop’s generated no-profile route uses the actual `DesktopCoreRuntimes.discover().getOrThrow().directory` for validation and Core startup. A selected traffic profile uses its own prepared assets. The resource seam adds no profile scope, global custom-resource catalog, or global screen.

## Tests-first chronology

| Checkpoint | Result | Evidence |
| --- | --- | --- |
| Android oracle before extraction | **10/10 GREEN in 29 seconds.** The new Android oracle passed 3/3 and the existing routing config suite passed 7/7. | [Counts](../../outputs/r01-routing-resources-android-baseline-reports-20261023/counts.json), [provenance](../../outputs/r01-routing-resources-android-baseline-reports-20261023/test-source-provenance.json), [Android oracle XML](../../outputs/r01-routing-resources-android-baseline-reports-20261023/TEST-engine.xray.XrayResourceRoutingAndroidOracleTest.xml), [routing config XML](../../outputs/r01-routing-resources-android-baseline-reports-20261023/TEST-engine.xray.XrayRoutingConfigTest.xml), [full Gradle output](../../outputs/r01-routing-resources-android-baseline-reports-20261023/gradle-output.txt). |
| Shared API RED | **Expected compile RED in 4 seconds; 0 tests executed.** The new common tests reported missing `XrayResourceRoutingRuleValidator` and `validateXrayExternalDomainResources`; other diagnostics cascaded from those absent APIs. | [Test-source provenance and diagnostics](../../outputs/r01-routing-resources-shared-api-red-reports-20261023/test-source-provenance.json), [full Gradle output](../../outputs/r01-routing-resources-shared-api-red-reports-20261023/gradle-output.txt). |
| First post-extraction policy gate | **Common policy tests: 12/12 GREEN** (6 Desktop + 6 Android host). Android app ran 10 tests, 9 passed and 1 failed, in a 62-second gate. The failure was `NoSuchMethodError`: same-package Kotlin files on shared and Android used the same filename, generating the same `XrayExternalRoutingResourcesKt` facade with different methods. `javap` confirmed the collision. The fix renamed only the shared source file to [`XrayExternalDomainResources.kt`](../../shared/core/src/commonMain/kotlin/engine/xray/XrayExternalDomainResources.kt); API and tests stayed unchanged. | [Counts](../../outputs/r01-routing-resources-policy-first-attempt-reports-20261023/counts.json), [Android oracle XML](../../outputs/r01-routing-resources-policy-first-attempt-reports-20261023/android-app/TEST-engine.xray.XrayResourceRoutingAndroidOracleTest.xml), [full Gradle output](../../outputs/r01-routing-resources-policy-first-attempt-reports-20261023/gradle-output.txt). |
| First Desktop resource API RED | **Compile RED in 24 seconds; 0 tests executed.** It found the intended missing `globalAssetsDirectory` and `routingDataDirectory` parameters, but also three invalid fixture `File(Path, "xray")`/`Path` constructions and cascaded type diagnostics. This was not a product-only API RED. Fixture conversions were corrected without changing assertions. | [Result](../../outputs/r01-routing-resources-desktop-api-red-reports-20261023/result.json), [provenance](../../outputs/r01-routing-resources-desktop-api-red-reports-20261023/test-source-provenance.json), [full compiler output](../../outputs/r01-routing-resources-desktop-api-red-reports-20261023/gradle-output.txt). |
| Corrected Desktop API RED, before Desktop production wiring | **Clean compile RED in 3 seconds; 0 tests executed.** Only six missing-parameter diagnostics remained: four `globalAssetsDirectory` and two `routingDataDirectory` call sites. The corrected resource-validation test SHA was `7467dc9f05407012814a550f9162ae7e23f33a0198d3daf004c288037ee99954`; the Core integration test SHA was `103b8cce5b8359be2b646ca889790a9822650ed96adbc9d1b950bdca64bc2549`. | [Corrected API RED provenance](../../outputs/r01-routing-resources-desktop-corrected-api-red-reports-20261023/test-source-provenance.json), [full compiler output](../../outputs/r01-routing-resources-desktop-corrected-api-red-reports-20261023/gradle-output.txt). |
| Final fresh full gate | **2,859/2,859 GREEN in 94 seconds; 0 failures, errors, or skips.** All eight test targets, Desktop compilation, and debug APK assembly passed. All 14 frozen Kotlin source hashes matched before and after the run. | [Counts](../../outputs/r01-final-full-reports-20261023/counts.json), [native Core counts](../../outputs/r01-final-full-reports-20261023/native-counts.json), [source provenance](../../outputs/r01-final-full-reports-20261023/source-provenance.json), [Android oracle XML](../../outputs/r01-final-full-reports-20261023/android-app/TEST-engine.xray.XrayResourceRoutingAndroidOracleTest.xml), [Desktop resource XML](../../outputs/r01-final-full-reports-20261023/desktop-app/TEST-app.skipi.desktop.DesktopGlobalRoutingResourceValidationTest.xml), [full Gradle output](../../outputs/r01-final-full-reports-20261023/gradle-output.txt). |

The common policy tests retained their source hashes throughout the post-extraction and final gates:

- [`XrayResourceRoutingRuleValidatorTest.kt`](../../shared/core/src/commonTest/kotlin/engine/xray/XrayResourceRoutingRuleValidatorTest.kt): `890eb5cc7aea781a82d9d370438814cee0fa11b548b9eea3254cd491adb856e2`
- [`XrayExternalRoutingResourcesTest.kt`](../../shared/core/src/commonTest/kotlin/engine/xray/XrayExternalRoutingResourcesTest.kt): `7f9e68942e5586b891e8274fd9e1466ff638cbf076d27eff41698bc1c01ddabb`

Run the command from the repository root. The final gate’s per-target inventory was:

| Target | Tests | Failures / errors / skips |
| --- | ---: | ---: |
| Shared core — Desktop | 380 | 0 / 0 / 0 |
| Shared core — Android host | 324 | 0 / 0 / 0 |
| Shared app — Desktop | 355 | 0 / 0 / 0 |
| Shared app — Android host | 347 | 0 / 0 / 0 |
| Shared UI — Desktop | 120 | 0 / 0 / 0 |
| Shared UI — Android host | 120 | 0 / 0 / 0 |
| Android app | 587 | 0 / 0 / 0 |
| Desktop app | 626 | 0 / 0 / 0 |
| **Total** | **2,859** | **0 / 0 / 0** |

Native Core coverage totaled nine cases: `DesktopCoreFfmIntegrationTest` 4, `DesktopProfileAssetsFfmIntegrationTest` 1, `DesktopSelectedServerCoreFfmIntegrationTest` 3, and `DesktopGlobalRoutingCoreResourcesIntegrationTest` 1. The final run produced `app-universal-debug.apk` (SHA-256 `34bad011263ca7486d4a5f98761fb4383fcc3de5b9c63cea1f81df1d44fb5a5d`); it was not installed.

## Exact final gate command

The run used JDK 26, the configured Android SDK, the generated Core resource directory, and JDK 21 for Android host tests. Each test task received `--rerun`; the invocation did not use `--rerun-tasks`.

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 ANDROID_HOME=/home/vqsego/Android/Sdk ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk SKIPI_CORE_INTEGRATION_DIR=/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/build/generated/skipi-core-resources/common ./gradlew --offline :shared:core:desktopTest --rerun :shared:core:testAndroidHostTest --rerun :shared:app:desktopTest --rerun :shared:app:testAndroidHostTest --rerun :shared:ui:desktopTest --rerun :shared:ui:testAndroidHostTest --rerun :app:testDebugUnitTest --rerun :desktopApp:test --rerun :desktopApp:compileKotlin :app:assembleDebug -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion > outputs/r01-final-full-20261023.log 2>&1
```

The final provenance records source head `efcdde71c88c535bcedbc53698cbbdfbd6ba1dc5` and confirms all 14 frozen Kotlin sources unchanged. The production change is recorded as commit `7a213bd`; the repository revision is `a5cba46`.

## Boundaries

This closes the source-backed R01 routing transfer slice without building a new global Routing UI. The final gate verifies compilation and automated host tests, not desktop GUI interaction, physical-device behavior, clipboard interaction, or APK installation. The work does not claim OS clipboard integration, Android native package enumeration, a global external-resource catalog, or support for Desktop-specific native proxy protocols. Those remain separate platform or feature boundaries. R01 is closed here; the user requested a pause afterward, and this report starts no subsequent work.
