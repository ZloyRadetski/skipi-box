# R03 Per-App preservation acceptance

## Scope

This is a tests-only acceptance slice for profile Per-App mode and selected IDs. It keeps three persistence paths distinct: the shared profile document codec canonicalizes list values, the portable backup DTO copies profile lists exactly, and Desktop edits a profile through its real `configs.json` adapter using a temporary test directory. The tests do not introduce platform package enumeration or runtime routing behavior.

## Source and method map

- `TrafficConfigState.withSkipiSettingsReadFromRawConfig()` in `shared/core/src/commonMain/kotlin/features/config/TrafficConfigDocument.kt` reads `[SKIPI]` Per-App values. It maps the last mode to global/blacklist/whitelist and trims, removes blanks, and de-duplicates package ID strings while preserving first-seen order. When there are no `[SKIPI]` values, it consults the legacy Per-App comments.
- `TrafficConfigState.withSkipiSettingsInRawConfig()` writes the current profile settings back into the profile document. The Per-App writer trims values and removes blanks and duplicates. The acceptance case includes a separate `[General]` value to check that this edit keeps an unrelated section.
- `TrafficConfigState.toAppBackupTrafficConfig()` and `AppBackupTrafficConfig.toTrafficConfigState()` in `shared/core/src/commonMain/kotlin/features/backup/AppBackupMappers.kt` copy the profile mode and selected ID list directly. `encodeAppBackup()` and `decodeAppBackup()` in `AppBackupCodec.kt` serialize the backup DTO. Global selection IDs are a separate `AppBackupData.proxyAppListSelectedApps` field, and the global mode lives in `AppBackupSettings.proxyAppListMode`; `activeTrafficConfigId` independently identifies the selected profile.
- `DesktopConfigLibraries.save()` and `load()` in `desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigLibrary.kt` persist and reload the profile library JSON. The test passes a temporary `configs.json` path. `DesktopTrafficConfigEditorProfileStore.update()` maps the shared editor update onto the latest profile row; `TrafficConfigEditorController.setProxyAppListMode()` and `setProxyAppListSelection()` make the two profile edits under test. `ConfigProfileLibrary.selectedConfigId` remains the active profile pointer while another row is edited.

## New acceptance tests

- `shared/core/src/commonTest/kotlin/features/config/PerAppProfileDocumentRoundTripTest.kt` verifies profile document parse/write/reparse of whitelist mode and opaque IDs. It pins canonical document behavior for whitespace, blanks, and duplicates, including an unprefixed legacy value and a `10:` other-user ID; it also checks that the separate `[General]` DNS line remains.
- `shared/core/src/commonTest/kotlin/features/backup/PerAppBackupRoundTripTest.kt` verifies both the direct traffic-config DTO mapper and encoded backup round-trip. Its deliberately noncanonical profile list (whitespace, blank, duplicate, and opaque IDs) must survive exactly. It separately checks the global selected IDs and mode, another profile's values, and the active profile ID.
- `desktopApp/src/test/kotlin/app/skipi/desktop/DesktopPerAppPreservationRoundTripTest.kt` writes a minimal two-profile library to temporary `configs.json`, edits the non-active profile through the shared controller and Desktop store, then reloads the file. It checks mode, legacy/unknown and other-user IDs, the newly checked ID, the selected sibling, row order, next ID, and the unrelated General DNS value.

## Preservation boundary and gaps

The profile-document format treats IDs as opaque strings. The test asserts that document normalization trims, drops blanks, and de-duplicates IDs; it does not promise byte-for-byte preservation of that input list. The backup DTO path performs a direct list copy and is expected to preserve the exact list, including noncanonical values.

These tests do not assert preservation of unknown JSON properties or unmodeled `[SKIPI]` keys. They do not test Android's application-state mapper, native package discovery, clipboard import/export, UI presentation, or actual OS routing. Desktop has no PackageManager-backed app catalog or per-app route enforcement; this slice checks storage and editing of portable IDs without treating them as installed applications. Existing shared editor/UI tests remain the source for controller presentation and selection behavior, so this acceptance set avoids repeating those layout assertions.
