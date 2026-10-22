# P04 Desktop traffic-config collection

Status: collection slice complete and committed (`0addf1a`, `a877bb5`). Final pooled inventory is **2676 GREEN**, with Desktop compile, debug APK, seven actual Core cases and ten inspected collection PNGs. P04/R01 remain open as broader plan cards; the next source-backed P03 gap is periodic profile-document refresh on Desktop.

## Source and integration contract

Android's shared collection presents global groups, then groups sourced from all traffic profiles, then profiles. The [shared list projections](p04-config-group-list-verification.md) pin eligibility, order, keys and labels. Global eligibility deliberately also admits another proxy type stored under group -2; its row click, Edit and Duplicate are safe no-ops, while Delete can remove the displayed record. The menu Edit crash was corrected separately before [sharing editor result factories](p04-global-group-editor-verification.md), `c32428b` / `09195af`.

Global Add opens the existing shared StrategyGroup draft, Edit retains its existing catalog ID, Duplicate is detached with a null ID and a localized copy name, and all three use group/return group -2. The shared editor owns the editable copy; no write or ID allocation happens before Save. Its existing blank `probeUrl` effect still supplies `NetworkDefaults.CONNECTIVITY_CHECK_URL` when the form opens. This is distinct from the pure factory's initially blank draft. Delete honors the host confirmation preference and applies the shared catalog removal; it does not edit the traffic profile document.

Desktop's new required `onApplyGlobalProxyGroupResult` callback binds directly to `applyProxyServerEditResult` and the existing shared store with the Desktop manual-group default -1. It does not inherit Home-only reconnect, `serverLink` or Home-status effects. Actual subscription ID 1 remains distinct from that synthetic manual ID. The actual-screen tests use a scripted callback outcome to verify the UI boundary; real store allocation/persistence semantics remain covered by the existing shared application tests and must not be inferred from a scripted UI callback alone.

Opening a sourced row enters that specific profile's ProxyGroups section without activating it. Back returns to the catalog. The existing Basics → Routing → ProxyGroups origin continues to return to Routing. Profile documents, active ID and proxy inputs must remain unchanged during navigation or discarded global edits.

The shared list must retain Desktop's existing toolbar/import actions, active-profile summary, empty state, status feedback, and profile URL, rule/group counts, update-lock and diagnostic metadata, with Copy and More controls. Optional presentation slots expose that existing metadata/actions through the common card; Android's default screen call stays unchanged.

## Tests before list implementation

Two original actual-screen cases first reproduced missing sourced/global rows: **2/2 RED in 29 seconds**, XML timestamp `2026-10-22T04:51:25.089Z`. Log: `outputs/p04-config-collection-host-section-red-20261022.log`; XML: `outputs/p04-config-collection-host-section-red-reports-20261022/`.

Add, Duplicate and Delete cases were added before the required callback existed. The clean compiler gate stopped only at the missing callback and its type-inference cascades: **API RED in 2 seconds**, `outputs/p04-config-collection-global-callback-api-red-20261022.log`. Existing unrelated Desktop screen tests provide explicit fail-fast callbacks. Root's source review corrected Add's erroneous blank-URL saved-result expectation before list implementation; the correction follows the existing shared form effect described above, not a new production policy.

Two existing-global cases then specified Save with unchanged ID 40 and Back → Discard without applying or mutating the original. Two legacy cases pin the current profile metadata/Copy/More and unchanged inactive-profile Edit/Back behavior. Only the required screen parameter and real Main callback wiring were added for a second baseline; the old profile-only list stayed in place.

That actual-screen gate executed **9 cases in 46 seconds: 2 legacy baseline passes and 7 expected missing-group behavioral failures**, errors/skips 0. Every failed case stops at the missing global/sourced row or global section Add action; both legacy metadata and Edit/Back cases pass. Log: `outputs/p04-config-collection-global-crud-host-red-20261022.log`; preserved XML/counts: `outputs/p04-config-collection-global-crud-host-red-reports-20261022/`, timestamp `2026-10-22T05:03:49.118Z`. Root reviewed the individual failures before opening the list/editor implementation gate. These nine behavior expectations are held unchanged during production work.

Independent source/test review then identified three additional cases before list/menu implementation began: the separate non-StrategyGroup context-menu Edit/Duplicate no-op, blank-remarks Duplicate using the protocol fallback, and a rejected apply outcome displaying its actual reason. Those three tests reproduced missing rows **3/3 RED in 14 seconds**, errors/skips 0, timestamp `2026-10-22T05:08:08.756Z`: `outputs/p04-config-collection-edge-cases-host-red-20261022.log` and `outputs/p04-config-collection-edge-cases-host-red-reports-20261022/`. Root caught and corrected harness/source-spec issues before production: suspend string loading belongs outside the non-suspend screen callback, and the group context menu uses `common_edit` rather than the profile-specific `configs_ui_edit` label. The latter expectation was corrected from the existing shared menu source; production labels are retained. The original nine cases are unchanged, and twelve behavior cases now define the new UI gate.

Before replacing the list/session wiring, a final audit requested two existing-flow baselines: normal Basics → Routing → Groups → Back returns to Routing, and the empty Desktop library retains its empty state and Add/import menu items. Root strengthened the Routing assertion to require Resources/Groups rows and absence of the Basics editable name field, because the Routing label alone also appears on Basics. Root also corrected the global Add harness target: the shared section header is not clickable; its neighboring Add icon is the accessible control. These are source-backed test targeting corrections before the corresponding list/session implementation.

The first legacy-baseline attempt did not execute tests: a newly added, still disconnected global adapter contained an invalid import of the member method `getInfo`, producing a compile failure in 10 seconds (`outputs/p04-config-collection-navigation-shell-baseline-20261022.log`). After removing only that import and simplifying the adapter's duplicate global mapping to reuse its existing records without a new global-projection remember, both unchanged legacy flows passed **2/2 in 14 seconds**, errors/failures/skips 0. Log: `outputs/p04-config-collection-navigation-shell-baseline-final-20261022.log`; XML/counts: `outputs/p04-config-collection-navigation-shell-baseline-reports-20261022/`, timestamp `2026-10-22T05:13:14.883Z`. Desktop's list and visual-editor session were still the original paths during this baseline. The complete list/session gate now has fourteen behavior cases.

## First integrated UI gate

The shared collection and separate Desktop global-group adapter are now connected, with the required Main store callback. Root's review caught duplicate `contentPadding` application before the first run; moving the adapter outside the catalog's padded Box restores a single editor inset. Global rows use the existing shared projection directly on the prepared editor records; no second mapping or new global-projection remember remains.

The first focused run executed **27 cases in 38 seconds: 25 passed, 2 failed**, errors/skips 0. Collection class **15 cases / 2 failures** includes fourteen behavior cases and one actual-screen renderer; prior ProxyGroups **7**, Rules **4** and rule-assets **1** all pass. Thus actual global CRUD, non-StrategyGroup menu no-op, blank-name copy fallback, failed-apply reason and both Back origins are GREEN at this gate. Log: `outputs/p04-config-collection-focused-first-20261022.log`; XML/counts and first PNG: `outputs/p04-config-collection-focused-first-reports-20261022/`, timestamp `2026-10-22T05:17:55.275Z` onward. This failed run is preserved rather than reported as complete.

One failure is a test targeting ambiguity introduced by valid sourced rows: a profile's name appears first as the source-group subtitle, so the old `rowCard` helper chooses a sourced card and looks there for Copy/More. The business expectations remain valid; the helper must identify the actual profile card. The narrow renderer exposes a real existing metadata-layout problem: the horizontal counter/lock/diagnostic Row squeezes later text to nearly zero width, producing a vertical column of characters and an over-height card at 390×844. Root inspected the PNG and opened a minimal wrapping-layout/semantic-color correction under the unchanged visibility requirements. Further diagnostics and retest results follow below when complete.

Review also found a source-parity gap in the newly connected profile list: hardcoding `hasUnsupportedSections=false` preserves the former Desktop card's omission but suppresses Android's existing preserved-unsupported badge. This is a migration gap, not a regression from the old Desktop card. A separate source-backed test must capture RED before that flag is corrected.

## Responsive and final source corrections

The wrapping metadata fix and profile-card harness disambiguation passed the four-case debug gate; two deliberately strengthened/new cases remained RED: the preserved-unsupported notice and failure feedback within the actual 390×844 viewport. This is **2 passed / 2 failed in 18 seconds**, timestamp `2026-10-22T05:24:07.936Z`, `outputs/p04-config-collection-renderer-debug-final-20261022.log` and `outputs/p04-config-collection-renderer-debug-reports-20261022/`. The preceding debug attempt stopped before tests because Desktop does not directly depend on Miuix types; the host now uses the existing public `SkipiTheme.colors` palette instead of adding a new dependency (`outputs/p04-config-collection-renderer-debug-20261022.log`, 1 second).

Only after that RED was the unsupported flag connected to the Android parser result and global apply failure moved outside the scrolling footer into a bottom status surface. The old profile-message footer remains separate. These changes retain all result fields, mutations, save/discard policy and a single application of content padding.

Root inspected the ten initial renderer PNGs. Catalog/editor/metadata layouts were correct after the wrapping fix, but menu/dialog captures were still at the start of their enter animations despite semantics being present. The renderer now advances the existing scene frame clock for at least thirty frames and requires three identical pixel frames before taking those two transient-layer screenshots, bounded by ninety frames. App animation code and business expectations are unchanged.

## First full checkpoint, one renderer failure

The full command with `SKIPI_CORE_INTEGRATION_DIR` executed **2676 tests in 1 minute 19 seconds**, with **2675 passes, 1 failure, errors/skips 0**. Shared core **354 Desktop + 298 Android**, shared app **341 + 333**, shared UI **120 + 120**, Android app **567** are all GREEN. Desktop app **543** has one renderer failure; all fifteen collection behavior cases, including unsupported notice and narrow failed-save feedback, pass. Desktop compile and debug APK passed. All seven actual Core cases passed with fresh XML timestamps: Core **4**, profile-assets **1**, selected-server **2**.

Log: `outputs/p04-config-collection-full-checkpoint-20261022.log`; complete immutable XML/counts and only fresh PNGs: `outputs/p04-config-collection-full-first-reports-20261022/`, timestamps `2026-10-22T11:25:41.210Z`–`2026-10-22T11:26:44.102Z`. Native tests have timestamps `11:26:11.914Z`, `11:26:13.394Z` and `11:26:23.514Z`. These are current outputs, not the earlier scoped results. Existing native artifacts were used, with HevTun build/sync tasks excluded; the foreign submodule remains untouched.

The remaining failure is in the renderer's second viewport, after returning from the global editor at 1100×800: the next long-press helper cannot identify the global row. The first 390×844 iteration completed and its settled context menu and confirmation PNGs were visibly opaque and correct. The wide menu/confirmation were not reached and their stale PNGs were excluded from this attempt's preserved evidence. Root is investigating the public Back/scene transition before accepting final visual QA; no unchecked production workaround or weakened assertion has been applied.

## Completed renderer and final checkpoint

The renderer now waits for the actual post-Back catalog node with Click and LongClick semantics, with diagnostics for an editor/discard state on timeout. It also dismisses the captured delete confirmation through its public Cancel action and waits for dismissal before closing a scene and opening the next viewport. No app flow or business expectation was bypassed. The focused renderer then passed **1/1 in 9 seconds**, timestamp `2026-10-22T11:33:15.262Z`: `outputs/p04-config-collection-renderer-final-20261022.log`, XML and ten fresh PNGs in `outputs/p04-config-collection-renderer-final-reports-20261022/`.

The subsequent full Desktop continuation passed **543/543 in 39 seconds**, failures/errors/skips 0. Collection is **16/16** (fifteen behavior cases and one renderer); actual Core cases are **7/7**, with fresh timestamps `2026-10-22T11:35:03.199Z` (4), `11:35:03.537Z` (1) and `11:35:12.888Z` (2). Log: `outputs/p04-config-collection-desktop-final-checkpoint-20261022.log`.

The final inventory combines the seven unchanged GREEN shared/Android suites from the full 79-second command with Desktop from the 39-second continuation. It is explicitly a two-run checkpoint, not a claim that the originally failed full command succeeded. Immutable XML, run provenance and counts: `outputs/p04-config-collection-final-reports-20261022/suite-counts.json`.

| Suite | Tests | Failures / errors / skips |
| --- | ---: | --- |
| Shared core Desktop | 354 | 0 / 0 / 0 |
| Shared core Android | 298 | 0 / 0 / 0 |
| Shared app Desktop | 341 | 0 / 0 / 0 |
| Shared app Android | 333 | 0 / 0 / 0 |
| Shared UI Desktop | 120 | 0 / 0 / 0 |
| Shared UI Android | 120 | 0 / 0 / 0 |
| Desktop app | 543 | 0 / 0 / 0 |
| Android app | 567 | 0 / 0 / 0 |
| **Total** | **2676** | **0 / 0 / 0** |

Root inspected the actual catalog, profile metadata/actions, editor, context menu and confirmation at **390×844** and **1100×800**. The settled menu/dialog images are opaque and their actions fit the viewports; the metadata no longer collapses into a column of characters. All ten final regenerated PNGs are byte-identical to the inspected successful renderer outputs. They are stored alongside the final XML; captures before settling are retained only as failed-attempt evidence. This was actual Compose screen/public-semantics and raster QA, not physical-window or Android-device interaction. Keyboard/device/manual reload QA was not performed.

Independent Luna review and root review found no remaining required issue in the scoped projection/factory/adapter/Back/apply/Android-default contract. The legacy Desktop profile wrapper was removed, no second filter or editor algorithm was introduced, and Main continues to use the shared apply/store boundary. Shared presentation API is committed as `0addf1a`; Desktop wiring and tests as `a877bb5`.

No user app restart, APK installation, key access, user-data reset or native rebuild was performed. The unrelated dirty HevTun submodule remains unchanged. This slice completes Desktop collection integration; it does not close P02/P03/P04/R01 or X04 by inference. Current-source P02/P03 findings and the next tests-first execution/scheduler slice are recorded in [the P03 research note](p03-profile-auto-refresh-research.md).
