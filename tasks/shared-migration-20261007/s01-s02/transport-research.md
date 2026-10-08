# S01–S02 subscription transport and binding audit

Read-only source audit for the approved S01 + S02 slice. No production code,
tests, Gradle tasks, or Git state were changed by this research.

## Source of behavior

`tasks/plan.md` S02 assigns load/validate/rebase/commit order, subscription
identity, server reconciliation, and metadata reconciliation to shared code;
the fetcher remains a port. S03 owns batch refresh, install, and schedule
rules. The Android refresh source is
`app/src/main/kotlin/features/subscription/usecase/SubscriptionUpdateUseCase.kt`;
the Android HTTP source is
`app/src/main/kotlin/features/subscription/runtime/AndroidSubscriptionFetcher.kt`.
Desktop transport is
`desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionFetcher.kt`.
The current verification note already records several Desktop deltas, but the
source below is the authority for this audit.

## What belongs in the shared request contract

Android's persisted subscription group carries `id`, `name`, `url`,
`userAgent`, `updateInterval`, `hwid`, `ageSecretKey`, `updateViaProxy`,
`autoOverrideRules`, `enabled`, `builtIn`, update timestamp, profile/traffic
metadata, and expiry notification settings. These are in
`app/src/main/kotlin/app/AppStateModels.kt` and
`app/src/main/kotlin/data/ListEntities.kt`. Desktop's current
`StoredSubscription` in `shared/core/.../SubscriptionProviderLibrary.kt`
already carries most of these fields, while S01 needs to preserve the
remaining Android-owned fields called out by its plan.

For a refresh request, per-subscription transport inputs are `url`,
`userAgent`, `ageSecretKey`, and `updateViaProxy`; `id` plus the current
subscription identity is needed for stale-result checks. The existing Android
identity includes `url`, `userAgent`, `updateInterval`, `ageSecretKey`,
`updateViaProxy`, and `enabled` (`ProxyServerUseCases.kt`). Reconciliation
reads the latest `autoOverrideRules` when committing. `hwid` is persisted on
the Android group but is not read by `AndroidSubscriptionFetcher`: request
device headers use the installation HWID from `AppSettingsPreferences`.

Global request context is separate from the per-subscription record: timeout,
the setting that enables device headers, and whether a requested running proxy
is actually available. A shared request/use-case can combine these values and
the subscription fields. It should receive host facts for device name/model,
OS/version, installation identity, and the live SOCKS endpoint/authenticator.
The Android `useRunningProxy` decision is `group.updateViaProxy && proxyRunning`
and the fetcher uses `LocalProxyRuntime.current()`; Desktop makes the same
opt-in decision against `coreState.isRunning` and its configured local SOCKS
port. Runtime state and the proxy implementation stay host-side.

Shared policy can define the standard header names and when to send them, the
Age request/decrypt sequence, embedded Basic-auth encoding, subscription
identity and response metadata handling. HTTP connections, redirect execution,
timeouts as implemented by each HTTP stack, stream decoding, proxy runtime
facts, and device facts stay in adapters. `SubscriptionFetchResponse` and
`SubscriptionMetadata` are already common types and metadata parsing is already
shared in `shared/core/.../SubscriptionMetadata.kt`.

## HTTP behavior found in source

| Behavior | Android | Desktop direct/root fetch | Assessment |
|---|---|---|---|
| Request | `HttpURLConnection`, GET, `Connection: close` | `HttpClient` GET by default; proxy path uses `HttpURLConnection` and `Connection: close` | Transport implementation detail; preserve GET semantics. |
| Redirects | Automatic redirects disabled; any status 300–399 is treated as redirect; `repeat(3)` allows two redirect hops before the next 3xx fails | Explicit redirect handling; only 301/302/303/307/308; permits five hops | Product behavior difference, not a platform limit. |
| Response body | Reads the complete UTF-8 body; no explicit byte cap; reads non-2xx error stream to build a structured error | Decoded body capped at 8 MiB; non-2xx body is also capped | Existing Desktop cap is policy, not a native limitation. It changes Android behavior for large subscriptions. |
| Timeout | Default 10 s; coerces to 3–600 s; applies to connect and read | Direct client default 30 s with 10 s client connect timeout and per-request `HttpRequest.timeout`; settings currently constrain 10–120 s. Proxy `URLConnection` path coerces to 1–600 s for connect/read | Defaults and clamps differ by policy/adapter. Keep the requested Android timeout semantics explicit in the shared request contract; each host maps it to its stack. |
| User-Agent | Blank or SKIPI/…/Android values normalize to `SKIPI/<version>/Android`; other supplied agents pass through | Blank resolves to `SKIPI Desktop`; per-subscription custom value is passed through | Preserve the existing host-specific default while sharing a pure “normalize SKIPI default” rule parameterized by host default. Do not send the Android UA as a device fact. |
| Device headers | Fetch options default enabled; `x-client`, generated `x-app-version`, `x-device-os=Android`, OS version, model, installation HWID in `x-hwid` and `X-Device-ID`; optional values fall back to `unknown`/`Android` | App settings default enabled and the refresh caller supplies `DesktopDeviceIdentity` headers; the fetcher method itself defaults to an empty map. Header names match, but OS/model and identity values are Desktop facts | Header policy is portable; values are host facts. Desktop currently hard-codes client version `0.4.1`, equal to current `BuildConfig.VERSION_NAME` but able to drift. |
| Basic auth | Reads URL userinfo and sends `Authorization: Basic …` using the shared Base64 helper | Uses URI userinfo and the same Base64 helper | Same intent and helper. Edge behavior differs for an explicitly empty userinfo because Desktop skips blank `URI.userInfo`; Android sends whenever URL userinfo is present. |
| IDN | Converts the initial URL host with `IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)` before making the connection | Constructs a URI/URL without an explicit IDN conversion | Formatting-policy difference; not evidence of a native limitation. Redirect hosts are resolved as URIs on both paths, though Android only does the explicit conversion on the initial URL. |
| Compression | Does not explicitly request `gzip, deflate` or run explicit gzip/deflate decoding | Explicitly sends `Accept-Encoding: gzip, deflate` and decodes both encodings, including in proxy path | Request/response behavior difference, not a platform limitation. |
| Automatic resource URLs | Provider and embedded-profile fetches call the same fetch path as the root subscription, without a local/private/metadata guard | Root/manual `fetch()` has no automatic-resource guard, as intended; provider URLs and embedded-config URLs use `fetchAutomaticResource()` and guard every hop against local/private/metadata targets | Keep these paths distinct. The direct manual root path matches the Android policy; the automatic-resource guard is an existing Desktop policy difference, not a platform restriction. Do not describe it as one. |

The user-provided clarification that Desktop manual direct fetch intentionally
matches Android is consistent with `automaticResource = false` for
`DesktopSubscriptionFetcher.fetch()` and the root call in `fetchAndImport()`.
It does not describe the nested YAML provider and embedded-config calls, which
currently take the guarded automatic-resource path on Desktop while Android
uses the same unrestricted fetcher for those URLs.

## Age encryption

Android trims the group's `ageSecretKey`; when non-empty it derives an
X25519 Age recipient and sends `X-Age-Public-Key`. After a successful HTTP
response, if `trimStart()` begins with `-----BEGIN AGE ENCRYPTED FILE-----`,
it requires a non-empty secret and decrypts the armored body before parsing
the subscription. The implementation is in
`app/src/main/kotlin/features/subscription/runtime/SubscriptionAgeCrypto.kt`
and uses `kage.Age` plus `X25519Identity` from `com.github.android-password-store:kage:0.6.0`.
Android selects that implementation on API 26+ and reports unsupported Age
below that level.

The local Gradle metadata for Kage exposes a `standard-jvm` artifact targeting
JVM 17, and its cached artifact is a normal JAR containing the `kage.Age` and
`kage.crypto.x25519` classes. Its runtime dependencies are JVM libraries
(Bouncy Castle, HKDF, Kotlin Result, Kotlin stdlib); there is no JNI, C-ABI,
or native Age binding. The dependency is currently declared only in
`app/build.gradle.kts`, so Desktop has not wired it, but it can use the same
Age implementation by adding the dependency to a JVM-capable source set and
keeping the common policy behind an interface/port. This is not a native
blocker and does not require new native functionality.

Desktop currently sends no Age public key and does not decrypt Age armor,
despite persisting `ageSecretKey` in `StoredSubscription`. That is a behavior
gap. The cryptographic implementation can stay an adapter while the shared
refresh flow owns when to request a public key and when to decrypt before
parsing.

## Minimal tests-first parity slice

Keep the first implementation slice to one subscription refresh, not batch,
install, or scheduling. Before production edits:

1. Extend `shared/app/.../SubscriptionRefreshUseCaseTest.kt` only for a missing
   single-request behavior: build the fetch request from the S01 record plus
   global context, capture the baseline before fetch, and prove a deleted or
   edited target cannot commit the delayed result while unrelated current data
   survives. Existing common tests already cover snapshot-before-load,
   load-failure, cancellation, and generic target/profile conflict helpers.
2. Add host adapter assertions for one direct request: root URL, selected UA,
   optional device headers, embedded Basic auth, and exact response headers/body
   reaching the common import/reconcile path. Android's current
   `AndroidSubscriptionFetcherTest` only checks that disabled update-via-proxy
   does not select SOCKS; Desktop already tests request headers, Basic auth,
   proxy selection, redirect handling, and the 8 MiB cap. Change only tests
   whose expectations represent policy that the approved Android contract
   replaces. Do not use a Desktop-only private URL guard or response cap as
   evidence of a host limitation.
3. Add a focused age fixture test before wiring Age: empty key leaves plaintext
   unchanged and sends no Age header; a key sends its derived public key and
   decrypts a known armor body before the common parser sees it. Kage is already
   the Android baseline and can serve Desktop without native work.

Do not fold batch eligibility/progress, WorkManager/timer behavior, install
dispatch, or global scheduler tests into S02; those are S03. No tests or builds
were run in this read-only audit.

## Blockers

No architectural or native blocker requires a user decision. Known gaps are
explicit behavior differences in the current Desktop policy and missing Age
wiring; the Android source and the existing host defaults provide enough
direction to implement them while keeping the host-specific UA/device/runtime
facts at the adapter boundary.
