# Android single-server and subscription-group deletion contract

Status: source-history/spec draft only. No test or production code has been added for these paths. The bulk helper draft is deliberately separate.

## Source checked

The behavior below is from `HEAD` (`d8b3e0a96c48c3f4eebd8415293377c4a36381e0`), `app/src/main/kotlin/features/proxy/server/list/ProxyServerListPage.kt`:

- Single-server deletion: `deleteProxyServer` at lines 483–529; DeleteServer dispatch at 861–866; confirmation callback at 1151–1160.
- Subscription-group deletion: `deleteSubscriptionGroup` at lines 539–592; DeleteGroup dispatch at 929–946; confirmation callback at 1162–1180.
- `runProxyServiceOperation` at lines 220–230 uses the app scope and always clears the in-progress flag in `finally`.
- Bulk deletion has a separate coordinator in `ProxyServerListDeleteCoordinator.kt`; its ordering must not be generalized to these operations.

## Contracts future behavior tests must freeze

**Single server**

- If a service operation is already in progress, the delete function returns without stopping or deleting.
- If the target is not selected, or the proxy is not running, Android does not call stop. It first checks the row still exists, dispatches `RemoveProxyServer` with the non-awaited store `dispatch`, then schedules the success tip.
- If the target is selected and running, stop is called. On `Success`, Android rechecks that the row still exists, dispatches `RemoveProxyServer` (fire-and-forget), then applies the successful stop state, then shows the deleted tip if the row existed. Do not require the remove action to finish before the runtime-state update.
- On `MissingServer`, no delete or stop-state update occurs; Android shows the select-server tip. On `Failed`, no delete occurs; Android sets `proxyRunning = false` and reports the stop error with the service-stopped message.
- A missing target at the post-stop existence check produces no dispatch, stop-state update, or deleted tip.

**Subscription group**

- Built-in groups and in-progress service operations are ignored.
- Android stops only when the proxy is running and the currently selected server belongs to the group being deleted. Otherwise it does not stop and launches group removal in the app scope.
- Group removal rechecks that the group still exists and is not built in, then awaits `subscriptionEditorController.remove(group.id)`. Cancellation is rethrown; another exception is reported and prevents later stop-state/success feedback.
- On a successful stop, Android awaits the group-removal controller first; only after it succeeds does Android apply the stop state and report deletion. This is the opposite order from single-server deletion.
- On `MissingServer`, Android does not remove the group or apply stop state; it shows the select-server tip. On `Failed`, it does not remove the group, sets `proxyRunning = false`, and reports the stop error.
- The caller moves the Home selection to the default group immediately after starting deletion of the selected group, including the confirmation callback. Preserve that existing caller timing when tests cover Home selection; do not infer that selection fallback is conditional on asynchronous removal success.

## Test seams needed before implementation

Use test-only coordinators/fakes only after each sequence is fixed against the Android source. The single-server test must assert dispatch invocation before stop-state publication without asserting awaited catalog completion. The group test must control `subscriptionEditorController.remove` completion/failure/cancellation and assert that state publication follows its completion. Both tests should include `MissingServer` and `Failed` outcomes and a selected target that disappears before the post-stop recheck. Keep Android's `serviceOperationInProgress`, app-scope launches, controller persistence, and UI selection as host adapters when a common operation is later extracted.

## Concrete test draft

Keep these Android cases separate from the bulk-delete coordinator tests; their ordering is intentionally different.

| Operation and case | Expected observable order / result |
| --- | --- |
| Single delete, not selected or not running | `dispatch(RemoveProxyServer)` then scheduled deleted tip; no stop or stop-state callback. |
| Single delete, selected and running, stop succeeds, row still exists | `stop` → live-row recheck → fire-and-forget `dispatch(RemoveProxyServer)` → `applyStopState` → deleted tip. Assert dispatch invocation, not reducer completion. |
| Single delete, selected and running, row disappears during stop | `stop` → live-row recheck; no delete dispatch, stop-state callback, or deleted tip. |
| Single delete, `MissingServer` / `Failed` | Missing: select-server tip only. Failed: `proxyRunning=false` then stop error; neither dispatches delete. |
| Group delete, no running selected member / not running | app-scope removal starts without a stop; controller removal is awaited by the launched work. |
| Group delete, running selected member, stop succeeds, controller removal succeeds | `stop` → group-exists recheck → `remove(groupId)` completes → `applyStopState` → in-function selected-group fallback if still selected → deleted tip. Separately assert caller selection fallback occurs immediately after starting deletion of the selected group. |
| Group delete, group disappears during stop | group-exists recheck suppresses controller removal, stop-state callback, and success tip. |
| Group delete, `MissingServer` / `Failed` | Missing: select-server tip only. Failed: `proxyRunning=false` then stop error; neither removes the group. |
| Group delete, controller exception / cancellation | Ordinary exception reports error and suppresses stop-state/success feedback; cancellation propagates and suppresses those callbacks. |

The tests should use ordered event logs plus deferred completion for the group controller, and a current-store recheck fake for the disappearing-target cases. Do not convert these into one generic stop/delete helper: single deletion dispatch is non-awaited and precedes runtime publication, while group deletion awaits persistence before runtime publication.
