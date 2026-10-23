# P04 — DNS validation and Resources screen verification

23 October 2026. This report records the first scoped Desktop integration baseline for the shared DNS validation dialogs and inactive-profile Resources route. Both requested cases passed; no production changes were needed for this slice. P04 is closed by [the consolidated acceptance](p04-acceptance-verification.md).

## Source contract and fixture setup

The profile DNS editor is shared UI used by the Android traffic-config route and the Desktop profile editor. Domain and host drafts use the shared traffic-profile validators; DNS server drafts use the Xray DNS server parser. Malformed domain, host, and server values show their existing inline localized errors and are not added to the saved settings. Profile TUN DNS accepts IPv4 only, but that dialog has no invalid-message resource, so its test checks rejection and retained state without asserting an error label. The dialog Save action can close after a guarded callback rejects a draft, so the test verifies the retained editor state and the final saved config. See [the shared DNS editor](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigDnsTunnelEditors.kt:162), [shared validation](../../shared/core/src/commonMain/kotlin/features/config/TrafficProfileSettings.kt:59), and [DNS server parsing](../../shared/core/src/commonMain/kotlin/engine/xray/XrayDnsServer.kt:23).

DNS dialog changes remain in the shared screen draft until toolbar Save or Back. Desktop saves that draft when returning from DNS to profile Basics. The screen test follows its visual order—TUN DNS, Direct DNS servers, domain rules, then hosts—and checks there are no library writes after entry or while editing, then exactly one write on Back. It reloads the temporary config JSON and checks both typed settings and the actual `[SKIPI]` representation against [the codec writer](../../shared/core/src/commonMain/kotlin/features/config/TrafficConfigDocument.kt:235). The malformed entries are absent; valid additions and the accepted IPv4 TUN DNS value are present. The DNS suite uses an `ImageComposeScene` at **1100×1300**.

The Resources case enters the inactive target profile using its More → UI Edit action, then Basics → Routing → Resources. The auto-update switch and interval field persist immediately through `updateSettings`; there is no Resources Save button. The test confirms `autoUpdate=true` and interval `96` are already in temporary `configs.json` before Back, and confirms Back adds no library transform. It inserts a newer same-ID target row marker while the screen is open, then checks that the two setting edits preserve that marker, the sibling profile, library envelope, profile metadata, and unrelated config sections. The screen uses an `ImageComposeScene` at **1100×900**. Source references: [Desktop route and latest-row store](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt:494), [resource controls](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/resourcefiles/ResourceManagementScreen.kt:394), and [profile store](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTrafficConfigEditorProfileStore.kt:22).

Before the first baseline, the test fixture was normalized with `toTrafficConfigState(ResourceUserAgent).withSkipiSettingsInRawConfig()` before its initial save. The hand-authored SKIPI fixture omitted canonical keys, so opening DNS could otherwise rewrite the file during screen setup and invalidate the “no write until Back” assertion. This was a test-fixture setup correction; it introduced no production behavior or source changes. The installed test source and retained reviewed draft are [here](../../desktopApp/src/test/kotlin/app/skipi/desktop/DesktopTrafficConfigValidationAndResourcesScreenIntegrationTest.kt) and [here](../../outputs/drafts/DesktopTrafficConfigValidationAndResourcesScreenIntegrationTest.kt.draft); the [source-backed spec](../../outputs/drafts/DesktopTrafficConfigValidationAndResourcesScreenIntegrationTest.spec.md) records the same behavior.

## Baseline result

The scoped run executed exactly these **2 tests: 2 GREEN, 0 failures, 0 errors, 0 skipped**:

| Case | Persistence boundary proved |
| --- | --- |
| `dnsDialogsRejectMalformedDraftsAndPersistOnlyAcceptedValuesOnBack` | Invalid domains, hosts, DNS servers, and TUN DNS are rejected; no file/library write occurs during dialog edits; Back commits accepted values once. |
| `inactiveProfileResourcesRouteBackPersistsAgainstTheLatestRowAndPreservesLibraryEnvelope` | Resource controls write immediately against the latest same-ID row; Back causes no extra write and target/sibling/envelope/raw sections survive. |

The JUnit XML reports **2.243 seconds** of test execution (`1.217s` and `1.023s`); the complete Gradle command took **30 seconds**. The run metadata records `production_changes: false`, and the test source hash remained `f3c6de9ffa87c7c563847458c9aa23e5ae5cccff375c2b31084e5758d2eb98b5` before and after the build. The exact evidence is [counts](../../outputs/p04-validation-resources-baseline-reports-20261023/counts.json), [JUnit XML](../../outputs/p04-validation-resources-baseline-reports-20261023/TEST-app.skipi.desktop.DesktopTrafficConfigValidationAndResourcesScreenIntegrationTest.xml), [source provenance](../../outputs/p04-validation-resources-baseline-reports-20261023/test-source-provenance.json), and [full Gradle log](../../outputs/p04-validation-resources-baseline-20261023.log).

Command, from the repository root (no `SKIPI_CORE_INTEGRATION_DIR` and no `--rerun`):

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  ./gradlew --offline \
  :desktopApp:test \
  --tests 'app.skipi.desktop.DesktopTrafficConfigValidationAndResourcesScreenIntegrationTest' \
  -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  -x :hevtun:buildHevTun \
  -x :hevtun:syncHevSocks5TunnelVersion \
  > outputs/p04-validation-resources-baseline-20261023.log 2>&1
```

This is a focused acceptance run, not a fresh full-suite run. The prior Per-App full inventory was **2,822 GREEN** tests in its own separate run. Adding these 2 cases gives a **2,824-test inventory across two runs**, not one 2,824-test execution. See [the Per-App screen report](p04-per-app-screen-verification.md) and [the profile-forms report](p04-profile-forms-screen-verification.md) for their separate results.

## Limits

These two tests use public Compose semantics in `ImageComposeScene`, isolated temporary profile/config data, and inert resource services. They do not exercise a physical OS window or device, OS clipboard, network access, package enumeration, or real external resource downloads. This scoped baseline produced no new screenshot matrix. Earlier visual evidence remains separate: six Per-App PNGs were reviewed in the [Per-App report](p04-per-app-screen-verification.md), and fourteen profile-forms PNGs are documented in the [profile-forms report](p04-profile-forms-screen-verification.md). Those images do not constitute visual review of the DNS dialogs or Resources screen in this new test.
