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

ROLE: Shared Home UI implementer.
FINAL REPORT: /home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/home-polish-20260930/reports/01-shared-home-ui.md

WRITE OWNERSHIP
- shared/ui/src/commonMain/kotlin/app/skipi/ui/home/**
- shared/ui/src/commonTest/kotlin/app/skipi/ui/home/**
- shared/ui/src/commonMain/composeResources/values*/home.xml (only Home strings needed for your changes).
Do not edit shared/app contracts/store, platform adapters, Android resources, build scripts, or VPN/native files. Read those files as needed.

READ FIRST
ProxyHomeScreen.kt, SkipiProxyHomeFloatingToolbar.kt, ProxyHomeActionAvailability.kt, ProxyHomeLayout.kt, home widgets used by HomeSubscriptionCard and compact/expanded hero, existing Home UI tests, and shared/app ProxyHomeStore.kt to understand the current contract. Locate the locally available Android 0.4.1 reference/report if present; do not fetch it via Git.

IMPLEMENT
A. REMOVE THE SUBSCRIPTION ENABLE/DISABLE TOGGLE. This is a direct new user requirement: the control is useless and currently broken. Remove it from common Home presentation and all common Home menus/gestures, including HomeSubscriptionCard's canToggleEnabled/onToggleEnabled wiring. Both Android and Desktop must lose this switch. Remove dead UI-only callback plumbing if possible within owned files. Preserve stored enabled fields and backup/Room/JSON compatibility. Do not force-enable old subscriptions, delete contract actions, or repurpose the switch. Subscription updating, import, group edit/delete, server selection and ping must remain.
B. Compact hero on a wide/Expanded layout currently provides no tunnel power action: wide branch renders hero/list/details while floating toolbar is restricted to the narrow branch, and compact hero has no toggle callback. Ensure an obvious, accessible connect/disconnect control exists in EVERY supported width and hero style. Derive label/status/enabled/busy from real UiState/capabilities; dispatch once; do not make text briefly say no server when busy. Do not add multiple redundant controls to the same layout unnecessarily.
C. Grid per-item rendering currently calls pageServers.indexOfFirst, producing repeated full-list scans. Use indexed lazy items with stable server-id keys and direct index. Preserve ordering, selection and reorder behavior, item animations, and scroll/pager state.
D. Review motion in touched Home controls: retain Android-reference transitions for group expand/collapse, pager switching, card/hero state changes, selection and operation feedback where already represented by state. Add minimal deliberate Compose transitions to abrupt touched elements, respecting accessible semantics; avoid broad visual redesign or unrelated animation frameworks.
E. Correct common menu/title wording for local/manual groups versus remote subscriptions where the existing state exposes group kind. A local group must not be called a subscription. Hide or disable an empty group's latency operation appropriately; retain operations on nonempty groups. Android-specific dialog text is handled by Agent 02.

ACCEPTANCE
No disable-subscription switch/command in common Home. Power action visible and usable on narrow/medium/wide layouts for compact and expanded style, disabled correctly during real busy operations. No per-item indexOfFirst. Stable lazy keys preserved. Common UI tests cover action availability or presentation logic where meaningful; no tests that merely search implementation text. Run available shared UI tests under the Gradle lock, or document sandbox execution limitation. Provide a concise list of widths/styles/themes requiring visual follow-up if you cannot render them.
