// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.net.Uri
import app.R
import app.CustomResourceFileState
import app.ResourceFileKind
import app.ResourceFilesStatus
import app.ResourceFileUpdateSource
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import engine.network.isPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import features.resources.ResourceFileUpdateOptions
import features.resources.ResourceFileUpdateDownload
import features.resources.ResourceFileUpdateDownloadPlan
import features.resources.planAllResourceFileDownloads
import features.resources.planBuiltInResourceFileDownload
import features.resources.planCustomResourceFileDownload

internal class AndroidResourceFileRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val downloader = AndroidResourceFileDownloader(appContext)

    private fun store(scope: XrayResourceFileScope): AndroidResourceFileStore =
        AndroidResourceFileStore(appContext, scope)

    suspend fun status(
        scope: XrayResourceFileScope,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus =
        withContext(Dispatchers.IO) {
            store(scope).status(customResourceFiles)
        }

    suspend fun synchronizeBundledFilesAfterPackageUpdate(scope: XrayResourceFileScope) {
        withContext(Dispatchers.IO) {
            store(scope).synchronizeBundledFilesAfterPackageUpdate(scope.resourceFileSource)
        }
    }

    suspend fun deleteCustom(
        scope: XrayResourceFileScope,
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState>,
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        val store = store(scope)
        store.deleteCustom(customFile)
        store.currentStatus(customResourceFiles)
    }

    suspend fun renameCustom(
        scope: XrayResourceFileScope,
        previousFile: CustomResourceFileState,
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState>,
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        val store = store(scope)
        store.renameCustom(previousFile, customFile)
        store.currentStatus(customResourceFiles)
    }

    suspend fun update(
        scope: XrayResourceFileScope,
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        updateTargets(
            plan = planAllResourceFileDownloads(
                scope = scope,
                source = source,
                customFiles = customResourceFiles,
            ),
            options = options,
            customResourceFiles = customResourceFiles,
        )
    }

    suspend fun update(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        updateTargets(
            plan = planBuiltInResourceFileDownload(
                scope = scope,
                kind = kind,
                source = source,
            ),
            options = options,
            customResourceFiles = customResourceFiles,
        )
    }

    suspend fun updateCustom(
        scope: XrayResourceFileScope,
        customFile: CustomResourceFileState,
        options: ResourceFileUpdateOptions,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        updateTargets(
            plan = planCustomResourceFileDownload(
                scope = scope,
                file = customFile,
            ),
            options = options,
            customResourceFiles = customResourceFiles,
        )
    }

    private fun updateTargets(
        plan: ResourceFileUpdateDownloadPlan,
        options: ResourceFileUpdateOptions,
        customResourceFiles: List<CustomResourceFileState>,
    ): ResourceFilesStatus {
        val scope = plan.scope
        val store = store(scope)
        val downloads = plan.downloads.map { download -> download.toDownloadTarget(store) }
        if (downloads.isEmpty()) {
            return store.currentStatus(customResourceFiles)
        }
        store.dataDir.mkdirs()
        AndroidResourceFileDownloadCancellation.begin()
        val notifier = AndroidResourceFileDownloadNotifier(
            context = appContext,
            notificationsEnabled = options.showNotifications,
        )
        val downloadProxy = options.toDownloadProxy()
        if (downloadProxy != null) {
            AndroidResourceFileLogger.info(
                "Resource file update will use local proxy ${downloadProxy.host}:${downloadProxy.port}",
            )
        }
        val userAgent = options.userAgent.ifBlank { null }
        val result = runCatching {
            downloads.forEachIndexed { index, download ->
                try {
                    notifier.showProgress(download.displayName, progress = null, force = true)
                    downloader.download(download.url, download.targetFile, downloadProxy, userAgent) { downloadedBytes, totalBytes ->
                        notifier.showProgress(
                            fileName = download.displayName,
                            progress = overallProgress(
                                fileIndex = index,
                                fileCount = downloads.size,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            ),
                        )
                    }
                    download.applyPermissions()
                } catch (error: Throwable) {
                    if (error is AndroidResourceFileDownloadCancelledException) throw error
                    throw ResourceFileDownloadFailedException(download.displayName, error)
                }
            }
            store.currentStatus(customResourceFiles)
        }
        result.onSuccess {
            if (plan.markResourceFileSourceAfterSuccess) {
                store.markResourceFileSource(scope.resourceFileSource)
            }
            runCatching { notifier.showComplete() }
        }.onFailure { error ->
            if (error is AndroidResourceFileDownloadCancelledException) {
                AndroidResourceFileLogger.info("Resource file update cancelled")
                runCatching { notifier.showCancelled() }
            } else {
                AndroidResourceFileLogger.error("Failed to update resource files", error)
                runCatching { notifier.showFailed(error.message ?: error::class.simpleName.orEmpty()) }
            }
        }
        return result.getOrElse { error ->
            if (error is AndroidResourceFileDownloadCancelledException) {
                throw AndroidResourceFileDownloadCancelledException(
                    appContext.getString(R.string.resource_file_download_notification_cancelled),
                )
            }
            throw error
        }
    }

    private fun ResourceFileUpdateDownload.toDownloadTarget(
        store: AndroidResourceFileStore,
    ): ResourceFileDownloadTarget = when (this) {
        is ResourceFileUpdateDownload.BuiltIn -> ResourceFileDownloadTarget(
            displayName = kind.displayName,
            url = url,
            targetFile = store.file(kind),
            applyPermissions = { store.applyPermissions(kind) },
        )

        is ResourceFileUpdateDownload.Custom -> ResourceFileDownloadTarget(
            displayName = file.name,
            url = url,
            targetFile = store.file(file),
        )
    }

    suspend fun replaceCustom(
        scope: XrayResourceFileScope,
        customFile: CustomResourceFileState,
        uri: Uri,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        val store = store(scope)
        store.replaceCustom(customFile, uri)
        store.currentStatus(customResourceFiles)
    }

    suspend fun replace(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        uri: Uri,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        val store = store(scope)
        store.replace(kind, uri)
        store.currentStatus(customResourceFiles)
    }

    suspend fun restoreBundled(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        val store = store(scope)
        store.restoreBundled(kind, scope.resourceFileSource)
        store.currentStatus(customResourceFiles)
    }
}

private data class ResourceFileDownloadTarget(
    val displayName: String,
    val url: String,
    val targetFile: java.io.File,
    val applyPermissions: () -> Unit = {},
)

private class ResourceFileDownloadFailedException(
    fileName: String,
    cause: Throwable,
) : RuntimeException("$fileName: ${cause.message ?: cause::class.simpleName.orEmpty()}", cause)

private fun ResourceFileUpdateOptions.toDownloadProxy(): AndroidResourceFileDownloadProxy? {
    if (!useRunningProxy) return null
    val runtimeOptions = LocalProxyRuntime.current()
    val port = runtimeOptions?.port
        ?: fallbackProxyPort?.takeIf(Int::isPort)
        ?: error("Local proxy port is unavailable")
    return AndroidResourceFileDownloadProxy(
        host = LocalProxyLoopbackAddress,
        port = port,
        username = runtimeOptions?.username ?: fallbackProxyUsername,
        password = runtimeOptions?.password ?: fallbackProxyPassword,
    )
}
