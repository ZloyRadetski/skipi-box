# Proxy catalogue consumers — Step 3, 2026-10-06

## Authorized scope

Continue the agreed migration by finding remaining Android proxy catalogue consumers which bypass shared repositories/actions, reusing or moving portable rules into shared, and replacing the legacy path only after its successor is tested. Android and Desktop JVM only; no iOS or forced feature parity. Delegate research, tests and production edits to GPT-6 Luna max agents. Root orchestrates, reviews, runs verification and commits only source/tests.

## Baseline

Commits e8ba986 and 0f4c0af are already verified with 1126 passing Android/Desktop JVM test executions and Android debug APK assembly. The checkout has no new source changes, so no duplicate baseline run is needed. Preserve the pre-existing HEV native submodule revision and all user-owned data. Planning artifacts remain untracked.

## Ordered tasks

1. Read-only Android consumer inventory; identify direct catalogue mutations and runtime/storage responsibilities.
2. Read-only canonical shared-rule inventory and test-seam research, performed independently in parallel.
3. Choose the smallest complete catalogue slice and finalize behavior-preservation criteria. Ask about real product decisions only if needed.
4. Write meaningful characterization/regression tests against existing production code. Confirm genuine RED for any changed behavior; passing preservation tests are valid for pure refactor paths.
5. Define shared contracts before delegating non-overlapping implementation files.
6. Implement the chosen slice, run focused GREEN, independently review, and run the appropriate integrated JVM checks and APK build.
7. Commit only code/tests and report the exact slice completed and remaining migration work.

## Selected first slice: single and bulk deletion

Research independently confirmed that shared/store single deletion, Android invalid/all/duplicate deletion, and Desktop Home deletion bypass the canonical reference-cleanup algorithm. The older Android AppState deletion wrapper has no callers. Import already uses a shared helper; import allocator overflow is a distinct follow-up.

Shared API: keep the existing `RemoveProxyServer` action and add `RemoveProxyServers(serverIds: Set<Int>)`. Both update the catalogue through one shared overload of `deleteProxyServerRecords(catalog, deletedServerIds)`. Lift the existing StrategyGroup/ChainProxy pruning into one private pure helper reused by the legacy list operation and the new catalogue overload. Preserve app-model fields with `record.copy(server = transformedServer)`. Do not duplicate pruning or rewrite both record models. The Android default group constant lives in the app module, so projecting through a fabricated group ID is unnecessary and would weaken the boundary.

Consumer wiring: Android TopBar invalid/all/duplicate commits use the set-valued shared action. Desktop Main single deletion uses the same shared store. Host tunnel stop/reconnect, messages and runtime fields remain host-owned and occur according to the existing success/failure gates.

## Acceptance criteria

- The selected consumer operation uses one shared portable rule and the shared repository/action path instead of a duplicate legacy mutation.
- Existing server identity/order, selection, group association, latency and strategy/chain references remain valid for the selected operation; host runtime effects stay host-owned.
- Desktop Home deletion uses the same shared action and rule, while raw opaque storage preservation and nullable selection remain adapter concerns.
- Single and multi-ID deletion remove dangling StrategyGroup/ChainProxy references and repair the selected strategy member; one catalogue update preserves record order, associations, flags and next-ID high water. The shared rule does not stop or mutate runtime repositories.
- A Desktop save failure leaves its persisted/host/observable catalogue unchanged and does not trigger successful-delete UI effects.

## Out of scope

Subscriptions/configuration/routing consumer migration beyond dependencies of the selected proxy operation, network automation, UI redesign, native engine changes, Android storage rewrite, new test frameworks and global cross-file transactions.

## Test evidence before implementation

- SharedApplicationStore current API: 9 focused tests, 1 expected behavioral failure (strategy retains [7,8] after deleting 7).
- Real Desktop adapter/current action: 3 tests, 1 expected behavioral failure (strategy retains [10,11] after deleting 10); last-row/null selection and save-failure controls pass.
- Android mapping: 9 focused tests pass, including the new deletion metadata/runtime characterization.
- Two shared-store bulk tests and one Desktop bulk test were written before production; API compilation RED confirms `RemoveProxyServers` is not defined. This is new-contract evidence, separate from runtime defect evidence above.
- An initial Desktop test compile error from an unavailable coroutine-test library was repaired using the existing runBlocking stack without dependency changes; it does not count as behavioral RED.

## Verification after implementation

- Focused GREEN: SharedApplicationStore 11, collection operations 4, real Desktop deletion action 4, Android mapping 9; all 28 pass.
- Integrated GREEN: shared core 215 and shared app 72 on each of Desktop JVM and Android host JVM, Desktop 186, Android app 379. Total 1139 test executions with zero failures, errors or skips.
- Android debug APK assembled using the existing prebuilt HEV native artifact. Native submodule HEAD remains e802f02bae0fc55cbf681466a60e89c2e6773401.
- Real user data, running applications, device installs, and visual layouts were not changed or exercised by this slice.

## Review follow-up

Independent review approved the shared transform, Desktop atomic publication and host deletion handoffs. It found an Android exceptional path: successful service stop followed by a failed/rejected catalog action could leave proxyRunning true. Repair this before the host-consumer commit, with a regression exercising the actual orchestration before production edits. Shared transform and integration tests were committed separately as a7785ba; no planning artifacts were staged.

The host-used stop/delete coordinator was extracted for direct testing. An initial Android test annotation import error was fixed without changing dependencies and was not counted as behavioral RED. The subsequent behavioral run confirmed missing runtime synchronization on both Failed and Rejected deletion outcomes and late synchronization on Completed. The MissingServer preservation control also caught an unwanted fabricated Success introduced during extraction; the final code removes it and retains the host's Completed-time flag reset.

Final repair applies the real stop result's runtime status and returned local proxy port before dispatch. All five coordinator tests pass, as do the Android mapping checks. The complete final suite and APK build pass: 1144 JVM test executions (215 core and 72 shared app on each target, Desktop 186, Android app 384), no failures/errors/skips. No real UI session or phone was exercised.

Independent final review approved the repair, including cancellation propagation and service-operation cleanup. The pre-existing gate covers the stop-required branch but allows direct deletion during start/restart. Global runtime/catalog serialization is a follow-up audit, outside this slice's behavior-preservation contract.

Completed commits: a7785ba (shared deletion/reference pruning and regression tests), 0284fc2 (host handoffs, stop-state synchronization and Android tests). Only source/test files were committed. Worktree remainder consists solely of the preserved native submodule revision and untracked task artifacts. Remaining next catalog slice: import/ID allocation, including the separately observed classic allocator overflow; subscriptions/config/routing migration remains future work.
