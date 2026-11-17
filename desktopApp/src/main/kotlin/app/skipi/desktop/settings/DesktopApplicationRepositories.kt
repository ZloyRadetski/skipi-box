// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigLibraryOperations
import app.modes.FontSizeModeSmall
import app.skipi.app.model.ApplicationPreferences
import app.skipi.app.model.CoreEnginePreferences
import app.skipi.app.model.LogPreferences
import app.skipi.app.model.NetworkAutomationPreferences
import app.skipi.app.model.VpnPreferences
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.LocalProxyPreferences
import app.skipi.app.model.SubscriptionPreferences
import app.skipi.ui.settings.AppearanceSettingValues
import app.skipi.ui.settings.AppearanceSettingsState
import app.skipi.ui.settings.LocalProxySettingsState
import app.skipi.ui.settings.SubscriptionSettingsState
import features.subscription.SubscriptionExpiryReminder
import app.skipi.app.model.HomeDisplaySettings
import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.ProxySelectionSettings
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.SubscriptionCatalog
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
import app.skipi.app.subscription.SubscriptionCatalogOperations
import features.config.ConfigProfile
import features.subscription.StoredSubscription
import features.subscription.StoredSubscriptionMetadata
import features.subscription.SubscriptionEmbeddedConfig
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.TunnelPhase
import platform.TunnelSnapshot
import app.skipi.app.model.customColors
import app.skipi.app.model.withCustomColors
import app.skipi.ui.settings.customColors
import app.skipi.ui.settings.withCustomColors

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
        var newSettings = oldSettings.withAppearance(requested.appearance).withHomeDisplay(requested.home)
        if (requested.application != before.application) {
            newSettings = newSettings.withApplicationPreferences(requested.application)
        }
        if (requested.vpn != before.vpn) newSettings = newSettings.withVpnPreferences(requested.vpn)
        if (requested.networkAutomation != before.networkAutomation) {
            newSettings = newSettings.withNetworkAutomationPreferences(requested.networkAutomation)
        }
        if (requested.engine != before.engine) newSettings = newSettings.withEnginePreferences(requested.engine)
        if (requested.logs != before.logs) newSettings = newSettings.withLogPreferences(requested.logs)
        if (requested.localProxy != before.localProxy) {
            newSettings = newSettings.withLocalProxyPreferences(
                requested = requested.localProxy,
                previous = before.localProxy,
            )
        }
        if (requested.subscriptions != before.subscriptions) {
            newSettings = newSettings.withSubscriptionPreferences(requested.subscriptions)
        }
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
data class DesktopConfigLibraryTransformResult(
    val previous: DesktopConfigLibrary,
    val updated: DesktopConfigLibrary,
)

class DesktopTrafficConfigRepository(
    initialLibrary: DesktopConfigLibrary,
    private val readLibrary: () -> DesktopConfigLibrary,
    private val saveLibrary: (DesktopConfigLibrary) -> Result<Unit>,
    private val publishLibrary: (DesktopConfigLibrary) -> Unit,
) : TrafficConfigRepository {
    private val lock = Any()
    private val mutableConfigs = MutableStateFlow(initialLibrary.toTrafficRecords())
    override val configs: StateFlow<List<TrafficConfigRecord>> = mutableConfigs.asStateFlow()

    fun refresh(library: DesktopConfigLibrary = readLibrary()) {
        synchronized(lock) {
            mutableConfigs.value = library.toTrafficRecords()
        }
    }

    fun transformLatest(
        transform: (DesktopConfigLibrary) -> DesktopConfigLibrary,
    ): DesktopConfigLibrary = transformLatestWithPrevious(transform).updated

    fun transformLatestWithPrevious(
        transform: (DesktopConfigLibrary) -> DesktopConfigLibrary,
    ): DesktopConfigLibraryTransformResult = synchronized(lock) {
        val previous = readLibrary()
        val updated = transform(previous)
        commit(updated)
        DesktopConfigLibraryTransformResult(previous = previous, updated = updated)
    }

    override suspend fun upsert(config: TrafficConfigRecord) {
        require(config.enabled) { "Disabled traffic configs are not supported by Desktop storage" }
        transformLatest { current ->
            val profile = ConfigProfile(
                id = config.id,
                name = config.name,
                content = config.rawDocument,
                sourceUrl = config.sourceUrl.orEmpty(),
                updateLocked = config.locked,
                lastUpdatedAtMillis = current.configs.firstOrNull { it.id == config.id }?.lastUpdatedAtMillis ?: 0L,
            )
            TrafficConfigLibraryOperations.upsert(current, profile)
        }
    }

    override suspend fun remove(configId: Int) {
        transformLatest { current -> TrafficConfigLibraryOperations.remove(current, configId) }
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
    private val lock = Any()
    private val mutableCatalog = MutableStateFlow(initialLibrary.toSubscriptionCatalog())
    private val mutableSubscriptions = MutableStateFlow(mutableCatalog.value.subscriptions)
    override val catalog: StateFlow<SubscriptionCatalog> = mutableCatalog.asStateFlow()
    override val subscriptions: StateFlow<List<SubscriptionRecord>> = mutableSubscriptions.asStateFlow()

    fun refresh(library: DesktopSubscriptionLibrary = readLibrary()) {
        synchronized(lock) {
            refreshSnapshot(library)
        }
    }

    override suspend fun upsert(subscription: SubscriptionRecord) {
        updateCatalog { current -> SubscriptionCatalogOperations.upsert(current, subscription) }
    }

    override suspend fun updateCatalog(transform: (SubscriptionCatalog) -> SubscriptionCatalog) {
        synchronized(lock) {
            val current = readLibrary()
            val currentCatalog = current.toSubscriptionCatalog()
            val requestedCatalog = transform(currentCatalog)
            val minimumNextId = maxOf(
                currentCatalog.nextSubscriptionId,
                saturatedIncrement(requestedCatalog.subscriptions.maxOfOrNull(SubscriptionRecord::id) ?: 0),
            )
            val updatedCatalog = requestedCatalog.copy(
                nextSubscriptionId = maxOf(requestedCatalog.nextSubscriptionId, minimumNextId),
            )
            require(updatedCatalog.nextSubscriptionId > 0) { "Next subscription ID must be positive" }
            require(updatedCatalog.subscriptions.all { it.id > 0 }) { "Subscription IDs must be positive" }
            require(updatedCatalog.subscriptions.map { it.id }.distinct().size == updatedCatalog.subscriptions.size) {
                "Subscription IDs must be unique"
            }

            if (updatedCatalog == currentCatalog) {
                refreshSnapshot(current)
                return@synchronized
            }

            val existingById = current.subscriptions.associateBy(StoredSubscription::id)
            val updatedLibrary = current.copy(
                subscriptions = updatedCatalog.subscriptions.map { record ->
                    record.toStoredSubscription(existingById[record.id])
                },
                nextSubscriptionId = updatedCatalog.nextSubscriptionId,
            )
            commit(updatedLibrary)
        }
    }

    override suspend fun remove(subscriptionId: Int) {
        synchronized(lock) {
            val current = readLibrary()
            require(current.subscriptions.any { it.id == subscriptionId }) {
                "Unknown subscription ID: $subscriptionId"
            }
            SubscriptionCatalogOperations.remove(
                catalog = current.toSubscriptionCatalog(),
                proxyCatalog = ProxyServerCatalog(),
                subscriptionId = subscriptionId,
            )
        }
        removeLinkedServers(subscriptionId)
        synchronized(lock) {
            val current = readLibrary()
            if (current.subscriptions.none { it.id == subscriptionId }) {
                refreshSnapshot(current)
                return@synchronized
            }
            val operation = SubscriptionCatalogOperations.remove(
                catalog = current.toSubscriptionCatalog(),
                proxyCatalog = ProxyServerCatalog(),
                subscriptionId = subscriptionId,
            )
            val existingById = current.subscriptions.associateBy(StoredSubscription::id)
            val updated = current.copy(
                subscriptions = operation.catalog.subscriptions.mapNotNull { record -> existingById[record.id] },
                nextSubscriptionId = operation.catalog.nextSubscriptionId,
            )
            if (updated == current) refreshSnapshot(current) else commit(updated)
        }
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
        refreshSnapshot(library)
    }

    private fun refreshSnapshot(library: DesktopSubscriptionLibrary) {
        val current = library.toSubscriptionCatalog()
        mutableCatalog.value = current
        mutableSubscriptions.value = current.subscriptions
    }
}

private fun SubscriptionRecord.toStoredSubscription(existing: StoredSubscription?): StoredSubscription {
    val providerProfileTitle = metadata?.profileTitle
    val previousMetadata = existing?.metadata ?: StoredSubscriptionMetadata()
    val storedProfileTitle = when {
        providerProfileTitle == null -> previousMetadata.profileTitle
        providerProfileTitle.isEmpty() -> ""
        existing?.metadata?.profileTitle == null && providerProfileTitle == title -> null
        else -> providerProfileTitle
    }
    return (existing ?: StoredSubscription(id = id, url = "")).copy(
        name = title,
        url = url,
        userAgent = userAgent,
        enabled = enabled,
        updateInterval = updateInterval,
        hwid = hwid,
        deviceHeaders = deviceHeaders,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        builtIn = builtIn,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
        metadata = previousMetadata.copy(
            profileTitle = storedProfileTitle,
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
            embeddedConfigPayload = metadata?.embeddedConfig?.payload
                ?: previousMetadata.embeddedConfigPayload,
            embeddedConfigActivate = metadata?.embeddedConfig?.activate
                ?: previousMetadata.embeddedConfigActivate,
            embeddedConfigIsUrl = metadata?.embeddedConfig?.isUrl
                ?: previousMetadata.embeddedConfigIsUrl,
            lastUpdatedAtMillis = lastUpdatedAtMillis ?: previousMetadata.lastUpdatedAtMillis,
        ),
    )
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
    val routing: DesktopRoutingRepository,
)

fun createDesktopSharedApplication(
    scope: CoroutineScope,
    settings: DesktopSettingsRepository,
    proxyServers: DesktopProxyServerRepository,
    subscriptions: DesktopSubscriptionRepository,
    trafficConfigs: DesktopTrafficConfigRepository,
    runtime: DesktopRuntimeStateRepository,
    routing: DesktopRoutingRepository,
): DesktopSharedApplication {
    val repositories = AppRepositories(
        settings = settings,
        proxyServers = proxyServers,
        subscriptions = subscriptions,
        trafficConfigs = trafficConfigs,
        runtime = runtime,
        routing = routing,
    )
    return DesktopSharedApplication(
        repositories = repositories,
        store = SharedApplicationStore(repositories, scope),
        settings = settings,
        proxyServers = proxyServers,
        subscriptions = subscriptions,
        trafficConfigs = trafficConfigs,
        runtime = runtime,
        routing = routing,
    )
}

internal fun DesktopAppSettings.toSharedAppearanceSettings() = AppearanceSettings(
        themeMode = when (themeMode) {
            DesktopThemeMode.Dark, DesktopThemeMode.Amoled -> ThemeMode.Dark
            DesktopThemeMode.Light -> ThemeMode.Light
            DesktopThemeMode.Aurora, DesktopThemeMode.Sakura, DesktopThemeMode.Forest, DesktopThemeMode.Sunset -> ThemeMode.Named
        },
        dynamicColors = dynamicColors,
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
        fontFamilyMode = fontFamilyMode,
        fontSizeMode = fontSizeMode,
        fontWeightMode = fontWeightMode,
        backgroundStyle = backgroundStyle,
        backgroundPhotoDimPercent = backgroundPhotoDimPercent,
        bottomBarSize = bottomBarSize,
        classicButtonSize = classicButtonSize,
        seasonalEvents = enableSeasonalEvents,
        customThemes = customThemes,
    ).withCustomColors(customColors())

private fun DesktopAppSettings.toPersistedSettings(
    servers: DesktopServerLibrary,
    configs: DesktopConfigLibrary,
) = PersistedSettings(
    appearance = toSharedAppearanceSettings(),
    proxy = ProxySelectionSettings(selectedServerId = servers.selectedServerId),
    home = HomeDisplaySettings(
        // Settings written before the mode existed only have the compact flag.
        connectionDisplayMode = connectionDisplayMode
            ?: AppearanceSettingValues.ConnectionDisplayModeCompact.takeIf { compactHome },
        pinConnectionPanel = pinConnectionPanelOnHome,
        subscriptionSwipeEnabled = enableSubscriptionSwipe,
        showFloatingPowerButton = classicShowFloatingPowerButton,
        confirmDeletion = confirmDeletion,
        showServerSearch = showServerSearch,
        proxyServerListSort = proxyServerListSort,
        enableAllProxyGroup = enableAllProxyGroup,
        showTunnelMemoryOnHome = showTunnelMemory,
        showTunnelIpOnHome = showTunnelIp,
        maskTunnelIpOnHome = maskTunnelIp,
    ),
    traffic = TrafficSelectionSettings(activeConfigId = configs.selectedConfigId),
    application = ApplicationPreferences(
        languageTag = languageTag,
        hapticsEnabled = hapticsEnabled,
        onboardingCompleted = onboardingCompleted,
        autoCheckAppUpdates = autoCheckAppUpdates,
        autoConnectOnAppOpen = autoConnectOnAppOpen,
    ),
    localProxy = LocalProxyPreferences(
        port = localProxyPortRaw ?: localProxyPort.toString(),
        dynamicPort = enableDynamicLocalProxyPort,
        listenAllInterfaces = localProxyListenAddress.trim() != "127.0.0.1",
        authenticationEnabled = enableLocalProxyAuth,
        username = localProxyUsername,
        password = localProxyPassword,
        authMode = localProxyAuthMode,
        httpEnabled = enableLocalHttpProxy,
        httpPort = localHttpProxyPort.toString(),
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
        deviceHeadersEnabled = sendDeviceHeaders,
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
        coreLogLevel = DesktopCoreLogLevels.indexOf(coreLogLevel).takeIf { it >= 0 } ?: DefaultDesktopCoreLogLevelIndex,
        accessLogEnabled = enableAccessLog,
        retentionDays = logRetentionDays,
    ),
)

/** "error", the level Desktop starts with. */
private val DefaultDesktopCoreLogLevelIndex = DesktopCoreLogLevels.indexOf("error")

internal fun DesktopAppSettings.toAppearanceSettingsState(): AppearanceSettingsState = AppearanceSettingsState(
    backgroundPhotoDimPercent = backgroundPhotoDimPercent ?: 45,
    backgroundStyle = backgroundStyle ?: AppearanceSettingValues.BackgroundStyleClassic,
    bottomBarSize = bottomBarSize ?: 1,
    classicButtonSize = classicButtonSize ?: AppearanceSettingValues.ClassicButtonSizeMedium,
    enableSeasonalEvents = enableSeasonalEvents,
    customThemes = customThemes,
    classicShowFloatingPowerButton = classicShowFloatingPowerButton,
    colorMode = when (themeMode) {
        DesktopThemeMode.Dark -> AppearanceSettingValues.ColorModeDark
        DesktopThemeMode.Amoled -> AppearanceSettingValues.ColorModeAmoled
        DesktopThemeMode.Light -> AppearanceSettingValues.ColorModeLight
        DesktopThemeMode.Aurora -> AppearanceSettingValues.ColorModeAurora
        DesktopThemeMode.Sakura -> AppearanceSettingValues.ColorModeSakura
        DesktopThemeMode.Forest -> AppearanceSettingValues.ColorModeForest
        DesktopThemeMode.Sunset -> AppearanceSettingValues.ColorModeSunset
    },
    connectionDisplayMode = connectionDisplayMode ?: if (compactHome) {
        AppearanceSettingValues.ConnectionDisplayModeCompact
    } else {
        AppearanceSettingValues.ConnectionDisplayModeClassic
    },
    customMaterialYouSeed = customMaterialYouSeed,
    enableAllProxyGroup = enableAllProxyGroup,
    enableCustomColors = customColorsEnabled,
    enableMaterialYou = dynamicColors,
    enableSubscriptionSwipe = enableSubscriptionSwipe,
    fontFamilyMode = fontFamilyMode ?: 0,
    fontSizeMode = fontSizeMode ?: FontSizeModeSmall,
    fontWeightMode = fontWeightMode ?: 0,
    pinConnectionPanelOnHome = pinConnectionPanelOnHome,
    seedIndex = seedIndex,
    showServerSearch = showServerSearch,
    showTunnelMemoryOnHome = showTunnelMemory,
    showTunnelIpOnHome = showTunnelIp,
    maskTunnelIpOnHome = maskTunnelIp,
    showConnectionLogOnHome = showConnectionLog,
    navigationStyle = navigationStyle,
).withCustomColors(customColors())

internal fun DesktopAppSettings.toLocalProxySettingsState(): LocalProxySettingsState {
    val address = localProxyListenAddress.trim().ifBlank { "127.0.0.1" }
    val portText = localProxyPortRaw ?: localProxyPort.toString()
    return LocalProxySettingsState(
        dynamicPort = enableDynamicLocalProxyPort,
        port = portText,
        listenAllInterfaces = address != "127.0.0.1",
        enableHttpEndpoint = httpInboundEnabled(),
        httpPort = localHttpProxyPort.toString(),
        socksAuthMode = socksAuthMode(),
        httpAuthMode = httpAuthMode(),
        username = localProxyUsername,
        password = localProxyPassword,
        httpUsername = localHttpProxyUsername,
        httpPassword = localHttpProxyPassword,
        displayIp = address,
        socksEndpoint = "$address:${portText.toIntOrNull() ?: localProxyPort}",
        httpEndpoint = "$address:$localHttpProxyPort",
        portError = null,
    )
}

internal fun DesktopAppSettings.toSubscriptionSettingsState(): SubscriptionSettingsState = SubscriptionSettingsState(
    fetchTimeoutSeconds = subscriptionFetchTimeoutSeconds,
    deviceHeadersEnabled = sendDeviceHeaders,
    expiryNotificationsEnabled = enableSubscriptionExpiryNotifications,
    expiryReminders = subscriptionExpiryReminders,
    pingSummary = "${if (subscriptionPingMode == app.modes.SubscriptionPingModeHttp) "HTTP" else "TCP"} · $subscriptionPingUrl · ${subscriptionPingTimeoutMillis} ms",
)

internal fun DesktopAppSettings.withSubscriptionSettingsState(state: SubscriptionSettingsState): DesktopAppSettings = copy(
    subscriptionFetchTimeoutSeconds = state.fetchTimeoutSeconds,
    sendDeviceHeaders = state.deviceHeadersEnabled,
    enableSubscriptionExpiryNotifications = state.expiryNotificationsEnabled,
    subscriptionExpiryReminders = state.expiryReminders,
)
internal fun DesktopAppSettings.withAppearanceState(state: AppearanceSettingsState): DesktopAppSettings {
    val nextTheme = when (AppearanceSettingValues.normalizeColorMode(state.colorMode)) {
        AppearanceSettingValues.ColorModeLight -> DesktopThemeMode.Light
        AppearanceSettingValues.ColorModeDark -> DesktopThemeMode.Dark
        AppearanceSettingValues.ColorModeAmoled -> DesktopThemeMode.Amoled
        AppearanceSettingValues.ColorModeAurora -> DesktopThemeMode.Aurora
        AppearanceSettingValues.ColorModeSakura -> DesktopThemeMode.Sakura
        AppearanceSettingValues.ColorModeForest -> DesktopThemeMode.Forest
        AppearanceSettingValues.ColorModeSunset -> DesktopThemeMode.Sunset
        else -> throw IllegalArgumentException("System theme mode is not supported by Desktop settings")
    }
    return copy(
        themeMode = nextTheme,
        dynamicColors = state.enableMaterialYou,
        compactHome = state.connectionDisplayMode == AppearanceSettingValues.ConnectionDisplayModeCompact,
        connectionDisplayMode = state.connectionDisplayMode,
        classicShowFloatingPowerButton = state.classicShowFloatingPowerButton,
        pinConnectionPanelOnHome = state.pinConnectionPanelOnHome,
        enableAllProxyGroup = state.enableAllProxyGroup,
        showServerSearch = state.showServerSearch,
        enableSubscriptionSwipe = state.enableSubscriptionSwipe,
        showTunnelMemory = state.showTunnelMemoryOnHome,
        showTunnelIp = state.showTunnelIpOnHome,
        maskTunnelIp = state.maskTunnelIpOnHome,
        showConnectionLog = state.showConnectionLogOnHome,
        navigationStyle = state.navigationStyle.coerceIn(0, 1),
        customColorsEnabled = state.enableCustomColors,
        fontFamilyMode = state.fontFamilyMode,
        fontSizeMode = state.fontSizeMode,
        fontWeightMode = state.fontWeightMode,
        backgroundStyle = state.backgroundStyle,
        backgroundPhotoDimPercent = state.backgroundPhotoDimPercent,
        bottomBarSize = state.bottomBarSize,
        classicButtonSize = state.classicButtonSize,
        enableSeasonalEvents = state.enableSeasonalEvents,
        customThemes = state.customThemes,
        seedIndex = state.seedIndex,
        customMaterialYouSeed = state.customMaterialYouSeed,
    ).withCustomColors(state.customColors())
}
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
        dynamicColors = appearance.dynamicColors,
        seedIndex = appearance.seedIndex,
        customMaterialYouSeed = appearance.customMaterialYouSeed,
        customColorsEnabled = appearance.customColorsEnabled,
        fontFamilyMode = appearance.fontFamilyMode,
        fontSizeMode = appearance.fontSizeMode,
        fontWeightMode = appearance.fontWeightMode,
        backgroundStyle = appearance.backgroundStyle,
        backgroundPhotoDimPercent = appearance.backgroundPhotoDimPercent,
        bottomBarSize = appearance.bottomBarSize,
        classicButtonSize = appearance.classicButtonSize,
        enableSeasonalEvents = appearance.seasonalEvents,
        customThemes = appearance.customThemes,
    ).withCustomColors(appearance.customColors())
}

private fun DesktopAppSettings.withHomeDisplay(home: HomeDisplaySettings): DesktopAppSettings = copy(
    connectionDisplayMode = home.connectionDisplayMode,
    // The compact layout is kept as its own flag as well; it follows the mode a screen writes.
    compactHome = home.connectionDisplayMode?.let { it == AppearanceSettingValues.ConnectionDisplayModeCompact } ?: compactHome,
    pinConnectionPanelOnHome = home.pinConnectionPanel,
    enableSubscriptionSwipe = home.subscriptionSwipeEnabled,
    classicShowFloatingPowerButton = home.showFloatingPowerButton,
    confirmDeletion = home.confirmDeletion,
    showServerSearch = home.showServerSearch,
    proxyServerListSort = home.proxyServerListSort ?: proxyServerListSort,
    enableAllProxyGroup = home.enableAllProxyGroup ?: enableAllProxyGroup,
    showTunnelMemory = home.showTunnelMemoryOnHome ?: showTunnelMemory,
    showTunnelIp = home.showTunnelIpOnHome ?: showTunnelIp,
    maskTunnelIp = home.maskTunnelIpOnHome ?: maskTunnelIp,
)

private fun DesktopAppSettings.withApplicationPreferences(preferences: ApplicationPreferences): DesktopAppSettings = copy(
    languageTag = preferences.languageTag,
    hapticsEnabled = preferences.hapticsEnabled,
    onboardingCompleted = preferences.onboardingCompleted,
    autoCheckAppUpdates = preferences.autoCheckAppUpdates,
    autoConnectOnAppOpen = preferences.autoConnectOnAppOpen,
)

private fun DesktopAppSettings.withVpnPreferences(vpn: VpnPreferences?): DesktopAppSettings = if (vpn == null) this else copy(
    enableStrictFullTunnel = vpn.strictFullTunnel,
    enableVpnLocalDns = vpn.localDns,
    tunMtu = vpn.mtu,
    tunVpnDns = vpn.dns,
    tunIpv4Cidr = vpn.ipv4Cidr,
    tunIpv6Cidr = vpn.ipv6Cidr,
    tunTcpKeepAliveInterval = vpn.tcpKeepAliveInterval,
    tunTcpUserTimeout = vpn.tcpUserTimeout,
)

private fun DesktopAppSettings.withNetworkAutomationPreferences(preferences: NetworkAutomationPreferences?): DesktopAppSettings =
    if (preferences == null) this else copy(
        enableNetworkAutomation = preferences.enabled,
        enableOnDemandVpn = preferences.onDemandVpn,
        networkAutomationRules = preferences.rules,
    )

private fun DesktopAppSettings.withEnginePreferences(engine: CoreEnginePreferences?): DesktopAppSettings = if (engine == null) this else copy(
    enableSniffing = engine.sniffing,
    enableSniffingRouteOnly = engine.sniffingRouteOnly,
    enableMux = engine.mux,
    muxConcurrency = engine.muxConcurrency,
    muxXudpConcurrency = engine.muxXudpConcurrency,
    muxXudpProxyUdp443 = engine.muxXudpProxyUdp443,
    enableFragment = engine.fragment,
    fragmentPackets = engine.fragmentPackets,
    fragmentLength = engine.fragmentLength,
    fragmentInterval = engine.fragmentInterval,
)

/** A level index Desktop has no name for keeps the current level. */
private fun DesktopAppSettings.withLogPreferences(logs: LogPreferences?): DesktopAppSettings = if (logs == null) this else copy(
    coreLogLevel = DesktopCoreLogLevels.getOrNull(logs.coreLogLevel) ?: coreLogLevel,
    enableAccessLog = logs.accessLogEnabled,
    logRetentionDays = logs.retentionDays,
)

private fun DesktopAppSettings.withLocalProxyPreferences(
    requested: LocalProxyPreferences?,
    previous: LocalProxyPreferences?,
): DesktopAppSettings {
    if (requested == null) return this
    val parsedPort = requested.port.toIntOrNull()?.takeIf { it in 1..65_535 }
    val listenAddress = if (requested.listenAllInterfaces != previous?.listenAllInterfaces) {
        if (requested.listenAllInterfaces) "0.0.0.0" else "127.0.0.1"
    } else {
        localProxyListenAddress
    }
    return copy(
        localProxyPort = parsedPort ?: localProxyPort,
        localProxyPortRaw = requested.port,
        localProxyListenAddress = listenAddress,
        enableDynamicLocalProxyPort = requested.dynamicPort,
        enableLocalProxyAuth = requested.authenticationEnabled,
        localProxyUsername = requested.username,
        localProxyPassword = requested.password,
        localProxyAuthMode = requested.authMode ?: localProxyAuthMode,
        enableLocalHttpProxy = requested.httpEnabled ?: enableLocalHttpProxy,
        localHttpProxyPort = requested.httpPort?.toIntOrNull()?.takeIf { it in 1..65_535 } ?: localHttpProxyPort,
        enableLocalHttpProxyAuth = requested.httpAuthenticationEnabled ?: enableLocalHttpProxyAuth,
        localHttpProxyAuthMode = requested.httpAuthMode ?: localHttpProxyAuthMode,
        localHttpProxyUsername = requested.httpUsername ?: localHttpProxyUsername,
        localHttpProxyPassword = requested.httpPassword ?: localHttpProxyPassword,
    )
}

private fun DesktopAppSettings.withSubscriptionPreferences(preferences: SubscriptionPreferences?): DesktopAppSettings =
    if (preferences == null) this else copy(
        subscriptionPingMode = preferences.pingMode,
        subscriptionPingUrl = preferences.pingUrl,
        subscriptionPingTimeoutMillis = preferences.pingTimeoutMillis,
        subscriptionPingConcurrency = preferences.pingConcurrency,
        subscriptionUserAgents = preferences.userAgents,
        sendDeviceHeaders = preferences.deviceHeadersEnabled,
        subscriptionFetchTimeoutSeconds = preferences.fetchTimeoutSeconds,
        enableSubscriptionExpiryNotifications = preferences.expiryNotificationsEnabled,
        subscriptionExpiryReminders = preferences.expiryReminders,
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
        userAgent = item.userAgent,
        updateInterval = item.updateInterval,
        hwid = item.hwid,
        deviceHeaders = item.deviceHeaders,
        ageSecretKey = item.ageSecretKey,
        updateViaProxy = item.updateViaProxy,
        autoOverrideRules = item.autoOverrideRules,
        enabled = item.enabled,
        builtIn = item.builtIn,
        metadata = SubscriptionMetadata(
            profileTitle = if (stored.profileTitle == null) {
                item.name.takeIf(String::isNotBlank)
            } else {
                stored.profileTitle?.takeIf(String::isNotBlank)
            },
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
            embeddedConfig = stored.takeIf {
                it.embeddedConfigPayload.isNotEmpty() || it.embeddedConfigActivate || it.embeddedConfigIsUrl
            }?.let { metadata ->
                SubscriptionEmbeddedConfig(
                    payload = metadata.embeddedConfigPayload,
                    activate = metadata.embeddedConfigActivate,
                    isUrl = metadata.embeddedConfigIsUrl,
                )
            },
        ),
        lastUpdatedAtMillis = stored.lastUpdatedAtMillis.takeIf { it > 0L },
        notifyOnExpiry = item.notifyOnExpiry,
        customExpiryReminders = item.customExpiryReminders,
    )
}

private fun DesktopSubscriptionLibrary.toSubscriptionCatalog(): SubscriptionCatalog {
    val records = toSubscriptionRecords()
    val firstAvailableAfterRows = saturatedIncrement(records.maxOfOrNull(SubscriptionRecord::id) ?: 0)
    return SubscriptionCatalog(
        subscriptions = records,
        nextSubscriptionId = maxOf(nextSubscriptionId?.takeIf { it > 0 } ?: 1, firstAvailableAfterRows),
    )
}

private fun saturatedIncrement(value: Int): Int = when {
    value >= Int.MAX_VALUE -> Int.MAX_VALUE
    value < 0 -> 1
    else -> value + 1
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

/** The theme as the custom-colour overrides Desktop draws: custom colours on, its six colours and its light or dark base. */
internal fun DesktopAppSettings.withCustomTheme(theme: features.config.CustomThemeShare): DesktopAppSettings = copy(
    themeMode = if (theme.dark) DesktopThemeMode.Dark else DesktopThemeMode.Light,
    customColorsEnabled = true,
    customAccentColor = theme.accent,
    customBackgroundColor = theme.background,
    customSurfaceColor = theme.surface,
    customSurfaceVariantColor = theme.surfaceVariant,
    customTextColor = theme.text,
    customTextSecondaryColor = theme.textSecondary,
)
