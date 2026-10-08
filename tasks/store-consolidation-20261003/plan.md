# Android `SharedApplicationStore` consolidation — Step 1

## Objective

Make Android's application-facing consumers use one app-scoped `SharedApplicationStore`, created over the existing Android repositories and the real host-owned tunnel runtime. Preserve Room and SharedPreferences as the Android persistence adapters. This is the first vertical slice of the larger Android/Desktop sharing effort.

## Scope

- Android and Desktop JVM are the supported targets for this work; iOS is out of scope.
- Consolidate Android store ownership and runtime wiring. Keep the Android legacy `AppState`/database mapping as the persistence boundary for this step.
- Exercise shared store behavior on Desktop JVM and Android app behavior with the repository's JVM test tasks.
- Do not add full Android/Desktop feature parity, migrate unrelated screens, alter native tunnel engines, or expand this into a broad state migration.
- Shared store action-ID allocation is in scope because the consolidated store receives actions from concurrent UI and background callers.

## Source areas

- Android composition root and app service wiring: `app/src/main/kotlin/app/App.kt`, `app/src/main/kotlin/app/AppServices.kt`.
- Android application-owned state and repository construction: `app/src/main/kotlin/data/AndroidAppStateStore.kt`, `app/src/main/kotlin/data/repository/AndroidAppRepositories.kt`.
- Shared action correlation and common tests: `shared/app/src/commonMain/kotlin/app/skipi/app/store/SharedApplicationStore.kt`, `shared/app/src/commonTest/kotlin/app/skipi/app/store/SharedApplicationStoreTest.kt`.
- Existing behavior context: `app/src/main/kotlin/features/proxy/server/usecase/AndroidTunnelController.kt`, `app/src/test/kotlin/features/proxy/server/usecase/AndroidTunnelRuntimeControllerTest.kt`, `app/src/test/kotlin/features/proxy/server/usecase/AndroidRuntimeStateRepositoryTest.kt`, and `app/src/test/kotlin/features/proxy/server/usecase/AndroidTunnelControllerTest.kt`. The process runtime-state repository and UI controller wrapper are separate responsibilities.

## Design invariants to review

- The Android composition root and application/background entry points reach the same store and repository graph.
- The store's process runtime-state repository reads the actual VPN service state, the existing traffic-sampler cache, and relevant app-state changes; the UI controller wrapper remains at the UI boundary for permission and controller operations.
- Application-lifetime state and collectors use an application-owned scope; wiring must not retain an Activity or depend on a Composition scope that ends when the screen leaves composition.
- Cancellation and store lifetime are explicit, and wiring does not create duplicate process-level runtime repository/state-publisher graphs. Scoped state or event collectors are acceptable when their owners and cancellation are clear.
- Action IDs stay unique when `dispatch` and `dispatchAndAwait` are called concurrently, and completed/cancelled actions do not leave stale in-flight IDs.
- Desktop behavior stays compatible with the common store contracts without adding Android-only behavior or a parity requirement.
