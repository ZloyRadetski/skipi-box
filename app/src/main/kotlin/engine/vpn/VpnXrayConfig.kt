// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.os.Process
import app.AppState
import app.effectiveLocalDnsEnabled
import engine.hevtun.DefaultHevSocks5TunnelTcpReadWriteTimeoutMillis
import engine.hevtun.HevSocks5TunnelConfig
import engine.hevtun.HevSocks5TunnelConfigFileName
import engine.hevtun.HevSocks5TunnelLogFileName
import engine.hevtun.hevSocks5TunnelLogFile
import engine.hevtun.hevSocks5TunnelSocksTargetAddress
import engine.proxy.LocalProxyOptions
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.buildLocalSocksInbound
import engine.proxy.toLocalProxyOptions
import engine.proxy.xrayStatsApiConfig
import engine.xray.XrayConfigFactory
import engine.xray.XrayConfigRequest
import engine.xray.XrayCoreLogPaths
import engine.xray.XrayOutboundPlan
import engine.xray.XrayTags
import engine.xray.buildXrayOutboundPlan
import engine.xray.prepareXrayCoreLogPaths
import engine.xray.validateXrayExternalRoutingResources
import engine.network.findAvailableTcpPort
import engine.network.isIpv4Address
import engine.network.isPort
import engine.network.isTcpPortAvailable
import features.resources.runtime.prepareXrayResourceFilePaths
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import features.proxy.server.model.OlcRtc
import system.toAndroidUserId
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


internal data class ActiveOlcRtcBridge(
    val socksPort: Int,
    val socksUser: String,
    val socksPass: String,
    val server: OlcRtc,
)

internal data class ActiveAmneziaWgBridge(
    val socksPort: Int,
    val server: AmneziaWg,
)

internal data class NativeBridgePlanResult(
    val outboundPlan: XrayOutboundPlan,
    val olcRtcYaml: String?,
    val olcRtcSocksPort: Int,
    val activeOlcRtcBridge: ActiveOlcRtcBridge?,
    val amneziaWgConfigJson: String?,
    val amneziaWgSocksPort: Int,
    val activeAmneziaWgBridge: ActiveAmneziaWgBridge?,
)

internal data class VpnServiceStartConfig(
    val sessionName: String,
    val mtu: Int = VpnDefaults.MTU,
    val ipv4Address: String = defaultIpv4TunAddress.address,
    val ipv4PrefixLength: Int = defaultIpv4TunAddress.prefixLength,
    val ipv6Address: String? = null,
    val ipv6PrefixLength: Int = defaultIpv6TunAddress.prefixLength,
    val enableIpv6: Boolean = false,
    val enableLocalDns: Boolean = true,
    val dnsServers: List<String>,
    val xrayConfigJson: String,
    val unappliedRules: List<String> = emptyList(),
    val applicationPolicy: VpnApplicationPolicy,
    val localProxyOptions: LocalProxyOptions,
    val appendHttpProxyOptions: VpnAppendHttpProxyOptions,
    val coreLogPaths: XrayCoreLogPaths,
    val enableAccessLog: Boolean = false,
    val enableWakeLock: Boolean = false,
    val enableSeamlessNetworkSwitching: Boolean = true,
    val enableKillSwitch: Boolean = false,
    val dataDir: String = "",
    val hevSocks5TunnelConfig: HevSocks5TunnelConfig? = null,
    val olcRtcConfigYaml: String? = null,
    val olcRtcSocksPort: Int = 0,
    val activeOlcRtcBridge: ActiveOlcRtcBridge? = null,
    val amneziaWgConfigJson: String? = null,
    val amneziaWgSocksPort: Int = 0,
    val activeAmneziaWgBridge: ActiveAmneziaWgBridge? = null,
)

internal fun VpnServiceStartConfig.xrayTunFd(vpnTunFd: Int): Int {
    return if (hevSocks5TunnelConfig == null) vpnTunFd else 0
}

internal object VpnXrayConfigFactory {
    fun create(context: Context, request: ProxyEngineStartRequest): VpnServiceStartConfig {
        val appState = request.appState
        val tunOptions = appState.toTunOptions()
        val localProxyOptions = appState.toLocalProxyOptions()
        val appendHttpProxyOptions = appState.toVpnAppendHttpProxyOptions(
            localProxyOptions = localProxyOptions,
            excludedPorts = setOfNotNull(request.xrayStatsApiPort),
        )
        val coreLogPaths = context.prepareXrayCoreLogPaths()
        val resourceFilePaths = context.prepareXrayResourceFilePaths()
        appState.validateXrayExternalRoutingResources(resourceFilePaths.dataDir)
        val rawOutboundPlan = appState.buildXrayOutboundPlan(request.selectedServer)

        val bridgePlan = buildNativeBridgePlan(
            rawOutboundPlan = rawOutboundPlan,
            tunOptions = tunOptions,
            reservedPorts = setOfNotNull(
                localProxyOptions.port,
                appendHttpProxyOptions.port.takeIf { appendHttpProxyOptions.enabled },
                request.xrayStatsApiPort,
            ),
        )
        val outboundPlan = bridgePlan.outboundPlan

        val dnsHosts = appState.xrayDnsHosts(outboundPlan.dnsHostServers)
        val xrayConfigResult = XrayConfigFactory.buildXrayConfigResult(
            XrayConfigRequest(
                appState = appState,
                selectedServer = request.selectedServer,
                inbounds = buildVpnXrayInbounds(
                    appState = appState,
                    tunOptions = tunOptions,
                    localProxyOptions = localProxyOptions,
                    appendHttpProxyOptions = appendHttpProxyOptions,
                ),
                coreLogPaths = coreLogPaths,
                dataDir = resourceFilePaths.dataDir,
                dnsHosts = dnsHosts,
                dnsHijackInboundTags = vpnDnsHijackInboundTags(appState.enableVpnHevTun),
                statsApiConfig = request.xrayStatsApiConfig(),
                outboundPlan = outboundPlan,
            ),
        )

        return VpnServiceStartConfig(
            sessionName = "SKIPI",
            mtu = tunOptions.mtu,
            ipv4Address = tunOptions.ipv4Address.address,
            ipv4PrefixLength = tunOptions.ipv4Address.prefixLength,
            enableIpv6 = appState.enableIpv6,
            ipv6Address = if (appState.enableIpv6) tunOptions.ipv6Address.address else null,
            ipv6PrefixLength = tunOptions.ipv6Address.prefixLength,
            enableLocalDns = appState.effectiveLocalDnsEnabled,
            dnsServers = tunOptions.dnsServers,
            xrayConfigJson = xrayConfigResult.json,
            unappliedRules = xrayConfigResult.unappliedRules,
            applicationPolicy = appState.toVpnApplicationPolicy(Process.myUid().toAndroidUserId()),
            localProxyOptions = localProxyOptions,
            appendHttpProxyOptions = appendHttpProxyOptions,
            coreLogPaths = coreLogPaths,
            enableAccessLog = appState.enableAccessLog,
            enableWakeLock = appState.enableWakeLock,
            enableSeamlessNetworkSwitching = appState.enableSeamlessNetworkSwitching,
            enableKillSwitch = appState.enableKillSwitch,
            dataDir = resourceFilePaths.dataDir,
            hevSocks5TunnelConfig = buildVpnHevSocks5TunnelConfig(
                dataDir = resourceFilePaths.dataDir,
                coreLogPaths = coreLogPaths,
                localProxyOptions = localProxyOptions,
                tunOptions = tunOptions,
                enableIpv6 = appState.enableIpv6,
                useHevTun = appState.enableVpnHevTun,
                tcpReadWriteTimeoutMillis = appState.hevTcpReadWriteTimeoutMillis,
            ),
            olcRtcConfigYaml = bridgePlan.olcRtcYaml,
            olcRtcSocksPort = bridgePlan.olcRtcSocksPort,
            activeOlcRtcBridge = bridgePlan.activeOlcRtcBridge,
            amneziaWgConfigJson = bridgePlan.amneziaWgConfigJson,
            amneziaWgSocksPort = bridgePlan.amneziaWgSocksPort,
            activeAmneziaWgBridge = bridgePlan.activeAmneziaWgBridge,
        )
    }
}

/**
 * olcRTC and AmneziaWG are process-wide native runtimes. Build their local
 * SOCKS bridges before Xray is started and fail clearly if a routing plan asks
 * one runtime to represent two different tunnels.
 */
internal fun buildNativeBridgePlan(
    rawOutboundPlan: XrayOutboundPlan,
    tunOptions: TunOptions,
    reservedPorts: Set<Int>,
): NativeBridgePlanResult {
    val olcRtcServer = rawOutboundPlan.proxyOutbounds
        .mapNotNull { it.server as? OlcRtc }
        .singleOlcRtcRuntimeOrNull()
    val amneziaWgServer = rawOutboundPlan.proxyOutbounds
        .mapNotNull { it.server as? AmneziaWg }
        .singleAmneziaWgRuntimeOrNull()

    var updatedPlan = rawOutboundPlan
    val usedPorts = reservedPorts.toMutableSet()

    var olcRtcYaml: String? = null
    var olcRtcSocksPort = 0
    var activeOlcRtcBridge: ActiveOlcRtcBridge? = null
    if (olcRtcServer != null) {
        val port = findAvailableLocalPort(
            preferredPort = olcRtcServer.localSocksPort.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 10808,
            reservedPorts = usedPorts,
        )
        usedPorts += port
        val user = "skipi_rtc_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        val pass = java.util.UUID.randomUUID().toString().replace("-", "")
        val bridge = ActiveOlcRtcBridge(
            socksPort = port,
            socksUser = user,
            socksPass = pass,
            server = olcRtcServer,
        )

        olcRtcYaml = olcRtcServer.toOlcRtcYamlConfig(
            socksPort = port,
            socksUser = user,
            socksPass = pass,
            // olcRTC accepts a raw DNS endpoint, so it follows the explicit
            // TUN adapter resolver rather than silently using 8.8.8.8.
            dnsServer = olcRtcRawDnsEndpoint(tunOptions),
        )
        updatedPlan = updatedPlan.copy(
            proxyOutbounds = updatedPlan.proxyOutbounds.map { item ->
                val olc = item.server as? OlcRtc
                if (olc != null && olc.matchesRuntimeConfig(olcRtcServer)) {
                    item.copy(
                        customOutbound = buildLoopbackSocksOutbound(
                            tag = item.tag,
                            port = port,
                            user = user,
                            pass = pass,
                        ),
                    )
                } else {
                    item
                }
            },
        )
        olcRtcSocksPort = port
        activeOlcRtcBridge = bridge
    }

    var amneziaWgConfigJson: String? = null
    var amneziaWgSocksPort = 0
    var activeAmneziaWgBridge: ActiveAmneziaWgBridge? = null
    if (amneziaWgServer != null) {
        require(amneziaWgServer.finalMask.isBlank()) {
            "AmneziaWG FinalMask is not supported by the native AmneziaWG runtime"
        }
        val port = findAvailableLocalPort(preferredPort = 10809, reservedPorts = usedPorts)
        usedPorts += port
        val bridge = ActiveAmneziaWgBridge(socksPort = port, server = amneziaWgServer)

        // Pass precisely this AWG outbound to skipi-core. Its generic JSON
        // rewriter would also rewrite ordinary WireGuard outbounds, which is
        // unsafe in mixed profiles.
        amneziaWgConfigJson = amneziaWgServer.toNativeRunnerConfigJson(
            tag = "skipi_awg_runtime",
            dnsServers = tunOptions.dnsServers,
        )
        updatedPlan = updatedPlan.copy(
            proxyOutbounds = updatedPlan.proxyOutbounds.map { item ->
                val awg = item.server as? AmneziaWg
                if (awg != null && awg.matchesRuntimeConfig(amneziaWgServer)) {
                    item.copy(customOutbound = buildLoopbackSocksOutbound(tag = item.tag, port = port))
                } else {
                    item
                }
            },
        )
        amneziaWgSocksPort = port
        activeAmneziaWgBridge = bridge
    }

    return NativeBridgePlanResult(
        outboundPlan = updatedPlan,
        olcRtcYaml = olcRtcYaml,
        olcRtcSocksPort = olcRtcSocksPort,
        activeOlcRtcBridge = activeOlcRtcBridge,
        amneziaWgConfigJson = amneziaWgConfigJson,
        amneziaWgSocksPort = amneziaWgSocksPort,
        activeAmneziaWgBridge = activeAmneziaWgBridge,
    )
}

/**
 * The native AmneziaWG runner uses its own netstack, so its resolver must be
 * supplied separately from the Xray outbound. Keep this runtime-only field out
 * of the Xray configuration: Xray's WireGuard outbound does not understand it.
 */
internal fun AmneziaWg.toNativeRunnerConfigJson(
    tag: String,
    dnsServers: List<String>,
): String {
    val outbound = toXrayOutbound(tag).toJsonObject()
    val settings = outbound["settings"] as? JsonObject
        ?: error("AmneziaWG outbound has no settings")
    return buildJsonObject {
        outbound.forEach { (key, value) ->
            if (key != "settings") put(key, value)
        }
        put(
            "settings",
            buildJsonObject {
                settings.forEach { (key, value) -> put(key, value) }
                put("dnsServers", buildJsonArray {
                    dnsServers.forEach { server -> add(server) }
                })
            },
        )
    }.toString()
}

private fun List<OlcRtc>.singleOlcRtcRuntimeOrNull(): OlcRtc? {
    val distinct = fold(mutableListOf<OlcRtc>()) { runtimes, server ->
        if (runtimes.none { it.matchesRuntimeConfig(server) }) runtimes += server
        runtimes
    }
    require(distinct.size <= 1) {
        "The selected route contains multiple different olcRTC tunnels; only one native olcRTC runtime can run at a time"
    }
    return distinct.singleOrNull()
}

private fun List<AmneziaWg>.singleAmneziaWgRuntimeOrNull(): AmneziaWg? {
    val distinct = fold(mutableListOf<AmneziaWg>()) { runtimes, server ->
        if (runtimes.none { it.matchesRuntimeConfig(server) }) runtimes += server
        runtimes
    }
    require(distinct.size <= 1) {
        "The selected route contains multiple different AmneziaWG tunnels; only one native AmneziaWG runtime can run at a time"
    }
    return distinct.singleOrNull()
}

/** Native olcRTC only supports UDP/TCP DNS, not a DoH URL. */
internal fun olcRtcRawDnsEndpoint(tunOptions: TunOptions): String {
    val resolver = tunOptions.dnsServers
        .asSequence()
        .map(String::trim)
        .firstOrNull(::isIpv4Address)
        ?: error("olcRTC requires a valid IPv4 DNS adapter address")
    return "$resolver:53"
}

/** Builds a private loopback SOCKS outbound for a native bridge. */
internal fun buildLoopbackSocksOutbound(
    tag: String,
    port: Int,
    user: String = "",
    pass: String = "",
): JsonObject {
    return buildJsonObject {
        put("tag", tag)
        put("protocol", "socks")
        put("settings", buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", "127.0.0.1")
                    put("port", port)
                    if (user.isNotBlank() || pass.isNotBlank()) {
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("user", user)
                                put("pass", pass)
                                put("level", 0)
                            })
                        })
                    }
                })
            })
        })
    }
}

internal fun buildVpnXrayInbounds(
    appState: AppState,
    tunOptions: TunOptions,
    localProxyOptions: LocalProxyOptions,
    appendHttpProxyOptions: VpnAppendHttpProxyOptions,
): List<JsonObject> {
    return buildList {
        add(buildLocalSocksInbound(appState, XrayTags.LOCAL_SOCKS_INBOUND, localProxyOptions))
        if (appendHttpProxyOptions.enabled) {
            add(buildVpnAppendHttpInbound(appendHttpProxyOptions))
        }
        if (!appState.enableVpnHevTun) {
            add(buildVpnTunInbound(appState, tunOptions))
        }
    }
}

internal fun vpnDnsHijackInboundTags(useHevTun: Boolean): List<String> {
    return if (useHevTun) {
        listOf(XrayTags.LOCAL_SOCKS_INBOUND)
    } else {
        listOf(XrayTags.VPN_TUN_INBOUND)
    }
}

internal fun buildVpnHevSocks5TunnelConfig(
    dataDir: String,
    coreLogPaths: XrayCoreLogPaths,
    localProxyOptions: LocalProxyOptions,
    tunOptions: TunOptions,
    enableIpv6: Boolean,
    useHevTun: Boolean = true,
    tcpReadWriteTimeoutMillis: Int = DefaultHevSocks5TunnelTcpReadWriteTimeoutMillis,
): HevSocks5TunnelConfig? {
    if (!useHevTun) return null
    return HevSocks5TunnelConfig(
        configPath = File(dataDir, HevSocks5TunnelConfigFileName).absolutePath,
        logPath = coreLogPaths.hevSocks5TunnelLogFile(HevSocks5TunnelLogFileName).absolutePath,
        socksAddress = hevSocks5TunnelSocksTargetAddress(localProxyOptions),
        socksPort = localProxyOptions.port,
        socksUsername = localProxyOptions.username,
        socksPassword = localProxyOptions.password,
        mtu = tunOptions.mtu,
        ipv4Address = tunOptions.ipv4Address.address,
        ipv6Address = tunOptions.ipv6Address.address.takeIf { enableIpv6 },
        tunnelName = "skipi0",
        enableMultiQueue = true,
        enableTcpFastOpen = true,
        tcpReadWriteTimeoutMillis = tcpReadWriteTimeoutMillis.takeIf { it > 0 }
            ?: DefaultHevSocks5TunnelTcpReadWriteTimeoutMillis,
    )
}

internal fun findAvailableLocalPort(preferredPort: Int, reservedPorts: Set<Int>): Int {
    if (preferredPort.isPort() && preferredPort !in reservedPorts && isTcpPortAvailable("127.0.0.1", preferredPort)) {
        return preferredPort
    }
    val discovered = findAvailableTcpPort("127.0.0.1", reservedPorts)
    if (discovered != null && discovered.isPort()) {
        return discovered
    }
    for (port in 10809..65535) {
        if (port !in reservedPorts && isTcpPortAvailable("127.0.0.1", port)) {
            return port
        }
    }
    throw IllegalStateException("No free local TCP port is available for the native proxy bridge")
}
