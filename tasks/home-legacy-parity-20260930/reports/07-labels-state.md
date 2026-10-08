# Home labels and subscription state

## Scope

Restored the v0.4.1 Home presentation details in the portable formatter and the Android/Desktop input adapters. No data persistence schema or VPN behavior changed.

## Presentation facts and contracts

- `ProxyServerPresentationFormatter` now drives the Home server summary on both platforms. Ordinary proxies keep `getInfo().address`. Strategy summaries use localized strategy/source/filter templates; Select shows the selected member only when `selectedMemberId` resolves to a real current member, otherwise it shows the localized Select label and member count. Chains show available member names joined with ` -> ` and use the localized hop-count fallback when there are no member labels. This avoids presenting the first configured Select member as the active member.
- Android derives formatter group labels from the real `ProxyServerListGroups.groupNames` and sends its visible server nodes. Desktop derives labels and memberships from its stored subscription records; its ungrouped manual servers retain a `null` group ID in the shared presentation node. Desktop `StoredSubscription` has no separate persisted `profileTitle`; the refresh path stores the provider profile title in `name`, so Desktop continues to display `name` while Android uses `profileTitle` with `name` as its fallback.
- `ProxySubscriptionSummary` carries additive, defaulted `pinging` and `canCancelPing` flags. Android sets both from its actual group ping operation and supports tapping again to cancel that group’s latency operation. Desktop sets `pinging` from the active endpoint-check run, disables ping actions while a run is active, and rejects another ping effect while busy; Desktop has no same-group cancellation handler. The UI leaves the Android cancel tap available and disables Desktop repeat starts.
- Both platforms keep the actual `lastUpdatedAtMillis` and format it using the host locale’s short date and time format. The shared UI receives that formatted value through localized timestamp templates in English, Russian, Persian, Simplified Chinese, and Traditional Chinese resources. Common code uses an expect/actual formatter so `java.text.DateFormat` stays in platform source sets.

## Files

- Shared model and logic: `shared/app/src/commonMain/kotlin/app/skipi/app/home/ProxyHomeStore.kt`; `shared/core/src/commonMain/kotlin/features/proxy/server/presentation/ProxyServerPresentation.kt`; `shared/core/src/commonMain/kotlin/features/subscription/SubscriptionUpdateDateTime.kt` and Android/Desktop actuals.
- Platform adapters: `app/src/main/kotlin/features/proxy/server/list/ProxyHomeAndroidInput.kt`, the narrow formatter/ping-state wiring in `app/src/main/kotlin/features/proxy/server/list/ProxyServerListPage.kt`, `desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt`, and the Desktop latency operation callbacks in `desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt`.
- Shared Home card mapping: the `HomeSubscriptionCard` section of `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt` and the five localized `home.xml` resources.
- Regression coverage: `shared/core/src/commonTest/kotlin/features/proxy/server/presentation/ProxyServerPresentationTest.kt`, `shared/core/src/commonTest/kotlin/features/subscription/SubscriptionUpdateDateTimeTest.kt`, `app/src/test/kotlin/features/proxy/server/list/ProxyHomeAndroidInputTest.kt`, and `desktopApp/src/test/kotlin/app/skipi/desktop/DesktopProxyHomeEffectTest.kt`.

## Validation

Passed, serialized with the required Gradle lock:

```text
ANDROID_HOME=/home/vqsego/Android/Sdk flock -w 60 /tmp/skipi-home-legacy-parity-gradle.lock ./gradlew \
  :shared:core:desktopTest --tests features.proxy.server.presentation.ProxyServerPresentationTest --tests features.subscription.SubscriptionUpdateDateTimeTest \
  :shared:app:desktopTest --tests app.skipi.app.home.ProxyHomeStoreTest \
  :app:testDebugUnitTest --tests features.proxy.server.list.ProxyHomeAndroidInputTest \
  :desktopApp:test --tests app.skipi.desktop.DesktopProxyHomeEffectTest --console=plain
```

Result: `BUILD SUCCESSFUL` (118 actionable tasks; 32 executed). The focused `git diff --check` over files in this task also passed.

## Limits

Desktop ping cancellation remains unsupported by its existing latency-operation API; Home now reflects the busy run and prevents another subscription ping while it is active. No live Android/Desktop UI session was used for visual verification.
