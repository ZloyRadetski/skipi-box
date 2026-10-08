# Shared Home UI Polish (Task 01) Final Report

**Date**: 2026-09-30  
**Scope**: Shared Home UI (`shared/ui/src/commonMain/kotlin/app/skipi/ui/home/**`, `shared/ui/src/commonTest/kotlin/app/skipi/ui/home/**`, `shared/ui/src/commonMain/composeResources/values*/home.xml`)  
**Specification**: `tasks/home-polish-20260930/01-shared-home-ui.md`  

---

## 1. Summary

This task resolves architectural defects, user experience inconsistencies, rendering performance bottlenecks, and wording bugs in SKIPI's shared Home UI across mobile, desktop, and tablet platforms:

- **Requirement A (Subscription Toggle Removal)**: Completely removed the subscription enable/disable switch from `SkipiSubscriptionSummaryCard` and its card header actions. Maintained data class backwards compatibility by defaulting `canToggleEnabled = false` and `onToggleEnabled = null`, and cleaned up the toggle plumbing in `ProxyHomeScreen.kt`.
- **Requirement B (Accessible Power Control & State Truthfulness)**: Equipped `SkipiProxyHeroCompactCard` with an accessible primary connect/disconnect power control supporting pressed motion feedback, connecting spinner, and accessible content descriptions. Wired this control into `HomeConnectionPanel` so wide/Expanded layouts have an accessible connection toggle regardless of connection widget style. Guaranteed state truthfulness during busy/connecting states so the subtitle never briefly flashes "Select a server first", and guarded against double-taps by disabling power control when operations are busy. Prevented duplicate controls by suppressing the card-level button when the floating toolbar power button is visible.
- **Requirement C ($O(N)$ Grid Lookup Elimination)**: Replaced `items(pageServers)` with `itemsIndexed(pageServers, key = { _, server -> "server-${server.id}" }, contentType = { _, _ -> "server" })` in `PageServerGrid`, completely eliminating per-item `pageServers.indexOfFirst` calls during LazyGrid composition. Replaced $O(N^2)$ `associate` + `indexOfFirst` in `HomeGroupSelector` with `mapIndexed`.
- **Requirement D (Motion Polish)**: Added smooth animated color transitions (`animateColorAsState` with `tween(200)`) for chip background and border on tab selection changes in `ProxyGroupChip`. Added tactile spring press scale feedback and animated icon/border color transitions to the compact hero power button.
- **Requirement E (Menu Wording & Empty Group Latency Action)**: Differentiated group action menu strings so local and manual proxy groups are labeled "Edit proxy group" and "Delete proxy group" (`home_edit_group`/`home_delete_group`) rather than "subscription", reserving subscription labels strictly for `ProxyHomeGroupKind.Subscription`. Hid the latency test action from group menus when `group.serverCount == 0`, and added defensive filtering in `dispatchHomeIntent`.

---

## 2. Changed Files and Rationale

1. **`shared/ui/src/commonMain/composeResources/values/home.xml`**
   - *Changes*: Added string resources `home_edit_group` ("Edit proxy group") and `home_delete_group` ("Delete proxy group").
   - *Rationale*: Manual and local proxy groups are not subscriptions and must not reuse `subscription_edit_group` and `subscription_delete_group`.

2. **`shared/ui/src/commonMain/composeResources/values-ru/home.xml`**
   - *Changes*: Added Russian translations `home_edit_group` ("Редактировать группу прокси") and `home_delete_group` ("Удалить группу прокси").
   - *Rationale*: Provides localized group management action titles matching existing Russian terminology (`home_add_group` = "Добавить группу прокси").

3. **`shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryCard.kt`**
   - *Changes*:
     - Removed `import androidx.compose.material3.Switch`.
     - In `SkipiSubscriptionSummaryState`, set default `canToggleEnabled: Boolean = false`.
     - In `SkipiSubscriptionSummaryActions`, changed `onToggleEnabled` to `val onToggleEnabled: (() -> Unit)? = null`.
     - In `SubscriptionHeaderActions`, removed the `Switch` composable block entirely.
   - *Rationale*: Fulfills Requirement A by removing the whole-subscription toggle from the Home screen cards while preserving constructor source compatibility.

4. **`shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyConnectionHero.kt`**
   - *Changes*:
     - Updated `SkipiProxyHeroCompactCard` signature to accept `onToggle: (() -> Unit)? = null`, `connectContentDescription`, and `disconnectContentDescription`.
     - Added `SkipiProxyHeroCompactPowerButton` helper with pressed state spring animation (`collectIsPressedAsState`, `animateFloatAsState`), animated container and border colors (`animateColorAsState`), connecting spinner (`SkipiProxyHeroConnectingSpinner`), power icon (`SkipiProxyHeroPowerIcon`), and accessible semantics.
     - Added the compact power button to both connected and disconnected/connecting rows when `onToggle != null`.
   - *Rationale*: Fulfills Requirement B and Requirement D by providing an accessible, animated power toggle on compact hero cards.

5. **`shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyGroupSelector.kt`**
   - *Changes*:
     - Added `animateColorAsState` and `tween` imports.
     - In `ProxyGroupChip`, animated `chipBackgroundColor` and `chipBorderColor` using `tween(200)` and applied them to `Surface`.
   - *Rationale*: Fulfills Requirement D by making tab selection transitions fluid and responsive.

6. **`shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeActionAvailability.kt`**
   - *Changes*:
     - Added `canTestHomeGroup(groupEnabled, serverCount, availableActions, busyActions)`.
     - Added `canToggleHomePower(canToggleTunnel, tunnelBusy, isConnecting, availableActions, busyActions)`.
   - *Rationale*: Centralizes action capability rules and busy state checks into pure, directly testable domain functions.

7. **`shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt`**
   - *Changes*:
     - Imported `itemsIndexed`, `home_edit_group`, and `home_delete_group`.
     - In `HomeSubscriptionCard`: removed `canToggleEnabled` and `onToggleEnabled` wiring.
     - In `HomeConnectionPanel`: added `showCardPowerControl: Boolean = true`, derived `isBusy`, ensured `subtitle` displays `home_connection_preparing` when busy/connecting with an empty server title (preventing "Select a server first"), wired `toggleEnabled` through `canToggleHomePower`, and passed `onToggle.takeIf { showCardPowerControl }` to `SkipiProxyHeroCompactCard`.
     - In `ProxyHomeScreenContent`: passed `showCardPowerControl = !showFloatingPower` on single-column layouts and `true` on wide layouts, preventing duplicate power buttons.
     - In `PageServerGrid`: replaced `items` with `itemsIndexed` using stable keys (`"server-${server.id}"`), eliminating the $O(N)$ `pageServers.indexOfFirst` call per item.
     - In `HomeGroupSelector`: replaced `associate` + `indexOfFirst` with `mapIndexed`, guarded latency tests with `canTestHomeGroup` (disabling/hiding for empty groups), and differentiated edit/delete labels by `group.kind`.
     - In `dispatchHomeIntent`: added `it.serverCount > 0` requirement for `TestGroup` actions.
   - *Rationale*: Fulfills Requirements A, B, C, and E.

8. **`shared/ui/src/commonTest/kotlin/app/skipi/ui/home/ProxyHomeActionAvailabilityTest.kt`**
   - *Changes*: Added test assertion verifying `canDispatchHomeAction(ToggleTunnel, allowBusy = false)` returns `false` when `ToggleTunnel` is in `busyActions`.
   - *Rationale*: Regression testing for busy action suppression.

9. **`shared/ui/src/commonTest/kotlin/app/skipi/ui/home/ProxyHomePolishTest.kt`**
   - *Changes*: Created new test suite covering empty group latency test prevention, power toggle suppression during busy/connecting states, subscription summary state defaults, and group kind distinction.
   - *Rationale*: Verifies all newly added rules and invariants across Requirements A, B, and E.

---

## 3. Proved Original Issues

1. **Subscription Toggle Confusion (Req A)**:
   - *Original Issue*: Users were confused by having an enable/disable switch on subscription summary cards on the Home screen. Toggling disabled whole subscriptions unintentionally.
   - *Fix*: Removed `Switch` from `SubscriptionHeaderActions` and cleaned up invocation wiring in `HomeSubscriptionCard`.
2. **Missing Power Control on Wide Layout (Req B)**:
   - *Original Issue*: On wide/Expanded screens (like desktop or tablets), `showFloatingPower` is disabled. When `connectionMode` was set to `Compact`, `SkipiProxyHeroCompactCard` had no toggle parameter or button, leaving wide compact users with no way to connect/disconnect from the Home screen.
   - *Fix*: Added `onToggle` parameter and animated compact power button to `SkipiProxyHeroCompactCard`, and wired it in `HomeConnectionPanel`.
3. **Flashing "Select a server first" During Tunnel Startup (Req B)**:
   - *Original Issue*: When connecting or preparing the tunnel before a selected server title was populated, the subtitle checked `!hasServer` first and displayed "Select a server first".
   - *Fix*: Computed `isConnectingPhase = isBusy || state.connectionPhase == ProxyConnectionPhase.Connecting` and prioritized preparing status when busy/connecting with blank title.
4. **$O(N)$ Index Lookup in Lazy Grid (Req C)**:
   - *Original Issue*: In `PageServerGrid`, `val serverIndex = pageServers.indexOfFirst { it.id == server.id }` was called inside every grid item during composition, leading to $O(K \times N)$ work per frame during scrolling.
   - *Fix*: Switched to Compose `itemsIndexed`, obtaining `serverIndex` directly with zero search overhead.
5. **Misleading Group Action Labels & Latency Testing Empty Groups (Req E)**:
   - *Original Issue*: Non-subscription proxy groups (Manual, AutoBalancer) showed "Edit subscription group" and "Delete subscription group" menu items. In addition, groups with 0 servers allowed clicking "Latency test".
   - *Fix*: Added dedicated proxy group edit/delete strings, conditioned menu labels on `group.kind == ProxyHomeGroupKind.Subscription`, and hid/disabled latency test when `serverCount == 0`.

---

## 4. Regression Coverage

The following tests verify the modified behavior and protect against regressions:

- **`ProxyHomeActionAvailabilityTest.kt`**:
  - `operationIsDispatchableOnlyWhenAvailableAndNotBusy`: Verifies that `ToggleTunnel` is dispatchable with `allowBusy = true` but rejected with `allowBusy = false` when present in `busyActions`.
- **`ProxyHomePolishTest.kt`**:
  - `emptyGroupDoesNotAllowLatencyTest`: Tests that `canTestHomeGroup` returns `true` for enabled non-empty groups, but returns `false` when `serverCount == 0`, when disabled, or when `TestGroup` is busy.
  - `powerActionDisabledDuringBusyOrConnectingStates`: Tests that `canToggleHomePower` returns `false` when `tunnelBusy == true`, `isConnecting == true`, or `ToggleTunnel in busyActions`.
  - `subscriptionSummaryDefaultsRemoveToggleContract`: Tests that `SkipiSubscriptionSummaryState.canToggleEnabled` defaults to `false` and `SkipiSubscriptionSummaryActions.onToggleEnabled` defaults to `null`.
  - `groupKindDistinguishesSubscriptionFromLocalManualGroups`: Validates type safety and separation between `ProxyHomeGroupKind.Subscription` and local/manual/auto group kinds.

---

## 5. Verification Commands / Limitations

- **Limitation**: Per Constraint 6 of the workspace profile, execution of shell compilation/test commands (`run_command`) in the CLI sandbox environment fails with `connecting to sandbox server: read unix @->@: recvmsg: connection reset by peer`.
- **Execution Status**: *Execution unverified via CLI sandbox command runner*. All code modifications were thoroughly inspected, verified against project typing conventions and import paths, mentally compiled, and applied via precision file operations.

---

## 6. Remaining Issues & Next Steps

- **Downstream Coordination**:
  - Platform adapters (Android / Desktop) that may have relied on subscription toggle events can safely ignore them as the contract defaults to `null`.
  - Group edit dialogs and subscription update workers remain owned by their respective modules and operate unmodified.
- No remaining defects or regressions identified within the Shared Home UI scope.
