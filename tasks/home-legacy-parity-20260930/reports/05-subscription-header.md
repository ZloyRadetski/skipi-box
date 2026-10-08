# Task 5: compact subscription header

## Reference

Compared the shared card with `v0.4.1` (`0326b6a7b7355f8bd7e33c2c8c7f5c6748f9e0c0`), especially `SubscriptionProviderHeader` in `app/src/main/kotlin/features/subscription/SubscriptionProviderCard.kt` and its continuous provider panel in `app/src/main/kotlin/features/proxy/server/list/ProxyServerListPager.kt`.

## Changes

- Rebuilt the summary as a compact, single-row header: 16 dp horizontal and 10 dp vertical inset, 48 dp expand/collapse control, one-line 16 sp semibold title, server count, and refresh, ping, and edit actions beside the title.
- Refresh shows a 20 dp progress indicator while busy. Ping uses the shared static hourglass at rest and the animated hourglass while active. The active Ping action remains enabled when the host exposes the action so Android's same-group tap can cancel its in-flight measurement.
- Header-row taps expand or collapse details; long-press edits when editing is available. Expanded details render only nonblank metadata, include a 6 dp traffic bar, and preserve announcement, support, and site actions.
- Kept meaningful enabled/auto-update status within the expanded details, so it adds no collapsed header row. The removed subscription-enabled toggle remains absent.
- Added `embeddedInPanel: Boolean = false` to `SkipiSubscriptionSummaryCard`. Set it to `true` when the caller places this header above server rows inside one continuous panel; it omits the standalone card surface and border. The standalone default uses an 18 dp radius and a 1 dp, 14% on-surface border.
- Added `pinging: Boolean = false` to `SkipiSubscriptionSummaryState` for the platform summary projection.

The card calls `SkipiProxyHeroStaticHourglassIcon(modifier: Modifier = Modifier, color: Color, size: Dp = 20.dp)` when idle and `SkipiProxyHeroAnimatedHourglassIcon(modifier: Modifier = Modifier, color: Color = Color.Unspecified, size: Dp = 20.dp)` while pinging.

## Files

- `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryCard.kt`
- `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryPanel.kt`
- `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryDetails.kt`

The two focused helpers keep the public state/card API separate from the header controls and expanded metadata rendering.

## Integration notes

- `ProxyHomeScreen` now forwards `subscription.pinging` to the new state property. Its caller should pass `embeddedInPanel = true` when the header shares the outer panel with the compact server rows.
- Android's subscription ping callback treats a second same-group ping as cancellation. The card leaves the action available for that active state. Desktop's current `PingSubscription` effect calls its ping callback and its effect handler reports latency-test cancellation as unsupported; Desktop should leave `pinging` false unless it gains a real cancellable run.
- No additional cancellation callback is needed in the card: the existing `onPing` intent carries the platform-specific behavior.

## Validation

- `git diff --check` passed for the owned UI files.
- `flock /tmp/skipi-home-legacy-parity-gradle.lock ./gradlew :shared:ui:compileKotlinDesktop --rerun-tasks` passed (`BUILD SUCCESSFUL`). This compiles the shared UI's common sources for the desktop target.
- `:shared:ui:compileAndroidMain` could not be verified because no Android SDK path is configured in this checkout (`ANDROID_HOME`/`sdk.dir` missing).
