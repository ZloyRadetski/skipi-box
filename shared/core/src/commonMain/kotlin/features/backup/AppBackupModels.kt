// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.backup

import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigNetworkActivation
import features.config.TrafficConfigResourceSettings
import features.networkautomation.model.NetworkAutomationRule
import features.resources.ResourceFileDirectCidrIpv4Url
import features.resources.ResourceFileDirectCidrIpv6Url
import features.resources.ResourceFileLoyalsoldierGeoIpUrl
import features.resources.ResourceFileLoyalsoldierGeoSiteUrl
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileV2FlyGeoIpOnlyCnPrivateUrl
import features.subscription.DefaultSubscriptionExpiryReminders
import features.subscription.SubscriptionExpiryReminder
import features.routing.model.DefaultRouteOutboundTag
import engine.network.NetworkDefaults
import engine.xray.DefaultDirectDnsDomains
import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxUdp443Mode
import engine.xray.DefaultMuxXudpConcurrency
import features.settings.servicecontrol.ServiceControlSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val AppBackupFormat = "skipi-backup"
const val CurrentAppBackupVersion = 1

private object BackupDefaults {
    const val appIcon = 0
    const val colorMode = 0
    const val languageMode = 0
    const val fontFamilyMode = 6
    const val fontSizeMode = 85
    const val fontWeightMode = 0
    const val enableMaterialYou = false
    const val seedIndex = 0
    const val enableAllProxyGroup = false
    const val enableDeletionConfirmation = true
    const val enableResolveProxyServerDomain = true
    const val enableVpnLocalDns = true
    const val localProxyPort = "10808"
    const val enableDynamicLocalProxyPort = false
    const val localProxyListenAllInterfaces = false
    const val enableLocalProxyAuth = true
    const val localProxyUsername = ""
    const val localProxyPassword = ""
    const val enableVpnAppendHttpProxy = false
    const val enableVpnHevTun = true
    const val enableKillSwitch = false
    const val enableStrictFullTunnel = false
    const val tunMtu = "1400"
    const val tunVpnDns = "8.8.8.8"
    const val tunIpv4Cidr = "172.19.0.1/30"
    const val tunIpv6Cidr = "fdfe:dcba:9876::1/126"
    const val enableWakeLock = false
    const val enableSeamlessNetworkSwitching = true
    const val enableNetworkAutomation = false
    const val enableOnDemandVpn = false
    const val tunTcpKeepAliveInterval = "60"
    const val tunTcpUserTimeout = "10000"
    const val selectedProxyServerId = 1
    const val proxyServerListLayout = 1
    const val proxyServerListSort = 0
    const val subscriptionPingMode = 1
    val subscriptionPingUrl = NetworkDefaults.CONNECTIVITY_CHECK_URL
    const val subscriptionPingTimeoutMillis = "5000"
    const val subscriptionPingConcurrency = 8
    const val enableSubscriptionDeviceHeaders = true
    const val subscriptionFetchTimeoutSeconds = 10
    const val routeDomainStrategy = 0
    const val defaultRouteOutboundTag = DefaultRouteOutboundTag
    const val coreLogLevel = 3
    const val enableAccessLog = false
    const val logRetentionDays = 7
    const val resourceFileSource = ResourceFileSourceLoyalsoldierGithub
    const val customResourceFileGeoIpUrl = ResourceFileLoyalsoldierGeoIpUrl
    const val customResourceFileGeoSiteUrl = ResourceFileLoyalsoldierGeoSiteUrl
    const val customResourceFileGeoIpOnlyCnPrivateUrl = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl
    const val customResourceFileDirectCidrIpv4Url = ResourceFileDirectCidrIpv4Url
    const val customResourceFileDirectCidrIpv6Url = ResourceFileDirectCidrIpv6Url
    const val enableSniffing = true
    const val enableSniffingRouteOnly = true
    const val enableMux = false
    const val muxConcurrency = DefaultMuxConcurrency
    const val muxXudpConcurrency = DefaultMuxXudpConcurrency
    const val muxXudpProxyUdp443 = DefaultMuxUdp443Mode
    const val enableFragment = false
    const val fragmentPackets = DefaultFragmentPackets
    const val fragmentLength = DefaultFragmentLength
    const val fragmentInterval = DefaultFragmentInterval
    const val enableTrafficStatsNotification = true
    const val trafficStatsNotificationRefreshIntervalSeconds = 2
    const val enableResourceFileNotifications = false
    const val showServerSearch = false
    const val connectionDisplayMode = 1
    const val pinConnectionPanelOnHome = false
    const val enableSubscriptionSwipe = true
    const val backgroundStyle = 0
    const val backgroundPhotoDimPercent = 45
    const val enableHaptics = true
    const val classicShowFloatingPowerButton = false
    const val showTunnelMemoryOnHome = false
    const val enableBroadcastControl = true
    const val enableIpv6 = false
    const val enableIpv6Prefer = false
    const val enableFakeDns = false
    val proxyDns = listOf("https://8.8.8.8/dns-query")
    val directDns = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query")
    val directDnsDomains = DefaultDirectDnsDomains
    const val enableDirectDnsForProxyServerDomains = true
    const val proxyAppListMode = 2
    const val enableSubscriptionExpiryNotifications = true
    val dnsHosts = emptyList<String>()
    val serviceControl = ServiceControlSettings()
}

@Serializable
data class AppBackupFile(
    val format: String = "",
    val version: Int = 0,
    val createdAtMillis: Long = 0L,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val data: AppBackupData = AppBackupData(),
)

@Serializable
data class AppBackupData(
    val settings: AppBackupSettings = AppBackupSettings(),
    val subscriptionGroups: List<AppBackupSubscriptionGroup> = emptyList(),
    val proxyServers: List<AppBackupProxyServer> = emptyList(),
    val routeRules: List<AppBackupRouteRule> = emptyList(),
    val proxyAppListSelectedApps: List<String> = emptyList(),
    val trafficConfigs: List<AppBackupTrafficConfig> = emptyList(),
    val activeTrafficConfigId: Int = 0,
)

@Serializable
data class AppBackupSettings(
    val appIcon: Int = BackupDefaults.appIcon,
    val colorMode: Int = BackupDefaults.colorMode,
    val languageMode: Int = BackupDefaults.languageMode,
    val fontFamilyMode: Int = BackupDefaults.fontFamilyMode,
    val fontSizeMode: Int = BackupDefaults.fontSizeMode,
    val fontWeightMode: Int = BackupDefaults.fontWeightMode,
    val enableMaterialYou: Boolean = BackupDefaults.enableMaterialYou,
    val seedIndex: Int = BackupDefaults.seedIndex,
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
    val customCategoryIconColor: Long? = null,
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
    val enableAllProxyGroup: Boolean = BackupDefaults.enableAllProxyGroup,
    val enableDeletionConfirmation: Boolean = BackupDefaults.enableDeletionConfirmation,
    val enableResolveProxyServerDomain: Boolean = BackupDefaults.enableResolveProxyServerDomain,
    val enableVpnLocalDns: Boolean = BackupDefaults.enableVpnLocalDns,
    val localProxyPort: String = BackupDefaults.localProxyPort,
    val enableDynamicLocalProxyPort: Boolean = BackupDefaults.enableDynamicLocalProxyPort,
    val localProxyListenAllInterfaces: Boolean = BackupDefaults.localProxyListenAllInterfaces,
    val enableLocalProxyAuth: Boolean = BackupDefaults.enableLocalProxyAuth,
    val localProxyUsername: String = BackupDefaults.localProxyUsername,
    val localProxyPassword: String = BackupDefaults.localProxyPassword,
    val enableVpnAppendHttpProxy: Boolean = BackupDefaults.enableVpnAppendHttpProxy,
    val enableVpnHevTun: Boolean = BackupDefaults.enableVpnHevTun,
    val enableKillSwitch: Boolean = BackupDefaults.enableKillSwitch,
    val enableStrictFullTunnel: Boolean = BackupDefaults.enableStrictFullTunnel,
    val tunMtu: String = BackupDefaults.tunMtu,
    val tunVpnDns: String = BackupDefaults.tunVpnDns,
    val tunIpv4Cidr: String = BackupDefaults.tunIpv4Cidr,
    val tunIpv6Cidr: String = BackupDefaults.tunIpv6Cidr,
    val enableWakeLock: Boolean = BackupDefaults.enableWakeLock,
    val enableSeamlessNetworkSwitching: Boolean = BackupDefaults.enableSeamlessNetworkSwitching,
    val enableNetworkAutomation: Boolean = BackupDefaults.enableNetworkAutomation,
    val enableOnDemandVpn: Boolean = BackupDefaults.enableOnDemandVpn,
    val networkAutomationRules: List<NetworkAutomationRule> = emptyList(),
    val tunTcpKeepAliveInterval: String = BackupDefaults.tunTcpKeepAliveInterval,
    val tunTcpUserTimeout: String = BackupDefaults.tunTcpUserTimeout,
    val selectedProxyServerId: Int = BackupDefaults.selectedProxyServerId,
    val proxyServerListLayout: Int = BackupDefaults.proxyServerListLayout,
    val proxyServerListSort: Int = BackupDefaults.proxyServerListSort,
    val subscriptionPingMode: Int = BackupDefaults.subscriptionPingMode,
    val subscriptionPingUrl: String = BackupDefaults.subscriptionPingUrl,
    val subscriptionPingTimeoutMillis: String = BackupDefaults.subscriptionPingTimeoutMillis,
    val subscriptionPingConcurrency: Int = BackupDefaults.subscriptionPingConcurrency,
    val subscriptionUserAgents: List<String> = emptyList(),
    val enableSubscriptionDeviceHeaders: Boolean = BackupDefaults.enableSubscriptionDeviceHeaders,
    val subscriptionFetchTimeoutSeconds: Int = BackupDefaults.subscriptionFetchTimeoutSeconds,
    val routeDomainStrategy: Int = BackupDefaults.routeDomainStrategy,
    val defaultRouteOutboundTag: String = BackupDefaults.defaultRouteOutboundTag,
    val coreLogLevel: Int = BackupDefaults.coreLogLevel,
    val enableAccessLog: Boolean = BackupDefaults.enableAccessLog,
    val logRetentionDays: Int = BackupDefaults.logRetentionDays,
    val resourceFileSource: Int = BackupDefaults.resourceFileSource,
    val customResourceFileGeoIpUrl: String = BackupDefaults.customResourceFileGeoIpUrl,
    val customResourceFileGeoSiteUrl: String = BackupDefaults.customResourceFileGeoSiteUrl,
    val customResourceFileGeoIpOnlyCnPrivateUrl: String = BackupDefaults.customResourceFileGeoIpOnlyCnPrivateUrl,
    val customResourceFileDirectCidrIpv4Url: String = BackupDefaults.customResourceFileDirectCidrIpv4Url,
    val customResourceFileDirectCidrIpv6Url: String = BackupDefaults.customResourceFileDirectCidrIpv6Url,
    val customResourceFiles: List<AppBackupCustomResourceFile> = emptyList(),
    val enableSniffing: Boolean = BackupDefaults.enableSniffing,
    val enableSniffingRouteOnly: Boolean = BackupDefaults.enableSniffingRouteOnly,
    val enableMux: Boolean = BackupDefaults.enableMux,
    val muxConcurrency: String = BackupDefaults.muxConcurrency,
    val muxXudpConcurrency: String = BackupDefaults.muxXudpConcurrency,
    val muxXudpProxyUdp443: Int = BackupDefaults.muxXudpProxyUdp443,
    val enableFragment: Boolean = BackupDefaults.enableFragment,
    val fragmentPackets: String = BackupDefaults.fragmentPackets,
    val fragmentLength: String = BackupDefaults.fragmentLength,
    val fragmentInterval: String = BackupDefaults.fragmentInterval,
    val enableTrafficStatsNotification: Boolean = BackupDefaults.enableTrafficStatsNotification,
    val trafficStatsNotificationRefreshIntervalSeconds: Int =
        BackupDefaults.trafficStatsNotificationRefreshIntervalSeconds,
    val enableResourceFileNotifications: Boolean = BackupDefaults.enableResourceFileNotifications,
    val showServerSearch: Boolean = BackupDefaults.showServerSearch,
    val connectionDisplayMode: Int = BackupDefaults.connectionDisplayMode,
    val pinConnectionPanelOnHome: Boolean = BackupDefaults.pinConnectionPanelOnHome,
    val enableSubscriptionSwipe: Boolean = BackupDefaults.enableSubscriptionSwipe,
    val backgroundStyle: Int = BackupDefaults.backgroundStyle,
    val backgroundPhotoDimPercent: Int = BackupDefaults.backgroundPhotoDimPercent,
    val enableHaptics: Boolean = BackupDefaults.enableHaptics,
    val hasCompletedOnboarding: Boolean = true,
    val classicShowFloatingPowerButton: Boolean = BackupDefaults.classicShowFloatingPowerButton,
    val showTunnelMemoryOnHome: Boolean = BackupDefaults.showTunnelMemoryOnHome,
    val enableBroadcastControl: Boolean = BackupDefaults.enableBroadcastControl,
    val enableIpv6: Boolean = BackupDefaults.enableIpv6,
    val enableIpv6Prefer: Boolean = BackupDefaults.enableIpv6Prefer,
    val enableFakeDns: Boolean = BackupDefaults.enableFakeDns,
    val proxyDns: List<String> = BackupDefaults.proxyDns,
    val directDns: List<String> = BackupDefaults.directDns,
    val directDnsDomains: List<String> = BackupDefaults.directDnsDomains,
    val enableDirectDnsForProxyServerDomains: Boolean = BackupDefaults.enableDirectDnsForProxyServerDomains,
    val dnsHosts: List<String> = BackupDefaults.dnsHosts,
    val serviceControl: AppBackupServiceControl = AppBackupServiceControl(),
    val proxyAppListMode: Int = BackupDefaults.proxyAppListMode,
    val enableSubscriptionExpiryNotifications: Boolean = BackupDefaults.enableSubscriptionExpiryNotifications,
    val subscriptionExpiryReminders: List<SubscriptionExpiryReminder> = DefaultSubscriptionExpiryReminders,
)

@Serializable
data class AppBackupServiceControl(
    val enabled: Boolean = BackupDefaults.serviceControl.enabled,
    val schedule: AppBackupServiceControlSchedule = AppBackupServiceControlSchedule(),
    val wifi: AppBackupServiceControlWifi = AppBackupServiceControlWifi(),
)

@Serializable
data class AppBackupServiceControlSchedule(
    val enabled: Boolean = BackupDefaults.serviceControl.schedule.enabled,
    val startCron: String = BackupDefaults.serviceControl.schedule.startCron,
    val stopCron: String = BackupDefaults.serviceControl.schedule.stopCron,
)

@Serializable
data class AppBackupServiceControlWifi(
    val enabled: Boolean = BackupDefaults.serviceControl.wifi.enabled,
    val connectStart: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val connectStop: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val disconnectStart: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val disconnectStop: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
)

@Serializable
data class AppBackupServiceControlWifiRule(
    val enabled: Boolean = false,
    val ssids: List<String> = emptyList(),
    val bssids: List<String> = emptyList(),
)

@Serializable
data class AppBackupCustomResourceFile(
    val id: Int = 0,
    val name: String = "",
    val url: String = "",
)

@Serializable
data class AppBackupSubscriptionGroup(
    val id: Int = 0,
    val name: String = "",
    val url: String = "",
    val userAgent: String = "",
    val updateInterval: String = "",
    val hwid: String = "",
    val ageSecretKey: String = "",
    val updateViaProxy: Boolean = false,
    val autoOverrideRules: Boolean = true,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
    val profileTitle: String = "",
    val announce: String = "",
    val supportUrl: String = "",
    val supportEmail: String = "",
    val profileWebPageUrl: String = "",
    val announceUrl: String = "",
    val trafficUploadBytes: Long = -1L,
    val trafficDownloadBytes: Long = -1L,
    val trafficTotalBytes: Long = -1L,
    val trafficExpireAtSeconds: Long = -1L,
    val notifyOnExpiry: Boolean = true,
    val customExpiryReminders: List<SubscriptionExpiryReminder>? = null,
)

@Serializable
data class AppBackupProxyServer(
    val id: Int = 0,
    val groupId: Int = 0,
    val protocol: String = "",
    val payload: JsonElement = JsonObject(emptyMap()),
)

@Serializable
data class AppBackupRouteRule(
    val id: Int = 0,
    val remarks: String = "",
    val outboundTag: String = BackupDefaults.defaultRouteOutboundTag,
    val domain: List<String> = emptyList(),
    val ip: List<String> = emptyList(),
    val process: List<String> = emptyList(),
    val port: String = "",
    val protocol: String = "",
    val network: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class AppBackupTrafficConfig(
    val id: Int = 0,
    val name: String = "",
    val rawConfig: String = "",
    val sourceUrl: String = "",
    val updateLocked: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
    val autoUpdate: Boolean = false,
    val updateInterval: String = "",
    val proxyAppListMode: Int = 0,
    val proxyAppListSelectedApps: List<String> = emptyList(),
    val androidSettings: TrafficConfigAndroidSettings = TrafficConfigAndroidSettings(),
    val networkActivation: TrafficConfigNetworkActivation = TrafficConfigNetworkActivation(),
    val resourceSettings: TrafficConfigResourceSettings = TrafficConfigResourceSettings(),
)
