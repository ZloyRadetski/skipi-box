// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigLibraryOperations
import app.skipi.app.model.ApplicationPreferences
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.HomeDisplaySettings
import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.ProxySelectionSettings
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.ThemeMode
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.model.TrafficSelectionSettings
import app.skipi.app.repository.AppRepositories
import app.skipi.app.repository.RuntimeStateRepository
import app.skipi.app.repository.SettingsRepository
import app.skipi.app.repository.SubscriptionRepository
import app.skipi.app.repository.TrafficConfigRepository
import app.skipi.app.runtime.AppRuntimeState
import app.skipi.app.runtime.RuntimeMessage
import app.skipi.app.store.SharedApplicationStore
import features.config.ConfigProfile
import features.subscription.StoredSubscription
import features.subscription.StoredSubscriptionMetadata
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.TunnelPhase
import platform.TunnelSnapshot

/** Adapts the Desktop JSON settings and library snapshots without owning a second copy. */
class DesktopSettingsRepository(
    private val readSettings: () -> DesktopAppSettings,
    private val saveSettings: (DesktopAppSettings) -> Result<Unit>,
    private val publishSettings: (DesktopAppSettings) -> Unit,
    private val readServers: () -> DesktopServerLibrary,
    private val saveServers: (DesktopServerLibrary) -> Result<Unit>,
    private val publishServers: (DesktopServerLibrary) -> Unit,
    private val readConfigs: () -> DesktopConfigLibrary,
    private val saveConfigs: (DesktopConfigLibrary) -> Result<Unit>,
    private val publishConfigs: (DesktopConfigLibrary) -> Unit,
) : SettingsRepository {
    private val mutableState = MutableStateFlow(snapshot())
    override val state: StateFlow<PersistedSettings> = mutableState.asStateFlow()

    /** Refreshes the derived contract view after existing Desktop UI/library writes. */
    fun refresh() {
        mutableState.value = snapshot()
    }

    override suspend fun update(transform: (PersistedSettings) -> PersistedSettings) {
        val oldSettings = readSettings()
        val oldServers = readServers()
        val oldConfigs = readConfigs()
        val before = oldSettings.toPersistedSettings(oldServers, oldConfigs)
        val requested = transform(before)
        require(requested.appearance.dynamicColors == before.appearance.dynamicColors) {
            "Dynamic colors are not supported by Desktop settings"
        }
        require(requested.application == before.application) {
            "Application preferences are not supported by Desktop settings"
        }

        val newSettings = oldSettings.withAppearance(requested.appearance).withHomeDisplay(requested.home)
        val newServers = requested.proxy.selectedServerId
            ?.let { DesktopServerLibraries.select(oldServers, it) }
            ?: oldServers.copy(selectedServerId = null)
        val newConfigs = TrafficConfigLibraryOperations.select(oldConfigs, requested.traffic.activeConfigId)

        val saved = mutableListOf<() -> Result<Unit>>()
        try {
            if (newSettings != oldSettings) {
                saveSettings(newSettings).getOrThrow()
                saved += { saveSettings(oldSettings) }
            }
            if (newServers != oldServers) {
                saveServers(newServers).getOrThrow()
                saved += { saveServers(oldServers) }
            }
            if (newConfigs != oldConfigs) {
                saveConfigs(newConfigs).getOrThrow()
                saved += { saveConfigs(oldConfigs) }
            }
        } catch (failure: Exception) {
            saved.asReversed().forEach { rollback -> runCatching { rollback().getOrThrow() } }
            throw failure
        }

        if (newSettings != oldSettings) publishSettings(newSettings)
        if (newServers != oldServers) publishServers(newServers)
        if (newConfigs != oldConfigs) publishConfigs(newConfigs)
        refresh()
    }

    private fun snapshot(): PersistedSettings =
        readSettings().toPersistedSettings(readServers(), readConfigs())
}

/** Maps traffic profile records to the existing configs.json schema. */
class DesktopTrafficConfigRepository(
    initialLibrary: DesktopConfigLibrary,
    private val readLibrary: () -> DesktopConfigLibrary,
    private val saveLibrary: (DesktopConfigLibrary) -> Result<Unit>,
    private val publishLibrary: (DesktopConfigLibrary) -> Unit,
) : TrafficConfigRepository {
    private val mutableConfigs = MutableStateFlow(initialLibrary.toTrafficRecords())
    override val configs: StateFlow<List<TrafficConfigRecord>> = mutableConfigs.asStateFlow()

    fun refresh(library: DesktopConfigLibrary = readLibrary()) {
        mutableConfigs.value = library.toTrafficRecords()
    }

    override suspend fun upsert(config: TrafficConfigRecord) {
        require(config.enabled) { "Disabled traffic configs are not supported by Desktop storage" }
        val current = readLibrary()
        val profile = ConfigProfile(
            id = config.id,
            name = config.name,
            content = config.rawDocument,
            sourceUrl = config.sourceUrl.orEmpty(),
            updateLocked = config.locked,
            lastUpdatedAtMillis = current.configs.firstOrNull { it.id == config.id }?.lastUpdatedAtMillis ?: 0L,
        )
        val updated = TrafficConfigLibraryOperations.upsert(current, profile)
        commit(updated)
    }

    override suspend fun remove(configId: Int) {
        val current = readLibrary()
        val updated = TrafficConfigLibraryOperations.remove(current, configId)
        commit(updated)
    }

    private fun commit(library: DesktopConfigLibrary) {
        saveLibrary(library).getOrThrow()
        publishLibrary(library)
        refresh(library)
    }
}

/** Maps providers.json records; network refresh remains a Desktop host operation. */
class DesktopSubscriptionRepository(
    initialLibrary: DesktopSubscriptionLibrary,
    private val readLibrary: () -> DesktopSubscriptionLibrary,
    private val saveLibrary: (DesktopSubscriptionLibrary) -> Result<Unit>,
    private val publishLibrary: (DesktopSubscriptionLibrary) -> Unit,
    private val removeLinkedServers: suspend (Int) -> Unit = {
        throw UnsupportedOperationException("Desktop linked-server removal callback is required")
    },
    private val refreshSubscription: suspend (Int) -> Result<SubscriptionRecord> = {
        Result.failure(UnsupportedOperationException("Subscription refresh is owned by the Desktop host"))
    },
) : SubscriptionRepository {
    private val mutableSubscriptions = MutableStateFlow(initialLibrary.toSubscriptionRecords())
    override val subscriptions: StateFlow<List<SubscriptionRecord>> = mutableSubscriptions.asStateFlow()

    fun refresh(library: DesktopSubscriptionLibrary = readLibrary()) {
        mutableSubscriptions.value = library.toSubscriptionRecords()
    }

    override suspend fun upsert(subscription: SubscriptionRecord) {
        val current = readLibrary()
        val existing = current.subscriptions.firstOrNull { it.id == subscription.id }
        val metadata = subscription.metadata
        val previousMetadata = existing?.metadata ?: StoredSubscriptionMetadata()
        val updatedProvider = (existing ?: StoredSubscription(id = subscription.id, url = "")).copy(
            name = subscription.title,
            url = subscription.url,
            enabled = subscription.enabled,
            metadata = previousMetadata.copy(
                description = metadata?.profileDescription ?: previousMetadata.description,
                announce = metadata?.announce ?: previousMetadata.announce,
                supportUrl = metadata?.supportUrl ?: previousMetadata.supportUrl,
                supportEmail = metadata?.supportEmail ?: previousMetadata.supportEmail,
                profileWebPageUrl = metadata?.profileWebPageUrl ?: previousMetadata.profileWebPageUrl,
                announceUrl = metadata?.announceUrl ?: previousMetadata.announceUrl,
                trafficUploadBytes = metadata?.takeIf { it.userInfoReceived }?.trafficUploadBytes
                    ?: previousMetadata.trafficUploadBytes,
                trafficDownloadBytes = metadata?.takeIf { it.userInfoReceived }?.trafficDownloadBytes
                    ?: previousMetadata.trafficDownloadBytes,
                trafficTotalBytes = metadata?.takeIf { it.userInfoReceived }?.trafficTotalBytes
                    ?: previousMetadata.trafficTotalBytes,
                trafficExpireAtSeconds = metadata?.takeIf { it.userInfoReceived }?.trafficExpireAtSeconds
                    ?: previousMetadata.trafficExpireAtSeconds,
                profileUpdateIntervalHours = metadata?.profileUpdateIntervalHours
                    ?: previousMetadata.profileUpdateIntervalHours,
                lastUpdatedAtMillis = subscription.lastUpdatedAtMillis ?: previousMetadata.lastUpdatedAtMillis,
            ),
        )
        commit(current.copy(subscriptions = current.subscriptions.filterNot { it.id == subscription.id } + updatedProvider))
    }

    override suspend fun remove(subscriptionId: Int) {
        require(readLibrary().subscriptions.any { it.id == subscriptionId }) {
            "Unknown subscription ID: $subscriptionId"
        }
        removeLinkedServers(subscriptionId)
        commit(DesktopSubscriptionLibraries.remove(readLibrary(), subscriptionId))
    }

    override suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord> {
        val refreshed = refreshSubscription(subscriptionId).getOrElse { return Result.failure(it) }
        if (refreshed.id != subscriptionId) {
            return Result.failure(
                IllegalArgumentException(
                    "Subscription refresh returned ID ${refreshed.id} for requested ID $subscriptionId",
                ),
            )
        }
        return try {
            upsert(refreshed)
            Result.success(refreshed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private fun commit(library: DesktopSubscriptionLibrary) {
        saveLibrary(library).getOrThrow()
        publishLibrary(library)
        refresh(library)
    }
}

/** Runtime snapshots are read from the Desktop composition root; mutations stay host-owned. */
class DesktopRuntimeStateRepository(
    private val readState: () -> AppRuntimeState,
) : RuntimeStateRepository {
    private val mutableState = MutableStateFlow(readState())
    override val state: StateFlow<AppRuntimeState> = mutableState.asStateFlow()

    fun refresh() {
        mutableState.value = readState()
    }

    override suspend fun update(transform: (AppRuntimeState) -> AppRuntimeState) {
        throw UnsupportedOperationException("Desktop runtime state is owned by the host controllers")
    }
}

/** Composition-root bundle used by Desktop Main. */
data class DesktopSharedApplication(
    val repositories: AppRepositories,
    val store: SharedApplicationStore,
    val settings: DesktopSettingsRepository,
    val proxyServers: DesktopProxyServerRepository,
    val subscriptions: DesktopSubscriptionRepository,
    val trafficConfigs: DesktopTrafficConfigRepository,
    val runtime: DesktopRuntimeStateRepository,
)

fun createDesktopSharedApplication(
    scope: CoroutineScope,
    settings: DesktopSettingsRepository,
    proxyServers: DesktopProxyServerRepository,
    subscriptions: DesktopSubscriptionRepository,
    trafficConfigs: DesktopTrafficConfigRepository,
    runtime: DesktopRuntimeStateRepository,
): DesktopSharedApplication {
    val repositories = AppRepositories(
        settings = settings,
        proxyServers = proxyServers,
        subscriptions = subscriptions,
        trafficConfigs = trafficConfigs,
        runtime = runtime,
    )
    return DesktopSharedApplication(
        repositories = repositories,
        store = SharedApplicationStore(repositories, scope),
        settings = settings,
        proxyServers = proxyServers,
        subscriptions = subscriptions,
        trafficConfigs = trafficConfigs,
        runtime = runtime,
    )
}

private fun DesktopAppSettings.toPersistedSettings(
    servers: DesktopServerLibrary,
    configs: DesktopConfigLibrary,
) = PersistedSettings(
    appearance = AppearanceSettings(
        themeMode = when (themeMode) {
            DesktopThemeMode.Dark, DesktopThemeMode.Amoled -> ThemeMode.Dark
            DesktopThemeMode.Light -> ThemeMode.Light
            DesktopThemeMode.Aurora, DesktopThemeMode.Sakura, DesktopThemeMode.Forest, DesktopThemeMode.Sunset -> ThemeMode.Named
        },
        dynamicColors = false,
        amoledBlack = themeMode == DesktopThemeMode.Amoled,
        themeVariant = when (themeMode) {
            DesktopThemeMode.Aurora -> "aurora"
            DesktopThemeMode.Sakura -> "sakura"
            DesktopThemeMode.Forest -> "forest"
            DesktopThemeMode.Sunset -> "sunset"
            else -> null
        },
        seedIndex = seedIndex,
        customMaterialYouSeed = customMaterialYouSeed,
        customColorsEnabled = customColorsEnabled,
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
    proxy = ProxySelectionSettings(selectedServerId = servers.selectedServerId),
    home = HomeDisplaySettings(
        connectionDisplayMode = connectionDisplayMode,
        pinConnectionPanel = pinConnectionPanelOnHome,
        subscriptionSwipeEnabled = enableSubscriptionSwipe,
        showFloatingPowerButton = classicShowFloatingPowerButton,
        confirmDeletion = confirmDeletion,
        showServerSearch = showServerSearch,
    ),
    traffic = TrafficSelectionSettings(activeConfigId = configs.selectedConfigId),
    application = ApplicationPreferences(),
)

private fun DesktopAppSettings.withAppearance(appearance: AppearanceSettings): DesktopAppSettings {
    require(appearance.themeMode != ThemeMode.System) {
        "System theme mode is not supported by Desktop settings"
    }
    val nextTheme = when (appearance.themeMode) {
        ThemeMode.Named -> when (appearance.themeVariant) {
            "aurora" -> DesktopThemeMode.Aurora
            "sakura" -> DesktopThemeMode.Sakura
            "forest" -> DesktopThemeMode.Forest
            "sunset" -> DesktopThemeMode.Sunset
            else -> themeMode.takeIf(DesktopThemeMode::isNamedDesktopTheme)
                ?: throw IllegalArgumentException("A named theme variant is required by Desktop settings")
        }
        ThemeMode.Light -> if (appearance.amoledBlack) DesktopThemeMode.Amoled else DesktopThemeMode.Light
        ThemeMode.Dark -> if (appearance.amoledBlack) DesktopThemeMode.Amoled else DesktopThemeMode.Dark
        ThemeMode.System -> error("System theme mode was rejected above")
    }
    return copy(
        themeMode = nextTheme,
        seedIndex = appearance.seedIndex,
        customMaterialYouSeed = appearance.customMaterialYouSeed,
        customColorsEnabled = appearance.customColorsEnabled,
        customAccentColor = appearance.customAccentColor,
        customBackgroundColor = appearance.customBackgroundColor,
        customSurfaceColor = appearance.customSurfaceColor,
        customSurfaceVariantColor = appearance.customSurfaceVariantColor,
        customTextColor = appearance.customTextColor,
        customTextSecondaryColor = appearance.customTextSecondaryColor,
        customStatusRunningColor = appearance.customStatusRunningColor,
        customStatusStoppedColor = appearance.customStatusStoppedColor,
        customPingFastColor = appearance.customPingFastColor,
        customPingMediumColor = appearance.customPingMediumColor,
        customPingSlowColor = appearance.customPingSlowColor,
        customCategoryIconColor = appearance.customCategoryIconColor,
        customProtocolVlessColor = appearance.customProtocolVlessColor,
        customProtocolVmessColor = appearance.customProtocolVmessColor,
        customProtocolHysteria2Color = appearance.customProtocolHysteria2Color,
        customProtocolTrojanColor = appearance.customProtocolTrojanColor,
        customProtocolShadowsocksColor = appearance.customProtocolShadowsocksColor,
        customProtocolWireguardColor = appearance.customProtocolWireguardColor,
        customProtocolSocksColor = appearance.customProtocolSocksColor,
        customProtocolHttpColor = appearance.customProtocolHttpColor,
        customProtocolStrategyColor = appearance.customProtocolStrategyColor,
        customProtocolChainColor = appearance.customProtocolChainColor,
        customProtocolJsonColor = appearance.customProtocolJsonColor,
        fontFamilyMode = appearance.fontFamilyMode,
        fontSizeMode = appearance.fontSizeMode,
        fontWeightMode = appearance.fontWeightMode,
        backgroundStyle = appearance.backgroundStyle,
        backgroundPhotoDimPercent = appearance.backgroundPhotoDimPercent,
        bottomBarSize = appearance.bottomBarSize,
    )
}

private fun DesktopAppSettings.withHomeDisplay(home: HomeDisplaySettings): DesktopAppSettings = copy(
    connectionDisplayMode = home.connectionDisplayMode,
    pinConnectionPanelOnHome = home.pinConnectionPanel,
    enableSubscriptionSwipe = home.subscriptionSwipeEnabled,
    classicShowFloatingPowerButton = home.showFloatingPowerButton,
    confirmDeletion = home.confirmDeletion,
    showServerSearch = home.showServerSearch,
)

private fun DesktopThemeMode.isNamedDesktopTheme(): Boolean = when (this) {
    DesktopThemeMode.Aurora, DesktopThemeMode.Sakura, DesktopThemeMode.Forest, DesktopThemeMode.Sunset -> true
    DesktopThemeMode.Dark, DesktopThemeMode.Amoled, DesktopThemeMode.Light -> false
}

private fun DesktopConfigLibrary.toTrafficRecords(): List<TrafficConfigRecord> = configs.map { profile ->
    TrafficConfigRecord(
        id = profile.id,
        name = profile.name,
        rawDocument = profile.content,
        sourceUrl = profile.sourceUrl.ifBlank { null },
        locked = profile.updateLocked,
        enabled = true,
    )
}

private fun DesktopSubscriptionLibrary.toSubscriptionRecords(): List<SubscriptionRecord> = subscriptions.map { item ->
    val stored = item.metadata
    SubscriptionRecord(
        id = item.id,
        title = item.name,
        url = item.url,
        enabled = item.enabled,
        metadata = SubscriptionMetadata(
            profileTitle = item.name.takeIf(String::isNotBlank),
            profileDescription = stored.description.takeIf(String::isNotBlank),
            announce = stored.announce.takeIf(String::isNotBlank),
            supportUrl = stored.supportUrl.takeIf(String::isNotBlank),
            supportEmail = stored.supportEmail.takeIf(String::isNotBlank),
            profileWebPageUrl = stored.profileWebPageUrl.takeIf(String::isNotBlank),
            announceUrl = stored.announceUrl.takeIf(String::isNotBlank),
            userInfoReceived = stored.trafficUploadBytes >= 0 || stored.trafficDownloadBytes >= 0 ||
                stored.trafficTotalBytes >= 0 || stored.trafficExpireAtSeconds >= 0,
            trafficUploadBytes = stored.trafficUploadBytes,
            trafficDownloadBytes = stored.trafficDownloadBytes,
            trafficTotalBytes = stored.trafficTotalBytes,
            trafficExpireAtSeconds = stored.trafficExpireAtSeconds,
            profileUpdateIntervalHours = stored.profileUpdateIntervalHours.takeIf(String::isNotBlank),
        ),
        lastUpdatedAtMillis = stored.lastUpdatedAtMillis.takeIf { it > 0L },
    )
}

fun desktopRuntimeState(
    coreState: DesktopCoreState,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    testingServerIds: Set<Int>,
    refreshingSubscriptionIds: Set<Int>,
    message: String,
    isMessageError: Boolean = false,
): AppRuntimeState = AppRuntimeState(
    tunnel = TunnelSnapshot(phase = if (coreState.isRunning) TunnelPhase.Connected else TunnelPhase.Disconnected),
    latencyByServerId = latencyByServerId.mapNotNull { (id, result) ->
        (result as? DesktopServerLatencyResult.Success)?.let { id to it.milliseconds }
    }.toMap(),
    testingServerIds = testingServerIds,
    refreshingSubscriptionIds = refreshingSubscriptionIds,
    message = message.takeIf(String::isNotBlank)?.let { RuntimeMessage(it, isMessageError) },
)
