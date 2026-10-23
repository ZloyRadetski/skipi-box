# S03 Android refresh dispatcher regression spec

Source baseline: repository base 3d87f1c, branch refactor/desktop-shared-outbound-plan. Acceptance source: [tasks/plan.md#s03](../../tasks/plan.md#s03). Android behavior is pinned to app/src/main/kotlin/features/subscription/usecase/SubscriptionUpdateUseCase.kt at e4c438d^, lines 60–90: updateSubscriptionsFromResponses() moved the concurrent load-and-import scope to Dispatchers.Default. The same history shows the reachable Home single-refresh path calls that helper.

## Expected behavior

When a caller invokes the retained batch runner from a single-thread UI-like dispatcher:

- the pre-eligibility callback, live-catalog capture, refresh timestamp, and commit callback stay on the exact calling thread;
- eligible enabled subscriptions with nonblank URLs are loaded off the calling thread; disabled and blank-URL rows never derive fetch options or fetch;
- the real Android parser imports a valid YAML response into the returned proxy model, and successful IDs, failures, URL count, and progress remain correct.

The active single-refresh updateSubscriptions(..., fetchText = ...) path also uses updateSubscriptionsFromResponses(). It must fetch and parse away from the caller thread while returning the same parsed update result.

The tests compare Thread identity only. They do not depend on thread names, a specific Dispatchers implementation, timings, or benchmarks. Each single-thread executor dispatcher is closed in finally.

## Test-first implementation gate

AndroidSubscriptionRefreshBatchDispatcherTest.kt specifies the batch runner and reachable single-refresh helper using current injectable APIs, real Android import parsers, and deterministic in-memory fetch callbacks. These tests should be run RED against the current implementation before changing production code. The expected minimal repair is an Android host dispatcher boundary around loading/import work, preserving capture, eligibility orchestration, commit, and UI callbacks on the caller dispatcher. Do not move shared algorithms or alter expectations to match implementation.

No tests were executed while preparing this spec and test file.
