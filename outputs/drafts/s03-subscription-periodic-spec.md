# S03 subscription periodic runtime: source contracts and test-first seam

Prepared against `HEAD 3d87f1c` on `refactor/desktop-shared-outbound-plan` (2026-10-23). This is a narrow source-backed design for subscription-group periodic refresh. It does not implement or extend P03 profile/geo-resource refresh.

## Android contract

The existing subscription schedule in [`SubscriptionRefreshScheduling.kt`](../../shared/core/src/commonMain/kotlin/features/subscription/runtime/SubscriptionRefreshScheduling.kt#L60) builds a `SubscriptionWorkSpec` for each target with `enabled == true`, a nonblank URL, and an enabled interval recognized by the common schedule parser. The spec uses unique name `subscription-update-<groupId>`, `requiresConnectedNetwork = true`, `SubscriptionExistingWorkPolicy.UPDATE`, and a 15-minute backoff base. [`SubscriptionScheduler.reconcile`](../../shared/core/src/commonMain/kotlin/features/subscription/runtime/SubscriptionRefreshScheduling.kt#L90) cancels previously tracked IDs no longer eligible, enqueues current specs, and stores the new set. The existing [`SubscriptionRefreshSchedulingTest`](../../shared/core/src/commonTest/kotlin/features/subscription/runtime/SubscriptionRefreshSchedulingTest.kt#L49) already proves stale-ID cancellation and the work spec fields.

The Android adapter [`AndroidSubscriptionScheduleGateway`](../../app/src/main/kotlin/features/subscription/runtime/AndroidSubscriptionScheduleGateway.kt#L32) turns that spec into a `PeriodicWorkRequest`, sets `NetworkType.CONNECTED`, inputs the group ID, sets `BackoffPolicy.EXPONENTIAL` with `spec.backoffMillis`, and calls `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.UPDATE, ...)`. It cancels by the same unique work name and stores scheduled IDs in preferences.

The worker source [`SubscriptionWorkerRunner`](../../app/src/main/kotlin/features/subscription/runtime/SubscriptionWorkerRunner.kt#L19) re-reads the current group and skips missing, disabled, or blank-URL groups with success. Otherwise it runs the single-group update and maps its classified failures through the common [`SubscriptionWorkerPolicy`](../../shared/app/src/commonMain/kotlin/app/skipi/app/subscription/SubscriptionWorkerPolicy.kt#L13): no failure is SUCCESS, any transient failure is RETRY, and failures with no transient member are FAILURE. [`SubscriptionWorker.doWork`](../../app/src/main/kotlin/features/subscription/runtime/SubscriptionWorker.kt#L19) maps those outcomes to `Result.success()`, `Result.retry()`, and `Result.failure()` respectively.

### WorkManager result semantics verified locally

The Gradle cache contains `androidx.work:work-runtime:2.11.2` as an AAR, but no source JAR. I inspected the cached AAR's compiled classes with `javap` rather than consulting a remote source. The evidence is the implementation of `PeriodicWorkRequest.Builder(Class, interval, TimeUnit)`, `WorkSpec.setPeriodic(long)`, `WorkSpec.Companion.calculateNextRunTime(...)`, and `WorkerWrapper.handleResult(...)` / `resetPeriodic()` in the cached `classes.jar`.

- The one-argument periodic builder calls `setPeriodic(interval)`, which sets its flex equal to the interval. For the default no-initial-delay request used here, WorkSpec's initial periodic calculation is due at enqueue time. Connectivity and OS scheduling can defer actual execution, so “immediate” means eligible to run at enqueue, not a wall-clock guarantee.
- `Result.retry()` follows `WorkerWrapper.reschedule(...)`; the next run uses the configured exponential backoff based on the prior attempt and caps the delay at 18,000,000 ms (5 hours). This request configures the 15-minute base.
- `Result.failure()` on a periodic work item takes the same `resetPeriodic()` path as success: WorkManager returns it to ENQUEUED, resets attempt count, and sets last-enqueue time to the completion clock. Thus a permanent worker failure does not terminate the periodic schedule; its next period begins after completion. SUCCESS follows that same reset path.

This bytecode check is specific to the locally resolved 2.11.2 AAR. It does not claim a local source-JAR inspection.

## Actual call paths and current Desktop gap

Android's app-level state observer maps persisted groups to shared targets in [`app/.../SubscriptionScheduler.kt`](../../app/src/main/kotlin/features/subscription/runtime/SubscriptionScheduler.kt#L8), then invokes the gateway above. WorkManager invokes `SubscriptionWorker`, which re-reads persisted app state, applies the common single-group refresh operation, and converts its result to a worker result. None of these steps depends on the currently visible screen.

Desktop's existing subscription timer in [`Main.kt`](../../desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt#L170) derives a due ID and stores it in `scheduledSubscriptionId`. [`DesktopProxyHome`](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt#L189) is the only consumer, while Main composes that screen only for the Proxy destination. A due schedule therefore remains pending on Settings or Configs. The selected row callback then invokes an inline single-refresh operation; on error, it clears UI progress but leaves the schedule source timestamp unchanged, allowing the planner's one-second minimum timer to retry the same failed request. The full proof and existing test inventory are recorded in [`s03-scheduling-source-audit.md`](s03-scheduling-source-audit.md).

The reusable Desktop timer is `runDesktopAutomaticRefreshLoop` in [`DesktopAutomaticRefreshLoop.kt`](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopAutomaticRefreshLoop.kt#L18). P03's `DesktopTrafficConfigAutoUpdater` and `DesktopResourceAutoUpdater` schedule different work types; S03 must not reuse their profile-specific state or duplicate P03.

## Proposed smallest API boundary

Add a subscription-only common state alongside the existing `SubscriptionWorkSpec` in `shared/core/features/subscription/runtime`:

```kotlin
data class SubscriptionPeriodicWorkState(
    val spec: SubscriptionWorkSpec,
    val periodAnchorMillis: Long,
    val completedPeriods: Long = 0L,
    val runAttemptCount: Int = 0,
    val retryAtMillis: Long? = null,
) {
    val nextRunAtMillis: Long
    fun withUpdatedSpec(spec: SubscriptionWorkSpec): SubscriptionPeriodicWorkState
    fun recordAttemptResult(retryable: Boolean, atMillis: Long): SubscriptionPeriodicWorkState
}
```

It is subscription-specific rather than broadening the profile work-state API. It stays in shared/core and takes `retryable` rather than depending on the higher `shared/app` worker outcome enum. A new state uses the observation/enqueue time as its anchor, making the first run due then. A non-retry result (both worker SUCCESS and permanent FAILURE) clears retry state and anchors the next interval to attempt completion. A retry increments the attempt count and sets its deadline from completion plus `workManagerExponentialBackoffDelayMillis(runAttemptCount, spec.backoffMillis)`, reusing the existing same-module helper. The new host maps only `SubscriptionWorkerOutcome.RETRY` to `retryable = true`.

Extract the existing `SubscriptionScheduler`'s `mapNotNull` spec construction into a public pure `SubscriptionRefreshTarget.subscriptionRefreshWorkSpecOrNull()` in the same shared-core file. Android `SubscriptionScheduler` must call this helper with no eligibility/backoff behavior change; Desktop maps targets through the same helper directly, like P03's profile host does with `profileRefreshWorkSpecOrNull()`. This avoids a fake in-memory scheduling gateway and keeps the source policy in one place.

Add `DesktopSubscriptionAutoUpdater` in `desktopApp` with four injected boundaries: latest shared subscription targets, the existing `isDesktopNetworkConnected` check, an app-lifetime single-group callback returning `SubscriptionWorkerOutcome`, and a clock. Its small host API is `observeTargets(targets)`, `runDue()`, and `nextWakeupAtMillis()`. The host keeps one state per eligible group using `subscriptionRefreshWorkSpecOrNull()`, reconciles latest targets before due work, leaves due work unchanged while disconnected, and cancels in-flight work when that group ceases to be eligible. A disabled or removed group therefore disappears from the host's desired state; re-enabling it creates a fresh immediate schedule, matching Android re-enqueue behavior.

Wire this host next to Main's existing app-lifetime `runDesktopAutomaticRefreshLoop`, not through `DesktopProxyHome`. Extract the current inline one-group operation into one remembered suspend callback callable by manual refresh, the new periodic host, and the install coordinator. Manual, automatic, and batch operations must share a host serial-admission mechanism such as one `Mutex`; a busy UI flag must never be converted into a false SUCCESS. After admission, the callback re-reads the latest record and rechecks Android eligibility before starting a fetch. It should return its worker outcome while retaining the existing result/error presentation for manual UI. The installation slice should call that same operation after persistence; no second fetch or commit path is needed. The concrete wrapper that carries both UI detail and `SubscriptionWorkerOutcome` belongs at the Desktop boundary and should be approved with the root before integration tests lock its public shape.

The shared classifier now approved by root supplies the Android retry classification. The Desktop JDK fetcher exposes `HttpTimeoutException`, which is not an Android `SocketTimeoutException`; the Desktop adapter must translate the same timeout fact to the shared classifier through its own tests-first step. Do not silently turn this network timeout into a permanent FAILURE.

## Small tests-first slice

Before production edits, add:

1. `SubscriptionPeriodicWorkStateTest` in `shared/core/commonTest`: a new schedule is due at its enqueue anchor; success and permanent failure both schedule at completion plus interval and clear retries; transient retries use the 15-minute base and 30-minute second delay (with the existing helper's 5-hour cap contract).
2. `DesktopSubscriptionAutoUpdaterTest` in `desktopApp/src/test`: a due eligible target dispatches from the app host without a Proxy screen; disconnected work stays due and dispatches after connectivity returns; disabling/removing a target cancels its in-flight callback and its late completion cannot rewrite a newly reconciled schedule; permanent failure waits until the next period rather than spinning.

Reuse existing proofs rather than mirror them: `SubscriptionRefreshSchedulingTest` already asserts the Android-equivalent `CONNECTED`/UPDATE/15-minute spec and cancellation of stale tracked IDs; `SubscriptionWorkerRunnerTest` covers missing/disabled/blank targets and transient/permanent result classification. The new host tests should assert outcomes and schedule deadlines, not a method call script. No tests should invoke actual WorkManager, schedule a real OS worker, contact a network endpoint, or exercise profile auto-refresh.

The tests can run RED with the proposed classes absent. Root authorized the test-only phase and approved the common state/host API. The later Main wiring must additionally test that manual/automatic/batch operations share serial admission, that automatic work waits instead of reporting a busy shortcut as SUCCESS, that eligibility is reread after waiting, and that Desktop timeout exceptions map to the Android transient contract. No production implementation should begin until root runs and reviews this focused test slice.
