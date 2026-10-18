# P04 editor slice: approved test seam

This is a connected P04 sub-slice: profile basics, General and DNS forms, plus raw-editor Back coordination. It does not close P04; routing, proxy groups, resources, and the remaining platform callbacks stay open.

## Shared controller seam

- `TrafficConfigEditorProfileStore.update(profileId, transform)` applies the transform to the latest row with that ID and returns `false` when the row is missing. A missing row is never inserted or recreated. This mirrors Android's current `withUpdatedTrafficConfigProfile` seam and Desktop's latest-library transform; it adds no revision/CAS or conflict policy.
- `TrafficConfigEditorController` exposes `saveBasics`, `saveGeneral`, `saveDns`, and `saveRaw`. Basics, General, and DNS reuse the existing `withProfileBasics`, `withGeneralOptions`, and `withDnsOptions` operations. The result is `Saved`, `Missing`, or `Rejected`.
- `leaveRaw(profileId, content, onLeave)` tries the same raw save, then always invokes the supplied host navigation callback. Valid text is saved as `trimEnd() + "\n"`; blank or error-diagnostic text is left unchanged and returns `Rejected`. Raw editing remains a separate mode.
- Shared composables may own editor composition and local draft state. Hosts provide storage mapping, navigation, labels, and platform callbacks; the common controller supplies portable profile transforms and save-before-navigation coordination. Reuse existing forms/scaffolds without adding a new flow.
- Basics, General, and DNS expose `saveBasicsThenNavigate`, `saveGeneralThenNavigate`, and `saveDnsThenNavigate`: each applies its save before invoking the host navigation callback. The callback runs even when the row is Missing, matching Android's current attempted-update-then-navigation behavior. Raw uses `leaveRaw`, which invokes navigation after a valid save or a Rejected result.
- Android's adapter must continue through `updateTrafficConfigProfile`, including its read-known-SKIPI-from-document then write-known-SKIPI-to-document step when raw content changes. Do not promise preservation of unknown keys inside `[SKIPI]`; unrelated raw sections follow the existing writer contract. Desktop conversion must keep `versionedDesktopProfileResourceUserAgent` as the profile fallback and preserve explicit empty profile values.

## Test-first boundary

- `TrafficConfigEditorControllerTest` pins latest same-ID updates, no mutation of other profiles, typed `[SKIPI]` round-trip, unrelated section preservation, DNS normalization, missing-row behavior, Basics-before-section/Back, General and DNS Back/Save-before-leave, and raw save-before-leave / rejected-save-still-leaves coordination.
- `TrafficConfigEditorAndroidOracleTest` exercises the current Android `AppState` row updater together with the exact shared transforms used by `TrafficConfigEditorPages`. It is a source oracle for state/document behavior, not a Compose rendering or navigation test.
- `DesktopTrafficConfigEditorProfileStoreTest` pins the Desktop adapter contract over `DesktopTrafficConfigRepository`: latest same-ID transform, selected/counter/sibling preservation, no resurrection, and per-profile resource-UA fallback semantics including explicit empty values.
- Android and Desktop integration tests must prove that UI Edit opens the visual mode, Raw Edit stays available, and both hosts bind the common controller before production wiring is accepted.
