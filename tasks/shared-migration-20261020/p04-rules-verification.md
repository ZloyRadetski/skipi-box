# P04 Rules continuation — verification contract

**Status: IN PROGRESS.** R02 resource work is complete. This checkpoint records the source-backed contract for the P04 continuation; it does not close P04/R01. Before production, the Android source oracles passed: `RoutingSuggestionsProviderAndroidOracleTest` 4/4 and `TrafficConfigRulesAndroidOracleTest` 6/6, with zero skips, failures, or errors. Log: `outputs/p04-rules-android-baseline-20261020.log`; XML reports: `outputs/p04-rules-android-baseline-reports-20261020/`. The reviewed Shared/Desktop tests produced the expected API RED before production: the shared JVM `GeoDatParser` and `DesktopRoutingSuggestionsProvider` were absent; log: `outputs/p04-routing-suggestions-api-red-20261020.log`. After stage 1 implementation, the focused Shared/Desktop plus Android verification passed 28/28 with zero skips, failures, or errors. Log: `outputs/p04-routing-suggestions-focused-green-20261020.log`; XML reports and suite counts: `outputs/p04-routing-suggestions-focused-reports-20261020/`. Stage 1 is verified; Stage 2 and P04/R01 remain in progress.

## Stage 1 — Desktop file-backed routing suggestions

Relocate `app/src/main/kotlin/features/routing/usecase/GeoDatParser.kt` to `shared/core/src/jvmMain/kotlin/features/routing/usecase/GeoDatParser.kt`, preserving the file/stream parsing and cache algorithm; only the platform-specific KDoc is now JVM-neutral. `shared/core/build.gradle.kts` makes `androidMain` and `desktopMain` depend on `jvmMain`; this lets both hosts use the existing file/stream parser and cache without a second implementation. The tested tag parser remains `shared/core/src/commonMain/kotlin/features/routing/usecase/GeoDatTagParser.kt`.

`DesktopRoutingSuggestionsProvider` is a thin adapter over `DesktopProfileResourceFiles`. Each API takes the explicit `TrafficConfigState` being edited and delegates to the existing common `resolveGeoSiteSuggestions`, `resolveGeoIpSuggestions`, or `resolveRuleSetSuggestions` in `shared/core/src/commonMain/kotlin/features/routing/usecase/RoutingSuggestions.kt`. Read files from one captured `resourceFiles.snapshot(profile)` and its profile-scoped data directory. Bind each filename to `GeoDatParser.parseTags`; missing, empty, or unreadable files produce no parsed tags. Do not prepare files, restore bundled assets, download resources, or fall back to another profile during suggestion lookup. Pass an empty `globalCustomFiles` list: Desktop currently has profile-scoped custom resources and no global custom-resource catalog.

The shared resolver owns suggestion ordering, normalization, fallback, and spelling. GeoSite tags use lowercase `geosite:<tag>`; normal GeoIP rules use lowercase `geoip:<tag>`; Shadowrocket GeoIP rules use uppercase bare tags. Custom rules use the resolver's existing `ext:<filename>:<tag>` form and filename-based fallback. Rule-set suggestions combine GeoSite before GeoIP and retain the existing distinct behavior. Do not duplicate these algorithms in the adapter.

Files and test contracts for this stage:

- Android adapter source/oracle: `app/src/main/kotlin/features/routing/usecase/RoutingSuggestionsProvider.kt` and `app/src/test/kotlin/features/routing/usecase/RoutingSuggestionsProviderAndroidOracleTest.kt`.
- Shared algorithms/parser: `shared/core/src/commonMain/kotlin/features/routing/usecase/RoutingSuggestions.kt`, `GeoDatTagParser.kt`, and `shared/core/src/commonTest/kotlin/features/routing/usecase/RoutingSuggestionsTest.kt`, `GeoDatTagParserTest.kt`.
- Desktop scoped file API: `desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProfileResourceFiles.kt` and `shared/core/src/jvmMain/kotlin/features/resources/ResourceSnapshotFiles.kt`.
- Tests-first additions are `shared/core/src/desktopTest/kotlin/features/routing/usecase/GeoDatParserFileTest.kt` and `desktopApp/src/test/kotlin/app/skipi/desktop/DesktopRoutingSuggestionsProviderTest.kt`. Focused verification passed 28/28: `GeoDatParserFileTest` 4, `GeoDatTagParserTest` 2, `RoutingSuggestionsTest` 8, `DesktopRoutingSuggestionsProviderTest` 4, and Android oracles 4 + 6. There were zero failures, errors, or skips; build result was `BUILD SUCCESSFUL` in 31 seconds. See `outputs/p04-routing-suggestions-focused-green-20261020.log` and `outputs/p04-routing-suggestions-focused-reports-20261020/`.

Reproduce the focused verification using the Q2 JDK/Android SDK environment. The three Core class filters run on Desktop; the host provider and two Android oracle classes run in their respective test tasks. The HevTun tasks are excluded so existing native artifacts are used.

```bash
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
    ANDROID_HOME=/home/vqsego/Android/Sdk \
    ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
    ./gradlew --offline \
    :shared:core:desktopTest \
    :desktopApp:test \
    :app:testDebugUnitTest \
    --tests 'features.routing.usecase.GeoDatParserFileTest' \
    --tests 'features.routing.usecase.GeoDatTagParserTest' \
    --tests 'features.routing.usecase.RoutingSuggestionsTest' \
    --tests 'app.skipi.desktop.DesktopRoutingSuggestionsProviderTest' \
    --tests 'features.routing.usecase.RoutingSuggestionsProviderAndroidOracleTest' \
    --tests 'features.config.TrafficConfigRulesAndroidOracleTest' \
    -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
    -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
```

## Stage 2 — Desktop profile Rules list, editor, policy selector, and pickers

Wire Desktop through the already shared Rules screens and editor controller. Do not reimplement the rule parser, list mutations, form state/defaults, serializer, outbound projection, or suggestion algorithms. Existing shared UI includes `shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRuleEditorScreens.kt`; `TrafficConfigEditorController` in `shared/app/src/commonMain/kotlin/app/skipi/app/config/TrafficConfigEditorController.kt` owns save-on-Back and latest-profile rule updates. Desktop already creates this controller and adapts latest-row writes through `DesktopTrafficConfigEditorProfileStore` in `desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt` and `DesktopTrafficConfigEditorProfileStore.kt`.

For the policy selector, Desktop `Main` should observe the existing common repository flows with `collectAsState` and pass current snapshots to the Rules host: `desktopSharedApplication.proxyServers.servers` (`List<app.skipi.app.model.ProxyServerRecord>`) and `desktopSharedApplication.subscriptions.catalog` (`SubscriptionCatalog`), together with current traffic profiles, `resourceFiles`, and `latencyByServerId`. Map the common proxy records narrowly to the routing projection inputs (`id`, subscription-derived `groupId`, `server`, and current latency if needed); do not add a second JSON decode/projection path from `DesktopServerLibrary`/`DesktopSubscriptionLibrary` or silently substitute an empty production catalog. The common `projectRouteOutboundPicker` owns picker grouping and policy behavior. The host inputs should be explicit `DesktopConfigsScreen` parameters: `proxyServers`, `subscriptionCatalog`, `resourceFiles`, and `latencyByServerId`.

Android behavior to preserve:

- `TrafficConfigEditorPages.kt` routes from the profile Basics screen to Rules. `TrafficConfigRulesPage` uses the shared list. It displays non-final rules in order and renders the `FINAL` rule separately. Reorder, delete, and adding a missing final rule save immediately. Reorder uses the list's `localRawConfig`; delete and the missing-final action derive a document from the displayed `profile.rawConfig`. The shared controller writes that exact list document to the latest same-ID profile row, preserving the Android snapshot semantics, including replacement of the document content captured by the list.
- List Back and Save return to Basics. There is no unsaved list draft or discard flow. Existing rule edit navigation passes its exact `lineNumber`; add passes no line number.
- `TrafficConfigRuleEditorPage` resolves an edit by exact line number from the selected profile's current parsed rules. A missing profile or missing requested rule pops immediately. New-rule state identity is `-profileId`; edit identity is the line number. Initial type is `DOMAIN-SUFFIX`, value is empty, and policy is the trimmed existing policy or `PROXY`. Final rules force type `FINAL`.
- Editor Back and system Back both save the draft and return to the list. Save edits the latest profile document by line number or appends to the latest document for a new rule. A blank policy, or blank value on a non-final rule, skips the write and still navigates back. Final rules ignore the value. Policy text is passed through as entered. A storage failure does not navigate away; the existing Desktop controller reports the failure.
- Policy selection uses the shared route-outbound projection and screen with `trafficConfigId` set to the edited profile. Its result key is `traffic-config-rule-policy-$profileId-$editorIdentity`. Confirm updates only that editor's selected policy and clears the result. Cancelling the selector leaves the draft selection unchanged and preserves the form's type/value state. The shared projection supplies profile-policy labels such as server remarks and profile proxy-group names.
- GeoSite selection inserts `fullRule` for `DOMAIN-SET` and `tag` for other rule types. GeoIP inserts `tag`; RULE-SET inserts `fullRule`. Dismissal leaves the value unchanged. Use the existing Shadowrocket network options and shared presets.

Android source-of-truth files: `app/src/main/kotlin/features/config/TrafficConfigEditorPages.kt`, `TrafficConfigRuleEditor.kt`, and `app/src/main/kotlin/features/routing/RouteOutboundSelectorPage.kt`; the source-derived tests are `app/src/test/kotlin/features/config/TrafficConfigRulesAndroidOracleTest.kt` and the shared controller tests are under `shared/app/src/commonTest/kotlin/app/skipi/app/config/TrafficConfigRuleEditorControllerTest.kt`.

## Scope and gate

This continuation adds no per-app routing flow, new global resource store, new native/runtime behavior, or changes to user resource data. Per-app selection remains a separate platform-backed flow. Stage 1 API RED and focused GREEN are recorded above. Stage 2 has a Desktop actual-screen behavior test draft; its RED result is pending and the Stage 2 production gate remains closed. The migration remains in progress until both hosts use the shared Rules flow and the required checks are reported.
