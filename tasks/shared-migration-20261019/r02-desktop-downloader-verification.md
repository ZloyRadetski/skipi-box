# R02 Desktop low-level resource downloader checkpoint — 19 October 2026

This tests-first slice adds the Desktop HTTP host adapter for the shared resource-file transfer and proxy-fallback policies. It keeps HTTP connections, the Desktop SOCKS authenticator, cancellation connection tracking, and atomic publication in the host. The adapter's six frozen local-fixture tests and the full regression passed. Desktop's batch runner and resource UI are not connected yet.

## Host boundary

`DesktopResourceFileDownloader` creates a JDK `HttpURLConnection` for each shared transfer hop, configures GET, 15-second connect and 60-second read timeouts, disables automatic redirects, and uses the existing default or supplied user agent. Shared code continues to own URI parsing, redirect and retry limits, byte copying and progress. The shared proxy-fallback policy runs the retry flow inside `DesktopSubscriptionSocksProxy.withAuthenticator`, falls back only after an `IOException`, and executes the fallback log after the previous global authenticator has been restored.

The adapter tracks the live `HttpURLConnection` in the shared cancellation object before reading the response and untracks/disconnects it in the shared transfer's `finally` path. `DesktopProfileResourceFiles.createDownloader()` supplies the Desktop atomic publisher. That publisher writes to a same-directory temporary file, rejects empty output, replaces the target with `ATOMIC_MOVE` and `REPLACE_EXISTING`, and removes staging on failure. It has no non-atomic move fallback.

The adapter is low-level only. Desktop does not yet connect it to a resource-update runner or resource UI. At this checkpoint, Android's atomic writer serialized writes by target path while Desktop's publisher did not. The later tests-first slice closed that gap by applying the shared absolute-path lock around both writers; see the [writer-lock and progress checkpoint](r02-write-lock-and-progress-verification.md). The runner and resource UI remain pending.

## Test-first and regression evidence

The first 17-second preproduction RED contained both the expected missing `DesktopResourceFileDownloader`/`createDownloader` API and a fixture compile error because non-inner `SocksReplyServer` called outer helper methods. A later 6-second fixture compile attempt failed on nullable-list type inference. Test-only corrections made the SOCKS helper inner, fixed its isolated `user.home` setup, and typed the list; assertions remained unchanged.

| Gate | Result | Evidence |
|---|---|---|
| Desktop adapter API and fixture | Initial **RED in 17 seconds** with missing API and fixture-helper scope errors; a later fixture-only compile failed after **6 seconds** on nullable-list type inference. | [Initial RED log](../../outputs/r02-desktop-downloader-api-red-20261019.log), [fixture compile log](../../outputs/r02-desktop-downloader-fixture-compile-20261019.log). |
| Focused downloader suite | **6/6 GREEN in 22 seconds** after fixture-only corrections, with no assertion changes. The tests cover direct GET/user-agent/progress, empty-output rejection with existing-target preservation, SOCKS credentials and authenticator restoration, IO-only proxy fallback, pre-cancellation, and cancellation while response headers are blocked. The blocked-header case uses a local HTTP fixture and confirms disconnect releases the request while preserving the old target. | [Focused log](../../outputs/r02-desktop-downloader-focused-green-20261019.log), [test report and counts](../../outputs/r02-desktop-downloader-focused-reports-20261019/). |
| Full regression | **2,423 executions in 37 seconds**, failures/errors/skips all 0: core 330 Desktop + 284 Android, app 317 Desktop + 309 Android, UI 94 Desktop + 94 Android, Android app 545, Desktop app 450. Desktop compile and Android debug APK passed. Seven actual native/Core tests passed with XML timestamps at `2026-10-19T19:55Z`. | [Full log](../../outputs/r02-desktop-downloader-full-green-20261019.log), [suite counts and XML](../../outputs/r02-desktop-downloader-final-reports-20261019/suite-counts.json). |

Production/tests are commit `380c76c`; evidence is commit `7fa43b3`. This slice implements only the Desktop low-level downloader and publisher factory. Broader R02/P04/R01 acceptance remains open in [the migration plan](../plan.md#r02).
