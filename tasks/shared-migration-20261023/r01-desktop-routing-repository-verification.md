# R01 — Desktop RoutingRepository verification

23 October 2026. This records the focused repository-only Desktop gate. **The persisted routing state foundation is ready; global Routing UI and runtime integration remain pending, so R01 is still open.** The research and remaining boundary are described in [R01 routing repository research](r01-routing-repository-research.md).

Production and tests: `a5cba46`; the focused gate ran on the same source content before the commit.

## Scope and behavior

`DesktopRoutingRepository` adapts the existing `DesktopAppSettings` routing fields to the common `RoutingRepository` contract. Each update reads the latest settings, transforms the current routing record, preserves unrelated settings and maintains `nextRouteRuleId = maxOf(currentCounter, maxRuleId + 1)` according to the Android adapter. It saves before publication and flow update; a failed save leaves published settings and repository state unchanged. `refresh()` reads the latest settings and updates only the repository flow. `Main` calls it alongside existing repository refreshes when host settings change.

The Desktop application factory requires a routing repository and passes the same instance to `AppRepositories` and `DesktopSharedApplication`. The integration case dispatches `SharedApplicationAction.UpdateRouting` through `SharedApplicationStore` and checks both the store state and temporary persisted settings JSON. The cases use real temporary settings files and public repository/store contracts.

The high-water counter rule verified here is specifically the existing `RoutingRepository.update` contract. The current global Android `RoutingPage` bypasses that repository: clipboard Replace restarts imported rule IDs at 1 and writes the counter derived from imported rules alone. A future global UI/controller migration must preserve that page-level Replace behavior instead of applying the repository high-water rule blindly or relying on `RoutingConfigRecord` alone, which has no counter. This source distinction is outside the current adapter slice; see the [global routing source audit](../../outputs/drafts/r01-global-routing-source-contract.md).

## Test-first and focused results

Before production code, the expected API RED stopped during compilation after **21 seconds**. The four planned routing cases did not execute (`tests_executed: 0`); diagnostics showed the missing `DesktopRoutingRepository`, factory `routing` input/bundle exposure, and `refresh` API with follow-on unresolved-reference errors. The RED test-source SHA-256 was `fb60f9c746cbd1da3dbff828453a23ecabab4f7561adb0651a2e16708dca94dc`. Evidence: [RED result](../../outputs/r01-desktop-routing-repository-api-red-reports-20261023/result.json), [compiler diagnostics](../../outputs/r01-desktop-routing-repository-api-red-reports-20261023/compiler-errors.txt), and [full RED log](../../outputs/r01-desktop-routing-repository-api-red-20261023.log).

After implementation, the focused run completed in **12 seconds**: **20 tests, 0 failures, 0 errors, 0 skipped**, and Desktop compilation passed. The same routing test-source SHA was used as in RED.

| Target | Tests | Result |
| --- | ---: | --- |
| `DesktopRoutingRepositoryTest` | 4 | 0 failures/errors/skips |
| `DesktopSettingsLibraryTest` | 12 | 0 failures/errors/skips |
| `DesktopProxyDeletionActionTest` | 4 | 0 failures/errors/skips |
| **Total** | **20** | **0 failures/errors/skips** |

The four routing cases cover: latest-file reads, unrelated settings and route-ID high-water preservation/advance; refresh from external settings without save or publish; save failure without file/state/publication change; and factory-to-shared-store persistence. Exact test names and counts are in [focused counts](../../outputs/r01-desktop-routing-repository-focused-green-reports-20261023/counts.json) and [JUnit reports](../../outputs/r01-desktop-routing-repository-focused-green-reports-20261023/).

## Commands

The expected API RED used the same environment and exclusions as the focused GREEN, with only the routing test selected and no Desktop compile task:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  ./gradlew --offline \
  :desktopApp:test \
  --tests 'app.skipi.desktop.DesktopRoutingRepositoryTest' \
  -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  -x :hevtun:buildHevTun \
  -x :hevtun:syncHevSocks5TunnelVersion \
  > outputs/r01-desktop-routing-repository-api-red-20261023.log 2>&1
```

The focused GREEN selected all three affected Desktop test classes and compiled the Desktop application:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  ./gradlew --offline \
  :desktopApp:test \
  --tests 'app.skipi.desktop.DesktopRoutingRepositoryTest' \
  --tests 'app.skipi.desktop.DesktopProxyDeletionActionTest' \
  --tests 'app.skipi.desktop.DesktopSettingsLibraryTest' \
  :desktopApp:compileKotlin \
  -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  -x :hevtun:buildHevTun \
  -x :hevtun:syncHevSocks5TunnelVersion \
  > outputs/r01-desktop-routing-repository-focused-green-20261023.log 2>&1
```

There was no `SKIPI_CORE_INTEGRATION_DIR` configured for these focused runs. The native Core suites, Android suites, APK assembly, and fresh full regression were not part of this gate. Prior P04/full-suite reports remain separate evidence; they were not rerun here.

## Remaining R01 work

This gate proves the persisted repository/state foundation and its Desktop composition path. It does not connect global Routing UI, editor navigation, full CRUD/import-export orchestration, or routing data to Desktop Xray runtime/planner. The global page's clipboard Replace counter/ID semantics also remain to be preserved in that future slice. Those R01 tasks remain open.
