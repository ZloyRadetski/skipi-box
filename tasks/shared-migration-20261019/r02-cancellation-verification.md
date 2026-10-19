# R02 shared HTTP cancellation-state checkpoint — 19 October 2026

The tests-first cancellation-state slice is complete for its tested scope. The shared JVM module now owns the one global cancellation state for the active resource-file HTTP request; Android keeps its existing source alias and all platform connection, proxy, authentication, and network setup. Desktop can use the shared JVM state object, but its resource downloader and UI are not wired yet. R02, P04, and R01 remain open.

## Behavior and ownership

`ResourceFileDownloadCancellation` remains a single Kotlin `object`, with the same global `AtomicBoolean` and `AtomicReference<HttpURLConnection?>` behavior as the former Android singleton. Operation order is preserved exactly: `begin()` sets the cancelled flag to false, then clears the current connection; `cancel()` sets the flag to true, then disconnects the currently tracked connection; `track()` stores the connection, then checks cancellation; `untrack()` clears only the same connection with `compareAndSet`; `isCancelled()` reads the flag; and `throwIfCancelled()` throws the shared `ResourceFileDownloadCancelledException` when cancellation is set. Android's internal `AndroidResourceFileDownloadCancellation` typealias remains, so existing App, repository/coordinator, downloader, and cancel-receiver Kotlin call sites keep their name. The Android cancellation-exception alias remains too. There is no per-instance copy of cancellation state.

Android still owns `HttpURLConnection` creation/configuration, VPN and SOCKS selection, proxy authentication/fallback, and actual connection cleanup through its adapter. The shared JVM holder can support a future Desktop HTTP downloader, but it does not by itself add a Desktop downloader or resource UI.

## Test-first and verification evidence

| Gate | Result | Evidence |
|---|---|---|
| Android source cancellation baseline | **6/6 GREEN** before the shared state move, 23 seconds. | Commit `960d10d`; [baseline log](../../outputs/r02-cancellation-android-baseline-20261019.log). |
| Shared cancellation API | Expected missing-API **RED** in 4 seconds before implementation. | [API RED log](../../outputs/r02-cancellation-shared-api-red-20261019.log). |
| Focused cancellation/proxy suite | **25/25 GREEN** in 31 seconds: shared cancellation 6, Android cancellation 6, Android downloader 9, Android proxy fallback 4. The proxy oracle's expected assertions were unchanged. Its first compile attempt hit a missing Android stub for `Authenticator.getDefault()`; the test harness now reflectively looks up that public method. | Android proxy baseline commit `7c1cdc5`; [focused log](../../outputs/r02-cancellation-focused-proxy-baseline-green-20261019.log), [focused reports](../../outputs/r02-cancellation-focused-proxy-baseline-reports-20261019/). |
| Full regression | **2,391 executions in 58 seconds**, failures/errors/skips all 0: core 316 Desktop + 274 Android, app 317 Desktop + 309 Android, UI 94 Desktop + 94 Android, Android app 543, Desktop app 444. Desktop compile and Android APK passed. Seven actual native/Core tests ran; their XML timestamp is `2026-10-19T19:27Z`. | [Full log](../../outputs/r02-cancellation-full-green-20261019.log), [suite counts and reports](../../outputs/r02-cancellation-final-reports-20261019/suite-counts.json). |

Cancellation state production/tests are commit `238b073`; evidence is commit `42aeb92`.

## Coverage limit

The latch-based cancellation tests block a fake connection read and verify that cancellation invokes `disconnect()` and releases that fake read. They do not prove that cancellation unblocks a real HTTP socket read. The Android downloader's progress-callback test covers synchronous cancellation during copy, not cross-thread cancellation while a real socket read is blocked. This real-socket limit does not block the pure shared state move; retain it as a boundary if the actual connection adapter's disconnect behavior changes.

## Remaining R02 work

The common proxy-policy tests recorded the expected missing-API **RED** before implementation, and the Android proxy baseline passed (`7c1cdc5`). The shared proxy fallback implementation is now in focused verification/review. See the [proxy-policy API RED log](../../outputs/r02-proxy-policy-shared-api-red-20261019.log). Desktop's resource download runner and resource UI remain unwired. Do not substitute global bundled geo files for profile-scoped resources; broader R02/P04/R01 acceptance remains open in [the migration plan](../plan.md#r02).
