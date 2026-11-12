// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.resources.ResourceFileDownloadCancelledException
import features.resources.ResourceFileKind
import features.resources.ResourceFileUpdateDownload
import features.resources.ResourceFileUpdateDownloadPlan
import features.resources.ResourceFilesStatus

/** Host operations captured for one non-empty resource-file download batch. */
data class ResourceFileUpdateBatchSession(
    val updateFile: (ResourceFileUpdateDownload, Int, Int) -> Unit,
    val notifyComplete: () -> Unit,
    val reportFailure: (Throwable) -> Unit,
)

/** A per-file update failure that retains the original cause and display name. */
class ResourceFileDownloadFailedException(
    fileName: String,
    cause: Throwable,
) : RuntimeException("$fileName: ${cause.message ?: cause::class.simpleName.orEmpty()}", cause)

/** Runs `task(0 until count)`; a host may run the indices on several threads, but every task must be over when it returns. */
typealias ResourceFileTaskRunner = (count: Int, task: (Int) -> Unit) -> Unit

/** The plain runner: one file after the other. */
val SequentialResourceFileTaskRunner: ResourceFileTaskRunner = { count, task -> repeat(count, task) }

private class BatchStop {
    @kotlin.concurrent.Volatile
    var stopped = false
}

/**
 * Executes a captured resource update plan while leaving platform operations to its host callbacks.
 *
 * The files go through [runTasks], so a host can download them side by side; [onFileUpdated] reports each file the
 * moment it is on disk, not when the whole batch ends. The first failure stops files that have not started yet, those
 * already running finish.
 */
fun executeResourceFileUpdateBatch(
    plan: ResourceFileUpdateDownloadPlan,
    currentStatus: () -> ResourceFilesStatus,
    prepareSession: () -> ResourceFileUpdateBatchSession,
    markSource: (Int) -> Unit,
    mapCancellationError: (ResourceFileDownloadCancelledException) -> ResourceFileDownloadCancelledException,
    runTasks: ResourceFileTaskRunner = SequentialResourceFileTaskRunner,
    onFileUpdated: (ResourceFileUpdateDownload) -> Unit = {},
): ResourceFilesStatus {
    if (plan.downloads.isEmpty()) return currentStatus()

    val session = prepareSession()
    val result = runCatching {
        val count = plan.downloads.size
        val errors = arrayOfNulls<Throwable>(count)
        val stop = BatchStop()
        runTasks(count) { index ->
            if (stop.stopped) return@runTasks
            val download = plan.downloads[index]
            try {
                session.updateFile(download, index, count)
            } catch (error: ResourceFileDownloadCancelledException) {
                errors[index] = error
                stop.stopped = true
                return@runTasks
            } catch (error: Throwable) {
                errors[index] = ResourceFileDownloadFailedException(download.displayName(), error)
                stop.stopped = true
                return@runTasks
            }
            runCatching { onFileUpdated(download) }
        }
        val failures = errors.filterNotNull()
        failures.firstOrNull { it is ResourceFileDownloadCancelledException }?.let { throw it }
        failures.firstOrNull()?.let { throw it }
        currentStatus()
    }

    result.onSuccess {
        if (plan.markResourceFileSourceAfterSuccess) {
            markSource(plan.scope.resourceFileSource)
        }
        runCatching { session.notifyComplete() }
    }.onFailure { error ->
        session.reportFailure(error)
    }

    return result.getOrElse { error ->
        if (error is ResourceFileDownloadCancelledException) {
            throw mapCancellationError(error)
        }
        throw error
    }
}

private fun ResourceFileUpdateDownload.displayName(): String = when (this) {
    is ResourceFileUpdateDownload.BuiltIn -> kind.displayName
    is ResourceFileUpdateDownload.Custom -> file.name
}
