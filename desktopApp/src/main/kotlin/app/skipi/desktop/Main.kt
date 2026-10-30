// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.skipi.app.home.ProxyHomeTunnelIp
import app.skipi.app.home.resolveTunnelIp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.app.server.applyProxyServerEditResult
import app.skipi.app.server.toProxyServerCopyTarget
import app.skipi.app.home.toAndroidValue
import app.skipi.app.home.resolveProxyHomeLatencyTimeoutMillis
import app.skipi.app.proxy.deleteProxyServerRecords
import app.skipi.app.proxy.ProxyServerStopOutcome
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import app.skipi.app.subscription.SubscriptionRefreshCommitScope
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshLoader
import app.skipi.app.subscription.SubscriptionRefreshResult
import app.skipi.ui.subscription.toSubscriptionRecord
import app.skipi.ui.home.SubscriptionRefreshMessageSummary
import app.skipi.ui.home.formatSubscriptionRefreshMessage
import app.skipi.ui.home.formatSubscriptionInstallMessage
import app.skipi.ui.navigation.SkipiMainDestination
import app.skipi.ui.navigation.SkipiExpressiveNavigationBar
import app.skipi.ui.updater.SkipiAppUpdateBanner
import features.updater.AppUpdateState
import features.updater.bannerModel
import features.networkautomation.policy.NetworkAutomationAction
import app.skipi.ui.navigation.navigationBarSizeForSetting
import app.skipi.ui.navigation.SkipiExpressiveNavigationColors
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_memory_kilobytes
import app.skipi.ui.resources.home_memory_megabytes
import app.skipi.ui.resources.ic_skipi_logo
import app.skipi.ui.resources.proxy_server_list_duplicates_deleted
import app.skipi.ui.resources.proxy_server_list_invalid_deleted
import app.skipi.ui.resources.proxy_server_list_no_duplicates
import app.skipi.ui.resources.proxy_server_list_no_invalid
import app.skipi.ui.resources.proxy_server_list_all_deleted
import app.skipi.ui.resources.proxy_server_list_no_servers_to_delete
import app.skipi.ui.resources.proxy_server_list_no_testable
import app.skipi.ui.resources.proxy_server_list_no_subscription_updates
import app.skipi.ui.resources.proxy_server_list_latency_done
import app.skipi.ui.resources.proxy_server_list_subscription_update_result
import app.skipi.ui.resources.proxy_server_list_subscription_update_result_with_failed
import app.skipi.ui.resources.subscription_install_existing_url
import app.skipi.ui.resources.resource_file_download_notification_cancelled
import app.skipi.ui.resources.resource_file_download_notification_failed
import app.skipi.ui.resources.settings_resource_file_updating
import app.skipi.ui.resources.settings_resource_files_updated
import app.skipi.ui.text.formatTemplate
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.stripLeadingCountryFlag
import features.config.runtime.ActiveGeoResourceRefreshTrigger
import features.config.TrafficConfigState
import app.skipi.app.model.SubscriptionRecord
import features.resources.ResourceFileDownloadCancelledException
import app.skipi.app.config.toTrafficConfigState
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    remember { features.logs.AppLog.install(DesktopAppLogSink) }
    val coreController = remember { DesktopCoreController() }
    val systemProxyManager = remember { DesktopSystemProxyManagers.create() }
    var desktopSettings by remember {
        mutableStateOf(DesktopSettingsLibraries.loadDefault().getOrElse { DesktopAppSettings() })
    }
    val tunnel = remember {
        DesktopTunnelSession(
            coreController = coreController,
            systemProxyManager = systemProxyManager,
            readUseSystemProxy = { desktopSettings.useSystemProxy },
        )
    }
    Window(
        state = WindowState(size = DpSize(1180.dp, 860.dp)),
        // Closing is handled from the composable below so it can serialize with an
        // in-flight connect/reconnect and keep Core alive if proxy restoration fails.
        onCloseRequest = { tunnel.closeRequested = true },
        title = "SKIPI",
        icon = org.jetbrains.compose.resources.painterResource(Res.drawable.ic_skipi_logo),
    ) {
                val homeSortSaveMutex = remember { Mutex() }
        var backgroundConnected by remember { mutableStateOf(false) }
        CompositionLocalProvider(
            LocalDesktopThemeMode provides desktopSettings.themeMode,
            LocalDesktopAppearanceSettings provides desktopSettings.toSharedAppearanceSettings(),
        ) {
            DesktopAppTheme {
                DesktopAppBackground(settings = desktopSettings, isConnected = backgroundConnected) {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
                val subscriptionScope = rememberCoroutineScope()
                val duplicatesDeletedTemplate = stringResource(Res.string.proxy_server_list_duplicates_deleted)
                val noDuplicatesMessage = stringResource(Res.string.proxy_server_list_no_duplicates)
                val invalidDeletedTemplate = stringResource(Res.string.proxy_server_list_invalid_deleted)
                val noInvalidMessage = stringResource(Res.string.proxy_server_list_no_invalid)
                val allDeletedTemplate = stringResource(Res.string.proxy_server_list_all_deleted)
                val noServersToDeleteMessage = stringResource(Res.string.proxy_server_list_no_servers_to_delete)
                val noTestableServersMessage = stringResource(Res.string.proxy_server_list_no_testable)
                val latencyDoneTemplate = stringResource(Res.string.proxy_server_list_latency_done)
                val noSubscriptionUpdatesMessage = stringResource(Res.string.proxy_server_list_no_subscription_updates)
                val subscriptionRefreshSuccessTemplate =
                    stringResource(Res.string.proxy_server_list_subscription_update_result)
                val subscriptionRefreshFailedTemplate =
                    stringResource(Res.string.proxy_server_list_subscription_update_result_with_failed)
                val subscriptionInstallExistingUrlTemplate =
                    stringResource(Res.string.subscription_install_existing_url)
                val resourceUpdatingTemplate = stringResource(Res.string.settings_resource_file_updating)
                val resourceUpdatedMessage = stringResource(Res.string.settings_resource_files_updated)
                val resourceCancelledMessage = stringResource(Res.string.resource_file_download_notification_cancelled)
                val resourceFailureTemplate = stringResource(
                    Res.string.resource_file_download_notification_failed,
                    "{message}",
                )
                val subscriptionFetcher = remember { DesktopSubscriptionFetcher() }
                val subscriptionPayloadParsers = remember {
                    listOf(DesktopMihomoPayloadImporter.subscriptionPayloadParser())
                }
                var configLibrary by remember { mutableStateOf(DesktopConfigLibraries.loadDefault().getOrElse { DesktopConfigLibrary() }) }
                var serverLink by remember { mutableStateOf("") }
                var subscriptionUrl by remember { mutableStateOf("") }
                var subscriptionUpdate by remember { mutableStateOf<DesktopSubscriptionUpdate?>(null) }
                var subscriptionMessage by remember { mutableStateOf("") }
                var subscriptionUpdateInProgress by remember { mutableStateOf(false) }
                var refreshingSubscriptionId by remember { mutableStateOf<Int?>(null) }
                var batchRefreshingSubscriptionIds by remember { mutableStateOf(emptySet<Int>()) }
                var subscriptionLibrary by remember {
                    mutableStateOf(DesktopSubscriptionLibraries.loadDefault().getOrElse { DesktopSubscriptionLibrary() })
                }
                var currentSection by remember { mutableStateOf(SkipiMainDestination.Proxy) }
                var serverLibrary by remember {
                    mutableStateOf(DesktopServerLibraries.loadDefault().getOrElse { DesktopServerLibrary() })
                }
                val proxyServerRepository = remember {
                    DesktopProxyServerRepository(
                        initialLibrary = serverLibrary,
                        readLibrary = { serverLibrary },
                        saveLibrary = DesktopServerLibraries::saveDefault,
                        publishLibrary = { serverLibrary = it },
                    )
                }
                val configProxyGroupReconciler = remember(proxyServerRepository) {
                    DesktopTrafficConfigProxyGroupReconciler(
                        serverRepository = proxyServerRepository,
                        readConfigLibrary = { configLibrary },
                        stripLeadingCountryFlag = { value -> value.stripLeadingCountryFlag() },
                    )
                }
                LaunchedEffect(serverLibrary) {
                    proxyServerRepository.refresh(serverLibrary)
                }
                var serverLibraryMessage by remember { mutableStateOf("") }
                val latencyTester = remember { DesktopServerLatencyTester() }
                var latencyByServerId by remember { mutableStateOf<Map<Int, DesktopServerLatencyResult>>(emptyMap()) }
                var testingServerIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
                var pingingSubscriptionIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
                val desktopHomeLatencyJobOwner = remember { DesktopProxyHomeLatencyJobOwner() }
                val resourceFileFeedback = remember { MutableStateFlow("") }
                val resourceFileStatusMessage by resourceFileFeedback.collectAsState()
                LaunchedEffect(tunnel.coreState.isRunning) { backgroundConnected = tunnel.coreState.isRunning }
                val localProxyReadiness = remember { DesktopLocalProxyReadiness() }
                val desktopLocalProxyRuntime = remember { DesktopLocalProxyRuntime() }
                val desktopCoreLogs = remember { DesktopCoreLogs() }
                LaunchedEffect(desktopCoreLogs) {
                    withContext(Dispatchers.IO) { desktopCoreLogs.restore(desktopSettings.logRetentionDays) }
                }
                val desktopDiagnostics = remember {
                    DesktopDiagnostics(
                        activeOptions = { desktopLocalProxyRuntime.activeOptions(coreController.state().isRunning) },
                    )
                }
                DisposableEffect(desktopDiagnostics) { onDispose { desktopDiagnostics.close() } }
                val desktopProfileResourceFiles = remember { DesktopProfileResourceFiles() }
                // The core lives in this process, so its footprint is the process's resident memory.
                val tunnelMemoryKb by produceState(0L, tunnel.coreState.isRunning, desktopSettings.showTunnelMemory) {
                    value = 0L
                    if (!tunnel.coreState.isRunning || !desktopSettings.showTunnelMemory) return@produceState
                    while (true) {
                        value = withContext(Dispatchers.IO) { currentProcessResidentKb() } ?: 0L
                        delay(3_000)
                    }
                }
                val tunnelMemoryText = if (tunnelMemoryKb > 0L) {
                    val megabytes = tunnelMemoryKb / 1_024.0
                    if (tunnelMemoryKb >= 1_024L) {
                        stringResource(Res.string.home_memory_megabytes, String.format(java.util.Locale.ROOT, "%.1f", megabytes))
                    } else {
                        stringResource(Res.string.home_memory_kilobytes, tunnelMemoryKb.toString())
                    }
                } else null
                // Re-resolved when the tunnel (re)starts or another server becomes active; off while the tile is off.
                val tunnelIp by produceState<ProxyHomeTunnelIp?>(
                    initialValue = null,
                    tunnel.coreState.isRunning,
                    desktopSettings.showTunnelIp,
                    serverLibrary.selectedServerId,
                ) {
                    value = null
                    if (!tunnel.coreState.isRunning || !desktopSettings.showTunnelIp) return@produceState
                    value = resolveTunnelIp { desktopDiagnostics.fetchIpInfo() }
                }

                var appUpdateState by remember { mutableStateOf(AppUpdateState()) }
                val appUpdateChecker = remember { DesktopAppUpdateChecker() }
                suspend fun checkForAppUpdate(): DesktopAppUpdateCheck {
                    val outcome = appUpdateChecker.check()
                    when (outcome) {
                        is DesktopAppUpdateCheck.Available ->
                            appUpdateState = appUpdateState.applyCheckedRelease(outcome.update).state
                        DesktopAppUpdateCheck.UpToDate -> appUpdateState = appUpdateState.applyCheckedRelease(null).state
                        DesktopAppUpdateCheck.Failed -> Unit
                    }
                    return outcome
                }
                LaunchedEffect(desktopSettings.autoCheckAppUpdates) {
                    if (desktopSettings.autoCheckAppUpdates) checkForAppUpdate()
                }

                val stopSelectedSubscriptionServer = remember { DesktopSubscriptionServerStopAction() }
                val desktopSharedApplication = remember {
                    val settingsRepository = DesktopSettingsRepository(
                        readSettings = { desktopSettings },
                        saveSettings = DesktopSettingsLibraries::saveDefault,
                        publishSettings = { desktopSettings = it },
                        readServers = { serverLibrary },
                        saveServers = DesktopServerLibraries::saveDefault,
                        publishServers = { serverLibrary = it },
                        readConfigs = { configLibrary },
                        saveConfigs = DesktopConfigLibraries::saveDefault,
                        publishConfigs = { configLibrary = it },
                    )
                    val subscriptionServerRemovalAdapter = DesktopSubscriptionServerRemovalAdapter(
                        readLibrary = { serverLibrary },
                        proxyServerRepository = proxyServerRepository,
                        isTunnelRunning = { coreController.state().isRunning },
                        stopSelectedServer = { stopSelectedSubscriptionServer.run() },
                    )
                    val subscriptionRepository = DesktopSubscriptionRepository(
                        initialLibrary = subscriptionLibrary,
                        readLibrary = { subscriptionLibrary },
                        saveLibrary = DesktopSubscriptionLibraries::saveDefault,
                        publishLibrary = { subscriptionLibrary = it },
                        removeLinkedServers = subscriptionServerRemovalAdapter::remove,
                    )
                    val trafficConfigRepository = DesktopTrafficConfigRepository(
                        initialLibrary = configLibrary,
                        readLibrary = { configLibrary },
                        saveLibrary = DesktopConfigLibraries::saveDefault,
                        publishLibrary = { configLibrary = it },
                    )
                    val runtimeRepository = DesktopRuntimeStateRepository {
                        desktopRuntimeState(
                            coreState = tunnel.coreState,
                            latencyByServerId = latencyByServerId,
                            testingServerIds = testingServerIds,
                            refreshingSubscriptionIds = setOfNotNull(refreshingSubscriptionId) +
                                batchRefreshingSubscriptionIds,
                            message = tunnel.coreMessage.ifBlank { subscriptionMessage },
                            isMessageError = tunnel.coreMessage.isNotBlank(),
                        )
                    }
                    val routingRepository = DesktopRoutingRepository(
                        initialSettings = desktopSettings,
                        readSettings = { desktopSettings },
                        saveSettings = DesktopSettingsLibraries::saveDefault,
                        publishSettings = { desktopSettings = it },
                    )
                    createDesktopSharedApplication(
                        scope = subscriptionScope,
                        settings = settingsRepository,
                        proxyServers = proxyServerRepository,
                        subscriptions = subscriptionRepository,
                        trafficConfigs = trafficConfigRepository,
                        runtime = runtimeRepository,
                        routing = routingRepository,
                    )
                }
                val routeProxyServers by desktopSharedApplication.proxyServers.servers.collectAsState()
                val routeSubscriptionCatalog by desktopSharedApplication.subscriptions.catalog.collectAsState()
                val desktopSubscriptionRefreshOperation = remember(
                    desktopSharedApplication,
                    proxyServerRepository,
                    subscriptionFetcher,
                    subscriptionPayloadParsers,
                ) {
                    DesktopSubscriptionRefreshOperation(
                        subscriptionRepository = desktopSharedApplication.subscriptions,
                        proxyServerRepository = proxyServerRepository,
                        readConfigs = { configLibrary },
                        saveConfigs = DesktopConfigLibraries::saveDefault,
                        publishConfigs = { configLibrary = it },
                        readLatencyByServerId = { latencyByServerId },
                        publishLatencyByServerId = { latencyByServerId = it },
                        fetchResponse = { url, userAgent, options ->
                            subscriptionFetcher.fetchSubscriptionRefreshResponse(
                                url = url,
                                storedUserAgent = userAgent,
                                options = options,
                            )
                        },
                        parsers = subscriptionPayloadParsers,
                        refreshedAtMillis = { System.currentTimeMillis() },
                    )
                }
                val desktopSubscriptionRefreshBatchRunner = remember(
                    desktopSharedApplication,
                    subscriptionFetcher,
                    desktopSubscriptionRefreshOperation,
                ) {
                    lateinit var runner: DesktopSubscriptionRefreshBatchRunner<DesktopSubscriptionRefreshFetchOptions>
                    runner = DesktopSubscriptionRefreshBatchRunner(
                        captureRequests = {
                            val settings = desktopSettings
                            val proxyRunning = tunnel.coreState.isRunning
                            desktopSharedApplication.subscriptions.catalog.value.subscriptions.map { subscription ->
                                SubscriptionRefreshLoadRequest(
                                    subscription = subscription,
                                    fetchOptions = desktopLocalProxyRuntime.subscriptionFetchOptions(
                                        settings = settings,
                                        subscription = subscription,
                                        coreRunning = proxyRunning,
                                    ),
                                )
                            }
                        },
                        loader = SubscriptionRefreshLoader { request ->
                            desktopSubscriptionRefreshOperation.load(request)
                        },
                        refreshedAtMillis = { System.currentTimeMillis() },
                        subscriptionRepository = desktopSharedApplication.subscriptions,
                        proxyServerRepository = proxyServerRepository,
                        readConfigs = { configLibrary },
                        saveConfigs = DesktopConfigLibraries::saveDefault,
                        publishConfigs = { configLibrary = it },
                        readLatencyByServerId = { latencyByServerId },
                        publishLatencyByServerId = { latencyByServerId = it },
                        onCommittedLibrariesChanged = {
                            val coreIsRunning = coreController.state().isRunning
                            val selectionChanged = shouldReconnectAfterSubscriptionRefresh(
                                coreIsRunning = coreIsRunning,
                                previousSelectedServerId = runner.previousSelectedServerId,
                                currentSelectedServerId = runner.currentSelectedServerId,
                            )
                            if (selectionChanged) {
                                tunnel.requestReconnect("Подписки обновлены.")
                            }
                        },
                    )
                    runner
                }
                LaunchedEffect(desktopSubscriptionRefreshBatchRunner) {
                    desktopSubscriptionRefreshBatchRunner.progress.collect { progress ->
                        batchRefreshingSubscriptionIds = progress.refreshingSubscriptionIds
                    }
                }

                suspend fun <T> runInSubscriptionScope(block: suspend () -> T): T =
                    subscriptionScope.async { block() }.await()

                LaunchedEffect(
                    desktopSettings,
                    serverLibrary,
                    configLibrary,
                    subscriptionLibrary,
                    tunnel.coreState,
                    latencyByServerId,
                    testingServerIds,
                    subscriptionUpdateInProgress,
                    refreshingSubscriptionId,
                    batchRefreshingSubscriptionIds,
                    tunnel.coreMessage,
                    subscriptionMessage,
                ) {
                    desktopSharedApplication.settings.refresh()
                    desktopSharedApplication.subscriptions.refresh(subscriptionLibrary)
                    desktopSharedApplication.trafficConfigs.refresh(configLibrary)
                    desktopSharedApplication.runtime.refresh()
                    desktopSharedApplication.routing.refresh()
                }

                LaunchedEffect(tunnel) { tunnel.recoverSystemProxy() }
                val localProxyOptions = remember(desktopSettings) {
                    desktopSettings.toLocalProxyXrayConfigOptions()
                }
                val routingSnapshot = desktopSettings.toRoutingConfigRecord()
                val trafficConfigContentById = remember(configLibrary) {
                    configLibrary.configs.associate { config -> config.id to config.content }
                }
                val selectedServerConfig = remember(
                    serverLibrary,
                    configLibrary,
                    localProxyOptions,
                    routingSnapshot,
                    trafficConfigContentById,
                    desktopSettings.subscriptionPingUrl,
                    latencyByServerId,
                ) {
                    buildDesktopSelectedServerConfig(
                        serverLibrary = serverLibrary,
                        configLibrary = configLibrary,
                        routing = routingSnapshot,
                        options = localProxyOptions,
                        trafficConfigContentById = trafficConfigContentById,
                        subscriptionPingUrl = desktopSettings.subscriptionPingUrl,
                        latencyByServerId = latencyByServerId,
                    )
                }
                val activeProfileName = configLibrary.selectedConfigId
                    ?.let { selectedId -> configLibrary.configs.firstOrNull { profile -> profile.id == selectedId } }
                    ?.name
                val exportProxyServerFullJson: suspend (ProxyServerEditResult) -> String = { editResult ->
                    val serverLibrarySnapshot = serverLibrary
                    val settingsSnapshot = desktopSettings
                    val trafficConfigContentSnapshot = trafficConfigContentById
                    val latencySnapshot = latencyByServerId.toMap()
                    withContext(Dispatchers.IO) {
                        val copyTarget = editResult.toProxyServerCopyTarget(
                            defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                        )
                        buildDesktopFullJsonExport(
                            serverLibrary = serverLibrarySnapshot.withProxyServerCopyTarget(copyTarget),
                            settings = settingsSnapshot,
                            trafficConfigContentById = trafficConfigContentSnapshot,
                            latencyByServerId = latencySnapshot,
                        ).getOrThrow()
                    }
                }
                val latestSelectedServerConfig by rememberUpdatedState(selectedServerConfig)
                val latestDesktopSettings by rememberUpdatedState(desktopSettings)
                val latestServerLibrary by rememberUpdatedState(serverLibrary)
                val latestConfigLibrary by rememberUpdatedState(configLibrary)
                val latestLatencyByServerId by rememberUpdatedState(latencyByServerId)
                val latestResourceUpdatingTemplate by rememberUpdatedState(resourceUpdatingTemplate)
                val latestResourceUpdatedMessage by rememberUpdatedState(resourceUpdatedMessage)
                val latestResourceCancelledMessage by rememberUpdatedState(resourceCancelledMessage)
                val latestResourceFailureTemplate by rememberUpdatedState(resourceFailureTemplate)
                val desktopSubscriptionRefreshCoordinator = remember(
                    desktopSharedApplication,
                    desktopSubscriptionRefreshOperation,
                    desktopLocalProxyRuntime,
                ) {
                    DesktopSubscriptionRefreshCoordinator(
                        subscriptionRepository = desktopSharedApplication.subscriptions,
                        isNetworkConnected = ::isDesktopNetworkConnected,
                        refresh = { subscription ->
                            subscriptionUrl = subscription.url
                            subscriptionUpdateInProgress = true
                            refreshingSubscriptionId = subscription.id
                            try {
                                subscriptionMessage = "Обновление подписки…"
                                subscriptionUpdate = null
                                val result = desktopSubscriptionRefreshOperation.run(
                                    request = SubscriptionRefreshLoadRequest(
                                        subscription = subscription,
                                        fetchOptions = desktopLocalProxyRuntime.subscriptionFetchOptions(
                                            settings = latestDesktopSettings,
                                            subscription = subscription,
                                            coreRunning = coreController.state().isRunning,
                                        ),
                                    ),
                                )
                                subscriptionMessage = desktopSubscriptionRefreshResultMessage(result.refresh)
                                if (shouldReconnectAfterSubscriptionRefresh(
                                        coreIsRunning = coreController.state().isRunning,
                                        previousSelectedServerId = result.previousSelectedServerId,
                                        currentSelectedServerId = result.currentSelectedServerId,
                                    )
                                ) {
                                    tunnel.requestReconnect("Подписка обновлена.")
                                }
                                result
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                subscriptionMessage = "Не удалось обновить подписку: ${error.message.orEmpty()}"
                                throw error
                            } finally {
                                refreshingSubscriptionId = null
                                subscriptionUpdateInProgress = false
                            }
                        },
                    )
                }
                val desktopSubscriptionAutoUpdater = remember(desktopSubscriptionRefreshCoordinator) {
                    DesktopSubscriptionAutoUpdater(
                        readLatestTargets = {
                            desktopSharedApplication.subscriptions.catalog.value.subscriptions
                                .map(SubscriptionRecord::toRefreshTarget)
                        },
                        isNetworkConnected = ::isDesktopNetworkConnected,
                        updateAutomatically = desktopSubscriptionRefreshCoordinator::refreshScheduled,
                        nowMillis = System::currentTimeMillis,
                    )
                }
                val desktopSubscriptionAutoUpdateWakeups = remember { Channel<Unit>(Channel.CONFLATED) }
                LaunchedEffect(desktopSubscriptionAutoUpdater, routeSubscriptionCatalog) {
                    desktopSubscriptionAutoUpdater.observeTargets(
                        routeSubscriptionCatalog.subscriptions.map(SubscriptionRecord::toRefreshTarget),
                    )
                    desktopSubscriptionAutoUpdateWakeups.trySend(Unit)
                }
                DesktopAutomaticRefresh(
                    updater = desktopSubscriptionAutoUpdater,
                    enabled = !tunnel.closeRequested,
                    failureSource = "Subscriptions",
                    failureMessage = "Automatic subscription update failed",
                    runDue = desktopSubscriptionAutoUpdater::runDue,
                    nextWakeupAtMillis = desktopSubscriptionAutoUpdater::nextWakeupAtMillis,
                    wakeups = desktopSubscriptionAutoUpdateWakeups,
                )
                val desktopResourceFileServices = remember(subscriptionScope, desktopProfileResourceFiles) {
                    DesktopResourceFileServices(
                        scope = subscriptionScope,
                        resourceFiles = desktopProfileResourceFiles,
                        readSettings = { latestDesktopSettings },
                        readProfile = { profileId ->
                            latestConfigLibrary.configs.firstOrNull { it.id == profileId }
                                ?.toTrafficConfigState(desktopResourceProfileFallbackUserAgent())
                        },
                        activeProxy = {
                            desktopLocalProxyRuntime.activeOptions(
                                coreRunning = coreController.state().isRunning,
                            )
                        },
                        prepareFeedback = {
                            DesktopResourceFileUpdateFeedback(
                                onProgress = { displayName, progress, _ ->
                                    val progressText = progress?.let { " ($it%)" }.orEmpty()
                                    resourceFileFeedback.value =
                                        "${latestResourceUpdatingTemplate}: $displayName$progressText"
                                },
                                onComplete = {
                                    resourceFileFeedback.value = latestResourceUpdatedMessage
                                },
                                onFailure = { error ->
                                    val failureText = error.message ?: error::class.simpleName.orEmpty()
                                    resourceFileFeedback.value = if (
                                        error is ResourceFileDownloadCancelledException
                                    ) {
                                        latestResourceCancelledMessage
                                    } else {
                                        latestResourceFailureTemplate.replace("{message}", failureText)
                                    }
                                },
                                mapCancellationError = { _ ->
                                    ResourceFileDownloadCancelledException(latestResourceCancelledMessage)
                                },
                            )
                        },
                        pickFile = { chooseDesktopResourceFile() },
                    )
                }
                val resourceAutoUpdater = remember(desktopResourceFileServices) {
                    DesktopResourceAutoUpdater(
                        readLatestTargets = { latestConfigLibrary.toRefreshTargets() },
                        isNetworkConnected = ::isDesktopNetworkConnected,
                        updateAutomatically = desktopResourceFileServices::updateAutomatically,
                        nowMillis = System::currentTimeMillis,
                    )
                }
                val resourceAutoUpdateWakeups = remember { Channel<Unit>(Channel.CONFLATED) }
                val desktopTrafficConfigProfileFetcher = remember(subscriptionFetcher, desktopLocalProxyRuntime) {
                    DesktopTrafficConfigProfileFetcher(
                        fetcher = subscriptionFetcher,
                        readSettings = { latestDesktopSettings },
                        activeProxy = {
                            desktopLocalProxyRuntime.activeOptions(
                                coreRunning = coreController.state().isRunning,
                            )
                        },
                    )
                }
                val desktopTrafficConfigManualProfileFetcher = remember(subscriptionFetcher) {
                    DesktopTrafficConfigProfileFetcher(
                        fetcher = subscriptionFetcher,
                        readSettings = { latestDesktopSettings },
                        activeProxy = { null },
                    )
                }
                val desktopTrafficConfigAutoRefresh = remember(
                    desktopSharedApplication,
                    desktopTrafficConfigProfileFetcher,
                    configProxyGroupReconciler,
                ) {
                    DesktopTrafficConfigAutoRefresh(
                        readLibrary = { latestConfigLibrary },
                        repository = desktopSharedApplication.trafficConfigs,
                        fetch = desktopTrafficConfigProfileFetcher::fetch,
                        reconcile = configProxyGroupReconciler::reconcile,
                        nowMillis = System::currentTimeMillis,
                        resourceUserAgentFallback = desktopResourceProfileFallbackUserAgent(),
                    )
                }
                val desktopTrafficConfigAutoUpdater = remember(desktopTrafficConfigAutoRefresh) {
                    DesktopTrafficConfigAutoUpdater(
                        readLatestTargets = { latestConfigLibrary.toRefreshTargets() },
                        isNetworkConnected = ::isDesktopNetworkConnected,
                        updateAutomatically = desktopTrafficConfigAutoRefresh::run,
                        nowMillis = System::currentTimeMillis,
                    )
                }
                val desktopTrafficConfigAutoUpdateWakeups = remember { Channel<Unit>(Channel.CONFLATED) }

                LaunchedEffect(desktopResourceFileServices) {
                    val startupLibrary = latestConfigLibrary
                    val activeOrFirst = startupLibrary.configs.firstOrNull { profile ->
                        profile.id == startupLibrary.selectedConfigId
                    } ?: startupLibrary.configs.firstOrNull()
                    val profile = activeOrFirst?.toTrafficConfigState(desktopResourceProfileFallbackUserAgent())
                    try {
                        desktopResourceFileServices.synchronizeBundledFilesAfterPackageUpdate(profile)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        DesktopLogger.warn(
                            "ResourceFiles",
                            "Failed to synchronize bundled resource files at startup",
                            error,
                        )
                    }
                }

                LaunchedEffect(resourceAutoUpdater, latestConfigLibrary) {
                    val library = latestConfigLibrary
                    val profiles = library.toTrafficConfigStates()
                    resourceAutoUpdater.observeTargets(
                        profiles.map(TrafficConfigState::toDesktopTrafficConfigRefreshTarget),
                    )
                    val activeOrFirst = profiles.firstOrNull { profile ->
                        profile.id == library.selectedConfigId
                    } ?: profiles.firstOrNull()
                    resourceAutoUpdater.observeActiveProfile(
                        ActiveGeoResourceRefreshTrigger(
                            profileId = activeOrFirst?.id ?: 0,
                            customGeoIpUrl = activeOrFirst?.resourceSettings?.customGeoIpUrl.orEmpty(),
                            customGeoSiteUrl = activeOrFirst?.resourceSettings?.customGeoSiteUrl.orEmpty(),
                        ),
                    )
                    resourceAutoUpdateWakeups.trySend(Unit)
                }

                DesktopAutomaticRefresh(
                    updater = resourceAutoUpdater,
                    failureSource = "ResourceFiles",
                    failureMessage = "Automatic resource update failed",
                    runDue = resourceAutoUpdater::runDue,
                    nextWakeupAtMillis = resourceAutoUpdater::nextWakeupAtMillis,
                    wakeups = resourceAutoUpdateWakeups,
                )

                LaunchedEffect(desktopTrafficConfigAutoUpdater, latestConfigLibrary) {
                    desktopTrafficConfigAutoUpdater.observeTargets(latestConfigLibrary.toRefreshTargets())
                    desktopTrafficConfigAutoUpdateWakeups.trySend(Unit)
                }

                DesktopAutomaticRefresh(
                    updater = desktopTrafficConfigAutoUpdater,
                    failureSource = "TrafficConfig",
                    failureMessage = "Automatic profile refresh failed",
                    runDue = desktopTrafficConfigAutoUpdater::runDue,
                    nextWakeupAtMillis = desktopTrafficConfigAutoUpdater::nextWakeupAtMillis,
                    wakeups = desktopTrafficConfigAutoUpdateWakeups,
                )
                val desktopTunnelController = remember(coreController, systemProxyManager) {
                    DesktopTunnelController(
                        configForProfile = {
                            val settingsSnapshot = latestDesktopSettings
                            val routingSnapshot = settingsSnapshot.toRoutingConfigRecord()
                            val serverLibrarySnapshot = latestServerLibrary
                            val configLibrarySnapshot = latestConfigLibrary
                            val trafficConfigContentSnapshot = configLibrarySnapshot.configs
                                .associate { config -> config.id to config.content }
                            val latencySnapshot = latestLatencyByServerId.toMap()
                            runCatching {
                                val globalAssetsDirectory = DesktopCoreRuntimes.discover().getOrThrow().directory
                                val options = desktopLocalProxyRuntime.prepareStart(settingsSnapshot)
                                buildDesktopSelectedServerConfig(
                                    serverLibrary = serverLibrarySnapshot,
                                    configLibrary = configLibrarySnapshot,
                                    routing = routingSnapshot,
                                    options = options,
                                    trafficConfigContentById = trafficConfigContentSnapshot,
                                    subscriptionPingUrl = settingsSnapshot.subscriptionPingUrl,
                                    latencyByServerId = latencySnapshot,
                                    routingDataDirectory = globalAssetsDirectory,
                                )?.getOrThrow()
                                    ?: error("Select a working server before connecting")
                            }
                        },
                        startPlanForProfile = { _ ->
                            runCatching {
                                val settingsSnapshot = latestDesktopSettings
                                val serverLibrarySnapshot = latestServerLibrary
                                val configLibrarySnapshot = latestConfigLibrary
                                val latencySnapshot = latestLatencyByServerId.toMap()
                                prepareDesktopTunnelStartPlan(
                                    settings = settingsSnapshot,
                                    serverLibrary = serverLibrarySnapshot,
                                    configLibrary = configLibrarySnapshot,
                                    latencyByServerId = latencySnapshot,
                                    localProxyRuntime = desktopLocalProxyRuntime,
                                    resourceFiles = desktopProfileResourceFiles,
                                    globalAssetsDirectory = DesktopCoreRuntimes.discover().getOrThrow().directory,
                                )
                            }
                        },
                        startCore = { request ->
                            // Like Android: truncate the previous run's files, then follow the new ones.
                            desktopCoreLogs.clear()
                            desktopCoreLogs.startTailers(latestDesktopSettings)
                            desktopLocalProxyRuntime.onCoreStarted(
                                coreController.start(request.configuration, request.assetsDirectory),
                            )
                        },
                        stopCore = {
                            desktopLocalProxyRuntime.onCoreStopped(coreController.stop()).also {
                                desktopCoreLogs.stopTailers()
                            }
                        },
                        coreState = coreController::state,
                        readCoreTraffic = coreController::readTunnelTraffic,
                        awaitSystemProxyEndpoint = {
                            val settings = latestDesktopSettings
                            if (!settings.useSystemProxy || !systemProxyManager.isSupportedHost()) {
                                Result.success(Unit)
                            } else {
                                val runningOptions = desktopLocalProxyRuntime.activeOptions(
                                    coreRunning = coreController.state().isRunning,
                                )
                                if (runningOptions?.enableHttpProxy != true) {
                                    Result.failure(
                                        IllegalStateException("The running Core has no configured system-proxy HTTP inbound"),
                                    )
                                } else {
                                    localProxyReadiness.awaitLoopbackHttpEndpoint(
                                        port = runningOptions.httpProxyPort,
                                        coreState = coreController::state,
                                    )
                                }
                            }
                        },
                        acquireSystemProxy = {
                            val settings = latestDesktopSettings
                            when {
                                !settings.useSystemProxy -> Result.success(Unit)
                                !systemProxyManager.isSupportedHost() -> Result.failure(
                                    IllegalStateException("Системный прокси не поддерживается на этой платформе"),
                                )

                                else -> {
                                    val runningOptions = desktopLocalProxyRuntime.activeOptions(
                                        coreRunning = coreController.state().isRunning,
                                    )
                                    if (runningOptions?.enableHttpProxy != true) {
                                        Result.failure(
                                            IllegalStateException("The running Core has no configured system-proxy HTTP inbound"),
                                        )
                                    } else {
                                        systemProxyManager.acquire(
                                            DesktopSystemProxyEndpoints(
                                                httpPort = runningOptions.httpProxyPort,
                                                socksPort = runningOptions.socksPort,
                                            ),
                                        ).map { }
                                    }
                                }
                            }
                        },
                        releaseSystemProxy = {
                            if (systemProxyManager.isSupportedHost()) {
                                systemProxyManager.release().map { }
                            } else {
                                Result.success(Unit)
                            }
                        },
                        systemProxySupported = systemProxyManager::isSupportedHost,
                    )
                }
                val selectedTunnelProfileId = configLibrary.selectedConfigId?.toString()
                    ?: serverLibrary.selectedServerId?.toString()
                    ?: "selected-server"

                stopSelectedSubscriptionServer.action = tunnel::stopForActiveServerDeletion

                LaunchedEffect(tunnel, desktopTunnelController) { tunnel.observeCore() }

                LaunchedEffect(tunnel.pendingReconnectReason, tunnel.pendingReconnectRevision, tunnel.closeRequested) {
                    tunnel.runPendingReconnect(
                        selectedProfileId = selectedTunnelProfileId,
                        systemProxyEnabled = { desktopSettings.useSystemProxy },
                    )
                }

                // A measurement started before a tunnel change would report the wrong path.
                LaunchedEffect(tunnel.coreState.isRunning) { desktopDiagnostics.stopAll() }

                LaunchedEffect(tunnel.closeRequested) {
                    if (tunnel.closeRequested) tunnel.shutDown(::exitApplication)
                }

                fun startDesktopHomeLatencyTest(
                    targets: List<Pair<Int, features.proxy.server.model.ProxyServer<*>>>,
                    subscriptionId: Int? = null,
                    onFinished: () -> Unit = {},
                ): Job {
                    if (subscriptionId != null && pingingSubscriptionIds.isNotEmpty()) {
                        return subscriptionScope.launch(start = CoroutineStart.UNDISPATCHED) { }
                    }
                    if (subscriptionId != null) pingingSubscriptionIds = pingingSubscriptionIds + subscriptionId
                    val latencyTimeout = Duration.ofMillis(
                        resolveProxyHomeLatencyTimeoutMillis(
                            desktopSettings.subscriptionPingTimeoutMillis,
                        ).toLong(),
                    )
                    val runner = DesktopProxyHomeLatencyRunner(
                        scope = subscriptionScope,
                        subscriptionPingConcurrency = desktopSettings.subscriptionPingConcurrency,
                        tcpTester = DesktopProxyHomeTcpTester { _, server ->
                            val endpoint = server.desktopTcpEndpointOrNull()
                            if (endpoint == null) {
                                DesktopServerLatencyResult.Error("No direct server endpoint")
                            } else {
                                withContext(Dispatchers.IO) {
                                    latencyTester.measure(
                                        host = endpoint.host,
                                        port = endpoint.port,
                                        timeout = latencyTimeout,
                                    )
                                }
                            }
                        },
                        jobOwner = desktopHomeLatencyJobOwner,
                        readTestingServerIds = { testingServerIds },
                        publishTestingServerIds = { testingServerIds = it },
                        readLatencyByServerId = { latencyByServerId },
                        publishLatencyByServerId = { latencyByServerId = it },
                        latencyDoneTemplate = latencyDoneTemplate,
                        noTestableServersMessage = noTestableServersMessage,
                        publishMessage = { serverLibraryMessage = it },
                    )
                    return runner.start(targets) {
                        if (subscriptionId != null) {
                            pingingSubscriptionIds = pingingSubscriptionIds - subscriptionId
                        }
                        onFinished()
                    }
                }

                suspend fun withHomeDeleteRuntime(
                    operation: suspend (
                        stopService: suspend () -> ProxyServerStopOutcome,
                        applyStoppedState: () -> Unit,
                        onStopFailed: suspend (Throwable) -> Unit,
                    ) -> Unit,
                ) = tunnel.withServerDeletionRuntime(
                    onStopFailed = { error ->
                        serverLibraryMessage = "Не удалось удалить серверы: ${error.message.orEmpty()}"
                    },
                    operation = operation,
                )

                suspend fun runHomeBulkDelete(
                    serverIds: Set<Int>,
                    selectedServerId: Int?,
                    tunnelRunning: Boolean,
                    serviceOperationInProgress: Boolean,
                    deletedTemplate: String,
                    emptyMessage: String,
                ) {
                    if (serverIds.isEmpty()) {
                        serverLibraryMessage = emptyMessage
                        return
                    }
                    withHomeDeleteRuntime { stopService, applyStoppedState, onStopFailed ->
                        runDesktopProxyHomeBulkDelete(
                            serverIds = serverIds,
                            selectedServerId = selectedServerId,
                            tunnelRunning = tunnelRunning,
                            serviceOperationInProgress = serviceOperationInProgress,
                            stopService = stopService,
                            applyStoppedState = applyStoppedState,
                            dispatchDelete = {
                                desktopSharedApplication.store.dispatchAndAwait(
                                    SharedApplicationAction.RemoveProxyServers(serverIds),
                                ).outcome
                            },
                            onDeleted = {
                                serverLibraryMessage = deletedTemplate.formatTemplate("count" to serverIds.size)
                            },
                            onDeleteError = { error ->
                                serverLibraryMessage = "Не удалось удалить серверы: ${error.message.orEmpty()}"
                            },
                            onStopFailed = onStopFailed,
                        )
                    }
                }

                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    bottomBar = {
                        if (desktopSettings.onboardingCompleted) DesktopBottomNavigation(
                            sizeSetting = desktopSettings.bottomBarSize ?: 1,
                            selected = currentSection,
                            onSelect = { currentSection = it },
                        )
                    },
                ) { contentPadding ->
                    val desktopBackupHost = remember {
                        object : DesktopBackupHost {
                            override fun currentUserData() = DesktopUserData(
                                settings = latestDesktopSettings,
                                servers = latestServerLibrary,
                                subscriptions = subscriptionLibrary,
                                configs = latestConfigLibrary,
                            )

                            override suspend fun stopTunnel(): Result<Unit> = tunnel.stopForBackup()

                            override suspend fun apply(userData: DesktopUserData): Result<Unit> {
                                val saved = withContext(Dispatchers.IO) {
                                    runCatching {
                                        DesktopServerLibraries.saveDefault(userData.servers).getOrThrow()
                                        DesktopSubscriptionLibraries.saveDefault(userData.subscriptions).getOrThrow()
                                        DesktopConfigLibraries.saveDefault(userData.configs).getOrThrow()
                                        DesktopSettingsLibraries.saveDefault(userData.settings).getOrThrow()
                                    }
                                }
                                saved.onSuccess {
                                    serverLibrary = userData.servers
                                    subscriptionLibrary = userData.subscriptions
                                    configLibrary = userData.configs
                                    desktopSettings = userData.settings
                                    desktopCoreLogs.pruneOlderThanDays(userData.settings.logRetentionDays)
                                }
                                return saved
                            }
                        }
                    }
                val desktopPrepareSubscription: suspend (DesktopSubscriptionInstallUri) -> Result<String> = { install ->
                                try {
                                    val installed = desktopSubscriptionRefreshCoordinator.install(install)
                                    val refreshMessage = installed.refreshResult.fold(
                                        onSuccess = { result -> desktopSubscriptionRefreshResultMessage(result.refresh) },
                                        onFailure = { error ->
                                            "Не удалось обновить подписку: ${error.message.orEmpty()}"
                                        },
                                    )
                                    val message = formatSubscriptionInstallMessage(
                                        existingTitle = installed.preparation.existingTitle,
                                        refreshMessage = refreshMessage,
                                        existingUrlTemplate = subscriptionInstallExistingUrlTemplate,
                                    )
                                    subscriptionMessage = message
                                    Result.success(message)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Throwable) {
                                    Result.failure(error)
                                }
                            }
                val desktopProxyImportRunner = remember {
                    DesktopProxyImportRunner(
                        readSettings = { desktopSettings },
                        readServers = { serverLibrary },
                        readSubscriptions = { subscriptionLibrary },
                        publishSubscriptions = { subscriptionLibrary = it },
                        readConfigs = { configLibrary },
                        publishConfigs = { configLibrary = it },
                        store = desktopSharedApplication.store,
                        fetcher = subscriptionFetcher,
                        isTunnelRunning = { tunnel.coreState.isRunning },
                        requestReconnect = tunnel::requestReconnect,
                    )
                }
                val desktopImportProxies: suspend (DesktopProxyImportInput) -> Result<String> = desktopProxyImportRunner::run
                val desktopToggleTunnel: () -> Unit = {
                    tunnel.toggle(
                        scope = subscriptionScope,
                        profileId = selectedTunnelProfileId,
                        systemProxyEnabled = { desktopSettings.useSystemProxy },
                    )
                }
                    val automationPlan = desktopNetworkObservationPlan(desktopSettings)
                    LaunchedEffect(
                        automationPlan.shouldListen,
                        desktopSettings.enableNetworkAutomation,
                        desktopSettings.enableOnDemandVpn,
                        desktopSettings.networkAutomationRules,
                    ) {
                        if (!automationPlan.shouldListen) return@LaunchedEffect
                        var previousNetworkId: String? = null
                        while (true) {
                            val observed = withContext(Dispatchers.IO) {
                                observeDesktopNetwork(
                                    interfaces = readDesktopNetworkInterfaces(),
                                    ssidProvider = { if (automationPlan.requiresWifiSsid) readDesktopWifiSsid() else null },
                                )
                            }
                            val step = evaluateDesktopNetworkAutomation(
                                settings = desktopSettings,
                                availableServerIds = serverLibrary.servers.mapTo(mutableSetOf()) { it.id },
                                selectedServerId = serverLibrary.selectedServerId,
                                vpnRunning = tunnel.coreState.isRunning,
                                previousNetworkId = previousNetworkId,
                                observed = observed,
                            )
                            previousNetworkId = step.networkId
                            when (val action = step.action) {
                                NetworkAutomationAction.None -> Unit
                                NetworkAutomationAction.Disconnect -> if (tunnel.desiredRunning && !tunnel.operationInProgress) {
                                    desktopToggleTunnel()
                                }
                                is NetworkAutomationAction.Restart -> {
                                    runCatching { proxyServerRepository.selectAndCommit(action.serverId) }
                                    tunnel.requestReconnect("Автоматизация сети: смена сервера.")
                                }
                                is NetworkAutomationAction.Start -> {
                                    runCatching { proxyServerRepository.selectAndCommit(action.serverId) }
                                    if (!tunnel.desiredRunning && !tunnel.operationInProgress) desktopToggleTunnel()
                                }
                            }
                            delay(NetworkAutomationPollMillis)
                        }
                    }
                    if (!desktopSettings.onboardingCompleted) {
                        val onboardingLocale = remember { DesktopApplicationLocale(java.util.Locale.getDefault(), java.util.Locale::setDefault) }
                        DesktopOnboardingScreen(
                            settings = desktopSettings,
                            serverCount = serverLibrary.servers.size,
                            subscriptionCount = subscriptionLibrary.subscriptions.size,
                            onSettingsChange = { next ->
                                if (next.languageTag != desktopSettings.languageTag) onboardingLocale.apply(next.languageTag)
                                desktopSettings = next
                                subscriptionScope.launch(Dispatchers.IO) { DesktopSettingsLibraries.saveDefault(next) }
                            },
                            onImportText = { text, _ ->
                                val trimmed = text.trim()
                                if (trimmed.isNotEmpty()) {
                                    val install = trimmed.toDesktopSubscriptionInstallClipboardUriOrNull()
                                    if (install != null) {
                                        desktopPrepareSubscription(install)
                                    } else {
                                        desktopImportProxies(DesktopProxyImportInput.Clipboard(trimmed))
                                    }
                                }
                            },
                            onFinish = {
                                val next = desktopSettings.withOnboardingCompleted()
                                desktopSettings = next
                                subscriptionScope.launch(Dispatchers.IO) { DesktopSettingsLibraries.saveDefault(next) }
                            },
                        )
                        return@Scaffold
                    }
                    when (currentSection) {
                        SkipiMainDestination.Proxy -> DesktopProxyHome(
                            updateBanner = appUpdateState.bannerModel()?.let { banner ->
                                {
                                    SkipiAppUpdateBanner(
                                        banner = banner,
                                        onDismiss = { appUpdateState = appUpdateState.dismissed() },
                                        onPrimaryAction = { browse(banner.update.downloadUrl) { tunnel.coreMessage = it } },
                                    )
                                }
                            },
                            serverLibrary = serverLibrary,
                            subscriptionLibrary = subscriptionLibrary,
                            subscriptionCatalog = desktopSharedApplication.subscriptions.catalog.value,
                            subscriptionUpdate = subscriptionUpdate,
                            serverLink = serverLink,
                            subscriptionUrl = subscriptionUrl,
                            updatingSubscription = subscriptionUpdateInProgress,
                            refreshingSubscriptionIds = setOfNotNull(refreshingSubscriptionId) +
                                batchRefreshingSubscriptionIds,
                            running = tunnel.coreState.isRunning,
                            tunnelIp = tunnelIp,
                            runtimeOutboundMetric = tunnelMemoryText,
                            connecting = tunnel.operationInProgress && tunnel.desiredRunning,
                            // Keep the connected hero actionable while a reconnect is underway:
                            // the handler records a newer user intent before it observes the
                            // operation guard, so a click on Power can still cancel/reverse it.
                            canToggleTunnel = !tunnel.closeRequested && (
                                tunnel.coreState.isRunning || (
                                    !tunnel.operationInProgress &&
                                    selectedServerConfig?.isSuccess == true && !tunnel.systemProxyRecoveryInProgress
                                    )
                                ),
                            tunnelMessage = tunnel.coreMessage,
                            serverMessage = serverLibraryMessage,
                            subscriptionMessage = subscriptionMessage,
                            confirmDeletion = desktopSettings.confirmDeletion,
                            activeProfileName = activeProfileName,
                            activeTrafficConfigId = configLibrary.selectedConfigId,
                            trafficConfigContentById = trafficConfigContentById,
                            exportFullJson = exportProxyServerFullJson,
                            latencyByServerId = latencyByServerId,
                            testingServerIds = testingServerIds,
                            pingingSubscriptionIds = pingingSubscriptionIds,
                            onServerLinkChange = { serverLink = it },
                            onSubscriptionUrlChange = { url ->
                                if (subscriptionUpdateInProgress) {
                                    subscriptionMessage = "Дождитесь завершения обновления подписки."
                                } else {
                                    subscriptionUrl = url
                                    subscriptionUpdate = null
                                    subscriptionMessage = ""
                                }
                            },
                            onToggleTunnel = desktopToggleTunnel,
                            onSelectServer = { serverId ->
                                runCatching {
                                    proxyServerRepository.selectAndCommit(serverId)
                                }
                                    .onSuccess {
                                        serverLibraryMessage = "Сервер выбран."
                                        if (tunnel.coreState.isRunning) {
                                            tunnel.requestReconnect("Сервер изменён.")
                                        }
                                    }
                                    .onFailure { error ->
                                        serverLibraryMessage = "Не удалось сохранить выбор: ${error.message.orEmpty()}"
                                    }
                            },
                            onDeleteServer = { serverId ->
                                val selectedServerId = serverLibrary.selectedServerId
                                val tunnelWasRunning = tunnel.coreState.isRunning
                                val serviceOperationIsInProgress = tunnel.closeRequested || tunnel.operationInProgress ||
                                    tunnel.systemProxyRecoveryInProgress
                                subscriptionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    withHomeDeleteRuntime { stopService, applyStoppedState, onStopFailed ->
                                        runDesktopProxyHomeSingleDelete(
                                            serverId = serverId,
                                            selectedServerId = selectedServerId,
                                            tunnelRunning = tunnelWasRunning,
                                            serviceOperationInProgress = serviceOperationIsInProgress,
                                            stopService = stopService,
                                            dispatchDeleteIfPresent = {
                                                if (proxyServerRepository.servers.value.none { it.id == serverId }) {
                                                    false
                                                } else {
                                                    desktopSharedApplication.store.dispatch(
                                                        SharedApplicationAction.RemoveProxyServer(serverId),
                                                    )
                                                    true
                                                }
                                            },
                                            applyStoppedState = applyStoppedState,
                                            onDeleted = { serverLibraryMessage = "Сервер удалён." },
                                            onMissingServer = {
                                                serverLibraryMessage = "Сначала выберите работающий сервер или профиль."
                                            },
                                            onStopFailed = onStopFailed,
                                        )
                                    }
                                }
                            },
                            onSaveServer = { result, onCompleted ->
                                subscriptionScope.launch {
                                    val outcome = applyProxyServerEditResult(
                                        result = result,
                                        store = desktopSharedApplication.store,
                                        defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                                    )
                                    when (outcome) {
                                        is ProxyServerEditApplyOutcome.Saved -> {
                                            if (result.serverId == null) {
                                                serverLink = ""
                                                val addedMessage = "Сервер ${result.server.getInfo().remarks} добавлен в список."
                                                serverLibraryMessage = addedMessage
                                                if (tunnel.coreState.isRunning && serverLibrary.selectedServerId == outcome.serverId) {
                                                    tunnel.requestReconnect(addedMessage)
                                                }
                                            } else {
                                                serverLibraryMessage = "Сервер изменён."
                                                if (serverLibrary.selectedServerId == result.serverId && tunnel.coreState.isRunning) {
                                                    tunnel.requestReconnect("Активный сервер изменён.")
                                                }
                                            }
                                        }

                                        ProxyServerEditApplyOutcome.Deleted -> {
                                            serverLibraryMessage = "Сервер удалён."
                                        }

                                        is ProxyServerEditApplyOutcome.Failed -> {
                                            serverLibraryMessage = if (result.serverId == null) {
                                                "Не удалось сохранить сервер: ${outcome.reason}"
                                            } else {
                                                "Не удалось сохранить изменения сервера: ${outcome.reason}"
                                            }
                                        }
                                    }
                                    onCompleted(outcome)
                                }
                            },
                            onSelectStrategyMember = { serverId, memberId ->
                                val strategy = serverLibrary.servers
                                    .firstOrNull { it.id == serverId }
                                    ?.decode()
                                    ?.getOrNull() as? StrategyGroup
                                if (strategy == null) {
                                    serverLibraryMessage = "Группа стратегий не найдена."
                                } else {
                                    runCatching {
                                        DesktopServerLibraries.update(
                                            serverLibrary,
                                            serverId,
                                            strategy.copy(selectedMemberId = memberId),
                                        )
                                    }.fold(
                                        onSuccess = { updated ->
                                            DesktopServerLibraries.saveDefault(updated).fold(
                                                onSuccess = {
                                                    serverLibrary = updated
                                                    serverLibraryMessage = "Активный сервер группы изменён."
                                                    if (tunnel.coreState.isRunning) {
                                                        tunnel.requestReconnect("Участник активной группы изменён.")
                                                    }
                                                },
                                                onFailure = { error ->
                                                    serverLibraryMessage = "Не удалось сохранить участника группы: ${error.message.orEmpty()}"
                                                },
                                            )
                                        },
                                        onFailure = { error ->
                                            serverLibraryMessage = "Не удалось изменить участника группы: ${error.message.orEmpty()}"
                                        },
                                    )
                                }
                            },
                            onMeasureServers = { targets -> startDesktopHomeLatencyTest(targets) },
                            onLaunchHomeLatencyTest = { targets, onFinished ->
                                startDesktopHomeLatencyTest(targets, onFinished = onFinished)
                            },
                            onPingSubscriptionServers = { subscriptionId, targets ->
                                startDesktopHomeLatencyTest(targets, subscriptionId)
                            },
                            onCancelLatencyTests = { desktopHomeLatencyJobOwner.cancel() },
                            onRestartService = restartService@{
                                if (tunnel.closeRequested || tunnel.operationInProgress || tunnel.systemProxyRecoveryInProgress) {
                                    return@restartService
                                }
                                if (selectedServerConfig?.isSuccess != true) {
                                    tunnel.coreMessage = "Сначала выберите работающий сервер или профиль."
                                    return@restartService
                                }
                                desktopHomeLatencyJobOwner.cancel()
                                tunnel.desiredRunning = true
                                tunnel.intentVersion += 1
                                tunnel.requestReconnect("Перезапуск SKIPI Core.")
                            },
                            onPrepareSubscription = desktopPrepareSubscription,
                            onDeleteDuplicateServers = { currentGroupServerIds ->
                                val duplicateIds = captureDesktopDuplicateProxyServerIds(
                                    library = serverLibrary,
                                    currentGroupServerIds = currentGroupServerIds,
                                )
                                subscriptionScope.launch {
                                    val result = desktopSharedApplication.store.dispatchAndAwait(
                                        SharedApplicationAction.UpdateProxyCatalog { catalog ->
                                            deleteProxyServerRecords(catalog, duplicateIds)
                                        },
                                    )
                                    serverLibraryMessage = when (val outcome = result.outcome) {
                                        SharedApplicationActionOutcome.Completed -> if (duplicateIds.isNotEmpty()) {
                                            duplicatesDeletedTemplate.formatTemplate("count" to duplicateIds.size)
                                        } else noDuplicatesMessage
                                        is SharedApplicationActionOutcome.Rejected -> outcome.reason
                                        is SharedApplicationActionOutcome.Failed -> outcome.reason
                                    }
                                }
                            },
                            onDeleteInvalidServers = { currentGroupServerIds ->
                                val capturedServers = proxyServerRepository.servers.value
                                val selectedServerId = serverLibrary.selectedServerId
                                val tunnelRunning = tunnel.coreState.isRunning
                                val serviceOperationInProgress = tunnel.closeRequested || tunnel.operationInProgress ||
                                    tunnel.systemProxyRecoveryInProgress
                                subscriptionScope.launch {
                                    withHomeDeleteRuntime { stopService, applyStoppedState, onStopFailed ->
                                        runDesktopProxyHomeDeleteInvalidServers(
                                            currentGroupServerIds = currentGroupServerIds,
                                            servers = capturedServers,
                                            selectedServerId = selectedServerId,
                                            tunnelRunning = tunnelRunning,
                                            serviceOperationInProgress = serviceOperationInProgress,
                                            stopService = stopService,
                                            applyStoppedState = applyStoppedState,
                                            dispatchDelete = { capturedIds ->
                                                desktopSharedApplication.store.dispatchAndAwait(
                                                    SharedApplicationAction.RemoveProxyServers(capturedIds),
                                                ).outcome
                                            },
                                            onDeleted = { count ->
                                                serverLibraryMessage = invalidDeletedTemplate.formatTemplate("count" to count)
                                            },
                                            onNoInvalidServers = { serverLibraryMessage = noInvalidMessage },
                                            onDeleteError = { error ->
                                                serverLibraryMessage = "Не удалось удалить серверы: ${error.message.orEmpty()}"
                                            },
                                            onStopFailed = onStopFailed,
                                        )
                                    }
                                }
                            },
                            onDeleteAllServers = { currentGroupServerIds ->
                                val selectedServerId = serverLibrary.selectedServerId
                                val tunnelRunning = tunnel.coreState.isRunning
                                val serviceOperationInProgress = tunnel.closeRequested || tunnel.operationInProgress ||
                                    tunnel.systemProxyRecoveryInProgress
                                subscriptionScope.launch {
                                    runHomeBulkDelete(
                                        serverIds = currentGroupServerIds,
                                        selectedServerId = selectedServerId,
                                        tunnelRunning = tunnelRunning,
                                        serviceOperationInProgress = serviceOperationInProgress,
                                        deletedTemplate = allDeletedTemplate,
                                        emptyMessage = noServersToDeleteMessage,
                                    )
                                }
                            },
                            onImport = desktopImportProxies,
                            onSaveSubscriptionGroup = { group, isNew, refreshWhenPreviouslyManual ->
                                val existing = desktopSharedApplication.subscriptions.catalog.value.subscriptions
                                    .firstOrNull { subscription -> subscription.id == group.id }
                                val draft = group.toSubscriptionRecord(existing = existing)
                                runInSubscriptionScope {
                                    desktopSharedApplication.store.subscriptionEditorController.save(
                                        draft = draft,
                                        isNew = isNew,
                                        refreshWhenPreviouslyManual = refreshWhenPreviouslyManual,
                                    )
                                }
                            },
                            onSetSubscriptionEnabled = { subscriptionId, enabled ->
                                runInSubscriptionScope {
                                    desktopSharedApplication.store.subscriptionEditorController.setEnabled(
                                        subscriptionId = subscriptionId,
                                        enabled = enabled,
                                    )
                                }
                            },
                            onMoveGroup = { subscriptionId, offset ->
                                runInSubscriptionScope {
                                    desktopSharedApplication.store.subscriptionEditorController.move(
                                        groupId = subscriptionId,
                                        offset = offset,
                                        fixedGroupId = null,
                                    )
                                }
                            },
                            onDeleteSubscription = { subscriptionId ->
                                val removedSubscription = desktopSharedApplication.subscriptions.catalog.value.subscriptions
                                    .firstOrNull { subscription -> subscription.id == subscriptionId }
                                val selectedServerId = serverLibrary.selectedServerId
                                val groupServerIds = proxyServerRepository.servers.value
                                    .asSequence()
                                    .filter { server -> server.sourceSubscriptionId == subscriptionId }
                                    .mapTo(linkedSetOf()) { server -> server.id }
                                val tunnelWasRunning = tunnel.coreState.isRunning
                                val serviceOperationIsInProgress = tunnel.closeRequested || tunnel.operationInProgress ||
                                    tunnel.systemProxyRecoveryInProgress
                                subscriptionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    withHomeDeleteRuntime { stopService, applyStoppedState, onStopFailed ->
                                        runDesktopProxyHomeSubscriptionGroupDelete(
                                            groupServerIds = groupServerIds,
                                            selectedServerId = selectedServerId,
                                            tunnelRunning = tunnelWasRunning,
                                            serviceOperationInProgress = serviceOperationIsInProgress,
                                            groupIsBuiltIn = removedSubscription?.builtIn == true,
                                            stopService = stopService,
                                            deleteGroupIfPresent = {
                                                if (desktopSharedApplication.subscriptions.catalog.value.subscriptions.none { it.id == subscriptionId }) {
                                                    false
                                                } else {
                                                    desktopSharedApplication.store.subscriptionEditorController.remove(subscriptionId)
                                                    true
                                                }
                                            },
                                            applyStoppedState = applyStoppedState,
                                            onDeleted = {
                                                if (removedSubscription?.url == subscriptionUrl) subscriptionUrl = ""
                                                subscriptionMessage = "Подписка и её серверы удалены."
                                            },
                                            onMissingServer = {
                                                serverLibraryMessage = "Сначала выберите работающий сервер или профиль."
                                            },
                                            onStopFailed = onStopFailed,
                                            onDeleteError = { error ->
                                                subscriptionMessage = "Не удалось удалить подписку: ${error.message.orEmpty()}"
                                            },
                                        )
                                    }
                                }
                            },
                            onUpdateSubscriptionById = { subscriptionId ->
                                subscriptionScope.launch {
                                    val result = desktopSubscriptionRefreshCoordinator.refresh(subscriptionId)
                                    val precondition = result.exceptionOrNull()
                                        as? DesktopSubscriptionRefreshPreconditionException
                                    if (precondition != null) {
                                        subscriptionMessage = precondition.message.orEmpty()
                                    }
                                }
                            },
                            onRefreshAllSubscriptions = {
                                subscriptionScope.launch {
                                    desktopSubscriptionRefreshCoordinator.runBatch {
                                        subscriptionUpdateInProgress = true
                                        refreshingSubscriptionId = null
                                        subscriptionUrl = ""
                                        try {
                                            subscriptionMessage = "Обновление подписок…"
                                            subscriptionUpdate = null
                                            val result = desktopSubscriptionRefreshBatchRunner.run()
                                            val loaded = result.refresh.updates
                                            val failures = result.refresh.failures
                                            val commit = result.commitResult
                                            val singleFailure = commit?.singleCommitResult?.failure
                                            subscriptionMessage = when {
                                                loaded.isEmpty() && failures.isEmpty() ->
                                                    noSubscriptionUpdatesMessage
                                                singleFailure != null -> when (singleFailure.scope) {
                                                    SubscriptionRefreshCommitScope.SERVERS ->
                                                        "Список получен, но серверы не сохранены: ${singleFailure.error.message.orEmpty()}"
                                                    SubscriptionRefreshCommitScope.SUBSCRIPTION ->
                                                        "Серверы обновлены, но метаданные подписки не сохранены: ${singleFailure.error.message.orEmpty()}"
                                                    SubscriptionRefreshCommitScope.PROFILE ->
                                                        "Серверы и подписка обновлены, но обновление профиля не завершено: ${singleFailure.error.message.orEmpty()}"
                                                }
                                                else -> formatSubscriptionRefreshMessage(
                                                    summary = SubscriptionRefreshMessageSummary(
                                                        updatedGroupCount = loaded.size,
                                                        failedGroupCount = failures.size,
                                                        importedServerCount = loaded.sumOf { update -> update.servers.size },
                                                        firstFailureMessage = failures.firstOrNull()?.error
                                                            ?.let(::desktopSubscriptionRefreshFailureMessage),
                                                    ),
                                                    successTemplate = subscriptionRefreshSuccessTemplate,
                                                    failedTemplate = subscriptionRefreshFailedTemplate,
                                                )
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Throwable) {
                                            subscriptionMessage = "Не удалось обновить подписки: ${error.message.orEmpty()}"
                                        } finally {
                                            subscriptionUpdateInProgress = false
                                        }
                                    }
                                }
                            },
                            contentPadding = contentPadding,
                            desktopSettings = desktopSettings,
                            onMoveServer = { fromServerId, toServerId ->
                                runCatching {
                                    proxyServerRepository.updateLibrary { latest ->
                                        latest.copy(
                                            servers = reorderServerInLibrary(
                                                latest.servers,
                                                fromServerId = fromServerId,
                                                toServerId = toServerId,
                                            ),
                                        )
                                    }
                                    Unit
                                }
                            },
                            onHomeSortModeChange = { mode ->
                                subscriptionScope.launch {
                                    homeSortSaveMutex.withLock {
                                        val updated = desktopSettings.copy(proxyServerListSort = mode.toAndroidValue())
                                        withContext(Dispatchers.IO) { DesktopSettingsLibraries.saveDefault(updated) }
                                            .onSuccess { desktopSettings = updated }
                                            .onFailure { error ->
                                                tunnel.coreMessage = "Не удалось сохранить настройки: ${error.message.orEmpty()}"
                                            }
                                    }
                                }
                            },
                        )

                        SkipiMainDestination.Configs -> DesktopConfigsScreen(
                            configLibrary = configLibrary,
                            onTransformConfigLibrary = { transform ->
                                val result = desktopSharedApplication.trafficConfigs.transformLatestWithPrevious(transform)
                                val activeBefore = result.previous.selectedConfigId
                                    ?.let { selectedId -> result.previous.configs.firstOrNull { profile -> profile.id == selectedId } }
                                val activeAfter = result.updated.selectedConfigId
                                    ?.let { selectedId -> result.updated.configs.firstOrNull { profile -> profile.id == selectedId } }
                                configLibrary = result.updated
                            val proxyGroupInputsChanged =
                                result.previous.configs != result.updated.configs ||
                                    result.previous.selectedConfigId != result.updated.selectedConfigId
                            if (proxyGroupInputsChanged) {
                                subscriptionScope.launch {
                                    try {
                                        configProxyGroupReconciler.reconcile()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Throwable) {
                                        serverLibraryMessage =
                                            "Не удалось синхронизировать группы серверов: ${error.message.orEmpty()}"
                                    }
                                }
                            }
                                if (tunnel.coreState.isRunning && (
                                    activeBefore?.id != activeAfter?.id || activeBefore?.content != activeAfter?.content
                                    )) {
                                    tunnel.requestReconnect("Активный профиль изменён.")
                                }
                                result.updated
                            },
                            fetchUserAgent = desktopSettings.subscriptionUserAgent,
                            fetchTimeoutSeconds = desktopSettings.subscriptionFetchTimeoutSeconds,
                            fetchProfileDocument = desktopTrafficConfigManualProfileFetcher::fetch,
                            contentPadding = contentPadding,
                            resourceActionScope = subscriptionScope,
                            onResourceFileStatusMessage = { message ->
                                resourceFileFeedback.value = message
                            },
                            desktopSettings = desktopSettings,
                            exportProxyServerFullJson = exportProxyServerFullJson,
                            onApplyGlobalProxyGroupResult = { result, onCompleted ->
                                subscriptionScope.launch {
                                    val outcome = applyProxyServerEditResult(
                                        result = result,
                                        store = desktopSharedApplication.store,
                                        defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                                    )
                                    onCompleted(outcome)
                                }
                            },
                            proxyServers = routeProxyServers,
                            subscriptionCatalog = routeSubscriptionCatalog,
                            resourceFiles = desktopProfileResourceFiles,
                            latencyByServerId = latencyByServerId,
                            resourceServices = desktopResourceFileServices,
                            resourceFileStatusMessage = resourceFileStatusMessage,
                        )

                        SkipiMainDestination.Settings -> DesktopSettingsScreen(
                            appUpdateState = appUpdateState,
                            onCheckForAppUpdate = ::checkForAppUpdate,
                            settings = desktopSettings,
                            diagnostics = desktopDiagnostics,
                            coreLogs = desktopCoreLogs,
                            automationServers = desktopNetworkAutomationServers(serverLibrary),
                            backupHost = desktopBackupHost,
                            onSettingsChange = { updated ->
                                val restartRequired = desktopSettings.localProxyPort != updated.localProxyPort ||
                                    desktopSettings.localHttpProxyPort != updated.localHttpProxyPort ||
                                    desktopSettings.useSystemProxy != updated.useSystemProxy ||
                                    desktopSettings.localProxyListenAddress != updated.localProxyListenAddress ||
                                    desktopSettings.coreLogLevel != updated.coreLogLevel ||
                                    desktopSettings.enableAccessLog != updated.enableAccessLog
                                desktopSettings = updated
                                if (restartRequired && tunnel.coreState.isRunning) {
                                    tunnel.requestReconnect("Параметры локального прокси изменены.")
                                }
                            },
                            contentPadding = contentPadding,
                            isTunnelRunning = tunnel.coreState.isRunning,
                            onClearSystemProxy = {
                                subscriptionScope.launch {
                                    withContext(Dispatchers.IO) { systemProxyManager.forceClear() }
                                        .onSuccess {
                                            tunnel.coreMessage = "Системный прокси успешно сброшен."
                                        }
                                        .onFailure { error ->
                                            tunnel.coreMessage = "Не удалось сбросить системный прокси: ${error.message.orEmpty()}"
                                        }
                                }
                            },
                        )
                    }
                }
                }
            }
        }
    }
}
}

private fun desktopSubscriptionRefreshResultMessage(result: SubscriptionRefreshResult): String {
    val commit = result.commit
    val serverWasApplied = SubscriptionRefreshCommitScope.SERVERS in commit.appliedScopes
    val failure = commit.failure
    return when {
        !commit.applicable && serverWasApplied ->
            "Серверы обновлены (${result.loaded.servers.size}), но подписка была изменена или удалена до сохранения метаданных."
        !commit.applicable ->
            "Подписка была изменена или удалена во время загрузки; ответ не применён."
        failure != null -> when (failure.scope) {
            SubscriptionRefreshCommitScope.SERVERS ->
                "Список получен, но серверы не сохранены: ${failure.error.message.orEmpty()}"
            SubscriptionRefreshCommitScope.SUBSCRIPTION ->
                "Серверы обновлены, но метаданные подписки не сохранены: ${failure.error.message.orEmpty()}"
            SubscriptionRefreshCommitScope.PROFILE ->
                "Серверы и подписка обновлены, но обновление профиля не завершено: ${failure.error.message.orEmpty()}"
        }
        else -> "Подписка обновлена: ${result.loaded.servers.size} серверов из ${result.loaded.urlCount} ссылок."
    }
}

@Composable
private fun DesktopBottomNavigation(
    selected: SkipiMainDestination,
    sizeSetting: Int,
    onSelect: (SkipiMainDestination) -> Unit,
) {
    val colors = SkipiTheme.colors
    SkipiExpressiveNavigationBar(
        selectedDestination = selected,
        onSelect = onSelect,
        colors = SkipiExpressiveNavigationColors(
            surface = colors.surface,
            accent = colors.accent,
            onAccent = colors.onAccent,
            onSurfaceVariant = colors.onSurfaceVariant,
            isDark = colors.isDark,
        ),
        size = navigationBarSizeForSetting(sizeSetting),
    )
}

private val SkipiBackground: Color
    @Composable get() = DesktopContentBackground
private const val NetworkAutomationPollMillis = 5_000L

private class DesktopSubscriptionServerStopAction {
    var action: suspend () -> Unit = {}

    suspend fun run() = action()
}
