// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileDownloadCancelledException
import features.resources.ResourceFileUpdateDownload
import features.resources.ResourceFileUpdateDownloadPlan
import features.resources.ResourceFileUpdateOptions
import features.resources.ResourceFileUpdateRequest
import features.resources.ResourceFileUpdateTarget
import features.resources.ResourceFilesStatus
import features.resources.planAllResourceFileDownloads
import features.resources.planBuiltInResourceFileDownload
import features.resources.planCustomResourceFileDownload
import features.resources.toUpdateTarget
import features.resources.runtime.ResourceFileDownloadCancellation
import features.resources.runtime.ResourceFileUpdateBatchSession
import features.resources.runtime.executeResourceFileUpdateBatch
import features.resources.runtime.ResourceFileBatchProgress
import features.resources.runtime.parallelResourceFileTaskRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Platform feedback captured for one resource-file download batch. */
internal data class DesktopResourceFileUpdateFeedback(
    val onProgress: (displayName: String, progress: Int?, force: Boolean) -> Unit,
    val onComplete: () -> Unit,
    val onFailure: (Throwable) -> Unit,
    val mapCancellationError: (ResourceFileDownloadCancelledException) -> ResourceFileDownloadCancelledException,
)

/** Executes resource-file plans against one captured Desktop profile scope. */
internal class DesktopResourceFileUpdateRunner(
    private val resourceFiles: DesktopProfileResourceFiles,
    private val resolveProxy: (ResourceFileUpdateOptions) -> DesktopSubscriptionSocksProxy?,
    private val prepareFeedback: (ResourceFileUpdateOptions) -> DesktopResourceFileUpdateFeedback,
) {
    suspend fun execute(
        request: ResourceFileUpdateRequest,
        onTargetFinished: (ResourceFileUpdateTarget) -> Unit = {},
    ): ResourceFilesStatus =
        withContext(Dispatchers.IO) {
            runCatching { executeOnIo(request, onTargetFinished) }
        }.getOrThrow()

    private fun executeOnIo(
        request: ResourceFileUpdateRequest,
        onTargetFinished: (ResourceFileUpdateTarget) -> Unit,
    ): ResourceFilesStatus {
        val (plan, options, customResourceFiles) = request.toExecutionInputs()
        val snapshot = resourceFiles.snapshot(plan.scope)
        val targets = plan.downloads.map { download ->
            when (download) {
                is ResourceFileUpdateDownload.BuiltIn -> ResourceFileDownloadTarget(
                    displayName = download.kind.displayName,
                    url = download.url,
                    file = snapshot.file(download.kind),
                )

                is ResourceFileUpdateDownload.Custom -> ResourceFileDownloadTarget(
                    displayName = download.file.name,
                    url = download.url,
                    file = snapshot.file(download.file),
                )
            }
        }
        val progress = ResourceFileBatchProgress(targets.map { it.displayName })
        var feedback: DesktopResourceFileUpdateFeedback? = null

        return executeResourceFileUpdateBatch(
            plan = plan,
            currentStatus = { snapshot.currentStatus(customResourceFiles) },
            prepareSession = {
                snapshot.dataDir.mkdirs()
                ResourceFileDownloadCancellation.begin()
                val preparedFeedback = prepareFeedback(options)
                feedback = preparedFeedback
                val proxy = if (options.useRunningProxy) resolveProxy(options) else null
                if (proxy != null) {
                    DesktopLogger.info(
                        "ResourceFiles",
                        "Resource file update will use local proxy ${proxy.host}:${proxy.port}",
                    )
                }
                val userAgent = options.userAgent.ifBlank { null }
                val downloader = resourceFiles.createDownloader()
                ResourceFileUpdateBatchSession(
                    updateFile = { _, fileIndex, _ ->
                        val target = targets[fileIndex]
                        val started = progress.snapshot()
                        preparedFeedback.onProgress(started.fileName, started.percent, true)
                        downloader.download(target.url, target.file, proxy, userAgent) { downloadedBytes, totalBytes ->
                            val shown = progress.report(fileIndex, downloadedBytes, totalBytes)
                            preparedFeedback.onProgress(shown.fileName, shown.percent, false)
                        }
                        progress.finish(fileIndex)
                    },
                    notifyComplete = preparedFeedback.onComplete,
                    reportFailure = { error ->
                        if (error is ResourceFileDownloadCancelledException) {
                            DesktopLogger.info("ResourceFiles", "Resource file update cancelled")
                        } else {
                            DesktopLogger.error("ResourceFiles", "Failed to update resource files", error)
                        }
                        runCatching { preparedFeedback.onFailure(error) }
                    },
                )
            },
            markSource = snapshot::markResourceFileSource,
            mapCancellationError = { error ->
                checkNotNull(feedback).mapCancellationError(error)
            },
            runTasks = parallelResourceFileTaskRunner(),
            onFileUpdated = { download -> onTargetFinished(download.toUpdateTarget()) },
        )
    }

    private fun ResourceFileUpdateRequest.toExecutionInputs(): ExecutionInputs = when (this) {
        is ResourceFileUpdateRequest.All -> ExecutionInputs(
            plan = planAllResourceFileDownloads(scope, source, customResourceFiles),
            options = options,
            customResourceFiles = customResourceFiles,
        )

        is ResourceFileUpdateRequest.BuiltIn -> ExecutionInputs(
            plan = planBuiltInResourceFileDownload(scope, kind, source),
            options = options,
            customResourceFiles = customResourceFiles,
        )

        is ResourceFileUpdateRequest.Custom -> ExecutionInputs(
            plan = planCustomResourceFileDownload(scope, file),
            options = options,
            customResourceFiles = customResourceFiles,
        )
    }

    private data class ExecutionInputs(
        val plan: ResourceFileUpdateDownloadPlan,
        val options: ResourceFileUpdateOptions,
        val customResourceFiles: List<TrafficConfigCustomResourceFile>,
    )

    private data class ResourceFileDownloadTarget(
        val displayName: String,
        val url: String,
        val file: File,
    )
}
