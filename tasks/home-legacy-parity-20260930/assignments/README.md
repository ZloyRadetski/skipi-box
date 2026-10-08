# Home parity round: shared instructions

User authorized implementation following the v0.4.1 Home audit. The orchestrator delegates seven separate tasks to gpt-6-luna with max reasoning and reviews only when every task finishes. Three agents can work simultaneously; remaining tasks are queued.

Working directory: `/home/vqsego/TorvaldsVPN/Skipi/skipi-box`.
Exact visual/behavior reference: local annotated tag `v0.4.1`, commit `0326b6a7b7355f8bd7e33c2c8c7f5c6748f9e0c0`.

- Read old files with `git show v0.4.1:path`. Verify current source first; the audit's line numbers can drift.
- Preserve the substantial existing uncommitted work. Do not overwrite working files with entire legacy files.
- No commits, branches, stash, reset, checkout, fetch, rebase, worktrees, PRs or other Git mutations. Read-only Git is allowed.
- Portable UI and presentation logic belong in shared modules. Platform adapters supply real facts/actions. Keep Go core, VPN/HEV behavior and persistence formats outside this UI round.
- Read applicable AGENTS.md and relevant installed skills. User scope and Git restrictions take precedence over skill commit advice.
- Use narrow patches against the latest file contents. Several agents have ownership of separate named functions in ProxyHomeScreen.kt; never reformat or rewrite that whole file.
- Preserve available actions, cancellation, real connection/latency state, localized labels, stable lazy-list keys and accessibility. Do not revive the removed subscription-disable toggle.
- No phone control, APK installation, app launching or screenshots in individual tasks. Final integration/visual review belongs to the orchestrator after all tasks finish.
- Do not send progress chatter to other agents. Message the parent only for a real blocking contract issue. Complete your own scope and report dependencies precisely.
- Run meaningful targeted verification, using configured project Gradle/JDK. Serialize all Gradle invocations with `flock /tmp/skipi-home-legacy-parity-gradle.lock ./gradlew ...`. Do not add source-string tests, tests mirroring drawing constants, new snapshot infrastructure or unrelated tests.
- Do not fix another agent's compilation issue by editing their owned files. Report exact diagnostics when a concurrent dependency is temporarily absent.
- Write your report to the task's specified path: reference compared, exact changed files/API, final behavior, validation command/results, unresolved limitations. Send a short completion message.

Tasks: 01 hourglass; 02 dropdown groups; 03 pinned-panel/visibility parity; 04 bottom insets; 05 compact subscription header; 06 subscription/server rows; 07 labels and real subscription state.

Shared contracts for this round:

- Static hourglass helper: `SkipiProxyHeroStaticHourglassIcon(modifier: Modifier = Modifier, color: Color, size: Dp = 20.dp)` in the common Home package. Task 01 implements it; 05 and 06 consume it. Keep existing animated hourglass behavior.
- Subscription busy property: `pinging: Boolean = false` in `ProxySubscriptionSummary` (task 07) and `SkipiSubscriptionSummaryState` (task 05); task 07 connects real platform facts to the UI mapping.
- Task 05 should expose a backward-compatible way to render a subscription header embedded in a continuous provider panel, preferably `embeddedInPanel: Boolean = false`. Task 06 consumes the final API after task 05 finishes.
