# S03: Android background load/import

Android source before the retained-runner extraction (`e4c438d^`, `SubscriptionUpdateUseCase.kt:60–90`) moved concurrent load/import to `Dispatchers.Default`. Current common orchestration correctly inherits its host caller dispatcher, but Android had lost its own dispatcher boundary. A Home caller could therefore run YAML/payload import on the UI thread.

Before production changes, `AndroidSubscriptionRefreshBatchDispatcherTest` ran **2/2 RED** with actual `assertNotSame` failures: options/fetch used the dedicated caller thread in both the retained batch and reachable single-refresh helper. Valid YAML passed through the real Android parser; eligibility/progress and caller-thread capture/commit expectations were retained. Spec: [s03-batch-dispatcher-spec.md](../../outputs/drafts/s03-batch-dispatcher-spec.md).

Minimal fix: the Android host acquires the existing per-ID lock, then executes options/fetch/import in `withContext(Dispatchers.Default)`. Shared algorithms remain dispatcher-neutral. Capture, pre-eligibility hook, refresh timestamp and commit stay on the caller context. Failure aggregation/logging and cancellation remain in their existing paths.

After production changes, focused Android subscription suites **39/39 GREEN**, plus refresh apply/cancellation parity **8/8 GREEN**, failures/errors/skips zero. No expectation changes. Test SHA-256 before/after: `d3b76833a24dbb2aa8074d33be30ed42a6b8455e6c17b91236bfc4ad46718fac`. XML, Gradle logs and counts are under `outputs/s03-dispatcher-reports-20261023/{red,green,commit-cancellation-green}`. Root reviewed the diff and context/lock/cancellation boundaries. S03 remains open for periodic/install integration slices; full integrated gate follows those changes.
