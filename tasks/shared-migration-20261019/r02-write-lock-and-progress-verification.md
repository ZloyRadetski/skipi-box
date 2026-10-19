# R02 shared writer lock and overall progress checkpoint — 19 October 2026

This tests-first slice preserves Android's per-target write serialization in the shared JVM layer and extracts Android's overall-download progress formula into common code. Android's store still performs its OS-specific file operations; Desktop now uses the same process-wide absolute-path lock around its atomic publication. No user-visible progress behavior changes.

## Behavior preserved

`withResourceFileWriteLock` retains Android's existing global map of per-target monitors keyed by `target.absolutePath`. Its map lookup remains synchronized, and the operation for one target is serialized without blocking a different target. Android uses the shared lock in both initial Xray Core candidate installation and its normal atomic resource writer. Initial installation retains its false/true return behavior, return labels, candidate staging, file sync, chmod, hard-link publication, directory sync, and cleanup. The normal Android writer keeps parent-directory creation before the lock and its existing temporary-file/`Os.rename` flow.

The Desktop publisher creates the parent directory before acquiring the same shared lock. It then creates its staging file, invokes the writer, validates the output, performs the existing same-directory `ATOMIC_MOVE` with `REPLACE_EXISTING`, and removes staging on failure, all under the per-target lock. It adds no non-atomic move fallback.

`resourceFileUpdateOverallProgress(fileIndex, fileCount, downloadedBytes, totalBytes)` is the Android formula moved to common code. Android forwards its inputs unchanged. The rules remain: nonpositive total or file count returns `null`; negative file index is clamped to zero; the current file's byte ratio is bounded to 0–1; the computed integer percentage truncates and is bounded to 0–100.

## Test-first and regression evidence

| Gate | Result | Evidence |
|---|---|---|
| Android source baseline | **12/12 GREEN in 27 seconds** before production, including the existing downloader cases and three writer-lock cases. | [Baseline log](../../outputs/r02-write-lock-and-progress-android-baseline-20261019.log), source baseline `953e9df`. |
| Desktop lock behavior baseline | **RED in 21 seconds** at the same-path serialization assertion; distinct-target behavior passed. | [RED log](../../outputs/r02-desktop-writer-lock-behavior-red-20261019.log), [reports](../../outputs/r02-desktop-writer-lock-behavior-red-reports-20261019/). |
| Shared API baseline | **RED in 1 second**, with only the missing lock/progress functions. | [API RED log](../../outputs/r02-write-lock-and-progress-shared-api-red-20261019.log). |
| Focused gate | **27/27 GREEN in 30 seconds**: shared lock 3 + progress 4, Android lock 3 + downloader 9, Desktop lock 2 + downloader 6. Independent source review found no required changes. | [Focused log](../../outputs/r02-write-lock-and-progress-focused-green-20261019.log), [reports](../../outputs/r02-write-lock-and-progress-focused-reports-20261019/). |
| Full regression | **2,439 executions in 17 seconds**, failures/errors/skips all 0: core 337 Desktop + 288 Android, app 317 Desktop + 309 Android, UI 94 Desktop + 94 Android, Android app 548, Desktop app 452. Desktop compile and Android debug APK passed; seven actual native/Core tests ran with XML timestamps at `2026-10-19T20:03Z` (Core 4 at 20:03:41, profile-assets 1 at 20:03:42, selected-server 2 at 20:03:46). | [Full log](../../outputs/r02-write-lock-and-progress-full-green-20261019.log), [suite counts and XML](../../outputs/r02-write-lock-and-progress-final-reports-20261019/suite-counts.json). |

Production/tests are commit `498aa5f`; evidence is commit `f5cc233`.

## Pause and resume point

This slice is complete and the full gate is green. The user requested that work pause here. Desktop runner tests and production code have not started; preliminary source research is available, but the callback contract still needs review. When the user resumes, derive and review the Desktop runner host-callback contract against the Android source and write its behavior tests. Then connect the existing shared queue and batch executor with the captured profile scope, followed by the resource UI. Low-level Desktop resource file I/O is in place; the resource UI remains pending. Preserve the unrelated native submodule changes.
