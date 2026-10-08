# Task 04: bottom inset correction

Reference checked: `v0.4.1` (`0326b6a7b7355f8bd7e33c2c8c7f5c6748f9e0c0`). Its Android Home page kept the page root on `pageWindowPadding(padding)` (horizontal and IME handling) and put the Scaffold bottom clearance into the scrolling list padding. The current shared Home had instead applied that bottom value to its background Box, shortening the viewport above the floating navigation island.

## Changes

- `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeLayout.kt` adds `proxyHomeBottomInsetTreatment`, which splits an overlaid bottom inset between viewport, list and floating-toolbar clearance.
- `shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt` adds the optional, backward-compatible `floatingNavigationBottomInset` parameter. The Android compact host supplies its measured Scaffold bottom padding. Home now leaves that inset out of the viewport padding, adds it once to every lazy-grid bottom clearance, and raises the floating Home toolbar by the same inset. The existing toolbar reserve remains 88 dp; the existing no-toolbar list spacing remains in place. Callers that use the default zero keep their prior viewport padding behavior.
- `app/src/main/kotlin/features/proxy/server/list/ProxyServerListPage.kt` passes the measured bottom padding only when using compact navigation. Wide Android keeps its existing system-navigation inset on the viewport. Existing `pageWindowPadding(padding)` still handles horizontal window padding and IME; cutout padding remains in the root `contentPadding`.
- `shared/ui/src/commonTest/kotlin/app/skipi/ui/home/ProxyHomeLayoutTest.kt` covers compact inset transfer and the unchanged Desktop default, including logical side padding.

## Resulting inset flow

On compact Android, the Scaffold's measured bottom-bar padding (including the navigation bar's own safe-area padding) no longer reduces Home's root viewport. The background fills behind the floating island. Each lazy grid reserves that same measured clearance, plus the existing Home toolbar reserve when shown, so the final row and its actions can scroll above both controls. The toolbar itself sits above the floating island. Pinned and scrolling-header layouts use the same list clearance.

Desktop callers pass no floating-navigation inset, so their supplied `contentPadding` remains on the viewport and the shared list spacing is unchanged. Wide Android also keeps the existing viewport bottom inset. Top, horizontal, cutout and IME handling were preserved.

## Validation

- Passed: `flock /tmp/skipi-home-legacy-parity-gradle.lock ./gradlew --offline :shared:ui:desktopTest --tests 'app.skipi.ui.home.ProxyHomeLayoutTest'` (`BUILD SUCCESSFUL`).
- Android compile attempted with `flock /tmp/skipi-home-legacy-parity-gradle.lock ./gradlew --offline :app:compileDebugKotlin`; it could not configure the task because no Android SDK location is configured (`ANDROID_HOME` or `local.properties` `sdk.dir`).
- Targeted `git diff --check` for `ProxyHomeScreen.kt` and `ProxyServerListPage.kt` was clean. The repository-wide check also reports an unrelated pre-existing extra blank line at EOF in `desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyGroups.kt`.
- No app or phone was launched and no visual screenshot was taken, per task instructions. Final integrated visual review remains with the orchestrator.
