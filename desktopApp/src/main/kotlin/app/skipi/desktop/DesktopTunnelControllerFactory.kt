// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import platform.LocalProxyXrayConfigOptions

/** Wires the Desktop tunnel controller to Core, the system proxy and the latest user data. */
internal fun createDesktopTunnelController(
    coreController: DesktopCoreController,
    systemProxyManager: DesktopSystemProxyManager,
    localProxyRuntime: DesktopLocalProxyRuntime,
    localProxyReadiness: DesktopLocalProxyReadiness,
    resourceFiles: DesktopProfileResourceFiles,
    coreLogs: DesktopCoreLogs,
    readSettings: () -> DesktopAppSettings,
    readServers: () -> DesktopServerLibrary,
    readConfigs: () -> DesktopConfigLibrary,
    readLatency: () -> Map<Int, DesktopServerLatencyResult>,
    tunHost: DesktopTunHost,
): DesktopTunnelController {
    fun runningHttpProxyOptions(): Result<LocalProxyXrayConfigOptions> {
        val options = localProxyRuntime.activeOptions(coreRunning = coreController.state().isRunning)
        return if (options?.enableHttpProxy == true) {
            Result.success(options)
        } else {
            Result.failure(IllegalStateException("The running Core has no configured system-proxy HTTP inbound"))
        }
    }

    return DesktopTunnelController(
        configForProfile = {
            val settingsSnapshot = readSettings()
            val routingSnapshot = settingsSnapshot.toRoutingConfigRecord()
            val serverLibrarySnapshot = readServers()
            val configLibrarySnapshot = readConfigs()
            val trafficConfigContentSnapshot = configLibrarySnapshot.configs
                .associate { config -> config.id to config.content }
            val latencySnapshot = readLatency().toMap()
            runCatching {
                val globalAssetsDirectory = DesktopCoreRuntimes.discover().getOrThrow().directory
                val options = localProxyRuntime.prepareStart(settingsSnapshot)
                buildDesktopSelectedServerConfig(
                    serverLibrary = serverLibrarySnapshot,
                    configLibrary = configLibrarySnapshot,
                    routing = routingSnapshot,
                    options = options,
                    trafficConfigContentById = trafficConfigContentSnapshot,
                    subscriptionPingUrl = settingsSnapshot.subscriptionPingUrl,
                    latencyByServerId = latencySnapshot,
                    routingDataDirectory = globalAssetsDirectory,
                    strictFullTunnel = settingsSnapshot.enableStrictFullTunnel,
                )?.getOrThrow()
                    ?: error("Select a working server before connecting")
            }
        },
        startPlanForProfile = { _ ->
            runCatching {
                val settingsSnapshot = readSettings()
                val serverLibrarySnapshot = readServers()
                val configLibrarySnapshot = readConfigs()
                val latencySnapshot = readLatency().toMap()
                prepareDesktopTunnelStartPlan(
                    settings = settingsSnapshot,
                    serverLibrary = serverLibrarySnapshot,
                    configLibrary = configLibrarySnapshot,
                    latencyByServerId = latencySnapshot,
                    localProxyRuntime = localProxyRuntime,
                    resourceFiles = resourceFiles,
                    globalAssetsDirectory = DesktopCoreRuntimes.discover().getOrThrow().directory,
                )
            }
        },
        startCore = { request ->
            // Like Android: truncate the previous run's files, then follow the new ones.
            coreLogs.clear()
            coreLogs.startTailers(readSettings())
            localProxyRuntime.onCoreStarted(
                coreController.start(request.configuration, request.assetsDirectory, request.amneziaWg),
            )
        },
        stopCore = {
            localProxyRuntime.onCoreStopped(coreController.stop()).also {
                coreLogs.stopTailers()
            }
        },
        coreState = coreController::state,
        readCoreTraffic = coreController::readTunnelTraffic,
        awaitSystemProxyEndpoint = {
            if (!readSettings().useSystemProxy || !systemProxyManager.isSupportedHost()) {
                Result.success(Unit)
            } else {
                runningHttpProxyOptions().fold(
                    onSuccess = { options ->
                        localProxyReadiness.awaitLoopbackHttpEndpoint(
                            port = options.httpProxyPort,
                            coreState = coreController::state,
                        )
                    },
                    onFailure = { error -> Result.failure(error) },
                )
            }
        },
        acquireSystemProxy = {
            when {
                !readSettings().useSystemProxy -> Result.success(Unit)
                !systemProxyManager.isSupportedHost() -> Result.failure(
                    IllegalStateException("Системный прокси не поддерживается на этой платформе"),
                )

                else -> runningHttpProxyOptions().mapCatching { options ->
                    systemProxyManager.acquire(
                        DesktopSystemProxyEndpoints(
                            httpPort = options.httpProxyPort,
                            socksPort = options.socksPort,
                        ),
                    ).getOrThrow()
                    Unit
                }
            }
        },
        releaseSystemProxy = {
            if (systemProxyManager.isSupportedHost()) {
                systemProxyManager.release().map { }
            } else {
                Result.success(Unit)
            }
        },
        systemProxySupported = systemProxyManager::isSupportedHost,
        // No silent fallback: with TUN mode on, a machine that cannot run it fails the connect and says why.
        tunMode = { readSettings().enableTunMode },
        prepareTun = {
            runCatching {
                tunHost.unavailableReason()?.let { reason -> error(reason) }
                tunHost.tunRuntime().prepare(desktopTunInterfacePlan(readSettings(), readConfigs())).getOrThrow()
            }
        },
        releaseTun = { runCatching { tunHost.tunRuntime().release().getOrThrow() } },
        startTun = {
            runCatching {
                tunHost.unavailableReason()?.let { reason -> error(reason) }
                val options = localProxyRuntime.activeOptions(coreRunning = coreController.state().isRunning)
                    ?: error("SKIPI Core is not running")
                val request = desktopTunStartRequest(
                    plan = desktopTunInterfacePlan(readSettings(), readConfigs()),
                    options = options,
                    logPath = DesktopLogger.logDirectory().resolve("tun2socks.log").toString(),
                )
                tunHost.tunRuntime().start(request).getOrThrow()
            }
        },
        stopTun = { runCatching { tunHost.tunRuntime().stop().getOrThrow() } },
        tunRunning = { tunHost.tunRuntime().isRunning() },
        tunSupported = { tunHost.hostSupported },
    )

}
