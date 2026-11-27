// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where every setting of the two settings models goes. The shared PersistedSettings is the one model both clients
 * map to, so a setting is in one of three places: carried by PersistedSettings, waiting to be carried (the same
 * setting exists on both clients but the shared model has no section for it yet), or owned by one client alone.
 *
 * A new field fails this test until it is classified, which is what keeps the two clients from drifting apart
 * again. The 24 picked colors are checked by [CustomColorsConversionTest].
 */
class SettingsFieldInventoryTest {
    private val carriedByPersistedSettingsOnAndroid = setOf(
        "activeTrafficConfigId",
        "autoCheckAppUpdates",
        "autoConnectOnAppOpen",
        "backgroundPhotoDimPercent",
        "backgroundStyle",
        "bottomBarSize",
        "classicButtonSize",
        "classicShowFloatingPowerButton",
        "colorMode",
        "connectionDisplayMode",
        "coreLogLevel",
        "customMaterialYouSeed",
        "customResourceFiles",
        "customThemes",
        "defaultRouteOutboundTag",
        "enableAccessLog",
        "enableAllProxyGroup",
        "enableCustomColors",
        "enableDeletionConfirmation",
        "enableDynamicLocalProxyPort",
        "enableFragment",
        "enableHaptics",
        "enableLocalHttpProxy",
        "enableLocalHttpProxyAuth",
        "enableLocalProxyAuth",
        "enableMaterialYou",
        "enableMux",
        "enableNetworkAutomation",
        "enableOnDemandVpn",
        "enableSeasonalEvents",
        "enableSniffing",
        "enableSniffingRouteOnly",
        "enableStrictFullTunnel",
        "enableSubscriptionDeviceHeaders",
        "enableSubscriptionExpiryNotifications",
        "enableSubscriptionSwipe",
        "enableVpnLocalDns",
        "floatingButtonX",
        "floatingButtonY",
        "fontFamilyMode",
        "fontSizeMode",
        "fontWeightMode",
        "fragmentInterval",
        "fragmentLength",
        "fragmentPackets",
        "hasCompletedOnboarding",
        "languageMode",
        "localHttpProxyAuthMode",
        "localHttpProxyPassword",
        "localHttpProxyPort",
        "localHttpProxyUsername",
        "localProxyAuthMode",
        "localProxyListenAllInterfaces",
        "localProxyPassword",
        "localProxyPort",
        "localProxyUsername",
        "logRetentionDays",
        "maskTunnelIpOnHome",
        "muxConcurrency",
        "muxXudpConcurrency",
        "muxXudpProxyUdp443",
        "networkAutomationRules",
        "nextProxyServerId",
        "nextSubscriptionGroupId",
        "pinConnectionPanelOnHome",
        "proxyServerListSort",
        "proxyServers",
        "resourceFileSource",
        "resourceFileUserAgent",
        "routeDomainStrategy",
        "routeRules",
        "seedIndex",
        "selectedProxyServerId",
        "showServerSearch",
        "showTunnelIpOnHome",
        "showTunnelMemoryOnHome",
        "subscriptionExpiryReminders",
        "subscriptionFetchTimeoutSeconds",
        "subscriptionGroups",
        "subscriptionPingConcurrency",
        "subscriptionPingMode",
        "subscriptionPingTimeoutMillis",
        "subscriptionPingUrl",
        "subscriptionUserAgents",
        "tunIpv4Cidr",
        "tunIpv6Cidr",
        "tunMtu",
        "tunTcpKeepAliveInterval",
        "tunTcpUserTimeout",
        "tunVpnDns",
    )

    private val carriedByPersistedSettingsOnDesktop = setOf(
        "autoCheckAppUpdates",
        "autoConnectOnAppOpen",
        "backgroundPhotoDimPercent",
        "backgroundStyle",
        "bottomBarSize",
        "classicButtonSize",
        "classicShowFloatingPowerButton",
        "compactHome",
        "confirmDeletion",
        "connectionDisplayMode",
        "coreLogLevel",
        "customColorsEnabled",
        "customMaterialYouSeed",
        "customThemes",
        "dynamicColors",
        "enableAccessLog",
        "enableAllProxyGroup",
        "enableDynamicLocalProxyPort",
        "enableFragment",
        "enableLocalHttpProxy",
        "enableLocalHttpProxyAuth",
        "enableLocalProxyAuth",
        "enableMux",
        "enableNetworkAutomation",
        "enableOnDemandVpn",
        "enableSeasonalEvents",
        "enableSniffing",
        "enableSniffingRouteOnly",
        "enableStrictFullTunnel",
        "enableSubscriptionExpiryNotifications",
        "enableSubscriptionSwipe",
        "enableVpnLocalDns",
        "floatingButtonX",
        "floatingButtonY",
        "fontFamilyMode",
        "fontSizeMode",
        "fontWeightMode",
        "fragmentInterval",
        "fragmentLength",
        "fragmentPackets",
        "hapticsEnabled",
        "languageTag",
        "localHttpProxyAuthMode",
        "localHttpProxyPassword",
        "localHttpProxyPort",
        "localHttpProxyUsername",
        "localProxyAuthMode",
        "localProxyListenAddress",
        "localProxyPassword",
        "localProxyPort",
        "localProxyPortRaw",
        "localProxyUsername",
        "logRetentionDays",
        "maskTunnelIp",
        "muxConcurrency",
        "muxXudpConcurrency",
        "muxXudpProxyUdp443",
        "navigationStyle",
        "networkAutomationRules",
        "onboardingCompleted",
        "pinConnectionPanelOnHome",
        "proxyServerListSort",
        "seedIndex",
        "sendDeviceHeaders",
        "showConnectionLog",
        "showServerSearch",
        "showTunnelIp",
        "showTunnelMemory",
        "subscriptionExpiryReminders",
        "subscriptionFetchTimeoutSeconds",
        "subscriptionPingConcurrency",
        "subscriptionPingMode",
        "subscriptionPingTimeoutMillis",
        "subscriptionPingUrl",
        "subscriptionUserAgents",
        "themeMode",
        "tunIpv4Cidr",
        "tunIpv6Cidr",
        "tunMtu",
        "tunTcpKeepAliveInterval",
        "tunTcpUserTimeout",
        "tunVpnDns",
    )

    /** On both clients, but PersistedSettings has no section for them yet: the routing rule counter, which waits for the routing stage. */
    private val onBothClientsNotYetShared = setOf(
        "nextRouteRuleId",
    )

    /** Android runtime state and settings Desktop keeps elsewhere or does not have. */
    private val androidOnly = setOf(
        "appIcon",
        "appUpdateApkFilePath",
        "appUpdateChannel",
        "appUpdateDownloadError",
        "appUpdateDownloadIsAutomatic",
        "appUpdateDownloadStatus",
        "appUpdateDownloadedBytes",
        "appUpdateTotalBytes",
        "autoConnectOnBoot",
        "autoInstallAppUpdatesAtNight",
        "availableAppUpdate",
        "customResourceFileDirectCidrIpv4Url",
        "customResourceFileDirectCidrIpv6Url",
        "customResourceFileGeoIpOnlyCnPrivateUrl",
        "customResourceFileGeoIpUrl",
        "customResourceFileGeoSiteUrl",
        "directDns",
        "directDnsDomains",
        "dismissedUpdateVersion",
        "dnsHosts",
        "enableBroadcastControl",
        "enableDirectDnsForProxyServerDomains",
        "enableFakeDns",
        "enableIpv6",
        "enableIpv6Prefer",
        "enableKillSwitch",
        "enableResolveProxyServerDomain",
        "enableResourceFileNotifications",
        "enableSeamlessNetworkSwitching",
        "enableTrafficStatsNotification",
        "enableVpnAppendHttpProxy",
        "enableVpnHevTun",
        "enableWakeLock",
        "fakeDnsIpPool",
        "fakeDnsPoolSize",
        "hevTcpReadWriteTimeoutMillis",
        "nextCustomResourceFileId",
        "nextTrafficConfigId",
        "proxyAppListMode",
        "proxyAppListSelectedApps",
        "proxyDns",
        "proxyRunning",
        "runMode",
        "serviceControl",
        "shadowrocketPolicyGroups",
        "trafficConfigs",
        "trafficStatsNotificationRefreshIntervalSeconds",
        "unappliedRoutingRules",
    )

    /** Desktop window, autostart and system-proxy settings, and the library-backed state. */
    private val desktopOnly = setOf(
        "allowExternalControl",
        "appUpdateChannelId",
        "closeToBackground",
        "defaultRouteOutboundTag",
        "enableTunMode",
        "homeLeftColumnExtraDp",
        "installationUuid",
        "launchOnLogin",
        // Link merging: Core spreads its connections over this machine's network adapters.
        "multilink",
        "routeDomainStrategy",
        "routeRules",
        "sidebarCollapsed",
        "useSystemProxy",
        "useWindowsSystemProxy",
    )

    private fun appStateFields(): Set<String> = AppState::class.java.declaredFields
        .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .map { it.name }
        .filterNot(::isPickedColor)
        .toSet()

    private fun desktopFields(): Set<String> = DesktopAppSettings.serializer().descriptor.let { d ->
        (0 until d.elementsCount).map(d::getElementName)
    }.filterNot(::isPickedColor).toSet()

    private fun isPickedColor(name: String) = Regex("custom[A-Za-z0-9]+Color").matches(name) &&
        name !in setOf("customMaterialYouSeed")

    @Test
    fun everyAppStateFieldIsClassified() {
        val classified = carriedByPersistedSettingsOnAndroid + onBothClientsNotYetShared + androidOnly
        assertEquals(emptySet(), appStateFields() - classified, "AppState fields nobody has classified")
        assertEquals(emptySet(), classified - appStateFields(), "classified names AppState no longer has")
    }

    @Test
    fun everyDesktopSettingIsClassified() {
        val classified = carriedByPersistedSettingsOnDesktop + onBothClientsNotYetShared + desktopOnly
        assertEquals(emptySet(), desktopFields() - classified, "DesktopAppSettings fields nobody has classified")
        assertEquals(emptySet(), classified - desktopFields(), "classified names DesktopAppSettings no longer has")
    }

    @Test
    fun aSettingIsInOnePlaceOnly() {
        val android = listOf(carriedByPersistedSettingsOnAndroid, onBothClientsNotYetShared, androidOnly)
        val desktop = listOf(carriedByPersistedSettingsOnDesktop, onBothClientsNotYetShared, desktopOnly)
        for (group in listOf(android, desktop)) {
            val flat = group.flatten()
            assertEquals(flat.size, flat.toSet().size, "a setting is listed twice: ${flat.groupBy { it }.filter { it.value.size > 1 }.keys}")
        }
    }

    @Test
    fun theSettingsStillToShareExistOnBothClients() {
        assertTrue(onBothClientsNotYetShared.all { it in appStateFields() && it in desktopFields() })
    }
}
