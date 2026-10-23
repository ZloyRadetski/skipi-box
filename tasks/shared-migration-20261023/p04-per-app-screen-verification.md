# P04 — Desktop Per-App screen verification

23 October 2026. This records the completed Desktop Per-App screen acceptance gate. **The Per-App screen gate is GREEN; P04 remains open** for DNS validation and Resources route tests that are still draft. Full package inventory and runtime enforcement remain separate R03 work. The source-backed eight-case plan is in [the Per-App screen spec](../../outputs/drafts/p04-per-app-screen-spec.md). Prior shared codec/selection and controller/store checkpoints are documented in [foundation verification](p04-per-app-foundation-verification.md), [controller verification](p04-per-app-controller-verification.md), and the [Per-App source audit](p04-remaining-per-app-research.md).

Subsequent status: the two DNS/Resources baseline cases passed without production changes, and [P04 acceptance](p04-acceptance-verification.md) closed the card. The open-P04 statements below describe this Per-App checkpoint before that additional verification.

## Scope under test

The new `DesktopTrafficConfigPerAppScreenIntegrationTest` exercises the existing profile-card More → UI Edit route for inactive profile 7 while profile 3 remains selected, then characterizes immediate per-profile mode and clipboard operations. It uses a real temporary `configs.json`, a `DesktopTrafficConfigRepository`, public Compose semantics actions, injected clipboard callbacks, and fresh `ImageComposeScene` instances at 390×844 and 1100×800.

The eight cases cover: immediate mode persistence and Back; Replace with a bare package name and the Android source fallback value 0; Merge after a late same-ID profile edit and sibling insertion; Merge after the target disappears from the latest file-backed library; malformed user-ID rejection; clipboard export without package inventory or library mutation; six viewport captures of body/menu/dialog; and handled save failures for both Merge and Replace. The failure case captures the computed repository candidate, injects an error at the save boundary before JSON publication, and checks that pending confirmation remains available without an Imported success notice.

The shared implementation now owns the mode and More menus, clipboard codec calls, pending import confirmation, controller operations, and localized notices. Android keeps search, user tabs, package pager/list, loading, refresh, labels/icons, and selection projection in its native host slots; Desktop supplies `currentUserId = null`, `selfPackageName = ""`, and the capability notice without fake inventory. The shared screen takes a required host-owned `actionScope: CoroutineScope`: Android creates it before the profile-existence guard, matching the original source location, and Desktop passes its existing `DesktopConfigsScreen` root scope. The Missing case passed the scrolled gate; this scope placement is source-lifecycle alignment, not a reproduced notice-loss defect.

The tests do not simulate Android package enumeration, self-package filtering on Desktop, OS clipboard access, VPN enforcement, physical-device/window input, or exhaustive editing of every field. The shared codec/controller behavior and Desktop file-backed store are covered by the cited prior checkpoints.

## Test-first chronology

The initial **seven-case API RED** ran for 18 seconds and executed no tests. It exposed the expected missing `readPerAppClipboardText` screen parameter and `configs_per_app_desktop_notice` resource. Its test-source SHA-256 was `7210d0ca5bba05cbfdd22aca3166124becf1e2dc175b9a9bafe31b2687a7fe2a`; see [API RED result](../../outputs/p04-per-app-screen-api-red-reports-20261023/result.json) and [compiler output](../../outputs/p04-per-app-screen-api-red-reports-20261023/compiler-errors.txt). Before UI implementation, the eighth case was added for the existing shared controller's `Failed` result and `onSaveFailure` path. The case verifies Merge and Replace independently with a test-only one-shot failure after transform computation but before the file save/publish boundary.

The callback/resource bootstrap was then applied without adding the Per-App route. The first post-bootstrap attempt reported **8/8 failures in 22 seconds**, but those failures did not establish product RED: the test helper selected a text child and tried to invoke `OnClick` on that child, although the action was exposed by an ancestor. Root captured that attempt separately as an initial-harness failure.

The helper now resolves the nearest ancestor with `SemanticsActions.OnClick` before dispatching text actions. The route and behavior expectations were unchanged. The clean behavior RED then completed in **32 seconds**: **8 tests, 8 failures**, all reaching Basics and failing because the Per-App section action `Apps for this configuration` is still absent. This is the expected route RED. Its test-source SHA-256 is `98fdc89fbe349749922e0c7c0942488fdddeb20d01bfb4339689b96e4d98df07`. Evidence: [clean behavior RED result](../../outputs/p04-per-app-screen-behavior-red-reports-20261023/result.json) and [JUnit XML](../../outputs/p04-per-app-screen-behavior-red-reports-20261023/TEST-app.skipi.desktop.DesktopTrafficConfigPerAppScreenIntegrationTest.xml).

## Implementation verification attempts

The first implementation build stopped before executing tests in **14 seconds**: the shared screen's public app-content callback exposes Miuix `ScrollBehavior`, while `shared/ui` declared that library as an implementation-only dependency. The dependency was corrected to `api(libs.miuix.ui)`; Desktop does not add its own direct dependency. Evidence: [compile diagnostics](../../outputs/p04-per-app-screen-first-compile-reports-20261023/compiler-errors.txt).

The next attempt compiled both Desktop and Android but reported **8/8 failures in 51 seconds** at the same Per-App section lookup. Source review distinguished this from the original absent-route RED: the new section exists at the end of Basics' `LazyColumn`, and the test had not scrolled far enough to compose the item. Evidence: [first route attempt](../../outputs/p04-per-app-screen-first-route-attempt-reports-20261023/result.json). This attempt does not establish any downstream import/export result.

The first scrolled behavior run completed in **33 seconds with 7/8 passing**. Passing cases included Replace with the source fallback user ID, late-row Merge, target-missing import with notice `0`, malformed ID handling, export without inventory, six viewport captures, and both handled save-failure branches. In the eighth test, immediate mode persistence succeeded before the Back lookup failed. A **13-second diagnostic** showed Basics was rendered correctly and the Per-App card was below the viewport. The helper was corrected to scroll after Back and the test gained an additional `targetName` assertion. The final pre-build test-source SHA-256 is `225d3b982267fe8643d3bd0e7a58f546cb48cb35013ddae1f3d5bb902da47756`; the difference from the clean RED source hash reflects harness/navigation assertion work, not changed route or Per-App behavior expectations. The clean RED hash remains `98fdc89fbe349749922e0c7c0942488fdddeb20d01bfb4339689b96e4d98df07`.

## Current gates

- **Behavior RED:** complete with the clean 8/8 expected route failures recorded above.
- **Implementation:** the shared editor and Desktop route are present. The initial scrolled run was 7/8 because the Back assertion did not scroll Basics; the diagnostic identified the viewport issue, and the helper was corrected before the fresh regression.
- **Desktop Per-App behavior:** the corrected harness passed **8/8** scenarios. The pre-build source SHA is `225d3b982267fe8643d3bd0e7a58f546cb48cb35013ddae1f3d5bb902da47756`.
- **Full regression:** completed in **117 seconds**, with **2,822 tests, 0 failures, 0 errors, 0 skipped** across the eight target groups. `:desktopApp:compileKotlin` and `:app:assembleDebug` passed. The desktop tests used configured `SKIPI_CORE_INTEGRATION_DIR`; all seven native Core cases ran: Core FFM 4, Profile Assets 1, Selected Server 2.

| Target | Tests | Result |
| --- | ---: | --- |
| shared/core Desktop | 372 | 0 failures/errors/skips |
| shared/core Android | 316 | 0 failures/errors/skips |
| shared/app Desktop | 355 | 0 failures/errors/skips |
| shared/app Android | 347 | 0 failures/errors/skips |
| shared/ui Desktop | 120 | 0 failures/errors/skips |
| shared/ui Android | 120 | 0 failures/errors/skips |
| Android app | 584 | 0 failures/errors/skips |
| Desktop app | 608 | 0 failures/errors/skips |
| **Total** | **2,822** | **0 failures/errors/skips** |

The full regression reran the suite task groups and added compile/APK tasks:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  SKIPI_CORE_INTEGRATION_DIR=/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/build/generated/skipi-core-resources/common \
  ./gradlew --offline \
  :shared:core:desktopTest --rerun :shared:core:testAndroidHostTest --rerun \
  :shared:app:desktopTest --rerun :shared:app:testAndroidHostTest --rerun \
  :shared:ui:desktopTest --rerun :shared:ui:testAndroidHostTest --rerun \
  :app:testDebugUnitTest --rerun :desktopApp:test --rerun \
  :desktopApp:compileKotlin :app:assembleDebug \
  -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
```

The Desktop run had `SKIPI_CORE_INTEGRATION_DIR` set to the configured Core runtime. The exact test counts, timestamps, native suite names, and screenshot hashes are in [counts.json](../../outputs/p04-per-app-screen-full-green-reports-20261023/counts.json). The final test-source hash matched the frozen pre-build hash, as recorded in [test-source-provenance.json](../../outputs/p04-per-app-screen-full-green-reports-20261023/test-source-provenance.json).

- **Raster QA:** root viewed all six scrolled-gate PNGs. Each final PNG is byte-identical to its inspected counterpart; file hashes are in `counts.json`, and final captures are under `../../outputs/p04-per-app-screen-full-green-reports-20261023/png/`.
- **Remaining P04 work:** DNS validation and Resources route test cases remain draft. Package enumeration, full projection/search extraction, and runtime enforcement remain R03 scope.

The initial post-bootstrap harness failure is excluded from product RED evidence. This report records the final GREEN Per-App screen gate; P04 remains open for the DNS validation and Resources route tests listed above. This full run updates the overall inventory; the earlier reports retain their source-baseline and scoped verification evidence. Production and tests are recorded in commit `35728b7`; the gate ran on the same source content before that commit.
