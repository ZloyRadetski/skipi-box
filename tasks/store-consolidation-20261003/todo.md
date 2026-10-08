# Step 1 work tracker

- [x] Confirm target scope: Android and Desktop JVM; no iOS and no full feature parity.
- [x] Read repository instructions and identify the existing store/runtime test coverage.
- [x] Run and record pre-change Android, shared-store, and Desktop JVM baselines.
- [x] Confirm a behavior regression for concurrent shared-store action IDs: the added test failed against the old allocator.
- [x] Consolidate Android store ownership while retaining the existing Android persistence adapters.
- [x] Ensure the Android shared store receives the host-owned runtime repository and an application-lifetime scope.
- [x] Keep concurrent action IDs unique and drain in-flight actions under concurrent dispatch; verify runtime-repository cancellation propagation.
- [x] Add behavior-level runtime tests. Confirm singleton identity from the final source graph; no separate identity test seam was added.
- [x] Independently review the implementation and tests for lifetime, cancellation, wiring, clarity, and regressions; fix the compile issue found during integration verification.
- [x] Run final integrated checks after production and test files stabilized; record that native/APK packaging was excluded because no NDK is installed.
