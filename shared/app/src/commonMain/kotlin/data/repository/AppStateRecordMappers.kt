// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import features.subscription.DefaultSubscriptionGroupId
import app.ProxyServerState
import app.AppState
import app.SubscriptionGroupState
import app.skipi.app.model.ApplicationPreferences
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
fun AppState.toPersistedSettings() = app.skipi.app.model.PersistedSettings(
    appearance = AppearanceSettings(
        themeMode = when (colorMode) {
            app.modes.ColorModeLight -> ThemeMode.Light
            app.modes.ColorModeDark -> ThemeMode.Dark
            app.modes.ColorModeAmoled -> ThemeMode.Dark
            in app.modes.ColorModeAurora..app.modes.ColorModeSunset -> ThemeMode.Named
            else -> ThemeMode.System
        },
        dynamicColors = enableMaterialYou,
        amoledBlack = colorMode == app.modes.ColorModeAmoled,
        themeVariant = when (colorMode) {
            app.modes.ColorModeAurora -> "aurora"
            app.modes.ColorModeSakura -> "sakura"
            app.modes.ColorModeForest -> "forest"
            app.modes.ColorModeSunset -> "sunset"
            else -> null
        },
        seedIndex = seedIndex,
        customMaterialYouSeed = customMaterialYouSeed,
        customColorsEnabled = enableCustomColors,
        customAccentColor = customAccentColor,
        customBackgroundColor = customBackgroundColor,
        customSurfaceColor = customSurfaceColor,
        customSurfaceVariantColor = customSurfaceVariantColor,
        customTextColor = customTextColor,
        customTextSecondaryColor = customTextSecondaryColor,
        customStatusRunningColor = customStatusRunningColor,
        customStatusStoppedColor = customStatusStoppedColor,
        customPingFastColor = customPingFastColor,
        customPingMediumColor = customPingMediumColor,
        customPingSlowColor = customPingSlowColor,
        customCategoryIconColor = customCategoryIconColor,
        customProtocolVlessColor = customProtocolVlessColor,
        customProtocolVmessColor = customProtocolVmessColor,
        customProtocolHysteria2Color = customProtocolHysteria2Color,
        customProtocolTrojanColor = customProtocolTrojanColor,
        customProtocolShadowsocksColor = customProtocolShadowsocksColor,
        customProtocolWireguardColor = customProtocolWireguardColor,
        customProtocolSocksColor = customProtocolSocksColor,
        customProtocolHttpColor = customProtocolHttpColor,
        customProtocolStrategyColor = customProtocolStrategyColor,
        customProtocolChainColor = customProtocolChainColor,
        customProtocolJsonColor = customProtocolJsonColor,
        fontFamilyMode = fontFamilyMode,
        fontSizeMode = fontSizeMode,
        fontWeightMode = fontWeightMode,
        backgroundStyle = backgroundStyle,
        backgroundPhotoDimPercent = backgroundPhotoDimPercent,
        bottomBarSize = bottomBarSize,
    ),
    proxy = ProxySelectionSettings(selectedServerId = selectedProxyServerId),
    home = HomeDisplaySettings(
        connectionDisplayMode = connectionDisplayMode,
        pinConnectionPanel = pinConnectionPanelOnHome,
        subscriptionSwipeEnabled = enableSubscriptionSwipe,
        showFloatingPowerButton = classicShowFloatingPowerButton,
        confirmDeletion = enableDeletionConfirmation,
        showServerSearch = showServerSearch,
        proxyServerListSort = proxyServerListSort,
        proxyServerListLayout = proxyServerListLayout,
        enableAllProxyGroup = enableAllProxyGroup,
        showTunnelMemoryOnHome = showTunnelMemoryOnHome,
        showTunnelIpOnHome = showTunnelIpOnHome,
    ),
    traffic = TrafficSelectionSettings(activeConfigId = activeTrafficConfigId),
    application = ApplicationPreferences(
        languageTag = when (languageMode) {
            app.modes.LanguageModeEnglish -> "en"
            app.modes.LanguageModeChinese -> "zh"
            app.modes.LanguageModeRussian -> "ru"
            app.modes.LanguageModePersian -> "fa"
            else -> null
        },
        hapticsEnabled = enableHaptics,
        onboardingCompleted = hasCompletedOnboarding,
    ),
    localProxy = LocalProxyPreferences(
        port = localProxyPort,
        dynamicPort = enableDynamicLocalProxyPort,
        listenAllInterfaces = localProxyListenAllInterfaces,
        authenticationEnabled = enableLocalProxyAuth,
        username = localProxyUsername,
        password = localProxyPassword,
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
)

/** Applies shared settings while leaving host-only and absent persisted fields untouched. */
fun AppState.applyPersistedSettings(settings: app.skipi.app.model.PersistedSettings): AppState {
    val languageTag = settings.application.languageTag
    val nextActiveConfigId = settings.traffic.activeConfigId ?: activeTrafficConfigId
    val nextColorMode = when {
        settings.appearance.amoledBlack -> app.modes.ColorModeAmoled
        settings.appearance.themeMode == ThemeMode.Light -> app.modes.ColorModeLight
        settings.appearance.themeMode == ThemeMode.Dark -> app.modes.ColorModeDark
        settings.appearance.themeMode == ThemeMode.System -> app.modes.ColorModeSystem
        else -> when (settings.appearance.themeVariant) {
            "aurora" -> app.modes.ColorModeAurora
            "sakura" -> app.modes.ColorModeSakura
            "forest" -> app.modes.ColorModeForest
            "sunset" -> app.modes.ColorModeSunset
            else -> colorMode // Preserve a named theme when older shared data has no variant key.
        }
    }
    val nextLanguageMode = when {
        languageTag == null -> app.modes.LanguageModeSystem
        else -> languageModeForTag(languageTag) ?: languageMode
    }
    val updated = copy(
        colorMode = nextColorMode,
        enableMaterialYou = settings.appearance.dynamicColors,
        connectionDisplayMode = settings.home.connectionDisplayMode ?: connectionDisplayMode,
        pinConnectionPanelOnHome = settings.home.pinConnectionPanel,
        enableSubscriptionSwipe = settings.home.subscriptionSwipeEnabled,
        classicShowFloatingPowerButton = settings.home.showFloatingPowerButton,
        enableDeletionConfirmation = settings.home.confirmDeletion,
        showServerSearch = settings.home.showServerSearch,
        proxyServerListSort = settings.home.proxyServerListSort ?: proxyServerListSort,
        proxyServerListLayout = settings.home.proxyServerListLayout ?: proxyServerListLayout,
        enableAllProxyGroup = settings.home.enableAllProxyGroup ?: enableAllProxyGroup,
        showTunnelMemoryOnHome = settings.home.showTunnelMemoryOnHome ?: showTunnelMemoryOnHome,
        showTunnelIpOnHome = settings.home.showTunnelIpOnHome ?: showTunnelIpOnHome,
        seedIndex = settings.appearance.seedIndex,
        customMaterialYouSeed = settings.appearance.customMaterialYouSeed,
        enableCustomColors = settings.appearance.customColorsEnabled,
        customAccentColor = settings.appearance.customAccentColor,
        customBackgroundColor = settings.appearance.customBackgroundColor,
        customSurfaceColor = settings.appearance.customSurfaceColor,
        customSurfaceVariantColor = settings.appearance.customSurfaceVariantColor,
        customTextColor = settings.appearance.customTextColor,
        customTextSecondaryColor = settings.appearance.customTextSecondaryColor,
        customStatusRunningColor = settings.appearance.customStatusRunningColor,
        customStatusStoppedColor = settings.appearance.customStatusStoppedColor,
        customPingFastColor = settings.appearance.customPingFastColor,
        customPingMediumColor = settings.appearance.customPingMediumColor,
        customPingSlowColor = settings.appearance.customPingSlowColor,
        customCategoryIconColor = settings.appearance.customCategoryIconColor,
        customProtocolVlessColor = settings.appearance.customProtocolVlessColor,
        customProtocolVmessColor = settings.appearance.customProtocolVmessColor,
        customProtocolHysteria2Color = settings.appearance.customProtocolHysteria2Color,
        customProtocolTrojanColor = settings.appearance.customProtocolTrojanColor,
        customProtocolShadowsocksColor = settings.appearance.customProtocolShadowsocksColor,
        customProtocolWireguardColor = settings.appearance.customProtocolWireguardColor,
        customProtocolSocksColor = settings.appearance.customProtocolSocksColor,
        customProtocolHttpColor = settings.appearance.customProtocolHttpColor,
        customProtocolStrategyColor = settings.appearance.customProtocolStrategyColor,
        customProtocolChainColor = settings.appearance.customProtocolChainColor,
        customProtocolJsonColor = settings.appearance.customProtocolJsonColor,
        fontFamilyMode = settings.appearance.fontFamilyMode ?: fontFamilyMode,
        fontSizeMode = settings.appearance.fontSizeMode ?: fontSizeMode,
        fontWeightMode = settings.appearance.fontWeightMode ?: fontWeightMode,
        backgroundStyle = settings.appearance.backgroundStyle ?: backgroundStyle,
        backgroundPhotoDimPercent = settings.appearance.backgroundPhotoDimPercent ?: backgroundPhotoDimPercent,
        bottomBarSize = settings.appearance.bottomBarSize ?: bottomBarSize,
        selectedProxyServerId = settings.proxy.selectedServerId ?: selectedProxyServerId,
        enableHaptics = settings.application.hapticsEnabled,
        hasCompletedOnboarding = settings.application.onboardingCompleted,
        languageMode = nextLanguageMode,
        localProxyPort = settings.localProxy?.port ?: localProxyPort,
        enableDynamicLocalProxyPort = settings.localProxy?.dynamicPort ?: enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = settings.localProxy?.listenAllInterfaces ?: localProxyListenAllInterfaces,
        enableLocalProxyAuth = settings.localProxy?.authenticationEnabled ?: enableLocalProxyAuth,
        localProxyUsername = settings.localProxy?.username ?: localProxyUsername,
        localProxyPassword = settings.localProxy?.password ?: localProxyPassword,
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

private fun languageModeForTag(tag: String): Int? = when (tag.lowercase().substringBefore('-')) {
    "en" -> app.modes.LanguageModeEnglish
    "zh" -> app.modes.LanguageModeChinese
    "ru" -> app.modes.LanguageModeRussian
    "fa" -> app.modes.LanguageModePersian
    else -> null
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
