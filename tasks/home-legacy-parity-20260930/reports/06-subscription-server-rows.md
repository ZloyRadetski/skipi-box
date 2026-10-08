# Task 06: subscription and server rows

## Reference

Compared the Home server rows and provider section with local tag `v0.4.1` (`0326b6a7b7355f8bd7e33c2c8c7f5c6748f9e0c0`), especially `ProxyServerListPager.kt` lines 312–429 and 640–656 and `ProxyServerListComponents.kt` lines 436–775. The restored provider panel uses an 18 dp radius, 1 dp border at 14% on-surface alpha, 0.8 dp separators at 8% alpha, and 6 dp horizontal row insets. Regular subscription rows are 66 dp; strategy rows are 58 dp. Both use 8 dp internal padding, a 32 dp flag, 15 sp single-line title, and 12 sp single-line summary and latency.

## Changes

- Subscription groups now render their provider header and visible server rows as adjacent full-span items in `LazyVerticalGrid`. Rows keep stable `server-${id}` keys and remain lazy. Search and collapse determine the visible last row so the provider panel’s rounded bottom edge follows the last visible item. The expanded empty state is also inside the panel.
- Added `SkipiSubscriptionServerPanelSegment`, a focused common helper that draws the provider surface, open header bottom, side borders, inset row separators, and rounded final bottom corners across the separate lazy items.
- `HomeSubscriptionCard` now sets `embeddedInPanel = true` on `SkipiSubscriptionSummaryCard`, consuming the API from task 05 while preserving task 07’s summary and action mapping.
- `ServerListItem` accepts `inSubscriptionGroup` and forces subscription servers through the compact component at every column count. The compact card now uses exact 66/58 dp subscription heights even when the theme’s normal 64 dp row minimum would enlarge a strategy row. Subscription typography and padding match the old dimensions; ordinary compact rows retain their existing adaptive sizing.
- Ordinary one-column server cards retain expanded layout. Supported copy formats, edit, and delete are available inline; QR, strategy member selection, and move actions remain in overflow. The idle test action uses `SkipiProxyHeroStaticHourglassIcon`; the existing 14/12 dp latency progress indicators remain.
- Set the grid’s vertical arrangement to zero so the provider items touch. Restored the prior ordinary-screen spacing with per-item padding for the header, status, section heading, empty state, and server cards.

## Files

- `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt`
- `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyServerCompactListCard.kt`
- `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionServerPanel.kt`

## Validation

- `git diff --check` for the modified tracked UI files — passed; `rg -n '[[:blank:]]+$'` found no trailing whitespace in the new panel helper or this report.
- `flock /tmp/skipi-home-legacy-parity-gradle.lock ./gradlew :shared:ui:compileKotlinDesktop --console=plain` — `BUILD SUCCESSFUL`.
- Read reports 01, 05, and 07 before final validation; the final compile included the available task 07 changes.

## Limits

No live Android or Desktop visual check was run, per the task instructions. Common code compiled for the desktop target; the Android target was not compiled in this task.
