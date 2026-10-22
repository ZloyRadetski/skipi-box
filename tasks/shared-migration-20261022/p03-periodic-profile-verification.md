# P03 profile periodic scheduling

Date: 22 October 2026. Common WorkSpec/timing extraction is verified. Desktop profile job ownership, wakeup loop and Main integration are the next tests-first gate; P03 remains open.

## Source contract

The Android gateway uses WorkManager **2.11.2**, unique profile work, connected-network constraint, `UPDATE`, and a 15-minute exponential-backoff base. Profile eligibility is auto-update enabled, unlocked, a nonblank URL and an enabled parsed interval. Resource eligibility is independent. The existing [R02 source/bytecode comparison](../shared-migration-20261020/r02-verification.md#refresh-source-facts-and-verified-boundary) records the enqueue/completion anchors, retry attempt formula and five-hour cap. The [UPDATE policy](https://developer.android.com/reference/androidx/work/ExistingPeriodicWorkPolicy) preserves the running iteration; [periodic work](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest.Builder) is initially eligible at enqueue without promising exact OS execution time.

Shared timing models the earliest due time: a new entry is due at its enqueue anchor; completion resets retry state and anchors the next interval; retry anchors at the attempt's reschedule time and uses 15, 30, ... minutes capped at five hours. UPDATE retains anchors, period/attempt counts and an existing retry deadline; its new interval determines the next periodic deadline from the retained anchor. Removing/re-enabling work creates a new immediately eligible entry. Existing Kotlin Long addition/overflow and saturated counters are retained.

## Tests before production and GREEN

- Source baseline: `outputs/p03-periodic-core-source-baseline-20261022.log`, **5 seconds**, existing `ResourceGeoAutoRefreshSchedulingTest` **7** and `TrafficConfigRefreshSchedulingTest` **2** on each platform, **18/18**, zero failures/errors/skips. Preserved XML/counts: `outputs/p03-periodic-core-source-baseline-reports-20261022/`, timestamps `2026-10-22T11:56:32.095Z`–`11:56:35.071Z`.
- Seven new common cases were written/reviewed before production: profile WorkSpec fields and eligibility without a positive-ID filter; immediate due/success anchoring; capped retries; UPDATE with a pending retry; direct Long deadline addition; saturated counters.
- API RED: `outputs/p03-periodic-core-api-red-20261022.log`, **1 second**, missing `profileRefreshWorkSpecOrNull` and `TrafficConfigPeriodicWorkState` at `:shared:core:compileTestKotlinDesktop`, with dependent inference/`copy` cascades. No behavioral execution is claimed for the compile failure.
- Implementation exposes the source's profile WorkSpec builder and makes `TrafficConfigScheduler` use it. The pure `TrafficConfigPeriodicWorkState` owns only one work item's timing. The geo facade retains its resource eligibility/new/removed-ID reconciliation and delegates timing to that record. The previous geo-specific timing record/math is removed, without a wrapper or duplicate algorithm. One existing geo boundary-test constructor was mechanically adapted to the same 20ms test interval/15-minute backoff; its inputs and assertions are unchanged.
- GREEN: `outputs/p03-periodic-core-green-20261022.log`, **5 seconds**, `:shared:core:allTests`: Desktop **361**, Android **305**, **666/666**, failures/errors/skips zero. Preserved XML/counts: `outputs/p03-periodic-core-reports-20261022/`, timestamps `2026-10-22T12:02:47.379Z`–`12:02:50.560Z`.
- Desktop consumer regression also passed in `outputs/p03-profile-auto-refresh-desktop-transport-green-20261022.log`, **11 seconds**, **34/34**, including existing geo updater **10** and its wakeup loop **1**. This is the same run documented in the [execution report](p03-profile-auto-refresh-execution-verification.md), not a second set of test executions.

Root and another Luna agent independently reviewed timing/spec parity and geo isolation. No required behavioral issue was found; a KDoc sentence was clarified to distinguish an interval update from a later completion. That comment-only change does not alter the tested algorithm.

## Remaining host gate and limits

Timers, connected-interface probing and cancellation/job ownership stay in Desktop. Profile and geo jobs use separate eligibility and execution paths. No content/revision/CAS fence is introduced. A cancelled job that receives the shared executor's caught-cancellation Retry must not schedule an ordinary retry or overwrite the schedule of a re-enabled entry.

The Desktop host runs only while the process is alive and uses the existing interface-level connected check. It does not provide Android WorkManager's persistent OS scheduler or guarantee internet reachability. No APK install, user application restart or real user data operation is part of this checkpoint. APK/full-project/native/UI checks are not claimed by the common-core gate above.
