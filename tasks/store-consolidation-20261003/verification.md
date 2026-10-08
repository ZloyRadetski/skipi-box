# Verification record

## Environment

- Repository baseline: `e4c438d`; the HevTun submodule was already dirty before this task and was not edited by this verification work.
- No repository `AGENTS.md` was found in the repository or its ancestors.
- Gradle wrapper: 9.7.0, already installed under the user's Gradle wrapper cache.
- Use the installed JDK 21 at `/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`; the default `java` was OpenJDK 27. The Android module also selects Java 21 for its unit-test process.
- Android SDK: `/home/vqsego/Android/Sdk`, with platforms 36.1 and 37.0 and build tools installed. `local.properties`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` were absent initially; commands set the SDK variables explicitly.
- No Android NDK is installed. The local sibling `../skipi-core` has a Desktop `.so` but no `skipicore.aar`; the Android AAR resolves from the configured GitHub release repository.
- The baseline commands ran before production edits. The online runs resolved already-declared dependencies into Gradle's user cache; no repository dependency or version files were changed.

## Successful pre-change baselines

The shared common store tests passed on Desktop JVM:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  PATH=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin:$PATH \
  ./gradlew --offline :shared:app:desktopTest
```

This passed before the new concurrent-action regression was added. It ran the existing `SharedApplicationStoreTest` and the other common store tests.

Android Kotlin compilation and app JVM tests passed after normal online dependency resolution, excluding the HevTun native build and submodule sync tasks:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  PATH=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin:$PATH \
  ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin \
    -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
```

The run completed successfully: 371 Android JVM tests passed and the app Kotlin source compiled. It does not validate a native HevTun rebuild, JNI packaging, or APK packaging. The normal compile emitted existing Kotlin warnings in `ProxyServerListPage.kt` and `AndroidTunnelController.kt`; unit-test compilation emitted existing no-cast-needed warnings.

The shared store and Desktop app suites also passed online:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  PATH=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin:$PATH \
  ./gradlew :shared:app:desktopTest :desktopApp:test
```

This completed successfully with 161 Desktop app tests passing. Before the new concurrent-action test was added, the common shared app suite had 67 tests and passed. The online run was needed because the offline cache lacked the already-declared `sh.calvin.reorderable:reorderable-jvm:3.1.0` variant.

## Environment-blocked attempts, not product failures

- The first all-target offline baseline stopped at `:hevtun:buildHevTunArm64V8aJni` because the Android NDK is absent. No test or Android Kotlin compile failure was reported.
- An offline Desktop attempt stopped at `:shared:ui:compileKotlinDesktop` because `sh.calvin.reorderable:reorderable-jvm:3.1.0` was not cached. Re-running online resolved the dependency and the Desktop suite passed.
- An offline Android attempt with HevTun native tasks excluded stopped before tests at `:app:processDebugNavigationResources`, because `app.skipi.core:skipicore:v1.0.18` was not cached and the sibling checkout has no AAR. Re-running online resolved the configured AAR and Android tests/compile passed.
- The wrapper's first attempt could not create a lock file under the read-only home Gradle cache. The successful reruns used the standard Gradle cache with the approved local build escalation.

## Test map and regression evidence

- `shared/app/src/commonTest/kotlin/app/skipi/app/store/SharedApplicationStoreTest.kt` covers repository aggregation, validation, selection, catalog commits, routing/resources, and runtime state. A new mixed `dispatch`/`dispatchAndAwait` concurrency regression exposed the old unsynchronized ID allocator: the focused Desktop run observed 509 unique IDs for 512 concurrent actions (`expected 512 but was 509`). After the fix, it observed 512 unique IDs and completed with no in-flight actions. This is a confirmed pre-fix failure and post-fix pass for the added regression, not a failure in the original baseline suite.
- `app/src/test/kotlin/features/proxy/server/usecase/AndroidTunnelControllerTest.kt` covers controller-to-legacy-AppState behavior; `AndroidTunnelRuntimeControllerTest.kt` covers wrapper snapshot publication and delegation. `AndroidRuntimeStateRepositoryTest.kt` covers cold-start status, background service/profile events, stopped traffic clearing, status failure/cancellation, and preservation of other runtime fields. `AndroidAppRepositoryMappingTest.kt` covers field conversion and preservation.
- The composition wiring was verified from the final source graph: `App.kt` reads `appRepositories`, `runtimeStateRepository`, and `sharedApplicationStore` from the same `AndroidAppStateStore`; those lazy properties use its application-owned scope. `AndroidAppRepositories` now requires the host runtime repository, and `AppServices` receives the exact same repository/store references. Background consumers still use `AndroidAppStateStore.sharedApplicationStore`, so both access paths resolve to the same lazy instance. A static source sweep found one Android and one Desktop production `SharedApplicationStore` construction, one Android repository graph construction, and no remaining `AndroidTunnelRuntimeRepository`, unavailable fallback, or `tunnelRuntimeRepository` references. No direct identity test seam was added; the report distinguishes this source-graph check from test assertions.
- The process runtime repository observes the service running state, the shared traffic sampler cache, and selected profile/AppState changes. Its traffic read uses the existing sampler value and starts no native polling. The UI tunnel-controller wrapper remains at the UI boundary for VPN permission and controller operations; the process repository does not retain an Activity. Cancellation is rethrown, status failures publish structured failure metadata, and snapshot publication preserves the other runtime fields.
- Lifecycle review covered application-scope ownership, Activity retention, duplicate process-level repository graphs, collector cancellation, runtime metadata, and action IDs. No unresolved critical or required code-review findings remain.

## Final integrated verification

The integrated Android, shared Android/Desktop, and Desktop JVM checks passed after the production fix:

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  PATH=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin:$PATH \
  ./gradlew :shared:app:allTests :app:testDebugUnitTest :app:compileDebugKotlin :desktopApp:test \
    -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
```

XML reports after that run showed zero failures, errors, or skipped tests in each suite:

| Suite | Tests passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Shared app Desktop JVM | 68 | 0 | 0 | 0 |
| Shared app Android host JVM | 68 | 0 | 0 | 0 |
| Android app JVM | 378 | 0 | 0 | 0 |
| Desktop app JVM | 161 | 0 | 0 | 0 |

The Android unit-test suite was run once more after the test-only cleanup (coroutines-test opt-in, controller test rename, and snapshot metadata assertion):

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  PATH=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin:$PATH \
  ./gradlew :app:testDebugUnitTest \
    -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
```

It passed all 378 Android JVM tests with no skipped tests. No common production or shared test source changed after the integrated green run.

The first integrated compile attempt found `services.sharedApplicationStore` out of scope in `ProxyServerListPager.kt`. It was corrected to `stateStore.sharedApplicationStore`, which resolves through the same Android singleton; the integrated rerun above compiled successfully. `git diff --check` is clean. The HevTun submodule HEAD remains `e802f02` (the same pre-existing dirty submodule state recorded at baseline). There is no NDK in the SDK, so native HevTun rebuild, JNI packaging, APK packaging, and device/instrumented verification were not run.

## Review status

Independent review is complete against correctness, readability, architecture, security, performance, and verification. No unresolved blockers remain. The pager reference found during the first integrated compile was fixed and passed the integrated rerun. Native/JNI/APK and device/instrumented verification remain outside the confirmed results because no NDK is installed and no device test was run.
