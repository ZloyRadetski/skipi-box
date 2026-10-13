# P04 read-only source contract and test draft

No production edits. The shared components exist, but Desktop does not wire the portable profile list/editor flow yet.

## Android source contract

- The profile catalog is `SkipiTrafficConfigScreen` (`shared/ui/.../SkipiTrafficConfigScreen.kt:67–139`), used by `TrafficConfigPage.kt:403`. Its context menu routes UI Edit to `Route.TrafficConfigEditor` and Raw Edit to `Route.TrafficConfigRawEditor` (`TrafficConfigPage.kt:518–529`).
- The profile-basis form exposes name, source URL, update lock, profile auto-update/interval when unlocked, and independent geo-resource auto-update/interval (`shared/ui/.../SkipiTrafficConfigProfileBasics.kt:41–105`). `TrafficConfigEditorPage` initializes those fields from the clicked profile, writes basics through `withProfileBasics`, saves on Back, and saves before entering each section (`app/.../TrafficConfigEditorPages.kt:96–245`).
- General, DNS, tunnel, and network sections use the shared forms and both Back and Save persist before leaving (`TrafficConfigEditorPages.kt:342–490`). Routing opens Resource, Rules, and Proxy Groups sections; their existing routes and save/update behavior remain the source contract.
- Android raw editing is a distinct route. `TrafficConfigRawEditorPage.save()` rejects blank/diagnostic-error text, otherwise persists `trimEnd() + "\n"`; Back always calls `save()` and then pops (`TrafficConfigEditorPages.kt:257–320`, especially 276–295). Therefore valid text is saved on Back; invalid/blank text is not saved, but Back still leaves the page. No new discard confirmation is part of the source behavior.
- Profile/model transforms are already portable and tested (`shared/app/.../TrafficConfigEditorOperations.kt:15–83`; `TrafficConfigEditorOperationsTest`). The shared raw-editor state only covers its save eligibility (`shared/ui/.../SkipiTrafficConfigRawEditor.kt:32–48`; `SkipiTrafficConfigRawEditorStateTest`); there is no shared profile-editor controller coordinating draft, navigation, Save, and Back.

## Desktop gap

`DesktopConfigsScreen` builds a separate profile list (`:313–440`) and reuses only the profile card (`:595–629`). Its context menu sets `showUiEdit = false` and routes `onUiEdit` to the raw editor (`:459–475`). The current draft has only ID/name/content/source URL/update lock/time (`:90–97`); `DesktopConfigEditor` has only name/source URL/update lock/content (`:648–695`), so it cannot edit Android's update and resource schedule fields. Its Back clears the draft (`:282–285`) rather than saving valid raw text as Android does.

The migration should connect the shared full list, shared full-screen scaffold/forms, and shared context menu in both raw and UI modes; do not grow another Desktop-specific catalog/card/editor family. Per-app installed-package enumeration and actual network/VPN facts remain host capabilities; they do not justify withholding the portable profile, DNS, general, routing, proxy-group, or resource forms.

## Tests to activate after the P04 API/UI seam is approved

1. Shared editor state/form contract: initial values match the selected profile; lock hides profile schedule controls; resource scheduling remains independent; typed changes call the existing shared operations and synchronize the known `[SKIPI]` fields while preserving unrelated `.conf` sections according to the current writer.
2. Android source behavior: profile basics Back and navigation persist changes; general/DNS/tunnel/network Save and Back persist; raw Back saves valid normalized content and leaves invalid/blank content unchanged while still navigating away. Keep raw editing separate from visual typed forms.
3. Desktop integration: selecting UI Edit reaches the shared visual editor; all portable fields and sections bind to the same typed operations; Save and Back update the latest profile ID; Raw Edit remains available and Back follows the valid/invalid behavior above. Preserve the current menu actions and confirm deletion/selection paths still use their host callbacks.
4. Run common state/operation tests and focused Android/Desktop adapter suites, followed by manual visual QA at both window sizes. Existing `TrafficConfigEditorOperationsTest` and `SkipiTrafficConfigRawEditorStateTest` are baseline coverage, not evidence of Desktop wiring or rendered parity.
