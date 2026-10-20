// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.network.toPortOrNull
import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileKind
import features.resources.ResourceFileOperations
import features.resources.ResourceFileScope
import features.resources.ResourceFileUpdateOptions
import features.resources.ResourceFilesStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.LocalProxyXrayConfigOptions
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Desktop adapter for the portable resource-file management screen. */
internal class DesktopResourceFileOperations(
    private val resourceFiles: DesktopProfileResourceFiles,
    private val pickFile: suspend () -> Path?,
) : ResourceFileOperations {
    override suspend fun status(
        scope: ResourceFileScope,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        resourceFiles.snapshot(scope).currentStatus(customResourceFiles)
    }

    override suspend fun replace(
        scope: ResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus? {
        val pickedFile = pickFile() ?: return null
        return withContext(Dispatchers.IO) {
            val snapshot = resourceFiles.snapshot(scope)
            snapshot.replace(kind) { Files.newInputStream(pickedFile) }
            snapshot.currentStatus(customResourceFiles)
        }
    }

    override suspend fun replaceCustom(
        scope: ResourceFileScope,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus? {
        val pickedFile = pickFile() ?: return null
        return withContext(Dispatchers.IO) {
            val snapshot = resourceFiles.snapshot(scope)
            snapshot.replaceCustom(customFile) { Files.newInputStream(pickedFile) }
            snapshot.currentStatus(customResourceFiles)
        }
    }

    override suspend fun restoreBundled(
        scope: ResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        resourceFiles.restoreBundled(scope, kind)
        resourceFiles.snapshot(scope).currentStatus(customResourceFiles)
    }

    override suspend fun renameCustom(
        scope: ResourceFileScope,
        previousFile: TrafficConfigCustomResourceFile,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        val snapshot = resourceFiles.snapshot(scope)
        snapshot.renameCustom(previousFile, customFile)
        snapshot.currentStatus(customResourceFiles)
    }

    override suspend fun deleteCustom(
        scope: ResourceFileScope,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        val snapshot = resourceFiles.snapshot(scope)
        snapshot.deleteCustom(customFile)
        snapshot.currentStatus(customResourceFiles)
    }
}

internal fun DesktopAppSettings.resourceFileUpdateOptions(
    userAgent: String,
    proxyRunning: Boolean,
): ResourceFileUpdateOptions = ResourceFileUpdateOptions(
    useRunningProxy = proxyRunning,
    fallbackProxyPort = (localProxyPortRaw ?: localProxyPort.toString()).toPortOrNull(),
    fallbackProxyUsername = localProxyUsername,
    fallbackProxyPassword = localProxyPassword,
    userAgent = userAgent,
    showNotifications = false,
)

internal fun resolveDesktopResourceFileDownloadProxy(
    options: ResourceFileUpdateOptions,
    runtime: LocalProxyXrayConfigOptions?,
): DesktopSubscriptionSocksProxy? {
    if (!options.useRunningProxy) return null

    val port = runtime?.socksPort ?: options.fallbackProxyPort
        ?.takeIf { it in 1..65_535 }
        ?: error("Local proxy port is unavailable")
    return DesktopSubscriptionSocksProxy(
        host = "127.0.0.1",
        port = port,
        username = runtime?.username ?: options.fallbackProxyUsername,
        password = runtime?.password ?: options.fallbackProxyPassword,
    )
}

/** Opens an unrestricted file picker on Swing's event-dispatch thread. */
internal suspend fun chooseDesktopResourceFile(
    showPicker: (JFileChooser) -> Int = { chooser -> chooser.showOpenDialog(null) },
): Path? = suspendCancellableCoroutine { continuation ->
    val activeChooser = AtomicReference<JFileChooser?>()
    continuation.invokeOnCancellation {
        SwingUtilities.invokeLater { activeChooser.get()?.cancelSelection() }
    }

    val showOnEdt = {
        if (continuation.isActive) {
            var chooser: JFileChooser? = null
            try {
                val createdChooser = JFileChooser()
                chooser = createdChooser
                activeChooser.set(createdChooser)
                val selectedPath = if (showPicker(createdChooser) == JFileChooser.APPROVE_OPTION) {
                    createdChooser.selectedFile?.toPath()
                } else {
                    null
                }
                if (continuation.isActive) continuation.resume(selectedPath)
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                chooser?.let { activeChooser.compareAndSet(it, null) }
            }
        }
    }

    if (SwingUtilities.isEventDispatchThread()) showOnEdt() else SwingUtilities.invokeLater { showOnEdt() }
}
