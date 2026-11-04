// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import app.effectiveFakeDnsEnabled
import app.httpProxyInboundEnabled
import app.localProxyHttpAuthMode
import app.localProxyHttpCredentials
import app.localProxySocksAuthMode
import app.localProxySocksCredentials
import engine.network.findAvailableTcpPort
import engine.network.NetworkDefaults
import engine.network.toPortOrNull
import engine.vpn.VpnDefaults
import engine.xray.toJsonStringArray
import engine.xray.xraySniffingDestOverrides
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import platform.LocalProxyAuth
import platform.LocalProxyJsonAuth
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val LocalProxyLoopbackAddress = NetworkDefaults.IPV4_LOOPBACK_ADDRESS
private const val LocalProxyAllInterfacesAddress = NetworkDefaults.IPV4_ANY_ADDRESS

data class LocalProxyOptions(
    val listenAddress: String,
    val port: Int,
    val username: String,
    val password: String,
    /** The HTTP inbound, which listens on [listenAddress] like SOCKS; only used while that inbound is enabled. */
    val httpPort: Int = VpnDefaults.LOCAL_HTTP_PROXY_PORT,
    val httpUsername: String = "",
    val httpPassword: String = "",
)

object LocalProxyRuntime {
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

fun AppState.withResolvedDynamicLocalProxyPort(
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
    val withSocksPort = if (localProxyPort == resolvedPortText) this else copy(localProxyPort = resolvedPortText)
    if (!httpProxyInboundEnabled) return withSocksPort

    // The HTTP inbound moves with the SOCKS one: a busy 10809 must not stop a dynamic start either.
    val resolvedHttpPort = availablePort(listenAddress, reservedPorts + resolvedPort)
        ?: error("Unable to allocate a local HTTP proxy port")
    val resolvedHttpPortText = resolvedHttpPort.toString()
    return if (localHttpProxyPort == resolvedHttpPortText) {
        withSocksPort
    } else {
        withSocksPort.copy(localHttpProxyPort = resolvedHttpPortText)
    }
}

/**
 * The listener options of this state. [jsonAuth] is the login a raw Xray JSON carries on its own
 * inbounds; the "auto" and "from JSON" modes read it, the others ignore it.
 */
fun AppState.toLocalProxyOptions(jsonAuth: LocalProxyJsonAuth? = null): LocalProxyOptions {
    val socks = LocalProxyAuth.resolve(localProxySocksAuthMode(), localProxySocksCredentials(), jsonAuth?.socks)
    val http = LocalProxyAuth.resolve(localProxyHttpAuthMode(), localProxyHttpCredentials(), jsonAuth?.http)
    return LocalProxyOptions(
        listenAddress = localProxyListenAddress(),
        port = localProxyPort.toPortOrNull() ?: VpnDefaults.LOCAL_PROXY_PORT,
        username = socks.username,
        password = socks.password,
        httpPort = localHttpProxyPort.toPortOrNull() ?: VpnDefaults.LOCAL_HTTP_PROXY_PORT,
        httpUsername = http.username,
        httpPassword = http.password,
    )
}

fun buildLocalSocksInbound(
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

fun availablePort(
    listenAddress: String,
    excludedPorts: Set<Int> = emptySet(),
): Int? {
    return findAvailableTcpPort(listenAddress, excludedPorts)
}

/**
 * The listener options for a start with [selectedServer]: a raw JSON server also supplies the login its
 * own inbounds carry, which the "auto" and "from JSON" modes read. Any other server carries none.
 */
fun AppState.toLocalProxyOptionsFor(selectedServer: ProxyServer<*>?): LocalProxyOptions {
    val jsonAuth = (selectedServer as? Custom)?.let { custom -> LocalProxyAuth.readJsonAuth(custom.configJson) }
    return toLocalProxyOptions(jsonAuth)
}
