# DesktopRoutingRepository — source-backed test contract

**Executed: four routing cases GREEN.** The adapter implements the existing `RoutingRepository` without a new common API. Four tests were installed before production, recorded expected API RED (21 s), then passed in the 20-case focused gate (12 s) with unchanged source SHA. Reviewed draft provenance remains in the adjacent `.kt.draft`; see `tasks/shared-migration-20261023/r01-desktop-routing-repository-verification.md`.

Android `AndroidRoutingRepository.update` maps `RoutingConfigRecord` to `AppState` and preserves `nextRouteRuleId` as `maxOf(currentCounter, maxRuleId + 1)` (`app/src/main/kotlin/data/repository/AndroidAppRepositories.kt:229–247`). Desktop has equivalent persisted fields, including the counter, in `DesktopAppSettings` (`desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsLibrary.kt:104–108`). `DesktopSettingsLibraries.save/load` uses the actual settings JSON and writes atomically when supported (`DesktopSettingsLibrary.kt:126–145`).

The shared contract is `RoutingRepository.state` plus `update(transform)` (`shared/app/src/commonMain/kotlin/app/skipi/app/repository/RepositoryContracts.kt:81–85`); `SharedApplicationStore` forwards `UpdateRouting` to the configured repository and exposes its state (`shared/app/src/commonMain/kotlin/app/skipi/app/store/SharedApplicationStore.kt:136, 253–255`). At the initial spec checkpoint, Desktop's application factory omitted that repository (`DesktopApplicationRepositories.kt:391–415`).

Reviewed and implemented host API, following Desktop repository callback style:

```kotlin
DesktopRoutingRepository(
    initialSettings: DesktopAppSettings,
    readSettings: () -> DesktopAppSettings,
    saveSettings: (DesktopAppSettings) -> Result<Unit>,
    publishSettings: (DesktopAppSettings) -> Unit,
)
```

`createDesktopSharedApplication` receives a required `routing: DesktopRoutingRepository`, passes the same instance into `AppRepositories.routing`, and exposes it as `DesktopSharedApplication.routing`. `Main` constructs this adapter with the existing settings read/save/publish callbacks.

The Desktop `LaunchedEffect` at `Main.kt:365–380` refreshes settings, subscriptions, traffic configs, and runtime snapshots when the corresponding host snapshots change. Android routing state remains a live projection of `AppState`; once the Desktop adapter is connected, its own flow must likewise refresh when settings reload or restore. Add `DesktopRoutingRepository.refresh()` to reread settings and update only its `RoutingConfigRecord` state. This follows the existing `DesktopSettingsRepository.refresh()` pattern; a refresh must not save settings or invoke the Desktop settings publish callback.

The adapter's host API therefore also exposes `fun refresh()` in addition to the `RoutingRepository` contract methods.

Tests must use a temporary `settings.json` and the real `DesktopSettingsLibraries` reader/writer. An update must transform a fresh `readSettings()` snapshot, modify only route domain strategy/default outbound/rules, and persist `nextRouteRuleId = maxOf(previousCounter, max(updatedRuleIds) + 1)`. Unrelated settings changed on disk after repository construction must survive. On save failure, the caller receives that failure and neither the file, the repository `StateFlow`, nor the publish callback changes. A factory/store case verifies `UpdateRouting` traverses the configured repository. No retries, compare-and-set, new runtime policy, UI, or generated-config behavior is part of this draft.

The fourth test changes routing fields, unrelated preferences, and the rule-ID counter in the real settings JSON after repository construction; `refresh()` must update the repository state from that file while leaving its contents, counter, save-callback count, and publish-callback count unchanged.
