// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import features.subscription.DefaultSubscriptionGroupId
import app.ProxyServerState
import app.AppState
import app.SubscriptionGroupState
import app.skipi.app.model.ApplicationPreferences
import app.skipi.app.model.languageModeOfTagOrNull
import app.skipi.app.model.languageTagOfMode
import app.skipi.app.model.colorModeOfAppearance
import app.skipi.app.model.themeChoiceOfColorMode
import app.skipi.app.model.CoreEnginePreferences
import app.skipi.app.model.LogPreferences
import app.skipi.app.model.NetworkAutomationPreferences
import app.skipi.app.model.VpnPreferences
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.HomeDisplaySettings
import app.skipi.app.model.LocalProxyPreferences
import app.skipi.app.model.ProxySelectionSettings
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.ResourceCatalogRecord
import app.skipi.app.model.ResourceDefinition
import app.skipi.app.model.RoutingConfigRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionPreferences
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.ThemeMode
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.model.TrafficSelectionSettings
import app.skipi.app.repository.AppRepositories
import app.skipi.app.repository.ResourceRepository
import app.skipi.app.repository.RoutingRepository
import app.skipi.app.repository.RuntimeStateRepository
import app.skipi.app.repository.SettingsRepository
import app.skipi.app.repository.SubscriptionRepository
import app.skipi.app.repository.TrafficConfigRepository
import app.skipi.app.subscription.SubscriptionCatalogOperations
import features.config.TrafficConfigState
import features.config.newAndroidTrafficConfig
import features.config.withActiveTrafficConfigProfile
import features.config.withConfigProxyGroupsReflected
import features.config.withUpdatedTrafficConfigProfile
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig
import features.subscription.SubscriptionMetadata
import app.customColors
import app.withCustomColors
import app.skipi.app.model.customColors
import app.skipi.app.model.withCustomColors
fun AppState.toPersistedSettings() = app.skipi.app.model.PersistedSettings(
    appearance = AppearanceSettings(
        themeMode = themeChoiceOfColorMode(colorMode).themeMode,
        dynamicColors = enableMaterialYou,
        amoledBlack = themeChoiceOfColorMode(colorMode).amoledBlack,
        themeVariant = themeChoiceOfColorMode(colorMode).themeVariant,
        seedIndex = seedIndex,
        customMaterialYouSeed = customMaterialYouSeed,
        customColorsEnabled = enableCustomColors,
        fontFamilyMode = fontFamilyMode,
        fontSizeMode = fontSizeMode,
        fontWeightMode = fontWeightMode,
        backgroundStyle = backgroundStyle,
        backgroundPhotoDimPercent = backgroundPhotoDimPercent,
        bottomBarSize = bottomBarSize,
        classicButtonSize = classicButtonSize,
        seasonalEvents = enableSeasonalEvents,
        customThemes = customThemes,
    ).withCustomColors(customColors()),
    proxy = ProxySelectionSettings(selectedServerId = selectedProxyServerId),
    home = HomeDisplaySettings(
        connectionDisplayMode = connectionDisplayMode,
        pinConnectionPanel = pinConnectionPanelOnHome,
        subscriptionSwipeEnabled = enableSubscriptionSwipe,
        showFloatingPowerButton = classicShowFloatingPowerButton,
        confirmDeletion = enableDeletionConfirmation,
        showServerSearch = showServerSearch,
        proxyServerListSort = proxyServerListSort,
        enableAllProxyGroup = enableAllProxyGroup,
        showTunnelMemoryOnHome = showTunnelMemoryOnHome,
        showTunnelIpOnHome = showTunnelIpOnHome,
        maskTunnelIpOnHome = maskTunnelIpOnHome,
    ),
    traffic = TrafficSelectionSettings(activeConfigId = activeTrafficConfigId),
    application = ApplicationPreferences(
        languageTag = languageTagOfMode(languageMode),
        hapticsEnabled = enableHaptics,
        onboardingCompleted = hasCompletedOnboarding,
        autoCheckAppUpdates = autoCheckAppUpdates,
        autoConnectOnAppOpen = autoConnectOnAppOpen,
    ),
    localProxy = LocalProxyPreferences(
        port = localProxyPort,
        dynamicPort = enableDynamicLocalProxyPort,
        listenAllInterfaces = localProxyListenAllInterfaces,
        authenticationEnabled = enableLocalProxyAuth,
        username = localProxyUsername,
        password = localProxyPassword,
        authMode = localProxyAuthMode,
        httpEnabled = enableLocalHttpProxy,
        httpPort = localHttpProxyPort,
        httpAuthenticationEnabled = enableLocalHttpProxyAuth,
        httpAuthMode = localHttpProxyAuthMode,
        httpUsername = localHttpProxyUsername,
        httpPassword = localHttpProxyPassword,
    ),
    subscriptions = SubscriptionPreferences(
        pingMode = subscriptionPingMode,
        pingUrl = subscriptionPingUrl,
        pingTimeoutMillis = subscriptionPingTimeoutMillis,
        pingConcurrency = subscriptionPingConcurrency,
        userAgents = subscriptionUserAgents,
        deviceHeadersEnabled = enableSubscriptionDeviceHeaders,
        fetchTimeoutSeconds = subscriptionFetchTimeoutSeconds,
        expiryNotificationsEnabled = enableSubscriptionExpiryNotifications,
        expiryReminders = subscriptionExpiryReminders,
    ),
    vpn = VpnPreferences(
        strictFullTunnel = enableStrictFullTunnel,
        localDns = enableVpnLocalDns,
        mtu = tunMtu,
        dns = tunVpnDns,
        ipv4Cidr = tunIpv4Cidr,
        ipv6Cidr = tunIpv6Cidr,
        tcpKeepAliveInterval = tunTcpKeepAliveInterval,
        tcpUserTimeout = tunTcpUserTimeout,
    ),
    networkAutomation = NetworkAutomationPreferences(
        enabled = enableNetworkAutomation,
        onDemandVpn = enableOnDemandVpn,
        rules = networkAutomationRules,
    ),
    engine = CoreEnginePreferences(
        sniffing = enableSniffing,
        sniffingRouteOnly = enableSniffingRouteOnly,
        mux = enableMux,
        muxConcurrency = muxConcurrency,
        muxXudpConcurrency = muxXudpConcurrency,
        muxXudpProxyUdp443 = muxXudpProxyUdp443,
        fragment = enableFragment,
        fragmentPackets = fragmentPackets,
        fragmentLength = fragmentLength,
        fragmentInterval = fragmentInterval,
    ),
    logs = LogPreferences(
        coreLogLevel = coreLogLevel,
        accessLogEnabled = enableAccessLog,
        retentionDays = logRetentionDays,
    ),
)

/**
 * Applies the appearance and Home sections of stored settings, leaving everything else alone. Settings the sections
 * leave open keep the state's own values.
 */
fun AppState.applyAppearanceSettings(
    appearance: app.skipi.app.model.AppearanceSettings,
    home: app.skipi.app.model.HomeDisplaySettings,
): AppState = copy(
        // A named theme older shared data has no variant for keeps the current one.
        colorMode = colorModeOfAppearance(appearance, fallback = colorMode),
        enableMaterialYou = appearance.dynamicColors,
        connectionDisplayMode = home.connectionDisplayMode ?: connectionDisplayMode,
        pinConnectionPanelOnHome = home.pinConnectionPanel,
        enableSubscriptionSwipe = home.subscriptionSwipeEnabled,
        classicShowFloatingPowerButton = home.showFloatingPowerButton,
        showServerSearch = home.showServerSearch,
        proxyServerListSort = home.proxyServerListSort ?: proxyServerListSort,
        enableAllProxyGroup = home.enableAllProxyGroup ?: enableAllProxyGroup,
        showTunnelMemoryOnHome = home.showTunnelMemoryOnHome ?: showTunnelMemoryOnHome,
        showTunnelIpOnHome = home.showTunnelIpOnHome ?: showTunnelIpOnHome,
        maskTunnelIpOnHome = home.maskTunnelIpOnHome ?: maskTunnelIpOnHome,
        seedIndex = appearance.seedIndex,
        customMaterialYouSeed = appearance.customMaterialYouSeed,
        enableCustomColors = appearance.customColorsEnabled,
        fontFamilyMode = appearance.fontFamilyMode ?: fontFamilyMode,
        fontSizeMode = appearance.fontSizeMode ?: fontSizeMode,
        fontWeightMode = appearance.fontWeightMode ?: fontWeightMode,
        backgroundStyle = appearance.backgroundStyle ?: backgroundStyle,
        backgroundPhotoDimPercent = appearance.backgroundPhotoDimPercent ?: backgroundPhotoDimPercent,
        bottomBarSize = appearance.bottomBarSize ?: bottomBarSize,
        classicButtonSize = appearance.classicButtonSize ?: classicButtonSize,
        enableSeasonalEvents = appearance.seasonalEvents,
        customThemes = appearance.customThemes,
).withCustomColors(appearance.customColors())

/** Applies shared settings while leaving host-only and absent persisted fields untouched. */
fun AppState.applyPersistedSettings(settings: app.skipi.app.model.PersistedSettings): AppState {
    val languageTag = settings.application.languageTag
    val nextActiveConfigId = settings.traffic.activeConfigId ?: activeTrafficConfigId
    val nextLanguageMode = when {
        languageTag == null -> app.modes.LanguageModeSystem
        else -> languageModeOfTagOrNull(languageTag) ?: languageMode
    }
    val updated = applyAppearanceSettings(settings.appearance, settings.home).copy(
        enableDeletionConfirmation = settings.home.confirmDeletion,
        selectedProxyServerId = settings.proxy.selectedServerId ?: selectedProxyServerId,
        enableHaptics = settings.application.hapticsEnabled,
        hasCompletedOnboarding = settings.application.onboardingCompleted,
        autoCheckAppUpdates = settings.application.autoCheckAppUpdates,
        autoConnectOnAppOpen = settings.application.autoConnectOnAppOpen,
        enableStrictFullTunnel = settings.vpn?.strictFullTunnel ?: enableStrictFullTunnel,
        enableVpnLocalDns = settings.vpn?.localDns ?: enableVpnLocalDns,
        tunMtu = settings.vpn?.mtu ?: tunMtu,
        tunVpnDns = settings.vpn?.dns ?: tunVpnDns,
        tunIpv4Cidr = settings.vpn?.ipv4Cidr ?: tunIpv4Cidr,
        tunIpv6Cidr = settings.vpn?.ipv6Cidr ?: tunIpv6Cidr,
        tunTcpKeepAliveInterval = settings.vpn?.tcpKeepAliveInterval ?: tunTcpKeepAliveInterval,
        tunTcpUserTimeout = settings.vpn?.tcpUserTimeout ?: tunTcpUserTimeout,
        enableNetworkAutomation = settings.networkAutomation?.enabled ?: enableNetworkAutomation,
        enableOnDemandVpn = settings.networkAutomation?.onDemandVpn ?: enableOnDemandVpn,
        networkAutomationRules = settings.networkAutomation?.rules ?: networkAutomationRules,
        enableSniffing = settings.engine?.sniffing ?: enableSniffing,
        enableSniffingRouteOnly = settings.engine?.sniffingRouteOnly ?: enableSniffingRouteOnly,
        enableMux = settings.engine?.mux ?: enableMux,
        muxConcurrency = settings.engine?.muxConcurrency ?: muxConcurrency,
        muxXudpConcurrency = settings.engine?.muxXudpConcurrency ?: muxXudpConcurrency,
        muxXudpProxyUdp443 = settings.engine?.muxXudpProxyUdp443 ?: muxXudpProxyUdp443,
        enableFragment = settings.engine?.fragment ?: enableFragment,
        fragmentPackets = settings.engine?.fragmentPackets ?: fragmentPackets,
        fragmentLength = settings.engine?.fragmentLength ?: fragmentLength,
        fragmentInterval = settings.engine?.fragmentInterval ?: fragmentInterval,
        coreLogLevel = settings.logs?.coreLogLevel ?: coreLogLevel,
        enableAccessLog = settings.logs?.accessLogEnabled ?: enableAccessLog,
        logRetentionDays = settings.logs?.retentionDays ?: logRetentionDays,
        languageMode = nextLanguageMode,
        localProxyPort = settings.localProxy?.port ?: localProxyPort,
        enableDynamicLocalProxyPort = settings.localProxy?.dynamicPort ?: enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = settings.localProxy?.listenAllInterfaces ?: localProxyListenAllInterfaces,
        enableLocalProxyAuth = settings.localProxy?.authenticationEnabled ?: enableLocalProxyAuth,
        localProxyUsername = settings.localProxy?.username ?: localProxyUsername,
        localProxyPassword = settings.localProxy?.password ?: localProxyPassword,
        localProxyAuthMode = settings.localProxy?.authMode ?: localProxyAuthMode,
        enableLocalHttpProxy = settings.localProxy?.httpEnabled ?: enableLocalHttpProxy,
        localHttpProxyPort = settings.localProxy?.httpPort ?: localHttpProxyPort,
        enableLocalHttpProxyAuth = settings.localProxy?.httpAuthenticationEnabled ?: enableLocalHttpProxyAuth,
        localHttpProxyAuthMode = settings.localProxy?.httpAuthMode ?: localHttpProxyAuthMode,
        localHttpProxyUsername = settings.localProxy?.httpUsername?.takeIf { it.isNotBlank() } ?: localHttpProxyUsername,
        localHttpProxyPassword = settings.localProxy?.httpPassword?.takeIf { it.isNotBlank() } ?: localHttpProxyPassword,
        subscriptionPingMode = settings.subscriptions?.pingMode ?: subscriptionPingMode,
        subscriptionPingUrl = settings.subscriptions?.pingUrl ?: subscriptionPingUrl,
        subscriptionPingTimeoutMillis = settings.subscriptions?.pingTimeoutMillis ?: subscriptionPingTimeoutMillis,
        subscriptionPingConcurrency = settings.subscriptions?.pingConcurrency ?: subscriptionPingConcurrency,
        subscriptionUserAgents = settings.subscriptions?.userAgents ?: subscriptionUserAgents,
        enableSubscriptionDeviceHeaders = settings.subscriptions?.deviceHeadersEnabled ?: enableSubscriptionDeviceHeaders,
        subscriptionFetchTimeoutSeconds = settings.subscriptions?.fetchTimeoutSeconds ?: subscriptionFetchTimeoutSeconds,
        enableSubscriptionExpiryNotifications =
            settings.subscriptions?.expiryNotificationsEnabled ?: enableSubscriptionExpiryNotifications,
        subscriptionExpiryReminders = settings.subscriptions?.expiryReminders ?: subscriptionExpiryReminders,
    )
    return if (updated.activeTrafficConfigId == nextActiveConfigId) updated
    else updated.withActiveTrafficConfigProfile(nextActiveConfigId)
}

fun AppState.toRoutingConfigRecord() = RoutingConfigRecord(
    domainStrategy = routeDomainStrategy,
    defaultOutboundTag = defaultRouteOutboundTag,
    rules = routeRules,
)

fun AppState.toResourceCatalogRecord() = ResourceCatalogRecord(
    sourceId = resourceFileSource.toString(),
    userAgent = resourceFileUserAgent,
    customResources = customResourceFiles.map { ResourceDefinition(it.id, it.name, it.url) },
)

fun SubscriptionGroupState.toSubscriptionRecord() = SubscriptionRecord(
    id = id,
    title = name,
    url = url,
    userAgent = userAgent,
    updateInterval = updateInterval,
    hwid = hwid,
    deviceHeaders = deviceHeaders,
    ageSecretKey = ageSecretKey,
    updateViaProxy = updateViaProxy,
    autoOverrideRules = autoOverrideRules,
    enabled = enabled,
    builtIn = builtIn,
    metadata = SubscriptionMetadata(
        profileTitle = profileTitle.takeIf(String::isNotBlank),
        announce = announce.takeIf(String::isNotBlank),
        supportUrl = supportUrl.takeIf(String::isNotBlank),
        supportEmail = supportEmail.takeIf(String::isNotBlank),
        profileWebPageUrl = profileWebPageUrl.takeIf(String::isNotBlank),
        announceUrl = announceUrl.takeIf(String::isNotBlank),
        userInfoReceived = listOf(trafficUploadBytes, trafficDownloadBytes, trafficTotalBytes, trafficExpireAtSeconds).any { it >= 0L },
        trafficUploadBytes = trafficUploadBytes,
        trafficDownloadBytes = trafficDownloadBytes,
        trafficTotalBytes = trafficTotalBytes,
        trafficExpireAtSeconds = trafficExpireAtSeconds,
    ),
    lastUpdatedAtMillis = lastUpdatedAtMillis.takeIf { it > 0L },
    notifyOnExpiry = notifyOnExpiry,
    customExpiryReminders = customExpiryReminders,
)

fun AppState.toSubscriptionCatalog(): SubscriptionCatalog {
    val records = subscriptionGroups.map(SubscriptionGroupState::toSubscriptionRecord)
    val nextAfterRows = records.maxOfOrNull(SubscriptionRecord::id)?.saturatedIncrement() ?: 1
    return SubscriptionCatalog(
        subscriptions = records,
        nextSubscriptionId = maxOf(nextSubscriptionGroupId, nextAfterRows),
    )
}

fun SubscriptionRecord.toAndroidGroup(existing: SubscriptionGroupState?): SubscriptionGroupState {
    val metadata = metadata
    return (existing ?: SubscriptionGroupState(
        id = id,
        name = title,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        enabled = enabled,
        builtIn = builtIn,
        hwid = hwid,
        deviceHeaders = deviceHeaders,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    )).copy(
        id = id,
        name = title,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        deviceHeaders = deviceHeaders,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        enabled = enabled,
        builtIn = builtIn,
        lastUpdatedAtMillis = lastUpdatedAtMillis ?: existing?.lastUpdatedAtMillis ?: 0L,
        profileTitle = metadata?.profileTitle ?: existing?.profileTitle.orEmpty(),
        announce = metadata?.announce ?: existing?.announce.orEmpty(),
        supportUrl = metadata?.supportUrl ?: existing?.supportUrl.orEmpty(),
        supportEmail = metadata?.supportEmail ?: existing?.supportEmail.orEmpty(),
        profileWebPageUrl = metadata?.profileWebPageUrl ?: existing?.profileWebPageUrl.orEmpty(),
        announceUrl = metadata?.announceUrl ?: existing?.announceUrl.orEmpty(),
        trafficUploadBytes = metadata?.takeIf { it.userInfoReceived }?.trafficUploadBytes ?: existing?.trafficUploadBytes ?: -1L,
        trafficDownloadBytes = metadata?.takeIf { it.userInfoReceived }?.trafficDownloadBytes ?: existing?.trafficDownloadBytes ?: -1L,
        trafficTotalBytes = metadata?.takeIf { it.userInfoReceived }?.trafficTotalBytes ?: existing?.trafficTotalBytes ?: -1L,
        trafficExpireAtSeconds = metadata?.takeIf { it.userInfoReceived }?.trafficExpireAtSeconds ?: existing?.trafficExpireAtSeconds ?: -1L,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    )
}

fun TrafficConfigState.toTrafficConfigRecord() = TrafficConfigRecord(
    id = id,
    name = name,
    rawDocument = rawConfig,
    sourceUrl = sourceUrl.takeIf(String::isNotBlank),
    enabled = true,
    locked = updateLocked,
)

fun TrafficConfigRecord.toAndroidTrafficConfig(existing: TrafficConfigState?): TrafficConfigState {
    val base = existing ?: newAndroidTrafficConfig(id = id, name = name, rawConfig = rawDocument)
        .withSkipiSettingsReadFromRawConfig()
        .withSkipiSettingsInRawConfig()
    return base.copy(
        id = id,
        name = name,
        rawConfig = if (existing == null) base.rawConfig else rawDocument,
        sourceUrl = sourceUrl.orEmpty(),
        updateLocked = locked,
    )
}

fun Int.saturatedIncrement(): Int = if (this == Int.MAX_VALUE) Int.MAX_VALUE else this + 1

fun ResourceDefinition.toAndroidResource() = features.config.TrafficConfigCustomResourceFile(id = id, name = name, url = url)

fun AppState.toProxyServerCatalog(): ProxyServerCatalog = ProxyServerCatalog(
    servers = proxyServers.map(ProxyServerState::toRecord),
    nextServerId = nextProxyServerId,
    selectedServerId = selectedProxyServerId,
)

fun AppState.withProxyServerCatalog(catalog: ProxyServerCatalog): AppState {
    require(catalog.servers.all { it.id > 0 }) { "Proxy server IDs must be positive" }
    require(catalog.servers.map { it.id }.distinct().size == catalog.servers.size) {
        "Proxy server IDs must be unique"
    }
    require(catalog.nextServerId > 0) { "Next proxy server ID must be positive" }
    val previousById = proxyServers.associateBy { it.id }
    val nextServers = catalog.servers.map { record ->
        record.toAndroidState().copy(latency = previousById[record.id]?.latency.orEmpty())
    }
    val nextServerId = maxOf(
        nextProxyServerId,
        catalog.nextServerId,
        (nextServers.maxOfOrNull { it.id } ?: 0).saturatedIncrement(),
    )
    val selectedId = if (nextServers.any { it.id == catalog.selectedServerId }) {
        catalog.selectedServerId
    } else {
        nextServers.firstOrNull()?.id ?: catalog.selectedServerId
    }
    return copy(
        proxyServers = nextServers,
        nextProxyServerId = nextServerId,
        selectedProxyServerId = selectedId,
    )
}

fun ProxyServerState.toRecord(): ProxyServerRecord = ProxyServerRecord(
    id = id,
    server = server,
    sourceSubscriptionId = groupId.takeIf { it != DefaultSubscriptionGroupId },
)

fun ProxyServerRecord.toAndroidState(
    groupId: Int = sourceSubscriptionId ?: DefaultSubscriptionGroupId,
): ProxyServerState = ProxyServerState(
    id = id,
    server = server,
    groupId = groupId,
)
