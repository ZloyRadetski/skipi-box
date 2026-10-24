# R03 native app-catalog and VPN-builder smoke

This tests-only slice exercises the current Android package catalog and the service's per-app builder-call selection with synthetic app entries. Robolectric runs against its in-memory PackageManager; the builder recorder captures calls and never establishes a TUN.

`AndroidPackageRepositorySyntheticOracleTest` installs synthetic `ApplicationInfo`/`PackageInfo` values and checks system/user scope filtering, UID/label ordering, requested-package filtering, and returned metadata. It does not call the host or device package manager.

`VpnApplicationPolicyBuilderAndroidOracleTest` records the existing private `SkipiVpnService.applyApplicationPolicy` path without changing production source. The tests cover global self-exclusion, blacklist selected/self exclusions, whitelist self filtering, the empty effective whitelist deny-list fallback, swallowing only `PackageManager.NameNotFoundException`, and propagation of a synthetic `SecurityException`. Successful selected package targets are installed as synthetic package records. A custom Robolectric shadow replaces only `VpnService.Builder.addAllowedApplication` and `.addDisallowedApplication`; package enumeration is synthetic and no `.establish()` call is made.

`VpnApplicationPolicyNoProfileAndroidOracleTest` adds the non-default no-profile fallback case: without traffic profiles, the root `AppState` per-app fields remain the source used for runtime policy projection.

The source method is private, so the current tests use reflection to invoke it with the real service receiver, a Robolectric-created `VpnService.Builder`, and a synthetic start config. If this reflective seam proves too brittle, the minimal production seam to propose in a separate RED-first change is an `internal` `SkipiVpnService` method that accepts the existing builder and `VpnServiceStartConfig` and delegates to the same private application-policy path. Keep policy branch behavior and Android `PackageManager` calls inside the Android module. Do not move builder execution or installed-package lookup into shared core.

No build or test was run for this draft. The parent task owns test execution and any subsequent source seam decision.
