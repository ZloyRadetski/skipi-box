// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import app.AppState
import app.SubscriptionGroupState
import app.skipi.app.model.ApplicationPreferences
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.HomeDisplaySettings
import app.skipi.app.model.ProxySelectionSettings
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.ResourceCatalogRecord
import app.skipi.app.model.ResourceDefinition
import app.skipi.app.model.RoutingConfigRecord
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
import app.skipi.app.runtime.AppRuntimeState
import app.skipi.app.runtime.RuntimeMessage
import app.skipi.app.proxy.ProxyServerRecord as CollectionProxyServerRecord
import app.skipi.app.proxy.deleteProxyServerRecords
import data.AndroidAppStateStore
import features.config.TrafficConfigState
import features.config.newAndroidTrafficConfig
import features.config.withActiveTrafficConfigProfile
import features.config.withConfigProxyGroupsReflected
import features.config.withUpdatedTrafficConfigProfile
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig
import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import platform.TunnelFailure
import platform.TunnelPhase
import platform.TunnelSnapshot

/** Builds shared repository contracts over Android's existing Room and SharedPreferences store. */
class AndroidAppRepositories(
    private val stateStore: AndroidAppStateStore,
    scope: CoroutineScope,
    subscriptionRefresh: (suspend (Int) -> Result<SubscriptionRecord>)? = null,
    hostRuntime: RuntimeStateRepository? = null,
) {
    val settings: SettingsRepository = AndroidSettingsRepository(stateStore, scope)
    val proxyServers: AndroidProxyServerRepository = stateStore.proxyServerRepository
    val subscriptions: SubscriptionRepository = AndroidSubscriptionRepository(
        stateStore = stateStore,
        scope = scope,
        subscriptionRefresh = subscriptionRefresh,
    )
    val trafficConfigs: TrafficConfigRepository = AndroidTrafficConfigRepository(stateStore, scope)
    val routing: RoutingRepository = AndroidRoutingRepository(stateStore, scope)
    val resources: ResourceRepository = AndroidResourceRepository(stateStore, scope)
    /** The host injects its real lifecycle bridge before shared runtime consumers are used. */
    val runtime: RuntimeStateRepository = hostRuntime ?: UnavailableAndroidRuntimeStateRepository

    val contracts = AppRepositories(
        settings = settings,
        proxyServers = proxyServers,
        subscriptions = subscriptions,
        trafficConfigs = trafficConfigs,
        runtime = runtime,
        routing = routing,
        resources = resources,
    )
}

/** Rebuilds config-derived proxy groups through the existing atomic catalog repository. */
internal suspend fun AndroidAppStateStore.reconcileTrafficConfigProxyGroups() {
    proxyServerRepository.updateCatalog { catalog ->
        currentState.withConfigProxyGroupsReflected(catalog)
    }
}

private class AndroidSettingsRepository(
    private val store: AndroidAppStateStore,
    scope: CoroutineScope,
) : SettingsRepository {
    override val state: StateFlow<app.skipi.app.model.PersistedSettings> = store.state
        .map(AppState::toPersistedSettings)
        .stateIn(scope, SharingStarted.Eagerly, store.currentState.toPersistedSettings())

    override suspend fun update(transform: (app.skipi.app.model.PersistedSettings) -> app.skipi.app.model.PersistedSettings) {
        store.update { current ->
            val previous = current.toPersistedSettings()
            val updated = current.applyPersistedSettings(transform(previous))
            updated.withProxyServerCatalog(
                current.toProxyServerCatalog().copy(selectedServerId = updated.selectedProxyServerId),
            )
        }
    }
}

private class AndroidSubscriptionRepository(
    private val stateStore: AndroidAppStateStore,
    scope: CoroutineScope,
    private val subscriptionRefresh: (suspend (Int) -> Result<SubscriptionRecord>)?,
) : SubscriptionRepository {
    override val subscriptions: StateFlow<List<SubscriptionRecord>> = stateStore.state
        .map { state -> state.subscriptionGroups.map(SubscriptionGroupState::toSubscriptionRecord) }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            stateStore.currentState.subscriptionGroups.map(SubscriptionGroupState::toSubscriptionRecord),
        )

    override suspend fun upsert(subscription: SubscriptionRecord) {
        stateStore.update { state ->
            val previous = state.subscriptionGroups.firstOrNull { it.id == subscription.id }
            val next = subscription.toAndroidGroup(previous)
            state.copy(
                subscriptionGroups = if (previous == null) state.subscriptionGroups + next
                else state.subscriptionGroups.map { if (it.id == subscription.id) next else it },
                nextSubscriptionGroupId = maxOf(state.nextSubscriptionGroupId, subscription.id + 1),
            )
        }
    }

    override suspend fun remove(subscriptionId: Int) {
        stateStore.update { state ->
            val group = state.subscriptionGroups.firstOrNull { it.id == subscriptionId }
                ?: return@update state
            require(!group.builtIn) { "Built-in subscription groups cannot be removed" }
            val removedServerIds = state.proxyServers.filter { it.groupId == subscriptionId }.mapTo(hashSetOf()) { it.id }
            val collection = deleteProxyServerRecords(
                servers = state.proxyServers.map { CollectionProxyServerRecord(it.id, it.groupId, it.server, it.latency) },
                deletedServerIds = removedServerIds,
                nextServerId = state.nextProxyServerId,
                selectedServerId = state.selectedProxyServerId,
                proxyRunning = state.proxyRunning,
            )
            val catalogUpdated = state.withProxyServerCatalog(
                ProxyServerCatalog(
                    servers = collection.servers.map { row ->
                        ProxyServerRecord(
                            id = row.id,
                            server = row.server,
                            sourceSubscriptionId = row.groupId.takeIf { it != features.subscription.DefaultSubscriptionGroupId },
                        )
                    },
                    nextServerId = collection.nextServerId,
                    selectedServerId = collection.selectedServerId,
                ),
            )
            catalogUpdated.copy(
                subscriptionGroups = state.subscriptionGroups.filterNot { it.id == subscriptionId },
                proxyRunning = collection.proxyRunning,
            )
        }
    }

    override suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord> {
        val refresh = subscriptionRefresh
            ?: return Result.failure(UnsupportedOperationException("Android subscription refresh is not wired"))
        val refreshed = refresh(subscriptionId).getOrElse { return Result.failure(it) }
        if (refreshed.id != subscriptionId) {
            return Result.failure(
                IllegalArgumentException(
                    "Subscription refresh for ID $subscriptionId returned ID ${refreshed.id}",
                ),
            )
        }
        upsert(refreshed)
        return Result.success(refreshed)
    }
}

private class AndroidTrafficConfigRepository(
    private val store: AndroidAppStateStore,
    scope: CoroutineScope,
) : TrafficConfigRepository {
    override val configs: StateFlow<List<TrafficConfigRecord>> = store.state
        .map { state -> state.trafficConfigs.map(TrafficConfigState::toTrafficConfigRecord) }
        .stateIn(scope, SharingStarted.Eagerly, store.currentState.trafficConfigs.map(TrafficConfigState::toTrafficConfigRecord))

    override suspend fun upsert(config: TrafficConfigRecord) {
        require(config.enabled) { "Android traffic profiles do not support disabling a profile" }
        require(config.id > 0) { "Traffic config ID must be positive" }
        store.update { state ->
            val old = state.trafficConfigs.firstOrNull { it.id == config.id }
            val next = config.toAndroidTrafficConfig(old)
            val updated = if (old != null) {
                state.withUpdatedTrafficConfigProfile(config.id) { next }
            } else {
                state.copy(trafficConfigs = state.trafficConfigs + next)
            }
            updated.copy(
                nextTrafficConfigId = maxOf(updated.nextTrafficConfigId, config.id.saturatedIncrement()),
            )
        }
        store.proxyServerRepository.updateCatalog { current ->
            store.currentState.withConfigProxyGroupsReflected(current)
        }
    }

    override suspend fun remove(configId: Int) {
        store.update { state ->
            val nextConfigs = state.trafficConfigs.filterNot { it.id == configId }
            if (nextConfigs.size == state.trafficConfigs.size || state.trafficConfigs.size <= 1) return@update state
            val nextActiveConfigId = if (state.activeTrafficConfigId == configId) {
                nextConfigs.first().id
            } else state.activeTrafficConfigId
            state.copy(
                trafficConfigs = nextConfigs,
                activeTrafficConfigId = nextActiveConfigId,
            )
        }
        store.proxyServerRepository.updateCatalog { current ->
            store.currentState.withConfigProxyGroupsReflected(current)
        }
    }
}

private class AndroidRoutingRepository(
    private val store: AndroidAppStateStore,
    scope: CoroutineScope,
) : RoutingRepository {
    override val state: StateFlow<RoutingConfigRecord> = store.state
        .map(AppState::toRoutingConfigRecord)
        .stateIn(scope, SharingStarted.Eagerly, store.currentState.toRoutingConfigRecord())

    override suspend fun update(transform: (RoutingConfigRecord) -> RoutingConfigRecord) {
        store.update { current ->
            val updated = transform(current.toRoutingConfigRecord())
            current.copy(
                routeDomainStrategy = updated.domainStrategy,
                defaultRouteOutboundTag = updated.defaultOutboundTag,
                routeRules = updated.rules,
                nextRouteRuleId = maxOf(current.nextRouteRuleId, (updated.rules.maxOfOrNull { it.id } ?: 0) + 1),
            )
        }
    }
}

private class AndroidResourceRepository(
    private val store: AndroidAppStateStore,
    scope: CoroutineScope,
) : ResourceRepository {
    override val state: StateFlow<ResourceCatalogRecord> = store.state
        .map(AppState::toResourceCatalogRecord)
        .stateIn(scope, SharingStarted.Eagerly, store.currentState.toResourceCatalogRecord())

    override suspend fun update(transform: (ResourceCatalogRecord) -> ResourceCatalogRecord) {
        store.update { current ->
            val updated = transform(current.toResourceCatalogRecord())
            val sourceId = updated.sourceId.toIntOrNull()
                ?: throw IllegalArgumentException("Android resource source must be an integer ID")
            current.copy(
                resourceFileSource = sourceId,
                resourceFileUserAgent = updated.userAgent ?: current.resourceFileUserAgent,
                customResourceFiles = updated.customResources.map { it.toAndroidResource() },
                nextCustomResourceFileId = maxOf(
                    current.nextCustomResourceFileId,
                    (updated.customResources.maxOfOrNull(ResourceDefinition::id) ?: 0) + 1,
                ),
            )
        }
    }
}

/** Explicit failure until the Android composition root supplies its host-owned tunnel bridge. */
private object UnavailableAndroidRuntimeStateRepository : RuntimeStateRepository {
    private const val unavailableMessage =
        "Android tunnel runtime is host-owned; inject a repository backed by AndroidTunnelController."
    private val mutableState = MutableStateFlow(
        AppRuntimeState(
            tunnel = TunnelSnapshot(
                phase = TunnelPhase.Failed,
                failure = TunnelFailure(
                    code = "android_runtime_unavailable",
                    message = unavailableMessage,
                    recoverable = true,
                ),
            ),
            message = RuntimeMessage(unavailableMessage),
        ),
    )
    override val state: StateFlow<AppRuntimeState> = mutableState.asStateFlow()

    override suspend fun update(transform: (AppRuntimeState) -> AppRuntimeState) {
        throw UnsupportedOperationException("Android runtime state is owned by the host tunnel controller")
    }
}

internal fun AppState.toPersistedSettings() = app.skipi.app.model.PersistedSettings(
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
)

/** Writes only fields represented without loss; Android-only preferences stay in AppState. */
internal fun AppState.applyPersistedSettings(settings: app.skipi.app.model.PersistedSettings): AppState {
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

internal fun AppState.toRoutingConfigRecord() = RoutingConfigRecord(
    domainStrategy = routeDomainStrategy,
    defaultOutboundTag = defaultRouteOutboundTag,
    rules = routeRules,
)

internal fun AppState.toResourceCatalogRecord() = ResourceCatalogRecord(
    sourceId = resourceFileSource.toString(),
    userAgent = resourceFileUserAgent,
    customResources = customResourceFiles.map { ResourceDefinition(it.id, it.name, it.url) },
)

internal fun SubscriptionGroupState.toSubscriptionRecord() = SubscriptionRecord(
    id = id,
    title = name,
    url = url,
    enabled = enabled,
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
)

internal fun SubscriptionRecord.toAndroidGroup(existing: SubscriptionGroupState?): SubscriptionGroupState {
    val metadata = metadata
    return (existing ?: SubscriptionGroupState(
        id = id,
        name = title,
        url = url,
        userAgent = DefaultSubscriptionUserAgent,
        updateInterval = DefaultAndroidSubscriptionUpdateInterval,
        enabled = enabled,
    )).copy(
        id = id,
        name = title,
        url = url,
        enabled = enabled,
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
    )
}

internal fun TrafficConfigState.toTrafficConfigRecord() = TrafficConfigRecord(
    id = id,
    name = name,
    rawDocument = rawConfig,
    sourceUrl = sourceUrl.takeIf(String::isNotBlank),
    enabled = true,
    locked = updateLocked,
)

internal fun TrafficConfigRecord.toAndroidTrafficConfig(existing: TrafficConfigState?): TrafficConfigState {
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

private fun Int.saturatedIncrement(): Int = if (this == Int.MAX_VALUE) Int.MAX_VALUE else this + 1

private fun ResourceDefinition.toAndroidResource() = app.CustomResourceFileState(id = id, name = name, url = url)

private const val DefaultAndroidSubscriptionUpdateInterval = "24"
