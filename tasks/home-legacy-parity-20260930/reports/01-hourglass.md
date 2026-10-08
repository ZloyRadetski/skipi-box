# Static hourglass restoration

- **Reference:** local tag `v0.4.1`, commit `0326b6a7b7355f8bd7e33c2c8c7f5c6748f9e0c0`. The old `StaticHourglass` in `app/src/main/kotlin/ui/icons/HourglassAnimation.kt` draws the hourglass and filled upper sand chamber on `Canvas`.
- **Changes:** added public shared composable `SkipiProxyHeroStaticHourglassIcon(modifier: Modifier = Modifier, color: Color, size: Dp = 20.dp)` in `SkipiProxyHeroVisuals.kt`; idle latency actions in `SkipiProxyHomeHeader.kt` and both idle branches of `SkipiProxyHomeFloatingToolbar.kt` now use this canvas drawing at the legacy 20 dp drawing size. Existing action bounds and content descriptions remain in place. The active 1600 ms animation code was not changed.
- **Integration:** `SkipiProxyHeroStaticHourglassIcon` is public in `app.skipi.ui.home`; remaining common Home call sites can reuse it for the same static appearance.
- **Validation:** `flock '/tmp/skipi-home-legacy-parity-gradle.lock' ./gradlew :shared:ui:compileKotlinDesktop --console=plain` — `BUILD SUCCESSFUL`.
- **Limitations:** verified the desktop target's common Kotlin compilation; no Android-target compilation or visual device check was run.
