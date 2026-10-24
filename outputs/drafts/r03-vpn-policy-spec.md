# R03 VPN application policy contract

This draft defines a tests-first extraction boundary for per-app policy projection. It covers the current Android behavior that can be computed from stored policy values and the current Android user ID. It does not move VPN builder calls, package enumeration, or UI behavior into shared code.

## Proposed common API

Place the pure contract in `shared/core/src/commonMain/kotlin/features/proxy/app/ProxyAppVpnPolicy.kt`, package `features.proxy.app`:

```kotlin
data class ProxyAppVpnPolicy(
    val mode: Int,
    val packageNames: List<String>,
)

fun projectProxyAppVpnPolicy(
    mode: Int,
    selectedAppKeys: List<String>,
    currentUserId: Int,
    strictFullTunnel: Boolean,
): ProxyAppVpnPolicy
```

Use the existing `app.modes.ProxyAppListModeBlacklist`, `ProxyAppListModeWhitelist`, and `ProxyAppListModeGlobal` constants. The common function should have no dependency on `AppState`, Android SDK classes, `PackageManager`, `VpnService.Builder`, `Process`, or clipboard APIs. The Android caller supplies the user ID obtained from the process UID.

## Contract cases

| Input | Expected projection |
|---|---|
| Unknown mode | Global mode and empty package list. |
| Strict full tunnel is enabled | Global mode and empty package list, without mutating the selected keys or stored profile. |
| Global mode | Preserve global mode and ignore selected keys. |
| Blacklist or whitelist mode | Preserve the recognized mode and normalize selected keys. |
| `7: com.example.first ` with current user `7` | Strip the user prefix, then trim to `com.example.first`. |
| `8:com.example.foreign` with current user `7` | Drop the key. |
| `not-a-user:com.example.bad` or ` 7:com.example.bad` | Drop the key: the prefix is parsed before trimming, and invalid integer prefixes do not match. |
| A bare ` com.example.legacy ` key | Preserve as a legacy package name, then trim it. |
| Empty/whitespace package after prefix handling | Drop it. |
| Duplicate normalized package names | Keep the first occurrence and preserve encounter order. |

The matching common fixtures and Android oracle intentionally use the same keys and expected ordering. Profile selection remains an Android runtime adapter: the active profile ID wins, an invalid ID falls back to the first profile, and no profiles leave the root `AppState` fallback unchanged. Strict full tunnel is applied to the effective runtime state after the active profile is copied in; it does not rewrite the stored profile.

## Android execution that stays in the adapter

`Process.myUid().toAndroidUserId()` remains Android-specific. So do the installed-app catalog (including labels, icons, system/user scope, and package visibility), every `VpnService.Builder.addAllowedApplication` and `addDisallowedApplication` call, and Android's `PackageManager.NameNotFoundException` handling for packages that were removed after selection.

The builder layer also retains the service's self-package exclusion. Whitelist mode filters the VPN app itself out of the allow list; blacklist/global modes deny the VPN app explicitly. An empty effective whitelist cannot be passed to `Builder.addAllowedApplication` as an empty list because Android interprets that as routing all apps, so the current adapter falls back to denying the installed-package snapshot. These decisions depend on Android services and should remain outside the common projection function.

## Baseline/runtime sources

- `app/src/main/kotlin/engine/vpn/VpnApplicationPolicy.kt:12` defines the Android policy value; lines 17–51 contain mode fallback, strict override, user-prefix parsing, and trim/dedup behavior.
- `app/src/main/kotlin/app/TrafficConfigRuntime.kt:14` applies the active profile's app-list fields; lines 76–79 resolve an invalid active profile ID to the first profile and return null when no profiles exist.
- `app/src/main/kotlin/engine/vpn/StrictFullTunnel.kt:20` applies the temporary strict runtime override and leaves the source state intact.
- `app/src/main/kotlin/engine/proxy/AndroidProxyEngine.kt:128` resolves active-profile state before strict runtime projection.
- `app/src/main/kotlin/engine/vpn/VpnXrayConfig.kt:103` enforces strict mode for the direct Android Always-on entry point; lines 167–168 build the VPN application policy with the Android user ID.
- `app/src/main/kotlin/engine/vpn/SkipiVpnService.kt:419` maps the projected policy to `VpnService.Builder`; lines 424–439 implement whitelist/blacklist/global and self-exclusion behavior, while lines 456–490 handle install checks and the installed-package fallback.
- `app/src/main/kotlin/system/AndroidConstants.kt:6–14` defines Android UID-range normalization and user-ID extraction.
- `shared/core/src/commonMain/kotlin/app/modes/AppModes.kt:8–10` contains the portable mode constants.
- `shared/core/src/commonMain/kotlin/utils/ValueParsing.kt:10–16` defines trim, empty removal, and stable distinct behavior used by the Android mapping.

## Test boundary and next smoke option

The Android oracle in `app/src/test/kotlin/engine/vpn/VpnApplicationPolicyAndroidOracleTest.kt` locks the current `AppState`/active-profile/strict-policy results. The common fixture in `shared/core/src/commonTest/kotlin/features/proxy/app/ProxyAppVpnPolicyTest.kt` mirrors the pure mode and selected-key cases. This first slice deliberately does not test builder calls.

A later isolated Robolectric smoke can verify the Android edge without a real phone or system setting changes: inject a narrow application-policy sink around the VPN builder operations and a fake package catalog, then capture allowed/disallowed package calls for global, blacklist, non-empty whitelist, empty whitelist, self-package exclusion, and a package that becomes uninstalled. The empty-whitelist case should verify the installed-package deny-list fallback. Avoid calling `VpnService.Builder.establish()` in that smoke; establishing a real TUN is device/platform behavior and is not needed to test policy call selection. Keep actual `Process.myUid()` and installed-app enumeration as a separately checked Android adapter boundary.
