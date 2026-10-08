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

ROLE: Common Home state ordering and performance implementer.
FINAL REPORT: /home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/home-polish-20260930/reports/03-home-store-ordering.md

WRITE OWNERSHIP
- shared/app/src/commonMain/kotlin/app/skipi/app/home/ProxyHomeStore.kt
- shared/app/src/commonTest/kotlin/app/skipi/app/home/ProxyHomeStoreTest.kt and focused new tests in that package
- app/src/main/kotlin/features/proxy/server/list/ProxyHomeAndroidInput.kt
- app/src/test/kotlin/features/proxy/server/list/ProxyHomeAndroidInputTest.kt
- desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt, only input/mapping/order logic
- focused Desktop input/mapping tests as needed.
READ ONLY: shared UI, Android ProxyServerListPage/effect bridge, Desktop settings/groups persistence, build scripts, VPN. Any additive contract field must have a backward-compatible default to avoid blocking peers.

READ FIRST
Store contract comments and sortMode, group pages/filters, Android mapper and flag-stripping title helper, Android input sort tests, Desktop mapper and sort tests. Keep selected-group-membership regression intact.

IMPLEMENT
A. Preserve name-sort semantics. Android sorts raw remarks, but shared display titles strip a leading country flag, after which the store sorts again and changes order (e.g. raw Zulu versus flag-prefixed Alpha). Determine the intended existing order from mapper contracts/tests. Prefer honoring already sorted authoritative host input, or carry an explicit canonical sorting key if the store must own sorting on both platforms. Do not use stripped display text as a different accidental sort key. Android/Desktop must consistently obey user sort mode without double sorting. Add regression coverage for flag-prefixed titles, equal names/stable order, latency/custom order where supported.
B. Improve per-snapshot page construction. It currently scans/sorts the full server list for every group, including latency updates, costing O(groups * servers) work and repeated sorts. Build reusable indices/group memberships once per snapshot and perform global sorting/filtering once where semantics allow; then construct pages with direct id lookup/buckets. Preserve groups with explicit overlapping serverIds, All, legacy groupId memberships, search, selected server outside current filter, hidden/deleted groups, empty groups, per-group collapse, missing selected group fallback and stable ordering. Do not introduce a fragile persistent cache or timing-dependent test.
C. Prove behavior through focused Store tests. Cover input updates, disappearing groups, explicit membership overriding stale groupId, search and active page behavior, error/busy state, and exactly-once platform effects for actions if existing coverage has gaps. No fake tunnel phase transitions.
D. Keep subscription enabled state and ToggleSubscriptionEnabled contract compatible; UI removal is Agent 01's task. Do not silently change stored enabled values or platform data formats.

ACCEPTANCE
Canonical sorting is identical before/after UI mapping; flag-stripped labels do not reorder data. No full global sort per group. Membership/selection/search regressions pass, existing public constructor callers still compile. Run shared app tests and focused Android/Desktop mapper tests with proper JVM versions under the lock if available. Explain complexity improvement and any unavoidable remaining traversal using actual code behavior, not invented benchmarks.
