# R03 projection tests-only slice

This draft pins the first R03 implementation boundary before production changes. It is a specification only: no test files or production source were changed, and no build or test command was run. The slice is pure projection from supplied package/user records; it does not call PackageManager, read settings/clipboard, use Compose, or implement VPN policy.

## Files and API under test

Add two new test files:

- Android source oracle: `app/src/test/kotlin/features/proxy/app/ProxyAppListProjectionAndroidOracleTest.kt`. It tests the current source API and must compile before production extraction.
- Common target: `shared/core/src/commonTest/kotlin/features/proxy/app/ProxyAppListProjectionTest.kt`. It targets the new API below and is expected to fail compilation while that API is absent.

The common production file is planned at `shared/core/src/commonMain/kotlin/features/proxy/app/model/ProxyAppListProjection.kt`, package `features.proxy.app.model`. Its public, plain Kotlin records are the portable `AppPackageEntry(packageName, appLabel, userId, uid, system)`, `ProxyAppListUserSpace(id, name)`, `ProxyAppListItem(key, app, sharedUid, selectionKeys)`, `ProxyAppListPreparedData(displayedUserSpaces, allItemsByUser, visibleItemsByUser)`, and `ProxyAppListUserSpaceTabUi(id, label, checkedCount)`. It may expose `AppPackageEntry.name` for label fallback.

The pure API is:

```kotlin
fun prepareProxyAppListProjection(
    userSpaces: List<ProxyAppListUserSpace>,
    appPackages: List<AppPackageEntry>,
    searchValue: String,
    fixedUserId: Int?,
): ProxyAppListPreparedData

fun List<AppPackageEntry>.sortedForProxyAppListRefresh(
    selectedAppKeys: Set<String>,
): List<AppPackageEntry>

fun ProxyAppListPreparedData.projectedUserSpaceTabs(
    selectedAppKeys: Set<String>,
): List<ProxyAppListUserSpaceTabUi>
```

All three functions are synchronous and deterministic over their arguments. Common records contain no `AndroidUserSpace`, Compose annotation, provider, dispatcher, or Android UID helper dependency. Keep their source filename distinct from Android `ProxyAppListData.kt` so same-package Kotlin/JVM facades do not collide. The Android adapter will later map provider records into these facts and map prepared user spaces back if needed.

## Shared oracle fixtures and expectations

Use equivalent records and assertions in both suites. The Android oracle calls `prepareProxyAppListData`, `toUserSpaceTabs`, and `sortedForProxyAppListRefresh` from current app source (the first two are suspend functions today, so use the existing test coroutine convention); the common test calls the pure API above. Keep assertions at output boundaries rather than duplicating each helper step.

1. **Trimmed, case-insensitive label and package search; fallback name.** Supply user 0 and, in input order:

   | Package | Label | UID |
   | --- | --- | ---: |
   | `Com.Example.Calendar` | `Travel Calendar` | 10001 |
   | `org.example.weather` | `Weather` | 10002 |
   | `org.example.blanklabel` | `   ` | 10003 |

   For query `  tRaVeL  `, visible keys are only `0:Com.Example.Calendar`; for ` EXAMPLE.WEATHER `, only `0:org.example.weather`; for ` blanklabel `, only `0:org.example.blanklabel` because a blank label falls back to package name. In each projection, `allItemsByUser[0]` still contains all three input rows in their source order.

2. **User partition, sorted fallback spaces, and represented tab counts.** Supply user-space records in this order: `(10, "Work")`, `(0, "")`. Supply packages for users 10, 3, and 0, respectively: `com.work.app`/UID 1010010, `com.other.app`/UID 310011, and `com.owner.app`/UID 10012. With selected keys `{ "0:com.owner.app", "3:com.other.app", "99:unknown.app" }`, displayed spaces and tabs sort by ID as `(0, "")`, `(3, "User 3")`, `(10, "Work")`; tab labels are `User 0`, `User 3`, `Work`, with checked counts `1, 1, 0`. The unknown key has no represented row and does not inflate a count. The projection does not accept or rewrite the stored selected-ID list; unknown-ID retention remains covered by the existing P04 selection/controller tests.

3. **Shared UID keys, counts, and per-user separation.** Supply user spaces 0 and 1 and these records in order:

   | Package | Label | User | UID |
   | --- | --- | ---: | ---: |
   | `com.example.zeta` | `Zeta` | 0 | 10042 |
   | `com.example.alpha` | `Alpha` | 0 | 10042 |
   | `com.example.alpha` | `Alpha work` | 1 | 110042 |

   User 0 has two rows, both `sharedUid = true` and both with ordered selection keys `["0:com.example.zeta", "0:com.example.alpha"]`. User 1 has one non-shared row with key and selection list `["1:com.example.alpha"]`; it is not grouped with the same package in user 0. With selected keys `{ "0:com.example.alpha", "9:unknown.app" }`, tab counts are 2 for user 0 (both rows represent the shared group) and 0 for user 1.

4. **Fixed-user remapping and package deduplication.** Use fixed user 9 and records, in order: `com.example.same`, user 1, UID 112345, label `First`; `com.example.same`, user 2, UID 212345, label `Duplicate`; `com.example.other`, user 2, UID 299999, label `Other`; `com.example.current`, user 9, UID 900007, label `Already fixed`. Expect one user bucket, 9. Its package order is `com.example.same`, `com.example.other`, `com.example.current`; the first same-package record wins. Remapped rows have user ID 9 and app IDs 12345 and 99999 (UID modulo 100000); the row already at user 9 retains UID 900007. Selection keys are respectively `9:com.example.same`, `9:com.example.other`, `9:com.example.current`.

5. **Selected-first refresh sort with group-name/key ties.** Supply unsorted user-0 records: unselected `com.aardvark` / label `Aardvark` / UID 801; selected singleton `com.echo` / label `Echo` / UID 2001; shared-UID `com.shared.fable` / label `Fable` / UID 100; and `com.shared.echo` / label `Echo` / UID 100. With selected keys `{ "0:com.echo", "0:com.shared.fable" }`, expect package order `com.echo`, `com.shared.echo`, `com.shared.fable`, `com.aardvark`. The shared group counts selected when any member key is present; equal selected group names resolve by `app:<key>` before `uid:<uid>`, and members then sort by normalized name/package.

## Boundary and RED/GREEN contract

The Android baseline test must use only supplied `AppPackageEntry` and `AndroidUserSpace` values; no provider fake or Android runtime setup is needed for projection behavior. It should compile against the existing native functions. The common target should point only at the specified pure API and provide the expected missing-API compile RED until the common production file is introduced. Do not add tests for provider scope, actual user enumeration, icon retrieval, loading/cancellation/toasts, UI presentation, clipboard, profile persistence, selection editing, or VPN enforcement in this slice. Existing selection tests already cover preserving unrelated/unknown IDs during toggles.

After review, the production extraction should retain Android loading/record conversion in `ProxyAppListData.kt` (or a narrow adapter), retarget both current consumers of that file, and remove same-FQCN Android model declarations only when common records are available. This spec does not authorize or implement that production step.
