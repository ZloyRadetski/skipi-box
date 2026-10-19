# R02 shared proxy-fallback policy checkpoint — 19 October 2026

This checkpoint covers only the generic proxy-fallback policy moved into shared JVM code. Android remains responsible for its actual authenticator, SOCKS proxy, VPN/network behavior, and HTTP connection setup. The shared transfer and cancellation-state slices are already available, but Desktop has no resource download runner or resource UI wired to them. R02, P04, and R01 remain open.

## Policy and host boundary

The shared policy runs the complete proxied download callback inside the host-provided authentication scope when the username is nonblank. A blank username still uses the configured proxy and skips authentication. A null proxy goes directly to the direct-transfer callback. Only `IOException` from the proxied/authenticated path triggers fallback; the host fallback callback runs after the authentication callback unwinds, and direct transfer runs outside the catch. Other exceptions propagate without a direct retry.

Android continues to own `TunnelNetworks.withSocksProxyAuthenticator`, SOCKS proxy construction, VPN binding, per-attempt connection creation, HTTP configuration, and logging. The existing VPN `SocketException` retry removes VPN binding but retains an explicitly selected SOCKS proxy. This shared policy does not add a Desktop downloader or connect the shared policy to a Desktop UI.

## Test-first and review evidence

| Gate | Result | Evidence |
|---|---|---|
| Android source proxy baseline | **4/4 GREEN** before the shared policy move. | Commit `7c1cdc5`; the baseline is included in the [cancellation focused reports](../../outputs/r02-cancellation-focused-proxy-baseline-reports-20261019/). |
| Shared proxy-policy contract | Expected missing-API **RED** in 5 seconds before implementation. | [API RED log](../../outputs/r02-proxy-policy-shared-api-red-20261019.log). |
| Focused proxy-policy gate | **17/17 GREEN** in 36 seconds. | [Focused log](../../outputs/r02-proxy-policy-focused-green-20261019.log), [reports](../../outputs/r02-proxy-policy-focused-reports-20261019/). |
| Independent source review | No required changes against the Android baseline. The review confirmed auth encloses the whole proxied retry flow; blank usernames skip auth while preserving proxy use; null proxy selects direct; only `IOException` triggers fallback; logging follows auth cleanup; and direct transfer is outside the catch. Android still owns auth and the connection policy. | Compared the current shared helper and Android adapter with commit `238b073`. |
| Full regression | **2,395 executions in 42 seconds**, failures/errors/skips all 0: core 320 Desktop + 274 Android, app 317 Desktop + 309 Android, UI 94 Desktop + 94 Android, Android app 543, Desktop app 444. Desktop compile and Android APK passed. Seven actual native/Core tests ran with XML timestamp `2026-10-19T19:32Z`. | [Full log](../../outputs/r02-proxy-policy-full-green-20261019.log), [suite counts and XML](../../outputs/r02-proxy-policy-final-reports-20261019/suite-counts.json). |

The Android proxy oracle initially hit an Android stub gap for `Authenticator.getDefault()`. The test harness now uses reflective lookup of that public method; the expected assertions were unchanged. Production/tests are commit `32a8a5a`; evidence is commit `d4be102`.

## Remaining R02 work

This slice shares only the proxy-fallback decision and ordering. Desktop's resource download runner and resource UI still need implementation and host verification. Preserve Android's proxy, authentication, VPN, and connection behavior as the source baseline. The next approved slice is tests-first: an Android baseline with two cases and ten common API tests are being drafted for a separate `commonMain` API; production has not started. Do not substitute global bundled geo files for profile-scoped resources; the broader R02/P04/R01 acceptance criteria remain open in [the migration plan](../plan.md#r02).
