// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.resolveSubscriptionDeviceHeaders
import engine.xray.XrayLogConfigRequest
import engine.xray.XraySniffingOptions
import engine.xray.XrayFragmentOutboundOptions
import engine.xray.XrayMuxOutboundOptions
import engine.xray.xrayTcpSockopt
import engine.xray.xrayDirectOutboundDomainStrategy
import app.skipi.app.model.SubscriptionRecord
import engine.network.toPortOrNull
import java.time.Duration
import platform.DefaultLocalSocksPort
import platform.LocalProxyAuth
import platform.LocalProxyCredentials
import platform.LocalProxyJsonAuth
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
/**
 * The Core listener options of these settings. [jsonAuth] is the login the selected custom JSON carries on
 * its own inbounds, which the "auto" and "from JSON" modes read.
 */
internal fun DesktopAppSettings.toLocalProxyXrayConfigOptions(
    resolvedSocksPort: Int? = null,
    resolvedHttpPort: Int? = null,
    logPaths: DesktopCoreLogPaths = DesktopCoreLogPaths.default(),
    jsonAuth: LocalProxyJsonAuth? = null,
): LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(
    socksPort = resolvedSocksPort
        ?: (localProxyPortRaw ?: localProxyPort.toString()).toPortOrNull()
        ?: DefaultLocalSocksPort,
    httpProxyPort = resolvedHttpPort ?: localHttpProxyPort,
    enableHttpProxy = httpInboundEnabled(),
    listenAddress = localProxyListenAddress,
    // The HTTP inbound listens like SOCKS; a system proxy still points at loopback, which a wildcard listener serves too.
    httpProxyListenAddress = localProxyListenAddress,
    username = resolvedSocksLogin(jsonAuth).username,
    password = resolvedSocksLogin(jsonAuth).password,
    httpUsername = resolvedHttpLogin(jsonAuth).username,
    httpPassword = resolvedHttpLogin(jsonAuth).password,
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
    tcpSockopt = xrayTcpSockopt(tunTcpKeepAliveInterval, tunTcpUserTimeout),
)

/**
 * Whether a running tunnel was built from values that [updated] changes: the listener, logging, the
 * TUN interface, the Xray transport options and the global sniffing, Mux, Fragment and routing a
 * start without a profile uses. Appearance and other settings the Core never sees do not count.
 */
internal fun desktopSettingsNeedReconnect(previous: DesktopAppSettings, updated: DesktopAppSettings): Boolean =
    previous.coreRuntimeInputs() != updated.coreRuntimeInputs()

private fun DesktopAppSettings.coreRuntimeInputs(): List<Any?> = listOf(
    localProxyPort, localProxyPortRaw, localHttpProxyPort, useSystemProxy, localProxyListenAddress,
    enableDynamicLocalProxyPort, enableLocalProxyAuth, localProxyUsername, localProxyPassword, localProxyAuthMode,
    enableLocalHttpProxy, enableLocalHttpProxyAuth, localHttpProxyAuthMode, localHttpProxyUsername, localHttpProxyPassword,
    coreLogLevel, enableAccessLog, subscriptionPingUrl,
    enableTunMode, enableStrictFullTunnel, tunMtu, tunIpv4Cidr, tunIpv6Cidr, tunVpnDns,
    tunTcpKeepAliveInterval, tunTcpUserTimeout,
    enableSniffing, enableSniffingRouteOnly,
    enableMux, muxConcurrency, muxXudpConcurrency, muxXudpProxyUdp443,
    enableFragment, fragmentPackets, fragmentLength, fragmentInterval,
    routeDomainStrategy, defaultRouteOutboundTag, routeRules,
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
        deviceHeaders = if (resolveSubscriptionDeviceHeaders(subscription.deviceHeaders, sendDeviceHeaders)) {
            DesktopDeviceIdentity.deviceHeaders(installationUuid)
        } else {
            emptyMap()
        },
        ageSecretKey = subscription.ageSecretKey,
        fallbackUserAgent = DefaultSubscriptionUserAgent,
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
        /** The login of the selected custom JSON, which the "auto" and "from JSON" modes read. */
        jsonAuth: LocalProxyJsonAuth? = null,
        allocatePort: (String, Set<Int>) -> Int? = ::allocateDesktopLocalProxyPort,
    ): LocalProxyXrayConfigOptions {
        synchronized(lock) { pendingStartOptions = null }

        val baseOptions = settings.toLocalProxyXrayConfigOptions(jsonAuth = jsonAuth)
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
        // The system proxy points at the HTTP inbound, so a dynamic port has to move it too: with only
        // the SOCKS port dynamic, a busy HTTP port (v2rayN and others default to 10809) still failed the start.
        val resolvedHttpPort = if (baseOptions.enableHttpProxy && settings.enableDynamicLocalProxyPort) {
            resolveDesktopLocalProxyPort(
                currentPort = baseOptions.httpProxyPort,
                dynamicEnabled = true,
                listenAddress = baseOptions.httpProxyListenAddress,
                excludedPorts = excludedPorts + resolvedSocksPort,
                allocatePort = allocatePort,
            ) ?: error("Unable to allocate a local HTTP proxy port")
        } else {
            null
        }
        val options = settings.toLocalProxyXrayConfigOptions(
            resolvedSocksPort = resolvedSocksPort,
            resolvedHttpPort = resolvedHttpPort,
            jsonAuth = jsonAuth,
        )
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

/** The HTTP inbound runs when the user switched it on or when the system proxy, which points at it, is on. */
internal fun DesktopAppSettings.httpInboundEnabled(): Boolean =
    enableLocalHttpProxy || (useSystemProxy && isDesktopSystemProxySupported())

private fun DesktopAppSettings.resolvedSocksLogin(jsonAuth: LocalProxyJsonAuth?) = LocalProxyAuth.resolve(
    socksAuthMode(),
    LocalProxyCredentials(localProxyUsername, localProxyPassword),
    jsonAuth?.socks,
)

private fun DesktopAppSettings.resolvedHttpLogin(jsonAuth: LocalProxyJsonAuth?) = LocalProxyAuth.resolve(
    httpAuthMode(),
    LocalProxyCredentials(localHttpProxyUsername, localHttpProxyPassword),
    jsonAuth?.http,
)
