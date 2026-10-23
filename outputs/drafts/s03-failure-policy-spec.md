# S03 scheduled refresh failure policy

## Android source contract

The current Android source of truth is [`SubscriptionFailureClassifier.kt`](../../app/src/main/kotlin/features/subscription/SubscriptionFailureClassifier.kt#L27), where `isTransientSubscriptionFailure` walks the complete `Throwable.cause` chain. It marks these failures transient:

- `SubscriptionHttpException` with status 408, 429, or 500–599.
- `ConnectException`, `NoRouteToHostException`, `SocketException`, `SocketTimeoutException`, or `UnknownHostException`.

Every other cause is ignored. In particular, a generic `IOException` and permanent HTTP statuses such as 400, 407, 409, 499, or 600 are not transient. The Android scheduled worker consumes that classifier for each refresh failure in [`SubscriptionWorkerRunner.kt`](../../app/src/main/kotlin/features/subscription/runtime/SubscriptionWorkerRunner.kt#L31) before mapping mixed failures to the shared worker outcome.

`SubscriptionHttpException` is itself an `IOException`; the status check is the typed exception check, not a general I/O retry rule. The shared JVM policy accepts a host-provided status extractor so Android and Desktop can keep their respective HTTP exception types while sharing the cause traversal and status/network rules.

## Tests added before implementation

- `app/src/test/kotlin/features/subscription/SubscriptionFailureClassifierTest.kt` freezes the Android baseline: retryable/permanent status boundaries, wrapped HTTP and connection failures, the exact listed connection classes, generic `IOException`, and permanent payload errors.
- `shared/core/src/desktopTest/kotlin/features/subscription/SubscriptionRefreshFailurePolicyTest.kt` specifies the proposed public `features.subscription.isTransientSubscriptionRefreshFailure(error, httpStatusCode)` contract with a test-local host exception and resolver. It mirrors the Android cases without depending on Android exception types.

The expected shared JVM API is intentionally absent at this test-first stage. No production implementation was changed and no Gradle test was run in this slice.
