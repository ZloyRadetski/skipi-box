// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigNetworkActivation
import features.config.TrafficConfigResourceSettings
import features.networkautomation.model.NetworkAutomationRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal const val AppBackupFormat = "skipi-backup"
internal const val CurrentAppBackupVersion = 1

private val BackupDefaults = AppState()

@Serializable
internal data class AppBackupFile(
    val format: String = "",
    val version: Int = 0,
    val createdAtMillis: Long = 0L,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val data: AppBackupData = AppBackupData(),
)

@Serializable
internal data class AppBackupData(
    val settings: AppBackupSettings = AppBackupSettings(),
    val subscriptionGroups: List<AppBackupSubscriptionGroup> = emptyList(),
    val proxyServers: List<AppBackupProxyServer> = emptyList(),
    val routeRules: List<AppBackupRouteRule> = emptyList(),
    val proxyAppListSelectedApps: List<String> = emptyList(),
    val trafficConfigs: List<AppBackupTrafficConfig> = emptyList(),
    val activeTrafficConfigId: Int = 0,
)

@Serializable
internal data class AppBackupSettings(
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
    val localProxyUsername: String = BackupDefaults.localProxyUsername,
    val localProxyPassword: String = BackupDefaults.localProxyPassword,
    val enableVpnAppendHttpProxy: Boolean = BackupDefaults.enableVpnAppendHttpProxy,
    val enableVpnHevTun: Boolean = BackupDefaults.enableVpnHevTun,
    val enableKillSwitch: Boolean = BackupDefaults.enableKillSwitch,
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
    val subscriptionExpiryReminders: List<app.SubscriptionExpiryReminder> = app.DefaultSubscriptionExpiryReminders,
)

@Serializable
internal data class AppBackupServiceControl(
    val enabled: Boolean = BackupDefaults.serviceControl.enabled,
    val schedule: AppBackupServiceControlSchedule = AppBackupServiceControlSchedule(),
    val wifi: AppBackupServiceControlWifi = AppBackupServiceControlWifi(),
)

@Serializable
internal data class AppBackupServiceControlSchedule(
    val enabled: Boolean = BackupDefaults.serviceControl.schedule.enabled,
    val startCron: String = BackupDefaults.serviceControl.schedule.startCron,
    val stopCron: String = BackupDefaults.serviceControl.schedule.stopCron,
)

@Serializable
internal data class AppBackupServiceControlWifi(
    val enabled: Boolean = BackupDefaults.serviceControl.wifi.enabled,
    val connectStart: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val connectStop: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val disconnectStart: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val disconnectStop: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
)

@Serializable
internal data class AppBackupServiceControlWifiRule(
    val enabled: Boolean = false,
    val ssids: List<String> = emptyList(),
    val bssids: List<String> = emptyList(),
)

@Serializable
internal data class AppBackupCustomResourceFile(
    val id: Int = 0,
    val name: String = "",
    val url: String = "",
)

@Serializable
internal data class AppBackupSubscriptionGroup(
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
    val customExpiryReminders: List<app.SubscriptionExpiryReminder>? = null,
)

@Serializable
internal data class AppBackupProxyServer(
    val id: Int = 0,
    val groupId: Int = 0,
    val protocol: String = "",
    val payload: JsonElement = JsonObject(emptyMap()),
)

@Serializable
internal data class AppBackupRouteRule(
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
internal data class AppBackupTrafficConfig(
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

internal data class AppBackupRestorePreview(
    val backup: AppBackupFile,
    val restoredState: AppState,
    val warnings: List<AppBackupWarning>,
) {
    val subscriptionGroupCount: Int
        get() = restoredState.subscriptionGroups.size

    val proxyServerCount: Int
        get() = restoredState.proxyServers.size

    val routeRuleCount: Int
        get() = restoredState.routeRules.size

    val trafficConfigCount: Int
        get() = restoredState.trafficConfigs.size
}

internal sealed interface AppBackupWarning {
    data class MissingChainProxyMembers(
        val count: Int,
    ) : AppBackupWarning

    data class MissingStrategyGroupMembers(
        val count: Int,
    ) : AppBackupWarning
}
