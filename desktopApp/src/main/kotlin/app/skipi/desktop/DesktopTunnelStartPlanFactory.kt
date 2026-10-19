// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import app.skipi.app.config.toTrafficConfigState

/** Captures one selected-profile resource directory and rebuildable Core configuration. */
internal fun prepareDesktopTunnelStartPlan(
    settings: DesktopAppSettings,
    serverLibrary: DesktopServerLibrary,
    configLibrary: DesktopConfigLibrary,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    localProxyRuntime: DesktopLocalProxyRuntime,
    resourceFiles: DesktopProfileResourceFiles,
    allocatePort: (String, Set<Int>) -> Int? = ::allocateDesktopLocalProxyPort,
): DesktopTunnelStartPlan {
    val capturedConfigLibrary = configLibrary.copy(configs = configLibrary.configs.toList())
    val selectedConfigId = capturedConfigLibrary.selectedConfigId
    val selectedProfile = selectedConfigId?.let { id ->
        capturedConfigLibrary.configs.firstOrNull { profile -> profile.id == id }
    }
    val assetsDirectory = selectedProfile
        ?.toTrafficConfigState(versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME))
        ?.let { profile ->
            resourceFiles.prepare(
                profile = profile,
                bundledUpdatedAtMillis = 0L,
                restoreAfterPackageUpdate = false,
            ).dataDir.toPath()
        }
    val capturedTrafficConfigContentById = capturedConfigLibrary.configs
        .associate { profile -> profile.id to profile.content }
    val capturedLatencyByServerId = latencyByServerId.toMap()

    fun buildPlan(excludedPorts: Set<Int>): DesktopTunnelStartPlan {
        val options = localProxyRuntime.prepareStart(
            settings = settings,
            excludedPorts = excludedPorts,
            allocatePort = allocatePort,
        )
        val configuration = buildDesktopSelectedServerConfig(
            serverLibrary = serverLibrary,
            configLibrary = capturedConfigLibrary,
            options = options,
            trafficConfigContentById = capturedTrafficConfigContentById,
            subscriptionPingUrl = settings.subscriptionPingUrl,
            latencyByServerId = capturedLatencyByServerId,
        )?.getOrThrow()
            ?: error("Select a working server before connecting")
        return DesktopTunnelStartPlan(
            configuration = configuration,
            dynamicPortEnabled = settings.enableDynamicLocalProxyPort,
            localProxyPort = options.socksPort,
            rebuild = ::buildPlan,
            assetsDirectory = assetsDirectory,
        )
    }

    return buildPlan(emptySet())
}
