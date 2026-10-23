# DesktopRoutingRepository — source-backed test contract

**Draft only.** The proposed adapter implements the existing `RoutingRepository`; it does not introduce a routing-specific API in shared code.

Android `AndroidRoutingRepository.update` maps `RoutingConfigRecord` to `AppState` and preserves `nextRouteRuleId` as `maxOf(currentCounter, maxRuleId + 1)` (`app/src/main/kotlin/data/repository/AndroidAppRepositories.kt:229–247`). Desktop has equivalent persisted fields, including the counter, in `DesktopAppSettings` (`desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsLibrary.kt:104–108`). `DesktopSettingsLibraries.save/load` uses the actual settings JSON and writes atomically when supported (`DesktopSettingsLibrary.kt:126–145`).

The shared contract is `RoutingRepository.state` plus `update(transform)` (`shared/app/src/commonMain/kotlin/app/skipi/app/repository/RepositoryContracts.kt:81–85`); `SharedApplicationStore` forwards `UpdateRouting` to the configured repository and exposes its state (`shared/app/src/commonMain/kotlin/app/skipi/app/store/SharedApplicationStore.kt:136, 253–255`). Desktop's current application factory omits that repository (`DesktopApplicationRepositories.kt:391–415`).

Proposed host API, following Desktop repository callback style:

```kotlin
DesktopRoutingRepository(
    initialSettings: DesktopAppSettings,
    readSettings: () -> DesktopAppSettings,
    saveSettings: (DesktopAppSettings) -> Result<Unit>,
    publishSettings: (DesktopAppSettings) -> Unit,
)
```

`createDesktopSharedApplication` receives a required `routing: DesktopRoutingRepository`, passes the same instance into `AppRepositories.routing`, and exposes it as `DesktopSharedApplication.routing`. `Main` constructs this adapter with the existing settings read/save/publish callbacks.

Tests must use a temporary `settings.json` and the real `DesktopSettingsLibraries` reader/writer. An update must transform a fresh `readSettings()` snapshot, modify only route domain strategy/default outbound/rules, and persist `nextRouteRuleId = maxOf(previousCounter, max(updatedRuleIds) + 1)`. Unrelated settings changed on disk after repository construction must survive. On save failure, the caller receives that failure and neither the file, the repository `StateFlow`, nor the publish callback changes. A factory/store case verifies `UpdateRouting` traverses the configured repository. No retries, compare-and-set, new runtime policy, UI, or generated-config behavior is part of this draft.
