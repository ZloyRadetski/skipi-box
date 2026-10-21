# Desktop Power — selected Custom with an active profile

Status: complete. Code verified and committed as `57bee17`; updated Linux package built and relaunched. This is a separate user-reported bug fix; it does not close any migration-plan card.

## Reproduction and source contract

The packaged Linux app opened successfully, but the user reported a disabled Power button with a selected server and a Disconnected status. A read-only diagnostic using the packaged config-builder APIs confirmed that the selected Custom server plus an active profile produced `IllegalArgumentException`, classified as `selected-server-unsupported-for-profile`. The diagnostic did not start Core, write application data, or print server/configuration contents.

`DesktopSelectedServerConfig.kt` previously selected the active-profile path before decoding the selected server. That path reaches `TrafficProfileXrayPlanner`, whose usable-server predicate excludes `Custom`. `Main.kt` consequently receives a failed selected-config result and disables the disconnected Power action.

The Android source takes a different order: `app/src/main/kotlin/engine/xray/XrayConfig.kt` checks for a selected `Custom` and returns `buildCustomXrayConfig` before generated profile routing. Active-profile settings are applied to AppState earlier through `app/TrafficConfigRuntime.kt`, but generated profile routes do not replace the Custom JSON document.

## Tests before production

- `engine.xray.CustomTrafficProfileAndroidOracleTest.selectedCustomStillStartsWithAnActiveTrafficProfile` applies a profile to Android AppState before invoking the real Xray config factory. The Custom outbound and routing markers must remain, with no profile routing inserted.
- `DesktopSelectedServerConfigEntryPointTest.selectedRawCustomKeepsPrecedenceOverAnActiveTrafficProfile` covers the same selection combination. It also pins Desktop's existing SOCKS/HTTP inbound and log adaptation and unchanged library values.

The fixtures contain synthetic local configuration, including a profile-only rule and an unmatched final policy so accidental profile evaluation is observable. User files are not test fixtures.

## Fix scope

Handle a selected Custom through the existing Desktop Custom adapter before choosing the active-profile/generated branches. Keep ordinary active-profile behavior, generated selected-server behavior, and the established Desktop inbound/log adaptation. No changes to profile selection, user data, generic Power availability, or reconnect policy are part of this fix.

## Verification

Before production, the Android oracle passed **1/1** in 33 seconds, with zero failures/errors/skips. Log: `outputs/desktop-custom-profile-android-baseline-20261021.log`; immutable XML: `outputs/desktop-custom-profile-android-baseline-reports-20261021/`.

The preproduction Desktop run compiled and executed **4 tests, with exactly 1 failure** in 5 seconds. Only the new combined Custom/profile case failed, with `IllegalArgumentException: Selected server 'Raw custom' cannot be used with a traffic profile`. The existing three entry-point cases passed. Log: `outputs/desktop-custom-profile-behavior-red-20261021.log`; immutable XML: `outputs/desktop-custom-profile-behavior-red-reports-20261021/`.

After the fix, **45/45 Desktop tests passed** in 9 seconds, with zero failures/errors/skips. These cover selected-config entry points (4), generated selected-server factory (9), profile config generation (10), unsupported native boundaries (4), tunnel controller (8), and rendered Home power/latency controls (10). Log: `outputs/desktop-custom-profile-focused-green-20261021.log`; per-suite counts and immutable XML: `outputs/desktop-custom-profile-focused-reports-20261021/`. The native-boundary cases assert rejection through the ordinary test boundary; they are not real Core executions. No new real Core session or user tunnel was started for this fix.

Independent Luna review found no required correctness changes. Root also reviewed the source and tests, and `git diff --check` passed. The patch preserves non-Custom active-profile behavior and the prior missing-selection/decode-failure behavior.

The running pre-fix app was stopped before replacing its packaged files. The existing `:desktopApp:createDistributable` task succeeded in **20 seconds**, using the already prepared Core shared library through `skipiCoreDesktopLibrary`; native Core/HevTun was not rebuilt. Log: `outputs/desktop-build-custom-fix-20261021.log`.

After packaging, the same read-only diagnostic against the actual saved selection changed from `CONFIG_BUILD=FAILURE` to `CONFIG_BUILD=SUCCESS`. The diagnostic did not modify the selection, profile, configuration, or credentials. That removes the observed selected-config failure from Main's Power capability gate.

The updated executable is `desktopApp/build/compose/binaries/main/app/SKIPI/bin/SKIPI`. Its bundled Java and libraries remain in the surrounding `SKIPI` directory. It was relaunched with the existing saved application data; process and X11 window properties confirmed a running `SKIPI` window in Normal state. Launch log: `outputs/desktop-launch-custom-fix-20261021.log`. The preexisting resource warning about the RoscomVPN `geoip-only-cn-private.dat` asset remains outside this fix. A real connection to the user's server was not initiated; verification covers config preparation, focused tests, packaging, and opening the application window.
