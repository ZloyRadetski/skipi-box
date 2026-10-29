// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.xray.XrayLogConfigRequest
import engine.xray.XraySniffingOptions
import engine.xray.XrayFragmentOutboundOptions
import engine.xray.XrayMuxOutboundOptions
import engine.xray.xrayDirectOutboundDomainStrategy
import app.skipi.app.model.SubscriptionRecord
import engine.network.toPortOrNull
import java.time.Duration
import platform.DefaultLocalSocksPort
import platform.LocalProxyXrayConfigOptions
import platform.allocateAvailableTcpPort

/** Shared policy around the host operation that binds a free TCP port. */
internal fun resolveDesktopLocalProxyPort(
    currentPort: Int,
    dynamicEnabled: Boolean,
    listenAddress: String,
    excludedPorts: Set<Int>,
    allocatePort: (String, Set<Int>) -> Int?,
): Int? = if (dynamicEnabled) {
    allocatePort(listenAddress, excludedPorts)
} else {
    currentPort
}

/** Uses the same bounded JVM socket probe as Android's existing port allocator. */
internal fun allocateDesktopLocalProxyPort(
    listenAddress: String,
    excludedPorts: Set<Int>,
): Int? = allocateAvailableTcpPort(listenAddress, excludedPorts)

/** Builds the current local Core listener options, including enabled SOCKS credentials. */
internal fun DesktopAppSettings.toLocalProxyXrayConfigOptions(
    resolvedSocksPort: Int? = null,
    logPaths: DesktopCoreLogPaths = DesktopCoreLogPaths.default(),
): LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(
    socksPort = resolvedSocksPort
        ?: (localProxyPortRaw ?: localProxyPort.toString()).toPortOrNull()
        ?: DefaultLocalSocksPort,
    httpProxyPort = localHttpProxyPort,
    enableHttpProxy = useSystemProxy && isDesktopSystemProxySupported(),
    listenAddress = localProxyListenAddress,
    username = if (enableLocalProxyAuth) localProxyUsername.trim() else "",
    password = if (enableLocalProxyAuth) localProxyPassword else "",
    logLevel = coreLogLevel,
    log = XrayLogConfigRequest(
        coreLogLevel = DesktopCoreLogLevels.indexOf(coreLogLevel).takeIf { it >= 0 } ?: 3,
        enableAccessLog = enableAccessLog,
        accessLogPath = logPaths.accessLogPath,
        errorLogPath = logPaths.errorLogPath,
    ),
    sniffing = XraySniffingOptions(enabled = enableSniffing, routeOnly = enableSniffingRouteOnly),
    mux = if (enableMux) XrayMuxOutboundOptions(muxConcurrency, muxXudpConcurrency, muxXudpProxyUdp443) else null,
    fragment = if (enableFragment) {
        XrayFragmentOutboundOptions(
            // Desktop has no IPv6 preference setting yet; Android's defaults select IPv4.
            domainStrategy = xrayDirectOutboundDomainStrategy(enableIpv6 = false, enableIpv6Prefer = false),
            packets = fragmentPackets,
            length = fragmentLength,
            interval = fragmentInterval,
        )
    } else {
        null
    },
)

/** Converts persisted Android-shaped settings into the Desktop HTTP fetch transport snapshot. */
internal fun DesktopAppSettings.toSubscriptionRefreshFetchOptions(
    subscription: SubscriptionRecord,
    proxyRunning: Boolean,
    resolvedLocalProxyPort: Int? = null,
): DesktopSubscriptionRefreshFetchOptions {
    val proxy = if (subscription.updateViaProxy && proxyRunning) {
        DesktopSubscriptionSocksProxy(
            // Android's fetch option uses the local VPN endpoint, never the LAN bind address.
            host = "127.0.0.1",
            port = resolvedLocalProxyPort ?: localProxyPort,
            username = if (enableLocalProxyAuth) localProxyUsername.trim() else "",
            password = if (enableLocalProxyAuth) localProxyPassword else "",
        )
    } else {
        null
    }
    return DesktopSubscriptionRefreshFetchOptions(
        timeout = Duration.ofSeconds(subscriptionFetchTimeoutSeconds.toLong()),
        proxy = proxy,
        deviceHeaders = if (sendDeviceHeaders) DesktopDeviceIdentity.deviceHeaders(installationUuid) else emptyMap(),
        ageSecretKey = subscription.ageSecretKey,
        fallbackUserAgent = subscriptionUserAgent.trim().ifBlank { DefaultDesktopSubscriptionUserAgent },
    )
}

/**
 * Captures the exact listener configuration prepared for a real Core start and
 * publishes it only while that Core session is actually running.
 */
internal class DesktopLocalProxyRuntime {
    private val lock = Any()
    private var pendingStartOptions: LocalProxyXrayConfigOptions? = null
    private var runningOptions: LocalProxyXrayConfigOptions? = null

    /** Call from the tunnel's real config callback; previews must use the static settings projection. */
    fun prepareStart(
        settings: DesktopAppSettings,
        excludedPorts: Set<Int> = emptySet(),
        allocatePort: (String, Set<Int>) -> Int? = ::allocateDesktopLocalProxyPort,
    ): LocalProxyXrayConfigOptions {
        synchronized(lock) { pendingStartOptions = null }

        val baseOptions = settings.toLocalProxyXrayConfigOptions()
        val reservedPorts = buildSet {
            addAll(excludedPorts)
            if (baseOptions.enableHttpProxy) add(baseOptions.httpProxyPort)
        }
        val resolvedSocksPort = resolveDesktopLocalProxyPort(
            currentPort = baseOptions.socksPort,
            dynamicEnabled = settings.enableDynamicLocalProxyPort,
            listenAddress = baseOptions.listenAddress,
            excludedPorts = reservedPorts,
            allocatePort = allocatePort,
        ) ?: error("Unable to allocate a local proxy port")
        val options = settings.toLocalProxyXrayConfigOptions(resolvedSocksPort = resolvedSocksPort)
        synchronized(lock) { pendingStartOptions = options }
        return options
    }

    /** Returns the Core result unchanged and publishes only the options for a successful running session. */
    fun onCoreStarted(result: Result<DesktopCoreState>): Result<DesktopCoreState> {
        synchronized(lock) {
            val prepared = pendingStartOptions
            pendingStartOptions = null
            runningOptions = result.getOrNull()
                ?.takeIf { it.isRunning }
                ?.let { prepared }
        }
        return result
    }

    /** A failed stop leaves the endpoint snapshot available while Core may still own that listener. */
    fun onCoreStopped(result: Result<DesktopCoreState>): Result<DesktopCoreState> {
        synchronized(lock) {
            pendingStartOptions = null
            if (result.getOrNull()?.isRunning == false) {
                runningOptions = null
            }
        }
        return result
    }

    /** Reads only the last successful running snapshot; persisted settings are not runtime truth. */
    fun activeOptions(coreRunning: Boolean): LocalProxyXrayConfigOptions? = synchronized(lock) {
        runningOptions.takeIf { coreRunning }
    }

    fun subscriptionFetchOptions(
        settings: DesktopAppSettings,
        subscription: SubscriptionRecord,
        coreRunning: Boolean,
    ): DesktopSubscriptionRefreshFetchOptions {
        val active = activeOptions(coreRunning)
        val base = settings.toSubscriptionRefreshFetchOptions(
            subscription = subscription,
            proxyRunning = active != null,
            resolvedLocalProxyPort = active?.socksPort,
        )
        val proxy = if (subscription.updateViaProxy && active != null) {
            DesktopSubscriptionSocksProxy(
                host = "127.0.0.1",
                port = active.socksPort,
                username = active.username,
                password = active.password,
            )
        } else {
            null
        }
        return base.copy(proxy = proxy)
    }
}
