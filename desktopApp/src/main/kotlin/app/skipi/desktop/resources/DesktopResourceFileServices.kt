// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import features.config.TrafficConfigState
import features.config.runtime.TrafficConfigRefreshTarget
import features.config.runtime.shouldRunGeoResourceAutoUpdate
import features.resources.ResourceFileScope
import features.resources.ResourceFileUpdateOptions
import features.resources.ResourceFileUpdateCoordinator
import features.resources.ResourceFileUpdateRequest
import features.resources.resourceFileUpdateSource
import features.resources.runtime.ResourceFileDownloadCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.LocalProxyXrayConfigOptions
import java.nio.file.Files
import java.nio.file.Path

/** App-lifetime Desktop composition of resource storage, operations, manual queue, and updates. */
internal class DesktopResourceFileServices(
    scope: CoroutineScope,
    private val resourceFiles: DesktopProfileResourceFiles,
    private val readSettings: () -> DesktopAppSettings,
    private val readProfile: (Int) -> TrafficConfigState?,
    private val activeProxy: () -> LocalProxyXrayConfigOptions?,
    private val prepareFeedback: (ResourceFileUpdateOptions) -> DesktopResourceFileUpdateFeedback,
    pickFile: suspend () -> Path?,
    private val bundledUpdatedAtMillis: () -> Long = ::desktopResourceAssetsUpdatedAtMillis,
) {
    private val runner = DesktopResourceFileUpdateRunner(
        resourceFiles = resourceFiles,
        resolveProxy = { options -> resolveDesktopResourceFileDownloadProxy(options, activeProxy()) },
        prepareFeedback = prepareFeedback,
    )

    val operations = DesktopResourceFileOperations(
        resourceFiles = resourceFiles,
        pickFile = pickFile,
    )

    val queue = ResourceFileUpdateCoordinator(
        scope = scope,
        execute = runner::execute,
        cancelRunning = { ResourceFileDownloadCancellation.cancel() },
    )

    /** Captures the current Desktop settings and actual local proxy availability for this request. */
    fun optionsForUserAgent(userAgent: String): ResourceFileUpdateOptions =
        readSettings().resourceFileUpdateOptions(
            userAgent = userAgent,
            proxyRunning = activeProxy() != null,
        )

    /** Re-reads the requested profile and runs the current automatic plan outside the manual queue. */
    suspend fun updateAutomatically(profileId: Int) {
        val profile = readProfile(profileId) ?: return
        if (!shouldRunGeoResourceAutoUpdate(profileId, listOf(profile.toRefreshTarget()))) return

        runner.execute(
            ResourceFileUpdateRequest.All(
                scope = ResourceFileScope(
                    trafficConfigId = profile.id,
                    resourceFileSource = profile.resourceSettings.source,
                ),
                source = profile.resourceSettings.resourceFileUpdateSource(),
                options = optionsForUserAgent(profile.resourceSettings.userAgent),
                customResourceFiles = profile.resourceSettings.customFiles,
            ),
        )
    }

    /** Applies package-update restoration to the active profile, or Android's (1, 0) fallback scope. */
    suspend fun synchronizeBundledFilesAfterPackageUpdate(profile: TrafficConfigState?) {
        val scope = profile?.let {
            ResourceFileScope(
                trafficConfigId = it.id,
                resourceFileSource = it.resourceSettings.source,
            )
        } ?: ResourceFileScope(trafficConfigId = 1, resourceFileSource = 0)

        withContext(Dispatchers.IO) {
            resourceFiles.prepare(
                scope = scope,
                bundledUpdatedAtMillis = bundledUpdatedAtMillis(),
                restoreAfterPackageUpdate = true,
            )
        }
    }

    private fun TrafficConfigState.toRefreshTarget() = TrafficConfigRefreshTarget(
        id = id,
        sourceUrl = sourceUrl,
        autoUpdate = autoUpdate,
        updateLocked = updateLocked,
        updateInterval = updateInterval,
        resourceAutoUpdate = resourceSettings.autoUpdate,
        resourceUpdateInterval = resourceSettings.updateInterval,
    )
}

/** Use the packaged code location's timestamp as Desktop's bundle update fact. */
private fun desktopResourceAssetsUpdatedAtMillis(): Long = runCatching {
    val location = DesktopResourceFileServices::class.java.protectionDomain?.codeSource?.location
        ?: return@runCatching 0L
    Files.getLastModifiedTime(Path.of(location.toURI())).toMillis()
}.getOrDefault(0L)

internal fun desktopResourceProfileFallbackUserAgent(): String =
    versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME)
