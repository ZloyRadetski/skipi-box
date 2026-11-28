// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.colorModeOfAppearance
import app.skipi.app.model.customColors
import app.skipi.app.model.languageModeOfTag
import features.backup.AppBackupSettings
import features.backup.withCustomColors

/**
 * The backup settings for what [PersistedSettings] carries, over [base]: the settings a client owns alone (and the
 * defaults of what a client does not have) are already in it. Both clients write their backup through this, so a
 * setting both have is saved the same way.
 */
fun PersistedSettings.toBackupSettings(base: AppBackupSettings = AppBackupSettings()): AppBackupSettings {
    val local = localProxy
    val subs = subscriptions
    return base.copy(
        colorMode = colorModeOfAppearance(appearance, fallback = base.colorMode),
        languageMode = languageModeOfTag(application.languageTag),
        fontFamilyMode = appearance.fontFamilyMode ?: base.fontFamilyMode,
        fontSizeMode = app.modes.normalizeFontSizeMode(appearance.fontSizeMode ?: base.fontSizeMode),
        fontWeightMode = appearance.fontWeightMode ?: base.fontWeightMode,
        enableMaterialYou = appearance.dynamicColors,
        seedIndex = appearance.seedIndex,
        customMaterialYouSeed = appearance.customMaterialYouSeed,
        enableCustomColors = appearance.customColorsEnabled,
        customThemes = appearance.customThemes,
        bottomBarSize = appearance.bottomBarSize ?: base.bottomBarSize,
        classicButtonSize = appearance.classicButtonSize ?: base.classicButtonSize,
        enableSeasonalEvents = appearance.seasonalEvents,
        backgroundStyle = appearance.backgroundStyle ?: base.backgroundStyle,
        backgroundPhotoDimPercent = appearance.backgroundPhotoDimPercent ?: base.backgroundPhotoDimPercent,
        backgroundPhotoBlurPercent = appearance.backgroundPhotoBlurPercent ?: base.backgroundPhotoBlurPercent,
        connectionDisplayMode = home.connectionDisplayMode ?: base.connectionDisplayMode,
        pinConnectionPanelOnHome = home.pinConnectionPanel,
        enableSubscriptionSwipe = home.subscriptionSwipeEnabled,
        classicShowFloatingPowerButton = home.showFloatingPowerButton,
        floatingButtonX = home.floatingButtonX ?: base.floatingButtonX,
        floatingButtonY = home.floatingButtonY ?: base.floatingButtonY,
        enableDeletionConfirmation = home.confirmDeletion,
        showServerSearch = home.showServerSearch,
        proxyServerListSort = home.proxyServerListSort ?: base.proxyServerListSort,
        enableAllProxyGroup = home.enableAllProxyGroup ?: base.enableAllProxyGroup,
        showTunnelMemoryOnHome = home.showTunnelMemoryOnHome ?: base.showTunnelMemoryOnHome,
        showTunnelIpOnHome = home.showTunnelIpOnHome ?: base.showTunnelIpOnHome,
        maskTunnelIpOnHome = home.maskTunnelIpOnHome ?: base.maskTunnelIpOnHome,
        selectedProxyServerId = proxy.selectedServerId ?: base.selectedProxyServerId,
        enableHaptics = application.hapticsEnabled,
        hasCompletedOnboarding = application.onboardingCompleted,
        autoCheckAppUpdates = application.autoCheckAppUpdates,
        localProxyPort = local?.port ?: base.localProxyPort,
        enableDynamicLocalProxyPort = local?.dynamicPort ?: base.enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = local?.listenAllInterfaces ?: base.localProxyListenAllInterfaces,
        enableLocalProxyAuth = local?.authenticationEnabled ?: base.enableLocalProxyAuth,
        localProxyUsername = local?.username ?: base.localProxyUsername,
        localProxyPassword = local?.password ?: base.localProxyPassword,
        localProxyAuthMode = local?.authMode?.name ?: base.localProxyAuthMode,
        enableLocalHttpProxy = local?.httpEnabled ?: base.enableLocalHttpProxy,
        localHttpProxyPort = local?.httpPort ?: base.localHttpProxyPort,
        enableLocalHttpProxyAuth = local?.httpAuthenticationEnabled ?: base.enableLocalHttpProxyAuth,
        localHttpProxyAuthMode = local?.httpAuthMode?.name ?: base.localHttpProxyAuthMode,
        localHttpProxyUsername = local?.httpUsername ?: base.localHttpProxyUsername,
        localHttpProxyPassword = local?.httpPassword ?: base.localHttpProxyPassword,
        subscriptionPingMode = subs?.pingMode ?: base.subscriptionPingMode,
        subscriptionPingUrl = subs?.pingUrl ?: base.subscriptionPingUrl,
        subscriptionPingTimeoutMillis = subs?.pingTimeoutMillis ?: base.subscriptionPingTimeoutMillis,
        subscriptionPingConcurrency = subs?.pingConcurrency ?: base.subscriptionPingConcurrency,
        subscriptionUserAgents = subs?.userAgents ?: base.subscriptionUserAgents,
        enableSubscriptionDeviceHeaders = subs?.deviceHeadersEnabled ?: base.enableSubscriptionDeviceHeaders,
        subscriptionFetchTimeoutSeconds = subs?.fetchTimeoutSeconds ?: base.subscriptionFetchTimeoutSeconds,
        enableSubscriptionExpiryNotifications = subs?.expiryNotificationsEnabled ?: base.enableSubscriptionExpiryNotifications,
        subscriptionExpiryReminders = subs?.expiryReminders ?: base.subscriptionExpiryReminders,
        enableStrictFullTunnel = vpn?.strictFullTunnel ?: base.enableStrictFullTunnel,
        enableVpnLocalDns = vpn?.localDns ?: base.enableVpnLocalDns,
        tunMtu = vpn?.mtu ?: base.tunMtu,
        tunVpnDns = vpn?.dns ?: base.tunVpnDns,
        tunIpv4Cidr = vpn?.ipv4Cidr ?: base.tunIpv4Cidr,
        tunIpv6Cidr = vpn?.ipv6Cidr ?: base.tunIpv6Cidr,
        tunTcpKeepAliveInterval = vpn?.tcpKeepAliveInterval ?: base.tunTcpKeepAliveInterval,
        tunTcpUserTimeout = vpn?.tcpUserTimeout ?: base.tunTcpUserTimeout,
        enableNetworkAutomation = networkAutomation?.enabled ?: base.enableNetworkAutomation,
        enableOnDemandVpn = networkAutomation?.onDemandVpn ?: base.enableOnDemandVpn,
        networkAutomationRules = networkAutomation?.rules ?: base.networkAutomationRules,
        enableSniffing = engine?.sniffing ?: base.enableSniffing,
        enableSniffingRouteOnly = engine?.sniffingRouteOnly ?: base.enableSniffingRouteOnly,
        enableMux = engine?.mux ?: base.enableMux,
        muxConcurrency = engine?.muxConcurrency ?: base.muxConcurrency,
        muxXudpConcurrency = engine?.muxXudpConcurrency ?: base.muxXudpConcurrency,
        muxXudpProxyUdp443 = engine?.muxXudpProxyUdp443 ?: base.muxXudpProxyUdp443,
        enableFragment = engine?.fragment ?: base.enableFragment,
        fragmentPackets = engine?.fragmentPackets ?: base.fragmentPackets,
        fragmentLength = engine?.fragmentLength ?: base.fragmentLength,
        fragmentInterval = engine?.fragmentInterval ?: base.fragmentInterval,
        coreLogLevel = logs?.coreLogLevel ?: base.coreLogLevel,
        enableAccessLog = logs?.accessLogEnabled ?: base.enableAccessLog,
        logRetentionDays = logs?.retentionDays ?: base.logRetentionDays,
    ).withCustomColors(appearance.customColors())
}
