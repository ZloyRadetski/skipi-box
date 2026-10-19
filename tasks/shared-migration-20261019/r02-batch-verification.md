# R02 shared resource-update batch executor checkpoint — 19 October 2026

This tests-first slice moves the ordered batch lifecycle into `shared/core` common code while Android continues to provide storage, networking, cancellation, progress, permissions, logging, and notification operations. The batch executor passed focused and full verification. Desktop does not yet have a resource download runner or resource UI. R02, P04, and R01 remain open.

## Shared policy and Android host boundary

The common `executeResourceFileUpdateBatch` handles the empty-plan short circuit, ordered per-file execution, wrapping per-file errors with the resource display name, status collection, raw profile-source marking, completion/failure callback order, and cancellation remapping. A resource cancellation is reported in its original form and then mapped to the existing localized Android exception at the boundary. Status failures are reported and rethrown unwrapped. Setup failures, marker-write failures, and failure-reporter exceptions escape at the same boundaries as before. Completion-notification errors remain best-effort and are swallowed.

Android still maps captured plan items to their storage targets before invoking the executor. For a nonempty plan, its `prepareSession` callback preserves the existing order: create the data directory, reset global cancellation state, construct the notifier, resolve and log the proxy, then normalize the user agent. Each update callback performs forced initial progress, calls the existing downloader with the same URL/target/proxy/user-agent, reports aggregate progress, and applies built-in permissions. Android keeps the real proxy/authenticator/VPN and HTTP adapters, notifier/logging behavior, and scoped file store.

Production/tests are commit `9637dcd`; evidence is commit `d52bc3a`.

## Test-first and review evidence

| Gate | Result | Evidence |
|---|---|---|
| Android batch source baseline | **2/2 GREEN** in 27 seconds before the shared API move. | Baseline commit `27a3acd`; [baseline log](../../outputs/r02-batch-android-baseline-20261019.log), [reports](../../outputs/r02-batch-android-baseline-20261019/). |
| Shared batch API | Expected missing-API **RED** in 4 seconds before implementation. | [API RED log](../../outputs/r02-batch-shared-api-red-20261019.log). |
| Focused batch gate | **15/15 GREEN** in 27 seconds: shared common tests 10, Android batch oracle 2, Android ordered-plan oracle 3. | [Focused log](../../outputs/r02-batch-focused-green-20261019.log), [reports and counts](../../outputs/r02-batch-focused-reports-20261019/). |
| Independent source review | No required changes against `27a3acd`. Empty setup, cancellation of the second ordered HTTP request, wrapped error FQN/message/cause, status/marker/report/notification boundaries, target mapping, proxy/user-agent, progress, and permissions matched the prior Android behavior. | Read-only comparison of the new common executor and Android adapter with the baseline repository implementation. |
| Full regression | **2,417 executions in 25 seconds**, failures/errors/skips all 0: core 330 Desktop + 284 Android, app 317 Desktop + 309 Android, UI 94 Desktop + 94 Android, Android app 545, Desktop app 444. Desktop compile and Android APK passed. Seven actual native/Core tests ran with XML timestamp `2026-10-19T19:46Z`. The ten `commonTest` cases run on both Core JVM targets in the full regression. | [Full log](../../outputs/r02-batch-full-green-20261019.log), [suite counts and XML](../../outputs/r02-batch-final-reports-20261019/suite-counts.json). |

## Next R02 work

The six-case Desktop low-level downloader suite is published and frozen. Its 17-second preproduction RED included the expected missing `DesktopResourceFileDownloader`/`createDownloader` API and a fixture compile error: the non-inner `SocksReplyServer` called outer helper methods. The fixture helper-scope correction and Desktop adapter implementation are underway; see [the RED log](../../outputs/r02-desktop-downloader-api-red-20261019.log). Desktop still needs a connected resource download runner and resource UI. Do not substitute global bundled geo files for profile-scoped resources; broader R02/P04/R01 acceptance remains open in [the migration plan](../plan.md#r02).
