You are one of FIVE independent Antigravity implementation agents working simultaneously on SKIPI.
Model requested: gemini-3.8-flash-high. Workspace: /home/vqsego/TorvaldsVPN/Skipi; Gradle project: /home/vqsego/TorvaldsVPN/Skipi/skipi-box; Go core: ../skipi-core.
Implement your assigned changes. The human explicitly requested delegation WITHOUT live supervision. Do not wait for the orchestrator, message other agents, create additional agents, or spend time writing intermediate updates. Work independently and write your final report to the path below.

MANDATORY CONSTRAINTS
1. Read applicable AGENTS.md and relevant local skills if present. Preserve existing uncommitted edits: this workspace already contains substantial implementation. Read the current files before editing.
2. You have exclusive WRITE ownership of the files/areas specified in your task. Other areas are READ ONLY. If a necessary change lies outside your area, describe it precisely in your report; do not edit another agent's files. Do not reformat entire files.
3. Run NO Git commands: no commits, branches, stash, checkout, fetch, pull, reset, worktree, submodule update, PR, or any build task that runs these operations. Native source is already present. Exclude :hevtun:syncHevSocks5TunnelVersion from builds if that task exists; inspect task dependencies before running native build tasks.
4. Keep portable Home state, presentation, and interactions shared; retain only OS-specific services, persistence, permissions, device integration in platform modules. Android SKIPI 0.4.1 Home is the visual reference. Do not redesign Home as a Desktop-first dashboard. Do not migrate unrelated screens or change data/backup/Room/JSON formats.
5. Do not clear data, delete user servers, reset settings, uninstall apps, change accounts, modify system-wide configuration, expose subscription credentials, or publish/send anything externally. Do not put complete subscription URLs, tokens, server passwords, or unrelated user windows into reports/screenshots. Use offline fixtures for tests.
6. A reported diagnosis is not a verified fix. Reproduce behavior or use focused regression tests for logic changes. Record EXACT commands and outcomes. Never claim a test/build/device check passed unless actually run successfully. If the CLI sandbox command tool fails (previously: recvmsg connection reset by peer), finish source changes and explicitly report execution unverified; do not bypass sandbox settings or invent results.
7. Avoid competing Gradle executions. Run commands from skipi-box with ANDROID_HOME=/home/vqsego/Android/Sdk and use flock /tmp/skipi-home-polish-gradle.lock around each Gradle invocation, with bounded waiting. Android unit tests need Java 21; installed JDK: /home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2. The project's daemon/compiler can use Java 26. Do not force ALL Desktop JVM tests to Java 21 (their bytecode may be version 70). Most Android unit checks can exclude :hevtun:buildHevTun and the submodule sync task. Do not introduce automatic screenshot infrastructure.
8. Finish with a report containing: summary; changed files and rationale; proved original issues; regression coverage; verification commands/results; remaining issues and limitations. Explain unverified hypotheses separately. No percentage guesses.

CONTEXT
Shared Home and real platform state adapters already exist. Shared/common tests, Desktop tests, and Android unit tests were previously green, but device testing showed HEV failure and UI/behavior gaps. A complete debug APK was previously installed with explicit user permission to update the same debug-signed package. Cached native HEV libraries were reused because no Android NDK was found. The phone may be disconnected now. Existing data must remain compatible.
IMPORTANT: a recently fixed selected-group regression requires actual group membership before trusting a stale selected server groupId. Preserve this behavior and its test. Host tunnel state is authoritative; UI must not simulate successful connection. Each action must dispatch to its platform effect exactly once.

ROLE: Android HEV runtime diagnosis and corrective implementation.
FINAL REPORT: /home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/home-polish-20260930/reports/04-hevtun-runtime.md

WRITE OWNERSHIP
- app/src/main/kotlin/engine/vpn/hevtun/**
- app/src/main/kotlin/engine/hevtun/** if present
- engine/vpn SkipiVpnService.kt / VpnXrayConfig.kt only narrowly necessary HEV integration changes
- app/src/test/kotlin/engine/vpn/hevtun/** and HevTunDefaultsTest.kt
- hevtun/build.gradle.kts
- buildSrc/src/main/kotlin/BuildHevTunTask.kt
- Existing native HEV sources ONLY when a proved defect requires changes; never mutate Git metadata or run Git.
READ ONLY: Home UI/store/adapters, other build scripts, Go core. You alone may use ADB/device; the other agents are told not to access it.

READ FIRST
HevTunNative.kt, HevTunRuntime.kt, VPN startup/stop/service error propagation and config generation, HEV packaging/build task, native JNI boundary and existing tests/reports. Read actual observed logs before deciding root cause.

USER PROBLEM
VPN fails when HevTun is enabled, and works when disabled. Fixing this by turning HEV off by default is unacceptable. The earlier complete APK reused cached HEV libraries because no NDK was found. A phone reported a 16KB-page compatibility warning for native libraries, but local APK zip alignment check passed and HEV JNI ELF LOAD alignment was 0x4000. Therefore 16KB incompatibility is only a hypothesis; prove which library/runtime step fails.

WORK
1. Check whether ADB is available and the authorized device is connected. Do not wait for human input if disconnected; continue local diagnosis/tests. If connected, inspect app-specific sanitized logcat and device/app/native ABI versions; avoid dumping credentials. Use existing configured test servers without copying their subscription token into files or reports.
2. Reproduce HEV-on failure and distinguish service/permission, loading JNI/CLI dependency, ABI/API/symbol mismatch, native configuration, TUN file descriptor ownership/lifetime, startup readiness, process exit, threading and shutdown issues. Capture the exact actionable error and failing boundary. Compare HEV-off behavior only if safe and useful. Record/restore settings changed for the check and stop any tunnel you started at the end; do not change system-wide host proxy settings.
3. Add a narrow regression test where feasible, implement the actual fix and improve diagnostic propagation if the existing code hides the native error. Preserve supported Android ABI/API and data/config compatibility. Do not mask failure by reporting connected early or falling back without making it explicit.
4. Native build: locate already installed NDK/toolchain before assuming availability. Do not run submodule sync/fetch/checkout. If a required toolchain is absent, document the precise blocker; do not install system packages or pretend cached native libs verify a changed native implementation. Avoid changing versions unless evidence requires it.
5. A same-package, same-debug-signature APK update preserving data was explicitly approved previously. Verify identity/signature before any update; do not uninstall, clear data, install a conflicting signer, or replace a release package. Build/install only when necessary and safe. Your job does not require phone UI screenshots if no reliable tool exists.

ACCEPTANCE
A proved HEV root cause with evidence, tested corrective change if achievable, actionable failure logs, correct cleanup/FD lifecycle, no default-off workaround. If device/toolchain/sandbox unavailable, report exactly what was and was not verified and the minimal next step. No guessed success claims.
