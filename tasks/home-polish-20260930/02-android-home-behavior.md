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

ROLE: Android Home behavior and latency operations implementer.
FINAL REPORT: /home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/home-polish-20260930/reports/02-android-home-behavior.md

WRITE OWNERSHIP
- app/src/main/kotlin/features/proxy/server/list/ProxyServerListPage.kt
- ProxyHomeAndroidEffectBridge.kt and related Android effect tests
- Android latency-operation helpers/tests needed to eliminate duplicated ping-mode selection
- Android subscription/manual-group dialogs and their directly related localized resources/tests, only for group-kind wording/behavior.
EXPLICIT READ ONLY: ProxyHomeAndroidInput.kt and ProxyHomeAndroidInputTest.kt (Agent 03 owns them); all shared modules; native/VPN code; SettingsAppearancePage; build scripts. No phone access: Agent 04 owns device use.

READ FIRST
ProxyServerListPage.kt action dispatch cases, legacy ProxyServerListTopBar.kt ping mode selection, ProxyHomeAndroidEffectBridge.kt and tests, existing subscriptionPingMode configuration and latency templates, subscription/manual-group dialog implementation.

IMPLEMENT
A. TestVisibleServers and TestGroup currently hardcode TcpConnect, unlike the legacy toolbar which chooses RealConnection or TcpConnect using subscriptionPingMode. Restore the actual configured behavior for both paths, including corresponding ping templates/arguments, operation busy state and cancellation. Prefer a small reusable selection helper over separate inconsistent branches. Keep selection and reconnect actions dispatched exactly once and input state authoritative.
B. Add focused regression coverage proving both configured modes choose the correct operation, group tests use only the group's members, visible-list tests obey the intended visible subset, cancel dispatch reaches the correct running operation, and unsupported/busy operations are not started twice. Test the actual helper/effect path rather than source text. Inspect existing tests and avoid duplicating them.
C. Local/manual group dialogs currently say Add Subscription / Edit Subscription Group. Where a dialog knows it is editing/creating a manual group, use group wording rather than subscription wording. Preserve subscription URL validation and actual remote subscription creation. Do not add a new domain contract just to change a label. Common Home menus are owned by Agent 01.
D. Inspect touched dispatch/effect paths for server selection while connected and reconnect behavior. Fix only proved small regressions inside your ownership; report cross-module problems precisely without editing those modules.

ACCEPTANCE
Both Home ping entry points respect the saved mode, cancel works, actions are single dispatch, manual group wording is correct in Android dialogs. Data/persistence and old appearance options remain intact. Run targeted Android tests with Java 21 and native build/sync exclusions under the lock when execution is available. Report any tests that cannot run. Do not connect the VPN or alter a real subscription to validate this task.
