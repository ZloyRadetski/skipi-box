// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import app.effectiveFakeDnsEnabled
import engine.network.findAvailableTcpPort
import engine.network.NetworkDefaults
import engine.network.toPortOrNull
import engine.vpn.VpnDefaults
import engine.xray.toJsonStringArray
import engine.xray.xraySniffingDestOverrides
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val LocalProxyLoopbackAddress = NetworkDefaults.IPV4_LOOPBACK_ADDRESS
private const val LocalProxyAllInterfacesAddress = NetworkDefaults.IPV4_ANY_ADDRESS

internal data class LocalProxyOptions(
    val listenAddress: String,
    val port: Int,
    val username: String,
    val password: String,
)

internal object LocalProxyRuntime {
    private val currentOptions = AtomicReference<LocalProxyOptions?>()

    fun update(options: LocalProxyOptions) {
        currentOptions.set(options)
    }

    fun clear() {
        currentOptions.set(null)
    }

    fun current(): LocalProxyOptions? {
        return currentOptions.get()
    }
}

internal fun AppState.withResolvedDynamicLocalProxyPort(
    excludedPorts: Set<Int> = emptySet(),
): AppState {
    if (!enableDynamicLocalProxyPort) return this

    val listenAddress = localProxyListenAddress()
    val reservedPorts = localProxyExcludedPorts() + excludedPorts
    // Dynamic means a fresh endpoint for every service start. Keeping the
    // previous value here made the option behave like a fallback only.
    val resolvedPort = availablePort(listenAddress, reservedPorts)
        ?: error("Unable to allocate a local proxy port")
    val resolvedPortText = resolvedPort.toString()
    return if (localProxyPort == resolvedPortText) this else copy(localProxyPort = resolvedPortText)
}

internal fun AppState.toLocalProxyOptions(): LocalProxyOptions {
    return LocalProxyOptions(
        listenAddress = localProxyListenAddress(),
        port = localProxyPort.toPortOrNull() ?: VpnDefaults.LOCAL_PROXY_PORT,
        username = if (enableLocalProxyAuth) localProxyUsername.trim() else "",
        password = if (enableLocalProxyAuth) localProxyPassword else "",
    )
}

internal fun buildLocalSocksInbound(
    appState: AppState,
    tag: String,
    options: LocalProxyOptions,
): JsonObject {
    val socksInbound = LocalProxyXrayConfigFactory.buildSocksInbound(
        options = LocalProxyXrayConfigOptions(
            socksPort = options.port,
            listenAddress = options.listenAddress,
            username = options.username,
            password = options.password,
        ),
        tag = tag,
    )
    return buildJsonObject {
        socksInbound.forEach { (key, value) -> put(key, value) }
        put(
            "sniffing",
            buildJsonObject {
                put("enabled", appState.enableSniffing)
                put("destOverride", xraySniffingDestOverrides(appState.effectiveFakeDnsEnabled).toJsonStringArray())
                put("routeOnly", appState.enableSniffingRouteOnly)
            },
        )
    }
}

private fun AppState.localProxyListenAddress(): String {
    return if (localProxyListenAllInterfaces) {
        LocalProxyAllInterfacesAddress
    } else {
        LocalProxyLoopbackAddress
    }
}

private fun AppState.localProxyExcludedPorts(): Set<Int> {
    return buildSet {
        // SKIPI is VPN-only, so ROOT-mode inbound ports are never reserved.
    }
}

internal fun availablePort(
    listenAddress: String,
    excludedPorts: Set<Int> = emptySet(),
): Int? {
    return findAvailableTcpPort(listenAddress, excludedPorts)
}
