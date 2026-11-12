---
title: "Resource files download side by side, and a card stops animating when its own file is done"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 21, tokens: 179851 }
id: l-87c2d840
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/app/App.kt
  - app/src/main/kotlin/features/resources/ResourceFileUseCase.kt
  - app/src/main/kotlin/features/resources/runtime/AndroidResourceFileRepository.kt
  - app/src/test/kotlin/features/resources/ResourceFileUpdateCoordinatorTest.kt
  - app/src/test/kotlin/features/resources/runtime/AndroidResourceFileDownloadCancellationOracleTest.kt
  - app/src/test/kotlin/features/resources/runtime/AndroidResourceFileUpdateBatchOracleTest.kt
  - app/src/test/kotlin/features/resources/runtime/AndroidResourceFileUpdatePlanOracleTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/resources/DesktopResourceFileServices.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/resources/DesktopResourceFileUpdateRunner.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/resources/DesktopResourceFileManualUpdateScreenIntegrationTest.kt
---

## Decided
"Update all" runs its files through a host task runner, up to four at a time, all through the same proxy settings, because the animation used to last until the slowest file ended. The batch executor reports every file the moment it is on disk, and the queue state keeps those targets as finished for the running request, so that card is idle again while the others still download.
The first failure only stops files that have not started; files already running finish, because their download cannot be interrupted safely and a finished file should keep its new content and its idle card.

## Tried and ruled out
Moving the executor to coroutines: shared/core has no coroutines dependency, and a plain task-runner callback kept the executor in common code and its old tests running with the default sequential runner.
Bumping completionRevision per finished file: tests and the page rely on it counting finished requests; the page now also keys its file-status refresh on the finished set.

## Watch out
- Cancel must disconnect every tracked connection (the old code kept only the last one), otherwise a file stuck in connect() survives a cancel.
- Two custom files that map to the same target file now race; one of them wins, no longer always the last in the list.
- Progress is the mean over files and shows the first unfinished file's name; the desktop screen-integration test fails on Windows with AccessDenied deleting the skiko dll, also on the commit before this change.
- Test servers that wait for another request need a multi-threaded executor, the JDK default is one thread.

In the code: executeResourceFileUpdateBatch (runTasks, onFileUpdated), parallelResourceFileTaskRunner, ResourceFileBatchProgress, ResourceFileUpdateQueueState.finishedTargets.
