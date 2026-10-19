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

/** Executes a captured resource update plan while leaving platform operations to its host callbacks. */
fun executeResourceFileUpdateBatch(
    plan: ResourceFileUpdateDownloadPlan,
    currentStatus: () -> ResourceFilesStatus,
    prepareSession: () -> ResourceFileUpdateBatchSession,
    markSource: (Int) -> Unit,
    mapCancellationError: (ResourceFileDownloadCancelledException) -> ResourceFileDownloadCancelledException,
): ResourceFilesStatus {
    if (plan.downloads.isEmpty()) return currentStatus()

    val session = prepareSession()
    val result = runCatching {
        plan.downloads.forEachIndexed { index, download ->
            try {
                session.updateFile(download, index, plan.downloads.size)
            } catch (error: ResourceFileDownloadCancelledException) {
                throw error
            } catch (error: Throwable) {
                throw ResourceFileDownloadFailedException(download.displayName(), error)
            }
        }
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
