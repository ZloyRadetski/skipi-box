// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.AppState
import engine.network.NetworkDefaults
import engine.proxy.LocalProxyLoopbackAddress
import engine.vpn.TunInterfacePlan
import engine.vpn.toTunInterfacePlan
import app.skipi.ui.settings.VpnSettingsCapabilities
import app.skipi.app.model.RoutingConfigRecord
import engine.vpn.strictFullTunnelRouting
import platform.LocalProxyXrayConfigOptions

/** The shared Android TUN plan for the TUN values stored in Desktop settings. */
internal fun DesktopAppSettings.tunInterfacePlan(enableIpv6: Boolean = false): TunInterfacePlan = AppState(
    tunMtu = tunMtu,
    tunIpv4Cidr = tunIpv4Cidr,
    tunIpv6Cidr = tunIpv6Cidr,
    enableIpv6 = enableIpv6,
).toTunInterfacePlan()

/**
 * The interface plan of a start: the MTU and addresses are the TUN settings'; whether the tunnel
 * carries IPv6 is the active profile's `ipv6`, the same value its generated Xray config is built
 * with, so the interface and the proxy core agree. Without a profile IPv6 stays off.
 */
internal fun desktopTunInterfacePlan(settings: DesktopAppSettings, configLibrary: DesktopConfigLibrary): TunInterfacePlan =
    settings.tunInterfacePlan(enableIpv6 = configLibrary.activeProfileRuntime()?.plan?.enableIpv6 ?: false)

/**
 * hev forwards the tunnel's traffic to the SOCKS inbound of the Core session that is running, with
 * the credentials that session was started with. A wildcard listen address is reached over loopback.
 */
internal fun desktopTunStartRequest(
    plan: TunInterfacePlan,
    options: LocalProxyXrayConfigOptions,
    logPath: String,
): DesktopTunStartRequest = DesktopTunStartRequest(
    plan = plan,
    socksAddress = if (options.listenAddress == NetworkDefaults.IPV4_ANY_ADDRESS) {
        LocalProxyLoopbackAddress
    } else {
        options.listenAddress
    },
    socksPort = options.socksPort,
    socksUsername = options.username,
    socksPassword = options.password,
    logPath = logPath,
)

/**
 * Desktop runs hev only, has its own HTTP inbound and reaches the system through the network
 * manager, so the Android rows for those, the kill switch and the battery/wake-lock stability
 * settings are not shown. "On boot" is the Desktop start at login.
 */
internal fun desktopVpnSettingsCapabilities() = VpnSettingsCapabilities(
    hevTun = false,
    appendHttpProxy = false,
    strictFullTunnel = true,
    autoConnectOnBoot = true,
    killSwitch = false,
    systemVpnSettings = false,
    batteryOptimization = false,
    wakeLock = false,
    seamlessSwitching = false,
)

/**
 * Android's "Reset VPN settings" for the fields Desktop has: tunnel, local proxy, sniffing, Mux and
 * Fragment defaults. Proxy credentials stay so external clients are not silently broken.
 */
internal fun DesktopAppSettings.withVpnSettingsReset(): DesktopAppSettings {
    val defaults = DesktopAppSettings()
    return copy(
        enableTunMode = defaults.enableTunMode,
        tunMtu = defaults.tunMtu,
        tunIpv4Cidr = defaults.tunIpv4Cidr,
        tunIpv6Cidr = defaults.tunIpv6Cidr,
        tunVpnDns = defaults.tunVpnDns,
        tunTcpKeepAliveInterval = defaults.tunTcpKeepAliveInterval,
        tunTcpUserTimeout = defaults.tunTcpUserTimeout,
        enableStrictFullTunnel = defaults.enableStrictFullTunnel,
        localProxyPort = defaults.localProxyPort,
        localProxyPortRaw = defaults.localProxyPortRaw,
        enableDynamicLocalProxyPort = defaults.enableDynamicLocalProxyPort,
        localProxyListenAddress = defaults.localProxyListenAddress,
        enableLocalProxyAuth = defaults.enableLocalProxyAuth,
        enableSniffing = defaults.enableSniffing,
        enableSniffingRouteOnly = defaults.enableSniffingRouteOnly,
        enableMux = defaults.enableMux,
        muxConcurrency = defaults.muxConcurrency,
        muxXudpConcurrency = defaults.muxXudpConcurrency,
        muxXudpProxyUdp443 = defaults.muxXudpProxyUdp443,
        enableFragment = defaults.enableFragment,
        fragmentPackets = defaults.fragmentPackets,
        fragmentLength = defaults.fragmentLength,
        fragmentInterval = defaults.fragmentInterval,
    )
}

/** Android's strict full tunnel for the global routing Desktop stores; the stored record stays as it is. */
internal fun RoutingConfigRecord.withStrictFullTunnelApplied(): RoutingConfigRecord {
    val routing = strictFullTunnelRouting(defaultOutboundTag, rules)
    return copy(defaultOutboundTag = routing.defaultOutboundTag, rules = routing.rules)
}
