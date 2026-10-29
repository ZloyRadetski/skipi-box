// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.settings.GeneralSettingsValues
import features.backup.AppBackupData
import features.backup.AppBackupFile
import features.backup.AppBackupFormat
import features.backup.AppBackupProxyServer
import features.backup.AppBackupSettings
import features.backup.AppBackupSubscriptionGroup
import features.backup.AppBackupWarning
import features.backup.CurrentAppBackupVersion
import features.backup.appBackupRestoreWarnings
import features.backup.migrateAppBackup
import features.backup.toAppBackupRouteRule
import features.backup.toAppBackupTrafficConfig
import features.backup.toRouteRule
import features.backup.toTrafficConfigState
import features.config.ConfigProfile
import features.config.ConfigProfileLibraries
import features.config.ConfigProfileLibrary
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig
import features.proxy.server.model.PersistedProxyServer
import features.proxy.server.model.decodeProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.model.toPersistedProxyServer
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.StoredSubscription
import features.subscription.StoredSubscriptionMetadata
import features.subscription.SubscriptionProviderLibrary
import platform.DefaultLocalSocksPort

/** Every Desktop store a backup covers, restored or reset together. */
internal data class DesktopUserData(
    val settings: DesktopAppSettings = DesktopAppSettings(),
    val servers: DesktopServerLibrary = DesktopServerLibrary(),
    val subscriptions: SubscriptionProviderLibrary = SubscriptionProviderLibrary(),
    val configs: ConfigProfileLibrary = ConfigProfileLibrary(),
)

internal data class DesktopRestorePreview(
    val userData: DesktopUserData,
    val warnings: List<AppBackupWarning>,
    val backupVersion: Int = CurrentAppBackupVersion,
    val appVersionName: String = "",
    val createdAtMillis: Long = 0L,
) {
    val routeRuleCount: Int get() = userData.settings.routeRules.size
    val serverCount: Int get() = userData.servers.servers.size
    val subscriptionCount: Int get() = userData.subscriptions.subscriptions.size
    val profileCount: Int get() = userData.configs.configs.size
}

private const val AutoBalancerGroupId = -2

/** Back to a first run: all user data cleared, while this installation's identity and onboarding stay. */
internal fun DesktopUserData.factoryReset(): DesktopUserData = DesktopUserData(
    settings = DesktopAppSettings().copy(
        installationUuid = settings.installationUuid,
        onboardingCompleted = settings.onboardingCompleted,
    ),
)

/**
 * Desktop stores the manual group as no subscription (null) and subscription ids from 1, whereas
 * the shared backup reserves group id 1 for the built-in manual group. A Desktop subscription
 * that uses id 1 is exported (and so restored) under a fresh id; its servers follow it.
 */
internal fun DesktopUserData.toAppBackupFile(
    createdAtMillis: Long,
    appVersionName: String,
    appVersionCode: Int,
): AppBackupFile {
    val storedSubscriptions = subscriptions.subscriptions
    var nextFreeId = maxOf(
        storedSubscriptions.maxOfOrNull(StoredSubscription::id) ?: 0,
        DefaultSubscriptionGroupId,
    ) + 1
    val exportedIds = storedSubscriptions.associate { subscription ->
        subscription.id to if (subscription.id == DefaultSubscriptionGroupId || subscription.id == AutoBalancerGroupId) {
            nextFreeId++
        } else {
            subscription.id
        }
    }
    val groups = buildList {
        add(AppBackupSubscriptionGroup(id = DefaultSubscriptionGroupId, name = "Default", builtIn = true))
        storedSubscriptions.forEach { add(it.toBackupGroup(exportedIds.getValue(it.id))) }
    }
    val exportedServers = servers.servers.mapNotNull { stored ->
        val decoded = stored.decode().getOrNull() ?: return@mapNotNull null
        val persisted = decoded.toPersistedProxyServer()
        AppBackupProxyServer(
            id = stored.id,
            groupId = when (val subscriptionId = stored.subscriptionId) {
                null -> DefaultSubscriptionGroupId
                AutoBalancerGroupId -> AutoBalancerGroupId
                else -> exportedIds[subscriptionId] ?: DefaultSubscriptionGroupId
            },
            protocol = persisted.protocol,
            payload = persisted.payload,
        )
    }
    return AppBackupFile(
        format = AppBackupFormat,
        version = CurrentAppBackupVersion,
        createdAtMillis = createdAtMillis,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        data = AppBackupData(
            settings = settings.toBackupSettings(selectedServerId = servers.selectedServerId),
            subscriptionGroups = groups,
            proxyServers = exportedServers,
            routeRules = settings.routeRules.map { it.toAppBackupRouteRule() },
            trafficConfigs = configs.configs.map { profile ->
                TrafficConfigState(
                    id = profile.id,
                    name = profile.name,
                    rawConfig = profile.content,
                    sourceUrl = profile.sourceUrl,
                    updateLocked = profile.updateLocked,
                    lastUpdatedAtMillis = profile.lastUpdatedAtMillis,
                ).withSkipiSettingsReadFromRawConfig().toAppBackupTrafficConfig()
            },
            activeTrafficConfigId = configs.selectedConfigId ?: 0,
        ),
    )
}

/** Validates and migrates the backup, then maps it onto Desktop's stores (current identity is kept). */
internal fun AppBackupFile.toDesktopRestorePreview(current: DesktopUserData): DesktopRestorePreview {
    val migrated = migrateAppBackup()
    val data = migrated.data
    val groups = data.subscriptionGroups.filter { !it.builtIn && it.id != DefaultSubscriptionGroupId }
    val groupIds = groups.mapTo(mutableSetOf()) { it.id }

    val restoredServers = data.proxyServers.mapNotNull { backup ->
        val decoded = runCatching {
            PersistedProxyServer(protocol = backup.protocol, payload = backup.payload).decodeProxyServer()
        }.getOrNull() ?: return@mapNotNull null
        val stored = DesktopStoredProxyServer(
            id = backup.id,
            serverJson = decoded.encodePersistedProxyServer(),
            subscriptionId = when {
                backup.groupId == AutoBalancerGroupId -> AutoBalancerGroupId
                backup.groupId in groupIds -> backup.groupId
                else -> null
            },
        )
        stored to decoded
    }
    val selectedServerId = data.settings.selectedProxyServerId
        .takeIf { id -> restoredServers.any { (stored, _) -> stored.id == id } }
        ?: restoredServers.firstOrNull()?.first?.id

    val subscriptions = groups.map { it.toStoredSubscription() }
    val profiles = data.trafficConfigs.map { backup ->
        val state = backup.toTrafficConfigState().withSkipiSettingsInRawConfig()
        ConfigProfile(
            id = state.id,
            name = state.name,
            content = state.rawConfig,
            sourceUrl = state.sourceUrl,
            updateLocked = state.updateLocked,
            lastUpdatedAtMillis = state.lastUpdatedAtMillis,
        )
    }
    val activeConfigId = data.activeTrafficConfigId.takeIf { id -> profiles.any { it.id == id } }

    val userData = DesktopUserData(
        settings = data.settings.toDesktopSettings(current.settings, data),
        servers = DesktopServerLibrary(
            selectedServerId = selectedServerId,
            servers = restoredServers.map { it.first },
        ),
        subscriptions = SubscriptionProviderLibrary(
            subscriptions = subscriptions,
            nextSubscriptionId = (subscriptions.maxOfOrNull(StoredSubscription::id) ?: 0).plus(1).coerceAtLeast(2),
        ),
        configs = ConfigProfileLibraries.normalize(
            ConfigProfileLibrary(selectedConfigId = activeConfigId, configs = profiles),
        ),
    )
    return DesktopRestorePreview(
        userData = userData,
        warnings = appBackupRestoreWarnings(restoredServers.map { (stored, server) -> stored.id to server }),
        backupVersion = migrated.version,
        appVersionName = migrated.appVersionName,
        createdAtMillis = migrated.createdAtMillis,
    )
}

private fun StoredSubscription.toBackupGroup(exportedId: Int) = AppBackupSubscriptionGroup(
    id = exportedId,
    name = name,
    url = url,
    userAgent = userAgent,
    updateInterval = updateInterval,
    hwid = hwid,
    ageSecretKey = ageSecretKey,
    updateViaProxy = updateViaProxy,
    autoOverrideRules = autoOverrideRules,
    enabled = enabled,
    builtIn = false,
    lastUpdatedAtMillis = metadata.lastUpdatedAtMillis,
    profileTitle = metadata.profileTitle.orEmpty(),
    announce = metadata.announce,
    supportUrl = metadata.supportUrl,
    supportEmail = metadata.supportEmail,
    profileWebPageUrl = metadata.profileWebPageUrl,
    announceUrl = metadata.announceUrl,
    trafficUploadBytes = metadata.trafficUploadBytes,
    trafficDownloadBytes = metadata.trafficDownloadBytes,
    trafficTotalBytes = metadata.trafficTotalBytes,
    trafficExpireAtSeconds = metadata.trafficExpireAtSeconds,
    notifyOnExpiry = notifyOnExpiry,
    customExpiryReminders = customExpiryReminders,
)

private fun AppBackupSubscriptionGroup.toStoredSubscription() = StoredSubscription(
    id = id,
    url = url,
    userAgent = userAgent,
    name = name,
    metadata = StoredSubscriptionMetadata(
        profileTitle = profileTitle.takeIf(String::isNotBlank),
        announce = announce,
        supportUrl = supportUrl,
        supportEmail = supportEmail,
        profileWebPageUrl = profileWebPageUrl,
        announceUrl = announceUrl,
        trafficUploadBytes = trafficUploadBytes,
        trafficDownloadBytes = trafficDownloadBytes,
        trafficTotalBytes = trafficTotalBytes,
        trafficExpireAtSeconds = trafficExpireAtSeconds,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    ),
    enabled = enabled,
    updateInterval = updateInterval,
    hwid = hwid,
    ageSecretKey = ageSecretKey,
    updateViaProxy = updateViaProxy,
    autoOverrideRules = autoOverrideRules,
    builtIn = false,
    notifyOnExpiry = notifyOnExpiry,
    customExpiryReminders = customExpiryReminders,
)

private fun DesktopAppSettings.toBackupSettings(selectedServerId: Int?): AppBackupSettings {
    val base = AppBackupSettings()
    return base.copy(
        languageMode = languageTag.toLanguageMode(),
        seedIndex = seedIndex,
        customMaterialYouSeed = customMaterialYouSeed,
        enableCustomColors = customColorsEnabled,
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
        fontFamilyMode = fontFamilyMode ?: base.fontFamilyMode,
        fontSizeMode = fontSizeMode ?: base.fontSizeMode,
        fontWeightMode = fontWeightMode ?: base.fontWeightMode,
        backgroundStyle = backgroundStyle ?: base.backgroundStyle,
        backgroundPhotoDimPercent = backgroundPhotoDimPercent ?: base.backgroundPhotoDimPercent,
        connectionDisplayMode = connectionDisplayMode ?: base.connectionDisplayMode,
        enableAllProxyGroup = enableAllProxyGroup,
        enableDeletionConfirmation = confirmDeletion,
        localProxyPort = localProxyPortRaw ?: localProxyPort.toString(),
        enableDynamicLocalProxyPort = enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = localProxyListenAddress == ListenAllInterfaces,
        enableLocalProxyAuth = enableLocalProxyAuth,
        localProxyUsername = localProxyUsername,
        localProxyPassword = localProxyPassword,
        enableNetworkAutomation = enableNetworkAutomation,
        enableOnDemandVpn = enableOnDemandVpn,
        networkAutomationRules = networkAutomationRules,
        selectedProxyServerId = selectedServerId ?: base.selectedProxyServerId,
        proxyServerListSort = proxyServerListSort,
        subscriptionPingMode = subscriptionPingMode,
        subscriptionPingUrl = subscriptionPingUrl,
        subscriptionPingTimeoutMillis = subscriptionPingTimeoutMillis,
        subscriptionPingConcurrency = subscriptionPingConcurrency,
        subscriptionUserAgents = subscriptionUserAgents,
        enableSubscriptionDeviceHeaders = sendDeviceHeaders,
        subscriptionFetchTimeoutSeconds = subscriptionFetchTimeoutSeconds,
        routeDomainStrategy = routeDomainStrategy,
        defaultRouteOutboundTag = defaultRouteOutboundTag,
        coreLogLevel = DesktopCoreLogLevels.indexOf(coreLogLevel).takeIf { it >= 0 } ?: base.coreLogLevel,
        enableAccessLog = enableAccessLog,
        logRetentionDays = logRetentionDays,
        enableSniffing = enableSniffing,
        enableSniffingRouteOnly = enableSniffingRouteOnly,
        enableMux = enableMux,
        muxConcurrency = muxConcurrency,
        muxXudpConcurrency = muxXudpConcurrency,
        muxXudpProxyUdp443 = muxXudpProxyUdp443,
        enableFragment = enableFragment,
        fragmentPackets = fragmentPackets,
        fragmentLength = fragmentLength,
        fragmentInterval = fragmentInterval,
        showServerSearch = showServerSearch,
        pinConnectionPanelOnHome = pinConnectionPanelOnHome,
        enableSubscriptionSwipe = enableSubscriptionSwipe,
        enableHaptics = hapticsEnabled,
        classicShowFloatingPowerButton = classicShowFloatingPowerButton,
        showTunnelMemoryOnHome = showTunnelMemory,
        enableSubscriptionExpiryNotifications = enableSubscriptionExpiryNotifications,
        subscriptionExpiryReminders = subscriptionExpiryReminders,
    )
}

/** Backup values replace Desktop's mapped fields; everything Android-only is ignored and the install's identity stays. */
private fun AppBackupSettings.toDesktopSettings(current: DesktopAppSettings, data: AppBackupData): DesktopAppSettings {
    val routeRules = data.routeRules.map { it.toRouteRule() }
    return current.copy(
        languageTag = languageMode.toLanguageTag(),
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
        connectionDisplayMode = connectionDisplayMode,
        enableAllProxyGroup = enableAllProxyGroup,
        confirmDeletion = enableDeletionConfirmation,
        localProxyPortRaw = localProxyPort,
        localProxyPort = localProxyPort.toIntOrNull()?.takeIf { it in 1..65_535 } ?: DefaultLocalSocksPort,
        enableDynamicLocalProxyPort = enableDynamicLocalProxyPort,
        localProxyListenAddress = if (localProxyListenAllInterfaces) ListenAllInterfaces else ListenLoopback,
        enableLocalProxyAuth = enableLocalProxyAuth,
        localProxyUsername = localProxyUsername,
        localProxyPassword = localProxyPassword,
        enableNetworkAutomation = enableNetworkAutomation,
        enableOnDemandVpn = enableOnDemandVpn,
        networkAutomationRules = networkAutomationRules,
        proxyServerListSort = proxyServerListSort,
        subscriptionPingMode = subscriptionPingMode,
        subscriptionPingUrl = subscriptionPingUrl,
        subscriptionPingTimeoutMillis = subscriptionPingTimeoutMillis,
        subscriptionPingConcurrency = subscriptionPingConcurrency,
        subscriptionUserAgents = subscriptionUserAgents,
        sendDeviceHeaders = enableSubscriptionDeviceHeaders,
        subscriptionFetchTimeoutSeconds = subscriptionFetchTimeoutSeconds.coerceIn(10, 120),
        routeDomainStrategy = routeDomainStrategy,
        defaultRouteOutboundTag = defaultRouteOutboundTag,
        routeRules = routeRules,
        nextRouteRuleId = maxOf(current.nextRouteRuleId, (routeRules.maxOfOrNull { it.id } ?: 0) + 1),
        coreLogLevel = DesktopCoreLogLevels.getOrNull(coreLogLevel) ?: "error",
        enableAccessLog = enableAccessLog,
        logRetentionDays = logRetentionDays.takeIf { it in DesktopLogRetentionDays } ?: DefaultLogRetentionDays,
        enableSniffing = enableSniffing,
        enableSniffingRouteOnly = enableSniffingRouteOnly,
        enableMux = enableMux,
        muxConcurrency = muxConcurrency,
        muxXudpConcurrency = muxXudpConcurrency,
        muxXudpProxyUdp443 = muxXudpProxyUdp443,
        enableFragment = enableFragment,
        fragmentPackets = fragmentPackets,
        fragmentLength = fragmentLength,
        fragmentInterval = fragmentInterval,
        showServerSearch = showServerSearch,
        pinConnectionPanelOnHome = pinConnectionPanelOnHome,
        enableSubscriptionSwipe = enableSubscriptionSwipe,
        hapticsEnabled = enableHaptics,
        classicShowFloatingPowerButton = classicShowFloatingPowerButton,
        showTunnelMemory = showTunnelMemoryOnHome,
        enableSubscriptionExpiryNotifications = enableSubscriptionExpiryNotifications,
        subscriptionExpiryReminders = subscriptionExpiryReminders,
    ).normalized()
}

private const val ListenAllInterfaces = "0.0.0.0"
private const val ListenLoopback = "127.0.0.1"

private fun String?.toLanguageMode(): Int = when (this?.substringBefore('-')?.lowercase()) {
    "en" -> GeneralSettingsValues.LanguageEnglish
    "zh" -> GeneralSettingsValues.LanguageChinese
    "ru" -> GeneralSettingsValues.LanguageRussian
    "fa" -> GeneralSettingsValues.LanguagePersian
    else -> GeneralSettingsValues.LanguageSystem
}

private fun Int.toLanguageTag(): String? = when (this) {
    GeneralSettingsValues.LanguageEnglish -> "en"
    GeneralSettingsValues.LanguageChinese -> "zh"
    GeneralSettingsValues.LanguageRussian -> "ru"
    GeneralSettingsValues.LanguagePersian -> "fa"
    else -> null
}
