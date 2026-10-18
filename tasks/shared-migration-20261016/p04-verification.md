# P04 focused editor verification — 16 October 2026

This records the connected P04 slice for profile Basics, General and DNS, plus save/navigation coordination for Raw Back. It does not close P04. Routing, proxy groups, resources, remaining platform-specific sections, complete list wiring, and visual QA remain open.

## Frozen Android contract

The behavior source is `outputs/baseline-20261016/app/src/main/kotlin/features/config/TrafficConfigEditorPages.kt`.

- Basics initializes its draft by profile ID with name, source URL, update lock, profile auto-update and interval, and resource auto-update and interval. It shows General, DNS, Tunnel, Network, Routing, then per-app selection. Back saves; tapping a section saves before navigation. The original screen has no top-bar Save action. An unchanged canonical Basics draft skips the store write but still performs the requested navigation.
- General initializes IPv6 and prefer-IPv6 from the parsed `[General]` section, keyed by profile ID and raw content. Back and Save both apply `withGeneralOptions` before popping. Other General values and unrelated sections follow the existing document writer behavior.
- DNS initializes from `androidSettings`, keyed by profile ID. Back and Save both apply `withDnsOptions` before popping.
- Raw opens the Android-normalized document from `withSkipiSettingsInRawConfig`. Copy still copies the current editor text. Back always leaves; it writes only nonblank text without Error diagnostics and normalizes a valid save as `trimEnd() + "\n"`. Android persistence uses the existing `withUpdatedTrafficConfig` / `updateTrafficConfigProfile` chain, including the known-SKIPI read/write normalization. Unknown keys inside `[SKIPI]` are not promised as preserved.
- Profile edits target the latest row with the same ID. If the row is missing, no row is created or restored. This slice adds no revision/CAS check or new conflict policy.

## Source review

`shared/ui/.../SkipiTrafficConfigVisualEditorScreens.kt` now owns the shared Basics, General and DNS compositions and their local drafts. The Android wrapper injects the original labels, routes and `NavigationBackHandler`; General/DNS use the same save-wrapped action for the scaffold Back button, top-bar Save, and system Back. Draft keys and the reviewed layout values match the frozen entry points: Basics and General/DNS list spacing is 10 dp, General retains 12 dp bottom padding, and the shared scaffold retains the existing content/list padding and 8 dp top inset. The raw Android Copy action, zero top inset and `ConfCodeEditor` remain in the Android entry point.

The common controller (`shared/app/.../TrafficConfigEditorController.kt`) applies transforms through the host store. Its default save-failure behavior rethrows, matching Android. Desktop supplies the existing error message handler: failed persistence produces `Failed`, displays the existing error status and does not navigate; a Raw `Rejected` result still leaves, as the Android Back contract requires. The Basics overload skips a write only when the captured profile is already equal to the canonical `withProfileBasics` result.

Desktop UI Edit is connected for Basics, General and DNS while the Raw Edit route remains available. The Desktop store receives `onTransformConfigLibrary` from `Main`, so persistence goes through the current library/reconnect/group side-effect path. It transforms only the selected latest profile through `updateTrafficConfigProfile`, then updates the existing envelope with `TrafficConfigLibraryOperations`; selection, counter, timestamp and sibling profiles are not replaced. The per-profile UA fallback remains in use and an explicit empty profile value wins. Raw Back passes the editor's current content to the shared controller.

Static review of `TrafficConfigEditorPages.kt`, `SkipiTrafficConfigVisualEditorScreens.kt`, `DesktopTrafficConfigEditorProfileStore.kt` and the corresponding `DesktopConfigsScreen.kt` wiring found no actionable P04 mismatch. This is source review, not rendered UI verification.

## Test-first evidence and current gates

1. Before production changes, the Android oracle exercised the current Android state updater and SKIPI collection-writer chain. Root confirmed 4/4 `TrafficConfigEditorAndroidOracleTest` cases GREEN.
2. Common behavior tests were added before the controller API. After correcting a test-source inference error, root captured compile RED for the missing coordination API; a later RED captured the missing profile overload, `onSaveFailure` and `Failed` result. The expected-API gates are recorded in `outputs/shared-migration-20261016-editor-coordination-api-red.log` and `outputs/shared-migration-20261016-editor-error-noop-api-red.log`.
3. The Desktop raw-normalization behavior gate ran before the adapter correction: 4 tests, exactly one intended failure, `controllerRawSaveUsesAndroidSkipiNormalizationAndUpdatesLatestDesktopEnvelope`. The persisted envelope kept `Envelope before` instead of the raw editor's `Raw editor name`. Evidence: `outputs/shared-migration-20261016-p04-envelope-behavior-red.log`.
4. After implementation, root confirmed all 13 common controller cases GREEN; shared/app ran 277 tests on each target, shared/ui ran 82 tests on each target, and Android ran 458 tests with `assembleDebug` GREEN. The latest focused UI/APK run is `outputs/shared-migration-20261016-p04-final-ui-apk.log`: shared UI Android/Desktop compilation and tests completed, Android app compiled, and `assembleDebug` ended `BUILD SUCCESSFUL`.
5. Desktop `compileMain` is GREEN. The later focused Desktop run compiled tests and ran 20 tests; root confirmed all five `DesktopTrafficConfigEditorProfileStoreTest` cases GREEN, including save-failure publication behavior. The run had one failure in the unrelated T02 test `DesktopLocalProxyRuntimeLifecycleTest > invalidRawStaticPortUsesAndroidDefaultRatherThanAStaleParsedDesktopPort`. Evidence: `outputs/shared-migration-20261016-t02-invalid-port-behavior-red.log`. The full Desktop suite is therefore still not GREEN; root reports it is pending the T02 canonical-default fix. The earlier test-compile RED in `outputs/shared-migration-20261016-editor-green-t02-api-red.log` was superseded by this later run.

No Android device interaction, Desktop rendered QA, or two-window-size visual QA was performed. APK assembly confirms packaging/compile only.

## Remaining P04 path

- Complete the shared catalog/list connection around Android `app/src/main/kotlin/features/config/TrafficConfigPage.kt` and Desktop `desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt`. Shared profile/list item components already exist; keep host selection, CRUD, import, refresh and context-menu effects on their existing operations/callbacks.
- Connect routing/rules and proxy-group list/edit screens using the Android routes in `TrafficConfigEditorPages.kt`, `TrafficConfigRuleEditor.kt` and `TrafficConfigProxyGroupEditor.kt`, reusing `shared/ui/.../SkipiTrafficConfigRulesList.kt`, `SkipiTrafficConfigRuleEditorForm.kt` and `SkipiTrafficConfigProxyGroups.kt` where their contracts match.
- Continue the Android resource screen from `app/src/main/kotlin/features/resources/ResourceManagementPage.kt`; keep file I/O, download/update execution and Android notifications in host adapters while moving only portable state/form behavior.
- Review Tunnel, Network and per-app routes in `TrafficConfigEditorPages.kt` against actual host capabilities. Retain Android package/VPN operations behind callbacks and avoid inventing Desktop capabilities.
- Finish parity checks for the two editor modes and profile context menu. The current slice wires save coordination for Raw Back; it does not claim the full raw/list/routing/resources P04 acceptance.
- Unblock the unrelated Desktop test compile issue, then run the focused store suite and the required final visual QA at two Desktop window sizes and on Android where available.

## Final superseding checkpoint

Root subsequently ran the complete Desktop suite successfully: **399 tests, zero failures/errors/skips**, in `outputs/shared-migration-20261016-desktop-final.log`. The P04 store suite is 5/5 GREEN. The T02 invalid-port behavioral RED was corrected after its frozen test run. The final UI/APK run remains GREEN. A separate real FFM run executed 3/3 native tests successfully; it does not replace visual QA. See [checkpoint.md](checkpoint.md) for the full suite counts, remaining migration work and Luna credit blocker. P04 remains open for the broader scope listed above; the old Desktop compile/test blocker is superseded.
