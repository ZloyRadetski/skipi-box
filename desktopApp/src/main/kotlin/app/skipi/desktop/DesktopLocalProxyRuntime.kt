// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import java.time.Duration
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
): LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(
    socksPort = resolvedSocksPort ?: localProxyPort,
    httpProxyPort = localHttpProxyPort,
    enableHttpProxy = useSystemProxy && isDesktopSystemProxySupported(),
    listenAddress = localProxyListenAddress,
    username = if (enableLocalProxyAuth) localProxyUsername.trim() else "",
    password = if (enableLocalProxyAuth) localProxyPassword else "",
    logLevel = coreLogLevel,
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
