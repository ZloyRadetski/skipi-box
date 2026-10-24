# R03 portable Per-App presentation slice

This is the source map and tests-first proposal for the next R03 slice. No production source was changed and no build or test command was run. A focused Compose scene test is in the new Desktop test source [ProxyAppListPresentationIntegrationTest.kt](../../desktopApp/src/test/kotlin/app/skipi/desktop/ProxyAppListPresentationIntegrationTest.kt). The test exercises shared components with fixture rows only; the actual Desktop Per-App route remains the existing capability notice with profile editing and no app inventory.

## Current seam and source responsibilities

P04 already provides the correct two slots: `headerContent` is placed below the common editor top bar; `appContent` receives content/list padding, `ScrollBehavior`, selected keys, mode index, and the selection callback ([shared screen parameters and call](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigProxyAppListEditorScreen.kt#L80), [slot invocation](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigProxyAppListEditorScreen.kt#L173)). Android supplies search/tabs and list/pager from those slots ([Android header](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPage.kt#L146), [Android app content](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPage.kt#L165)). Desktop supplies only its existing notice and message region in `appContent`; it passes no catalog, search, or user tabs ([Desktop call](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt#L523)). The slots can remain unchanged.

| Current source | Move into `shared/ui` | Keep in Android host |
| --- | --- | --- |
| [`ProxyAppListComponents.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListComponents.kt#L58) search, user tabs, empty state, card, and UID chip | Common SearchBar and its 150 ms latest-query debounce; tabs and their `tabs.size > 1` guard; empty/result row; app card and UID/SUID chip. The common row owns label, package text, checkbox, card toggle, and selection callback. Global mode is identified by shared `ProxyAppListModeGlobal` (index 2). | The icon node itself. Pass `iconContent(app, enabled)` from Android; it keeps the current 44 dp frame, Coil request, animated icon background, alpha, and grayscale treatment. |
| [`ProxyAppListPage.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPage.kt#L247) HorizontalPager, user page, LazyColumn, empty/list row dispatch, scrollbar, refresh UI | Common pages own pager state keyed by current tab IDs, selected-page synchronization/clamping, page-to-selected-user callbacks, empty/list rows, scrollbar, and the shared `AppPullToRefresh` wrapper ([pull-to-refresh](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/components/AppPullToRefresh.kt#L270), [page layout helpers](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/layout/PageLayout.kt#L33)). | The loading overlay wrapper and minimum-visible timing based on `SystemClock.elapsedRealtime()` ([overlay](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPage.kt#L195)); Android passes `isRefreshing`/`onRefresh` and its actual icon composable. |
| [`ProxyAppListPageState.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPageState.kt#L45) 150 ms search debounce, tab/pager synchronization | Search debounce belongs with common SearchBar behavior; pager index clamp, tab-to-page animation, and page-to-selected-user callback belong with common pages. | Raw query and selected user ID remain Android state values/callbacks. User-space/package provider facts, `showSystemApps`, refresh/load flags, VPN-service current-user restriction, package-filter key, and record conversion remain Android-owned. |
| [`ProxyAppListPageState.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPageState.kt#L224) provider effects | None. These are lifecycle and host adapter duties. | PackageManager scopes and own-package exclusion, provider/user-space calls and fallback, cancellation propagation, error-to-toast behavior, loading flags, and current-user-only selection for VPN-service mode. |

Shared UI already has the needed package summary, search, empty, pull-to-refresh, and loading strings in every current shared locale ([resource entries](../../shared/ui/src/commonMain/composeResources/values/strings.xml#L60), [Per-App strings](../../shared/ui/src/commonMain/composeResources/values/strings.xml#L512)). Use generated `Res.string.*` and `SkipiTheme`/`MiuixTheme` in common code. The Android `ui.AppTheme` is a compatibility alias over the shared theme; common components should not import app `R`, `ui.AppTheme`, `ANDROID_APP_ICON_SIZE_DP`, `ProxyAppIconRequest`, or Coil. The R03 core projection is expected to supply `AppPackageEntry` and `ProxyAppListItem` in shared core. Shared UI already depends on shared core, and the P04 editor's API exposes the common Miuix `ScrollBehavior` through `shared/ui`'s public dependency ([module dependencies](../../shared/ui/build.gradle.kts#L36)).

The user tabs and pager are portable behavior, but obtaining the tab values is Android work. The common header displays the supplied `ProxyAppListUserSpaceTabUi` values and calls `onSelectedUserIdChange`; the common pages keep a `PagerState`, key/reset it from current tab IDs, clamp when tabs shrink, animate toward the externally selected user when not swiping, and publish the target page through that callback. Android continues to initialize/repair `selectedUserId` after user-space provider loads. The shared search bar reports raw edits immediately and debounced edits after the current 150 ms interval; Android stores both values and gives the debounced one to the common projection.

## Approved common API

Place these components in `shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigProxyAppListComponents.kt`, package `app.skipi.ui.config`. These approved signatures deliberately fit P04's existing slots:

```kotlin
@Composable
fun SkipiTrafficConfigProxyAppListSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onDebouncedQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun SkipiTrafficConfigProxyAppListUserTabs(
    tabs: List<ProxyAppListUserSpaceTabUi>,
    selectedUserId: Int?,
    onSelectedUserIdChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun SkipiTrafficConfigProxyAppListPages(
    itemsByUser: Map<Int, List<ProxyAppListItem>>,
    tabs: List<ProxyAppListUserSpaceTabUi>,
    selectedUserId: Int?,
    onSelectedUserIdChange: (Int) -> Unit,
    selectedKeys: Set<String>,
    modeIndex: Int,
    contentPadding: PaddingValues,
    listPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    iconContent: @Composable (app: AppPackageEntry, enabled: Boolean) -> Unit,
    onSelectionChange: (selectionKeys: List<String>, isChecked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
```

`SkipiTrafficConfigProxyAppListPages` would own the shared pager/list/refresh presentation, not package loading. It computes checked state from any `item.selectionKeys` intersecting `selectedKeys`, derives row enablement from `modeIndex != ProxyAppListModeGlobal`, and forwards `item.selectionKeys` plus the next checked value to `onSelectionChange`. A private common row calls the Android icon slot. The common empty row uses `Res.string.common_empty`. The current shared `AppPullToRefresh` accepts a `HapticFeedback`; the common pages can pass `LocalHapticFeedback.current` and preserve current refresh behavior. `showSystemApps` stays in the P04 More menu callback; Android continues to own its value because it changes the PackageManager query.

The approved boundary places `AppPullToRefresh` inside `SkipiTrafficConfigProxyAppListPages`, with refresh state/callback passed in. It moves the portable list presentation while the 500 ms minimum-loading clock and provider lifecycle remain Android-owned. `SkipiTrafficConfigProxyAppListUserTabs` owns the existing `tabs.size > 1` display guard, so VPN-service/current-user mode continues to show one filtered user page without a tab strip. The common page receives shared `ProxyAppListModeGlobal` (value 2) rather than a new mode value or desktop-specific enforcement.

## Test proposal and current test coverage

There is no Android Compose UI test for `ProxyAppListPage` or its PackageManager list. The Android sources currently have the R03 projection oracle at [`ProxyAppListProjectionAndroidOracleTest.kt`](../../app/src/test/kotlin/features/proxy/app/ProxyAppListProjectionAndroidOracleTest.kt) and the existing toggle/unknown-ID guard at [`ProxyAppListSelectionTest.kt`](../../app/src/test/kotlin/features/proxy/app/ProxyAppListSelectionTest.kt). These remain appropriate Android data/selection checks; they do not render common components.

The P04 editor has an established Compose semantics harness in [`DesktopTrafficConfigPerAppScreenIntegrationTest.kt`](../../desktopApp/src/test/kotlin/app/skipi/desktop/DesktopTrafficConfigPerAppScreenIntegrationTest.kt#L67), which creates `ImageComposeScene` and provides the shared theme/resources ([harness setup](../../desktopApp/src/test/kotlin/app/skipi/desktop/DesktopTrafficConfigPerAppScreenIntegrationTest.kt#L1266)). The `shared/ui` common tests currently use `kotlin.test` for pure state/projection; the module's `commonTest` dependencies do not add a Compose UI scene-testing library ([test dependencies](../../shared/ui/build.gradle.kts#L50)). The new Desktop host test renders shared components directly with test-only package facts and a fake icon slot. It does not route Desktop's actual Per-App screen through fake rows or a fake provider. The initial Compose scene test is the common UI behavior oracle because current Android app tests lack a Compose UI test harness without dependency changes.

The new component test pins these source-backed behaviors:

1. Entering a query publishes its raw value immediately; a newer query cancels the older 150 ms debounce; after the quiet interval the common callback reports only the latest query. The Android projection oracle already covers search matching; the UI test pins only debounce behavior. The existing `ImageComposeScene` host has wall-clock frame rendering rather than a coroutine test scheduler connected to its composition, so the staged test uses the established scene render/delay pattern without adding dependencies.
2. Tab labels include provided checked counts. Clicking a tab calls the selected-user callback and changes the visible page; externally changing the selected ID changes the visible page. Horizontal pager `ScrollBy` semantics update the selected-user callback, and shrinking the tabs while on the removed page clamps to the remaining user. A one-tab/current-user fixture shows that user page and no tab strip.
3. A rendered row shows the provided label, package, UID/SUID state, and host icon-slot content. Clicking the row calls the selection callback with the full `selectionKeys` list, including its shared-UID companion. A fixture with only the companion key selected renders its checkbox checked, then unchecks the entire shared UID group. The ordinary UID label is also pinned. In global mode the row and checkbox do not change selection; the checkbox exposes disabled semantics.
4. A user page with no visible rows displays the localized empty state and does not render a fabricated package row.

The existing Desktop P04 integration remains the guard for profile routing/storage, clipboard, immediate edits, and the capability notice; its current Per-App assertions state that there is no Save draft action or editable package-name field ([screen assertions](../../desktopApp/src/test/kotlin/app/skipi/desktop/DesktopTrafficConfigPerAppScreenIntegrationTest.kt#L72)). Keep that route test using the real no-inventory behavior. A future Android adapter/screen smoke verifies real PackageManager enumeration, icon fetch, system/user filter, refresh/error/loading lifecycle, and VPN policy separately; those are not assertions of this shared component test. There is no Android Compose test harness in the current app test dependencies, so the original Android data/selection tests remain the baseline oracle during this shared UI slice.
