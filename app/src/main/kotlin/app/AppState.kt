// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import app.modes.AppIconDefault
import app.modes.BottomBarSizeLarge
import app.modes.ColorModeSystem
import app.modes.LanguageModeSystem
import app.modes.ConnectionDisplayModeCompact
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyServerListLayoutSingle
import app.modes.ProxyServerListSortDefault
import app.modes.RunModeVpnService
import app.modes.SubscriptionPingModeHttp
import app.modes.SubscriptionPingModeTcp
import engine.vpn.VpnDefaults
import engine.network.NetworkDefaults
import engine.xray.DefaultDirectDnsDomains
import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import features.networkautomation.model.NetworkAutomationRule
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxUdp443Mode
import engine.xray.DefaultMuxXudpConcurrency
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileLoyalsoldierGeoIpUrl
import features.resources.ResourceFileLoyalsoldierGeoSiteUrl
import features.resources.ResourceFileDirectCidrIpv4Url
import features.resources.ResourceFileDirectCidrIpv6Url
import features.resources.ResourceFileV2FlyGeoIpOnlyCnPrivateUrl
import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.DefaultSubscriptionUserAgents
import features.routing.model.RouteRule
import features.config.DefaultTrafficConfigs
import features.config.TrafficConfigState
import features.config.ShadowrocketPolicyGroup

data class AppState(
    val appIcon: Int = AppIconDefault,
    val colorMode: Int = ColorModeSystem,
    val languageMode: Int = LanguageModeSystem,
    val enableMaterialYou: Boolean = true,
    val seedIndex: Int = 0,
    val customMaterialYouSeed: Long? = null,
    val enableCustomColors: Boolean = false,
    val customAccentColor: Long? = null,
    val customBackgroundColor: Long? = null,
    val customSurfaceColor: Long? = null,
    val customSurfaceVariantColor: Long? = null,
    val customTextColor: Long? = null,
    val customTextSecondaryColor: Long? = null,
    val customStatusRunningColor: Long? = null,
    val customStatusStoppedColor: Long? = null,
    val customPingFastColor: Long? = null,
    val customPingMediumColor: Long? = null,
    val customPingSlowColor: Long? = null,
    val customCategoryAppearanceColor: Long? = null,
    val customCategoryVpnColor: Long? = null,
    val customCategoryProxyColor: Long? = null,
    val customCategorySubscriptionsColor: Long? = null,
    val customCategoryIntegrationColor: Long? = null,
    val customCategoryLogsColor: Long? = null,
    val customCategoryBackupColor: Long? = null,
    val customCategoryAboutColor: Long? = null,
    val customProtocolVlessColor: Long? = null,
    val customProtocolVmessColor: Long? = null,
    val customProtocolHysteria2Color: Long? = null,
    val customProtocolTrojanColor: Long? = null,
    val customProtocolShadowsocksColor: Long? = null,
    val customProtocolWireguardColor: Long? = null,
    val customProtocolSocksColor: Long? = null,
    val customProtocolHttpColor: Long? = null,
    val customProtocolStrategyColor: Long? = null,
    val customProtocolChainColor: Long? = null,
    val customProtocolJsonColor: Long? = null,

    val subscriptionGroups: List<SubscriptionGroupState> = DefaultSubscriptionGroups,
    val nextSubscriptionGroupId: Int = 4,
    val enableAllProxyGroup: Boolean = false,
    val enableDeletionConfirmation: Boolean = true,
    val connectionDisplayMode: Int = ConnectionDisplayModeCompact,
    val classicShowFloatingPowerButton: Boolean = false,
    val bottomBarSize: Int = BottomBarSizeLarge,
    val hasCompletedOnboarding: Boolean = false,

    val runMode: Int = RunModeVpnService,
    val enableResolveProxyServerDomain: Boolean = false,

    val enableVpnLocalDns: Boolean = true,
    val localProxyPort: String = VpnDefaults.LOCAL_PROXY_PORT.toString(),
    val enableDynamicLocalProxyPort: Boolean = false,
    val localProxyListenAllInterfaces: Boolean = false,
    val enableLocalProxyAuth: Boolean = true,
    val localProxyUsername: String = generateRandomProxyCredential(),
    val localProxyPassword: String = generateRandomProxyCredential(),
    val enableVpnAppendHttpProxy: Boolean = false,
    val enableVpnHevTun: Boolean = false,
    val tunMtu: String = VpnDefaults.MTU.toString(),
    val tunVpnDns: String = VpnDefaults.IPV4_DNS,
    val tunIpv4Cidr: String = VpnDefaults.IPV4_CIDR,
    val tunIpv6Cidr: String = VpnDefaults.IPV6_CIDR,
    val enableWakeLock: Boolean = true,
    val enableSeamlessNetworkSwitching: Boolean = true,
    val enableNetworkAutomation: Boolean = false,
    val enableOnDemandVpn: Boolean = false,
    val networkAutomationRules: List<NetworkAutomationRule> = emptyList(),
    val tunTcpKeepAliveInterval: String = VpnDefaults.TCP_KEEP_ALIVE_INTERVAL,
    val tunTcpUserTimeout: String = VpnDefaults.TCP_USER_TIMEOUT,

    val proxyServers: List<ProxyServerState> = emptyList(),
    val nextProxyServerId: Int = 10,
    val selectedProxyServerId: Int = 1,
    val proxyServerListLayout: Int = ProxyServerListLayoutSingle,
    val proxyServerListSort: Int = ProxyServerListSortDefault,
    val subscriptionPingMode: Int = SubscriptionPingModeHttp,
    val subscriptionPingUrl: String = NetworkDefaults.CONNECTIVITY_CHECK_URL,
    val subscriptionPingTimeoutMillis: String = "5000",
    val subscriptionPingConcurrency: Int = 8,
    /** User-Agent presets offered when a subscription is fetched. */
    val subscriptionUserAgents: List<String> = DefaultSubscriptionUserAgents,
    /** Sends the app-scoped subscription identifier and basic Android metadata. */
    val enableSubscriptionDeviceHeaders: Boolean = true,
    val subscriptionFetchTimeoutSeconds: Int = 10,
    val enableSubscriptionExpiryNotifications: Boolean = true,
    val subscriptionExpiryReminders: List<SubscriptionExpiryReminder> = DefaultSubscriptionExpiryReminders,
    val proxyRunning: Boolean = false,

    val trafficConfigs: List<TrafficConfigState> = DefaultTrafficConfigs,
    val nextTrafficConfigId: Int = 2,
    val activeTrafficConfigId: Int = DefaultTrafficConfigs.first().id,
    /** Transient routing aliases parsed from the active Shadowrocket [Proxy Group] section. */
    val shadowrocketPolicyGroups: List<ShadowrocketPolicyGroup> = emptyList(),

    val routeDomainStrategy: Int = 0,
    val defaultRouteOutboundTag: String = DefaultRouteOutboundTag,
    val routeRules: List<RouteRule> = DefaultRouteRules,
    val nextRouteRuleId: Int = 10,

    val coreLogLevel: Int = 3,
    val enableAccessLog: Boolean = false,
    val logRetentionDays: Int = 7,
    val resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub,
    val customResourceFileGeoIpUrl: String = ResourceFileLoyalsoldierGeoIpUrl,
    val customResourceFileGeoSiteUrl: String = ResourceFileLoyalsoldierGeoSiteUrl,
    val customResourceFileGeoIpOnlyCnPrivateUrl: String = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
    val customResourceFileDirectCidrIpv4Url: String = ResourceFileDirectCidrIpv4Url,
    val customResourceFileDirectCidrIpv6Url: String = ResourceFileDirectCidrIpv6Url,
    val customResourceFiles: List<CustomResourceFileState> = emptyList(),
    val nextCustomResourceFileId: Int = 1,
    val resourceFileUserAgent: String = DefaultSubscriptionUserAgent,
    val enableSniffing: Boolean = true,
    val enableSniffingRouteOnly: Boolean = true,

    val enableMux: Boolean = false,
    val muxConcurrency: String = DefaultMuxConcurrency,
    val muxXudpConcurrency: String = DefaultMuxXudpConcurrency,
    val muxXudpProxyUdp443: Int = DefaultMuxUdp443Mode,

    val enableFragment: Boolean = false,
    val fragmentPackets: String = DefaultFragmentPackets,
    val fragmentLength: String = DefaultFragmentLength,
    val fragmentInterval: String = DefaultFragmentInterval,

    val enableTrafficStatsNotification: Boolean = true,
    val showServerSearch: Boolean = false,
    /** Show the current VPN-core memory footprint above the server search field. */
    val showTunnelMemoryOnHome: Boolean = false,
    val enableBroadcastControl: Boolean = true,
    val enableIpv6: Boolean = false,
    val enableIpv6Prefer: Boolean = false,
    val enableFakeDns: Boolean = false,
    val proxyDns: List<String> = VpnDefaults.PROXY_DNS_SERVERS,
    val directDns: List<String> = VpnDefaults.DIRECT_DNS_SERVERS,
    val directDnsDomains: List<String> = DefaultDirectDnsDomains,
    val enableDirectDnsForProxyServerDomains: Boolean = true,
    val dnsHosts: List<String> = emptyList(),

    val serviceControl: ServiceControlSettings = ServiceControlSettings(),

    val proxyAppListMode: Int = ProxyAppListModeGlobal,
    val proxyAppListSelectedApps: List<String> = emptyList(),

    val autoCheckAppUpdates: Boolean = true,
    val autoInstallAppUpdatesAtNight: Boolean = true,
    val availableAppUpdate: features.updater.AppUpdateInfo? = null,
    val dismissedUpdateVersion: String = "",
)

val AppState.effectiveLocalDnsEnabled: Boolean
    get() = enableVpnLocalDns

val AppState.effectiveFakeDnsEnabled: Boolean
    get() = effectiveLocalDnsEnabled && enableFakeDns
