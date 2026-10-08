# Task 03: Pinned-panel and visibility parity

## Reference and findings

- Compared `v0.4.1` `ProxyServerListPage.kt` around lines 646–808 and `ProxyServerListTopBar.kt` around lines 329–367. The old page places the unpinned connection panel, group tabs, and search field in the pager page header. When pinned, the connection panel and the animated group/search controls are top-bar siblings before the weighted pager. The old group selector visibility rule is `groupState.showGroupTabs`, which is `visibleGroups.size > 1`; the Home summaries passed to the new UI use those same `groupTabs`. Search visibility is gated by the search preference and its presentation toggle.
- Pin preference is still wired to the actual Home: it defaults to `false` in `AppState`, is read and written by `AppSettingsPreferences`, is included in `AppState.toProxyHomeInput()` as `ProxyHomeDisplayOptions.pinConnectionPanel`, then reaches `ProxyHomeScreen` through `ProxyHomeStore`. The v0.4.1 default was also `false`.
- The active root remains in `ProxyHomeScreen.kt`; `SkipiProxyHomeScaffold` currently has no call sites, so no ownership adjustment was needed.
- Narrow and medium layouts already had the right pin structure: pinned controls are normal siblings above the weighted list box, while `pinConnection` removes the scrolling header to prevent duplication. Unpinned controls live in each pager page's scrolling header. This keeps the panel in normal flow without an overlay or `zIndex`.
- Wide layout retains its existing list/details arrangement: the connection panel occupies a fixed row above the list/details split, while group and search controls stay in the selected page's scrolling header. The pin setting is applied in the one-column branch; I left the established wide arrangement intact.

## Changes

- In `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt`, wrapped group-selector and search visibility in `AnimatedVisibility` with the v0.4.1 transitions: `fadeIn() + expandVertically()` and `shrinkVertically() + fadeOut()`. This covers the wide scrolling header, the pinned narrow/middle header, the empty-page fallback, and every pager page's unpinned scrolling header.
- Preserved the existing search action, query value, localized label, group-selector calls, and their semantics. The wrappers do not add focus state or change the existing accessibility labels.
- Kept sibling spacing conditional around visible group/search controls so collapsed `AnimatedVisibility` children do not leave an extra `Arrangement.spacedBy` gap. The pinned connection panel only reserves space for controls that are shown, matching the old pinned header's conditional bottom spacing.
- No public API changed. No changes were made to root insets, the selector implementation, list/server rendering, or subscription mapping.

## Validation

- `git diff --check -- shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt` — passed.
- `flock /tmp/skipi-home-legacy-parity-gradle.lock env GRADLE_USER_HOME=/tmp/skipi-home-legacy-parity-gradle-home /home/vqsego/.gradle/wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6/gradle-9.7.0/bin/gradle --offline --no-daemon :shared:ui:compileKotlinDesktop :shared:ui:desktopTest` — passed; shared UI desktop compile and existing desktop tests completed successfully.
- `flock /tmp/skipi-home-legacy-parity-gradle.lock env GRADLE_USER_HOME=/tmp/skipi-home-legacy-parity-gradle-home /home/vqsego/.gradle/wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6/gradle-9.7.0/bin/gradle --offline --no-daemon --rerun-tasks :shared:ui:compileKotlinDesktop` — passed; forced compilation recompiled the shared UI source after the first compile task reported up-to-date.

No remaining code or compile limitation. Per task instructions, I did not launch the app or perform device/visual checks; those remain for integration review.
