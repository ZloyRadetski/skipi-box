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
import engine.network.isPort
import engine.network.isTcpPortAvailable
import features.resources.runtime.prepareXrayResourceFilePaths
import features.proxy.server.model.Custom
import features.proxy.server.model.OlcRtc
import system.toAndroidUserId
import java.io.File
import kotlinx.serialization.json.JsonObject


internal data class ActiveOlcRtcBridge(
    val socksPort: Int,
    val socksUser: String,
    val socksPass: String,
    val server: OlcRtc,
)

private data class OlcRtcPlanResult(
    val outboundPlan: XrayOutboundPlan,
    val yaml: String?,
    val socksPort: Int,
    val activeBridge: ActiveOlcRtcBridge?,
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

        val olcRtcServer = (request.selectedServer.server as? OlcRtc)
            ?: rawOutboundPlan.proxyOutbounds.mapNotNull { it.server as? OlcRtc }.firstOrNull()

        val (outboundPlan, olcRtcConfigYaml, olcRtcSocksPort, activeBridge) = if (olcRtcServer != null) {
            val reservedPorts = setOfNotNull(
                localProxyOptions.port,
                appendHttpProxyOptions.port.takeIf { appendHttpProxyOptions.enabled },
                request.xrayStatsApiPort,
            )
            val preferredPort = olcRtcServer.localSocksPort.toIntOrNull()?.takeIf { p -> p in 1024..65535 } ?: 10808
            val allocatedPort = findAvailableLocalPort(preferredPort, reservedPorts)
            val olcUser = "skipi_rtc_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            val olcPass = java.util.UUID.randomUUID().toString().replace("-", "")

            val yaml = olcRtcServer.toOlcRtcYamlConfig(
                socksPort = allocatedPort,
                socksUser = olcUser,
                socksPass = olcPass,
            )

            val updatedProxyOutbounds = rawOutboundPlan.proxyOutbounds.map { item ->
                if (item.server is OlcRtc) {
                    val customOutbound = item.server.toXrayOutboundWithPortAndAuth(
                        tag = item.tag,
                        port = allocatedPort,
                        user = olcUser,
                        pass = olcPass,
                    ).toJsonObject()
                    item.copy(customOutbound = customOutbound)
                } else {
                    item
                }
            }
            val bridge = ActiveOlcRtcBridge(
                socksPort = allocatedPort,
                socksUser = olcUser,
                socksPass = olcPass,
                server = olcRtcServer,
            )
            OlcRtcPlanResult(rawOutboundPlan.copy(proxyOutbounds = updatedProxyOutbounds), yaml, allocatedPort, bridge)
        } else {
            OlcRtcPlanResult(rawOutboundPlan, null, 0, null)
        }

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
            olcRtcConfigYaml = olcRtcConfigYaml,
            olcRtcSocksPort = olcRtcSocksPort,
            activeOlcRtcBridge = activeBridge,
        )
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
    return if (preferredPort.isPort()) preferredPort else 10808
}
