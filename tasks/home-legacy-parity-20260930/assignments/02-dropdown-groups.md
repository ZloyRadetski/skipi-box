# Task 02: Restore the v0.4.1 selected-group dropdown

First read README.md in this directory. Implement, not merely research.

Read legacy `app/src/main/kotlin/features/proxy/server/list/ProxyServerListComponents.kt` at v0.4.1, especially `ProxyServerListGroupSelector` (approximately 130–290). Read current `SkipiProxyGroupPicker.kt`, `SkipiProxyGroupSelector.kt`, `ProxyHomeScreen.kt` and group state/action contracts.

Expected result: active shared Home presents one full-width rounded selected-group surface, selected group name and server count, animated chevron; tapping opens an anchored dropdown. Dropdown marks the selected item. Selecting dismisses the popup and selects the corresponding pager page through the existing single action path. Retain appropriate haptic feedback via existing shared/platform facilities. Long press on a user-managed group exposes supported reorder/group operations. Keep actual platform action availability. Menu must work with one group, many groups, long names, translated labels, removed selected group and empty groups without crashes.

The current common picker already resembles the reference: reuse/extend it. Active `HomeGroupSelector` currently chooses chip-based `SkipiProxyGroupSelector`; replace that integration. Do not invent a second independent picker, new local selected-group truth, or duplicate dispatches. Preserve group swipe/pager synchronization and supported test/edit/delete/reorder actions. Selected dropdown indication should be accessible rather than color alone.

File ownership: `SkipiProxyGroupPicker.kt`; `ProxyHomeScreen.kt` ONLY the `HomeGroupSelector` function and strictly needed imports. Keep its external signature stable if possible. Any focused new group helper/test is allowed. Do not change root padding, pinned/scrolling layout, server rows, subscription header mapping, model/adapters. Other agents own those sections. Do not delete the old chip component just because active Home stops using it; check other call sites and preserve working compatibility.

Parity includes 16dp corner radius, 1dp border, 16dp horizontal/12dp vertical padding, 15sp semibold one-line name and 12sp count where theme scaling allows. Match old selector scale/border/background/chevron animations. Preserve touch targets at least48dp. Common Compose only, no Android Miuix dependency.

Validation: relevant shared UI compile and existing Home/pager tests if behavior changes. Use the shared Gradle lock. Do not write tests asserting Kotlin source text. Report to `tasks/home-legacy-parity-20260930/reports/02-dropdown-groups.md`.
