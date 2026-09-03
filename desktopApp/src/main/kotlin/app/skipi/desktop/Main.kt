// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions
import platform.TunnelConnectRequest
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    val xrayController = remember { DesktopXrayProcessController() }
    val windowsSystemProxy = remember { DesktopWindowsSystemProxyLeaseManager() }
    // Closing is handled from the composable below so it can serialize with an
    // in-flight connect/reconnect and keep Xray alive if proxy restoration fails.
    var closeRequested by remember { mutableStateOf(false) }
    Window(
        state = WindowState(size = DpSize(1180.dp, 860.dp)),
        onCloseRequest = { closeRequested = true },
        title = "SKIPI",
    ) {
        var desktopSettings by remember {
            mutableStateOf(DesktopSettingsLibraries.loadDefault().getOrElse { DesktopAppSettings() })
        }
        CompositionLocalProvider(LocalDesktopThemeMode provides desktopSettings.themeMode) {
            MaterialTheme(colorScheme = desktopColorScheme(desktopSettings.themeMode)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DesktopContentBackground) {
                val subscriptionScope = rememberCoroutineScope()
                val subscriptionFetcher = remember { DesktopSubscriptionFetcher() }
                var configLibrary by remember { mutableStateOf(DesktopConfigLibraries.loadDefault().getOrElse { DesktopConfigLibrary() }) }
                var serverLink by remember { mutableStateOf("") }
                var subscriptionUrl by remember { mutableStateOf("") }
                var subscriptionUpdate by remember { mutableStateOf<DesktopSubscriptionUpdate?>(null) }
                var subscriptionMessage by remember { mutableStateOf("") }
                var subscriptionUpdateInProgress by remember { mutableStateOf(false) }
                var scheduledSubscriptionId by remember { mutableStateOf<Int?>(null) }
                var subscriptionSchedulerTick by remember { mutableStateOf(0) }
                var subscriptionLibrary by remember {
                    mutableStateOf(DesktopSubscriptionLibraries.loadDefault().getOrElse { DesktopSubscriptionLibrary() })
                }
                var currentSection by remember { mutableStateOf(DesktopSection.Proxy) }
                var serverLibrary by remember {
                    mutableStateOf(DesktopServerLibraries.loadDefault().getOrElse { DesktopServerLibrary() })
                }
                LaunchedEffect(
                    subscriptionLibrary,
                    subscriptionUpdateInProgress,
                    scheduledSubscriptionId,
                    subscriptionSchedulerTick,
                    closeRequested,
                ) {
                    if (closeRequested || subscriptionUpdateInProgress || scheduledSubscriptionId != null) {
                        return@LaunchedEffect
                    }
                    val refreshPlan = DesktopSubscriptionRefreshPlanner.plan(
                        library = subscriptionLibrary,
                        nowMillis = System.currentTimeMillis(),
                    )
                    val dueSubscription = refreshPlan.dueSubscriptions.firstOrNull()
                    if (dueSubscription != null) {
                        scheduledSubscriptionId = dueSubscription.id
                        return@LaunchedEffect
                    }
                    delay(
                        (refreshPlan.nextDelayMillis ?: DesktopSubscriptionSchedulerIdleDelayMillis)
                            .coerceIn(
                                DesktopSubscriptionSchedulerMinimumDelayMillis,
                                DesktopSubscriptionSchedulerIdleDelayMillis,
                            ),
                    )
                    subscriptionSchedulerTick += 1
                }
                var serverLibraryMessage by remember { mutableStateOf("") }
                val latencyTester = remember { DesktopServerLatencyTester() }
                var latencyByServerId by remember { mutableStateOf<Map<Int, DesktopServerLatencyResult>>(emptyMap()) }
                var testingServerIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
                var xrayProcessState by remember { mutableStateOf(xrayController.state()) }
                var xrayProcessMessage by remember { mutableStateOf("") }
                var pendingTunnelReconnectReason by remember { mutableStateOf<String?>(null) }
                // The reason is user-facing text; the revision is the actual event
                // identity, so two successive edits with identical text still restart.
                var pendingTunnelReconnectRevision by remember { mutableStateOf(0L) }
                var systemProxyRecoveryInProgress by remember { mutableStateOf(windowsSystemProxy.isSupportedHost()) }
                var tunnelOperationInProgress by remember { mutableStateOf(false) }
                var desiredTunnelRunning by remember { mutableStateOf(xrayProcessState.isRunning) }
                var tunnelIntentVersion by remember { mutableStateOf(0L) }
                val localProxyReadiness = remember { DesktopLocalProxyReadiness() }

                fun requestTunnelReconnect(reason: String) {
                    pendingTunnelReconnectReason = reason
                    pendingTunnelReconnectRevision += 1
                }

                LaunchedEffect(windowsSystemProxy) {
                    if (!windowsSystemProxy.isSupportedHost()) {
                        systemProxyRecoveryInProgress = false
                        return@LaunchedEffect
                    }
                    systemProxyRecoveryInProgress = true
                    withContext(Dispatchers.IO) { windowsSystemProxy.recover() }
                        .onSuccess { recovery ->
                            if (recovery.action != DesktopWindowsSystemProxyLeaseAction.NothingToRelease) {
                                xrayProcessMessage = recovery.message
                            }
                        }
                        .onFailure { error ->
                            xrayProcessMessage = "Не удалось восстановить системный прокси Windows: ${error.message.orEmpty()}"
                        }
                    systemProxyRecoveryInProgress = false
                }
                val localProxyOptions = remember(desktopSettings) {
                    LocalProxyXrayConfigOptions(
                        socksPort = desktopSettings.localProxyPort,
                        httpProxyPort = desktopSettings.localHttpProxyPort,
                        enableHttpProxy = desktopSettings.useWindowsSystemProxy,
                        listenAddress = desktopSettings.localProxyListenAddress,
                        logLevel = desktopSettings.coreLogLevel,
                    )
                }
                val selectedServerConfig = remember(serverLibrary, configLibrary, localProxyOptions) {
                    val activeProfile = configLibrary.selectedConfigId
                        ?.let { selectedId -> configLibrary.configs.firstOrNull { profile -> profile.id == selectedId } }
                    if (activeProfile != null) {
                        runCatching {
                            DesktopTrafficProfileXrayConfigFactory.build(
                                profile = activeProfile,
                                serverLibrary = serverLibrary,
                                options = localProxyOptions,
                            )
                        }
                    } else {
                        serverLibrary.selectedServerId
                            ?.let { selectedId -> serverLibrary.servers.firstOrNull { it.id == selectedId } }
                            ?.decode()
                            ?.mapCatching { server ->
                                LocalProxyXrayConfigFactory.build(
                                    server = server,
                                    options = localProxyOptions,
                                )
                            }
                    }
                }
                val activeProfileName = configLibrary.selectedConfigId
                    ?.let { selectedId -> configLibrary.configs.firstOrNull { profile -> profile.id == selectedId } }
                    ?.name
                val latestSelectedServerConfig by rememberUpdatedState(selectedServerConfig)
                val latestDesktopSettings by rememberUpdatedState(desktopSettings)
                val desktopTunnelController = remember(xrayController, windowsSystemProxy) {
                    DesktopTunnelController(
                        configForProfile = {
                            latestSelectedServerConfig?.fold(
                                onSuccess = { config -> Result.success(config) },
                                onFailure = { error -> Result.failure(error) },
                            ) ?: Result.failure(IllegalStateException("Select a working server before connecting"))
                        },
                        startProcess = xrayController::start,
                        stopProcess = xrayController::stop,
                        processState = xrayController::state,
                        awaitSystemProxyEndpoint = {
                            val settings = latestDesktopSettings
                            if (!settings.useWindowsSystemProxy || !windowsSystemProxy.isSupportedHost()) {
                                Result.success(Unit)
                            } else {
                                localProxyReadiness.awaitLoopbackHttpEndpoint(
                                    port = settings.localHttpProxyPort,
                                    processState = xrayController::state,
                                )
                            }
                        },
                        acquireSystemProxy = {
                            val settings = latestDesktopSettings
                            when {
                                !settings.useWindowsSystemProxy -> Result.success(Unit)
                                !windowsSystemProxy.isSupportedHost() -> Result.failure(
                                    IllegalStateException("Windows system proxy is available only on Windows"),
                                )

                                else -> windowsSystemProxy.acquire(
                                    DesktopWindowsHttpProxyEndpoint(port = settings.localHttpProxyPort),
                                ).map { }
                            }
                        },
                        releaseSystemProxy = {
                            if (windowsSystemProxy.isSupportedHost()) {
                                windowsSystemProxy.release().map { }
                            } else {
                                Result.success(Unit)
                            }
                        },
                        systemProxySupported = windowsSystemProxy::isSupportedHost,
                    )
                }
                val selectedTunnelProfileId = configLibrary.selectedConfigId?.toString()
                    ?: serverLibrary.selectedServerId?.toString()
                    ?: "selected-server"

                LaunchedEffect(xrayController, desktopTunnelController) {
                    while (true) {
                        delay(750)
                        val previous = xrayProcessState
                        val current = xrayController.state()
                        if (current != previous) {
                            xrayProcessState = current
                            if (previous.isRunning && !current.isRunning) {
                                val snapshot = withContext(Dispatchers.IO) { desktopTunnelController.snapshot() }
                                desiredTunnelRunning = false
                                val detail = snapshot.failure?.message.orEmpty()
                                    .ifBlank { current.lastOutput.lineSequence().lastOrNull().orEmpty() }
                                xrayProcessMessage = "Процесс Xray остановился. $detail"
                            }
                        }
                    }
                }

                LaunchedEffect(pendingTunnelReconnectReason, pendingTunnelReconnectRevision, closeRequested) {
                    val reason = pendingTunnelReconnectReason ?: return@LaunchedEffect
                    if (closeRequested) return@LaunchedEffect
                    // A manual action carries a generation.  It can supersede a queued
                    // automatic reconnect even after this coroutine has started waiting.
                    val operationIntentVersion = tunnelIntentVersion
                    val reconnectRevision = pendingTunnelReconnectRevision
                    while (tunnelOperationInProgress || systemProxyRecoveryInProgress) {
                        delay(50)
                    }
                    if (
                        closeRequested ||
                        operationIntentVersion != tunnelIntentVersion ||
                        reconnectRevision != pendingTunnelReconnectRevision
                    ) {
                        return@LaunchedEffect
                    }
                    tunnelOperationInProgress = true
                    try {
                        val shouldRun = desiredTunnelRunning
                        xrayProcessMessage = if (shouldRun) "$reason Переподключение Xray…" else "$reason Отключение Xray…"
                        withContext(Dispatchers.IO) { desktopTunnelController.disconnect() }.onFailure { error ->
                            xrayProcessState = xrayController.state()
                            if (operationIntentVersion == tunnelIntentVersion) {
                                desiredTunnelRunning = xrayProcessState.isRunning
                            }
                            xrayProcessMessage = "Не удалось остановить Xray: ${error.message.orEmpty()}"
                            return@LaunchedEffect
                        }
                        xrayProcessState = xrayController.state()
                        if (
                            !shouldRun ||
                            closeRequested ||
                            operationIntentVersion != tunnelIntentVersion ||
                            reconnectRevision != pendingTunnelReconnectRevision
                        ) {
                            if (operationIntentVersion == tunnelIntentVersion) {
                                desiredTunnelRunning = false
                                xrayProcessMessage = "$reason Локальный туннель Xray остановлен."
                            }
                            return@LaunchedEffect
                        }
                        withContext(Dispatchers.IO) {
                            desktopTunnelController.connect(TunnelConnectRequest(selectedTunnelProfileId))
                        }.onSuccess {
                            xrayProcessState = xrayController.state()
                            val systemProxySuffix = if (desktopSettings.useWindowsSystemProxy) {
                                " и системный прокси Windows"
                            } else {
                                ""
                            }
                            xrayProcessMessage = "$reason Xray переподключён$systemProxySuffix, PID ${xrayProcessState.pid}."
                        }.onFailure { error ->
                            xrayProcessState = xrayController.state()
                            if (operationIntentVersion == tunnelIntentVersion) {
                                desiredTunnelRunning = xrayProcessState.isRunning
                            }
                            xrayProcessMessage = "Не удалось переподключить Xray: ${error.message.orEmpty()}"
                        }
                    } finally {
                        tunnelOperationInProgress = false
                        if (
                            !closeRequested &&
                            operationIntentVersion != tunnelIntentVersion &&
                            desiredTunnelRunning != xrayController.state().isRunning
                        ) {
                            requestTunnelReconnect("Выполняю последнее действие с туннелем.")
                        } else if (
                            pendingTunnelReconnectRevision == reconnectRevision &&
                            pendingTunnelReconnectReason == reason
                        ) {
                            pendingTunnelReconnectReason = null
                        }
                    }
                }

                LaunchedEffect(closeRequested) {
                    if (!closeRequested) return@LaunchedEffect
                    // Do not let a queued settings/profile reconnect race shutdown.
                    pendingTunnelReconnectReason = null
                    while (tunnelOperationInProgress || systemProxyRecoveryInProgress) {
                        delay(50)
                    }
                    tunnelOperationInProgress = true
                    try {
                        xrayProcessMessage = "Безопасное завершение SKIPI…"
                        withContext(Dispatchers.IO) { desktopTunnelController.disconnect() }
                            .onSuccess {
                                xrayProcessState = xrayController.state()
                                exitApplication()
                            }
                            .onFailure { error ->
                                xrayProcessState = xrayController.state()
                                xrayProcessMessage =
                                    "Не удалось вернуть системный прокси; Xray оставлен запущенным. " +
                                        "Повторите закрытие: ${error.message.orEmpty()}"
                                closeRequested = false
                            }
                    } finally {
                        tunnelOperationInProgress = false
                    }
                }

                Scaffold(
                    bottomBar = {
                        DesktopBottomNavigation(
                            selected = currentSection,
                            onSelect = { currentSection = it },
                        )
                    },
                ) { contentPadding ->
                    when (currentSection) {
                        DesktopSection.Proxy -> DesktopProxyHome(
                            serverLibrary = serverLibrary,
                            subscriptionLibrary = subscriptionLibrary,
                            subscriptionUpdate = subscriptionUpdate,
                            serverLink = serverLink,
                            subscriptionUrl = subscriptionUrl,
                            updatingSubscription = subscriptionUpdateInProgress,
                            running = xrayProcessState.isRunning,
                            // Keep the connected hero actionable while a reconnect is underway:
                            // the handler records a newer user intent before it observes the
                            // operation guard, so a click on Power can still cancel/reverse it.
                            canToggleTunnel = !closeRequested && (
                                xrayProcessState.isRunning || (
                                    !tunnelOperationInProgress &&
                                    selectedServerConfig?.isSuccess == true && !systemProxyRecoveryInProgress
                                    )
                                ),
                            tunnelMessage = xrayProcessMessage,
                            serverMessage = serverLibraryMessage,
                            subscriptionMessage = subscriptionMessage,
                            compactConnection = desktopSettings.compactHome,
                            confirmDeletion = desktopSettings.confirmDeletion,
                            activeProfileName = activeProfileName,
                            activeTrafficConfigId = configLibrary.selectedConfigId,
                            latencyByServerId = latencyByServerId,
                            testingServerIds = testingServerIds,
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
                            onToggleTunnel = toggleTunnel@{
                                if (closeRequested) return@toggleTunnel
                                // Record intent before looking at the in-flight operation.  This
                                // makes a Power click stronger than an older automatic reconnect.
                                desiredTunnelRunning = !desiredTunnelRunning
                                tunnelIntentVersion += 1
                                val operationIntentVersion = tunnelIntentVersion
                                pendingTunnelReconnectReason = null
                                if (tunnelOperationInProgress) {
                                    xrayProcessMessage = if (desiredTunnelRunning) {
                                        "Подключение будет выполнено после текущей операции Xray."
                                    } else {
                                        "Отключение будет выполнено после текущей операции Xray."
                                    }
                                } else subscriptionScope.launch {
                                    tunnelOperationInProgress = true
                                    try {
                                        if (desiredTunnelRunning) {
                                            if (xrayController.state().isRunning) {
                                                xrayProcessState = xrayController.state()
                                                xrayProcessMessage = "Туннель Xray уже подключён."
                                            } else {
                                                xrayProcessMessage = "Подключение Xray…"
                                                withContext(Dispatchers.IO) {
                                                    desktopTunnelController.connect(TunnelConnectRequest(selectedTunnelProfileId))
                                                }.onSuccess {
                                                    xrayProcessState = xrayController.state()
                                                    val systemProxySuffix = if (desktopSettings.useWindowsSystemProxy) {
                                                        " и системный прокси Windows"
                                                    } else {
                                                        ""
                                                    }
                                                    xrayProcessMessage = "Туннель Xray$systemProxySuffix запущен, PID ${xrayProcessState.pid}."
                                                }.onFailure { error ->
                                                    xrayProcessState = xrayController.state()
                                                    if (operationIntentVersion == tunnelIntentVersion) {
                                                        desiredTunnelRunning = xrayProcessState.isRunning
                                                    }
                                                    xrayProcessMessage = "Не удалось запустить Xray: ${error.message.orEmpty()}"
                                                }
                                            }
                                        } else {
                                            xrayProcessMessage = "Отключение Xray…"
                                            withContext(Dispatchers.IO) { desktopTunnelController.disconnect() }
                                                .onSuccess {
                                                    xrayProcessState = xrayController.state()
                                                    if (operationIntentVersion == tunnelIntentVersion) {
                                                        desiredTunnelRunning = false
                                                    }
                                                    xrayProcessMessage = if (desktopSettings.useWindowsSystemProxy) {
                                                        "Туннель Xray и системный прокси Windows остановлены."
                                                    } else {
                                                        "Локальный туннель Xray остановлен."
                                                    }
                                                }
                                                .onFailure { error ->
                                                    xrayProcessState = xrayController.state()
                                                    if (operationIntentVersion == tunnelIntentVersion) {
                                                        desiredTunnelRunning = xrayProcessState.isRunning
                                                    }
                                                    xrayProcessMessage = "Не удалось остановить Xray: ${error.message.orEmpty()}"
                                                }
                                        }
                                    } finally {
                                        tunnelOperationInProgress = false
                                        if (
                                            !closeRequested &&
                                            operationIntentVersion != tunnelIntentVersion &&
                                            desiredTunnelRunning != xrayController.state().isRunning
                                        ) {
                                            requestTunnelReconnect("Выполняю последнее действие с туннелем.")
                                        }
                                    }
                                }
                            },
                            onSelectServer = { serverId ->
                                runCatching { DesktopServerLibraries.select(serverLibrary, serverId) }
                                    .onSuccess { updated ->
                                        DesktopServerLibraries.saveDefault(updated).onSuccess {
                                            serverLibrary = updated
                                            serverLibraryMessage = "Сервер выбран."
                                            if (xrayProcessState.isRunning) {
                                                requestTunnelReconnect("Сервер изменён.")
                                            }
                                        }.onFailure { error ->
                                            serverLibraryMessage = "Не удалось сохранить выбор: ${error.message.orEmpty()}"
                                        }
                                    }
                            },
                            onDeleteServer = { serverId ->
                                val deletedSelectedServer = serverLibrary.selectedServerId == serverId
                                val updated = DesktopServerLibraries.remove(serverLibrary, serverId)
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLibraryMessage = "Сервер удалён."
                                    if (deletedSelectedServer && xrayProcessState.isRunning) {
                                        requestTunnelReconnect("Активный сервер удалён.")
                                    }
                                }.onFailure { error ->
                                    serverLibraryMessage = "Не удалось удалить сервер: ${error.message.orEmpty()}"
                                }
                            },
                            onAddServer = { server ->
                                val updated = DesktopServerLibraries.add(serverLibrary, server)
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLink = ""
                                    serverLibraryMessage = "Сервер добавлен и выбран."
                                    if (xrayProcessState.isRunning) {
                                        requestTunnelReconnect("Сервер добавлен и выбран.")
                                    }
                                }.onFailure { error ->
                                    serverLibraryMessage = "Не удалось сохранить сервер: ${error.message.orEmpty()}"
                                }
                            },
                            onUpdateServer = { serverId, server ->
                                runCatching {
                                    DesktopServerLibraries.update(serverLibrary, serverId, server)
                                }.onSuccess { updated ->
                                    DesktopServerLibraries.saveDefault(updated).onSuccess {
                                        serverLibrary = updated
                                        serverLibraryMessage = "Сервер изменён."
                                        if (serverLibrary.selectedServerId == serverId && xrayProcessState.isRunning) {
                                            requestTunnelReconnect("Активный сервер изменён.")
                                        }
                                    }.onFailure { error ->
                                        serverLibraryMessage = "Не удалось сохранить изменения сервера: ${error.message.orEmpty()}"
                                    }
                                }.onFailure { error ->
                                    serverLibraryMessage = "Не удалось изменить сервер: ${error.message.orEmpty()}"
                                }
                            },
                            onMeasureServers = { targets ->
                                val eligible = targets
                                    .distinctBy { (serverId, _) -> serverId }
                                    .filter { (serverId, server) ->
                                        serverId !in testingServerIds && server.desktopTcpEndpointOrNull() != null
                                    }
                                    .take(MaxHomeLatencyChecks)
                                if (eligible.isEmpty()) {
                                    serverLibraryMessage = "Нет доступных серверов для TCP-проверки."
                                } else {
                                    subscriptionScope.launch {
                                        val testedIds = eligible.map { (serverId, _) -> serverId }.toSet()
                                        testingServerIds = testingServerIds + testedIds
                                        try {
                                            val results = withContext(Dispatchers.IO) {
                                                eligible.map { (serverId, server) ->
                                                    async {
                                                        val result = server.desktopTcpEndpointOrNull()
                                                            ?.let { endpoint ->
                                                                latencyTester.measure(
                                                                    host = endpoint.host,
                                                                    port = endpoint.port,
                                                                    timeout = Duration.ofSeconds(2),
                                                                )
                                                            }
                                                            ?: DesktopServerLatencyResult.Error("No direct server endpoint")
                                                        serverId to result
                                                    }
                                                }.awaitAll()
                                            }
                                            latencyByServerId = latencyByServerId + results.toMap()
                                            val success = results.count { (_, result) -> result is DesktopServerLatencyResult.Success }
                                            val timeout = results.count { (_, result) -> result == DesktopServerLatencyResult.Timeout }
                                            val failed = results.size - success - timeout
                                            val skipped = (targets.size - eligible.size).coerceAtLeast(0)
                                            serverLibraryMessage = "TCP-проверка: $success доступны, $timeout тайм-аут, $failed ошибок." +
                                                if (skipped > 0) " Проверены первые $MaxHomeLatencyChecks доступных серверов." else ""
                                        } finally {
                                            testingServerIds = testingServerIds - testedIds
                                        }
                                    }
                                }
                            },
                            onPrepareSubscription = { install ->
                                if (subscriptionUpdateInProgress) {
                                    Result.failure(IllegalStateException("Дождитесь завершения обновления подписки."))
                                } else {
                                    runCatching {
                                        val existing = subscriptionLibrary.subscriptions
                                            .firstOrNull { subscription -> subscription.url == install.url }
                                        val prepared = DesktopSubscriptionLibraries.addOrReplace(
                                            library = subscriptionLibrary,
                                            url = install.url,
                                            userAgent = install.userAgent,
                                            name = existing?.name.orEmpty().ifBlank { install.name },
                                        )
                                        DesktopSubscriptionLibraries.saveDefault(prepared).getOrThrow()
                                        subscriptionLibrary = prepared
                                        subscriptionUrl = install.url
                                        subscriptionMessage = "Подписка сохранена. Загружаю серверы…"
                                    }
                                }
                            },
                            onImport = import@{ input ->
                                val selectedServerBefore = serverLibrary.selectedServerId
                                val plan = DesktopProxyImportPlanner.plan(
                                    input = input,
                                    existing = DesktopProxyImportExisting(
                                        serverFingerprints = serverLibrary.servers.mapNotNull { stored ->
                                            stored.decode().getOrNull()?.connectionFingerprint()
                                        }.toSet(),
                                        subscriptionUrls = subscriptionLibrary.subscriptions.mapTo(mutableSetOf()) { it.url },
                                        configContents = configLibrary.configs.mapTo(mutableSetOf()) { it.content },
                                        serverDuplicatePolicy = DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated,
                                        subscriptionDuplicatePolicy =
                                            DesktopProxyImportDuplicatePolicy.KeepExistingDeduplicateRepeated,
                                    ),
                                )
                                val errors = plan.diagnostics.filter { diagnostic ->
                                    diagnostic.severity == DesktopProxyImportDiagnosticSeverity.Error
                                }
                                if (errors.isNotEmpty()) {
                                    return@import Result.failure(
                                        IllegalArgumentException(errors.joinToString(" ") { it.message }),
                                    )
                                }
                                val committed = DesktopProxyImportCommitter.commit(
                                    plan = plan,
                                    serverLibrary = serverLibrary,
                                    subscriptionLibrary = subscriptionLibrary,
                                    configLibrary = configLibrary,
                                    subscriptionUserAgent = desktopSettings.subscriptionUserAgent,
                                )
                                val savedSections = mutableListOf<String>()
                                try {
                                    if (committed.serverLibrary != serverLibrary) {
                                        DesktopServerLibraries.saveDefault(committed.serverLibrary).getOrThrow()
                                        serverLibrary = committed.serverLibrary
                                        savedSections += "серверы"
                                    }
                                    if (committed.subscriptionLibrary != subscriptionLibrary) {
                                        DesktopSubscriptionLibraries.saveDefault(committed.subscriptionLibrary).getOrThrow()
                                        subscriptionLibrary = committed.subscriptionLibrary
                                        savedSections += "подписки"
                                    }
                                    if (committed.configLibrary != configLibrary) {
                                        val activeBefore = configLibrary.selectedConfigId
                                        DesktopConfigLibraries.saveDefault(committed.configLibrary).getOrThrow()
                                        configLibrary = committed.configLibrary
                                        savedSections += "конфиги"
                                        if (xrayProcessState.isRunning && activeBefore != committed.configLibrary.selectedConfigId) {
                                            requestTunnelReconnect("Импорт изменил активный профиль.")
                                        }
                                    }
                                } catch (error: Throwable) {
                                    val prefix = if (savedSections.isEmpty()) {
                                        "Импорт не сохранён"
                                    } else {
                                        "Часть импорта уже сохранена (${savedSections.joinToString()})"
                                    }
                                    return@import Result.failure(
                                        IllegalStateException("$prefix: ${error.message.orEmpty()}", error),
                                    )
                                }
                                if (xrayProcessState.isRunning &&
                                    committed.serverLibrary.selectedServerId != selectedServerBefore
                                ) {
                                    requestTunnelReconnect("Импорт изменил выбранный сервер.")
                                }
                                val diagnostics = plan.diagnostics
                                    .filter { diagnostic -> diagnostic.severity != DesktopProxyImportDiagnosticSeverity.Error }
                                    .map(DesktopProxyImportDiagnostic::message)
                                    .take(2)
                                    .joinToString(" ")
                                val subscriptionHint = if (committed.counts.addedSubscriptions + committed.counts.updatedSubscriptions > 0) {
                                    " Подписки сохранены; их можно обновить из карточки."
                                } else {
                                    ""
                                }
                                Result.success(
                                    committed.summary + subscriptionHint +
                                        diagnostics.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty(),
                                )
                            },
                            onUpdateSubscription = {
                                if (subscriptionUpdateInProgress) {
                                    subscriptionMessage = "Обновление подписки уже выполняется."
                                } else {
                                    val requestedUrl = subscriptionUrl.trim()
                                    // Keep only a baseline for conflict detection.  The actual
                                    // libraries are intentionally read again after all network
                                    // I/O, so a delayed response cannot write an old snapshot over
                                    // a server/config action made by the user in the meantime.
                                    val refreshBaseline = DesktopSubscriptionRefreshCommitter.captureBaseline(
                                        subscriptions = subscriptionLibrary,
                                        servers = serverLibrary,
                                        configs = configLibrary,
                                        url = requestedUrl,
                                    )
                                    val subscriptionUserAgent = subscriptionLibrary.subscriptions
                                        .firstOrNull { subscription -> subscription.url == requestedUrl }
                                        ?.userAgent
                                        ?.takeIf(String::isNotBlank)
                                        ?: desktopSettings.subscriptionUserAgent
                                    subscriptionUpdateInProgress = true
                                    subscriptionScope.launch {
                                        try {
                                        subscriptionMessage = "Обновление подписки…"
                                        subscriptionUpdate = null
                                        val update = runCatching {
                                            withContext(Dispatchers.IO) {
                                                subscriptionFetcher.fetchAndImport(
                                                    url = requestedUrl,
                                                    userAgent = subscriptionUserAgent,
                                                    timeout = Duration.ofSeconds(
                                                        desktopSettings.subscriptionFetchTimeoutSeconds.toLong(),
                                                    ),
                                                )
                                            }
                                        }.getOrElse { error ->
                                            subscriptionMessage = "Не удалось обновить подписку: ${error.message.orEmpty()}"
                                            return@launch
                                        }
                                        if (update.importResult.servers.isEmpty()) {
                                            val metadataOnlyLibrary = runCatching {
                                                DesktopSubscriptionLibraries.addOrReplace(
                                                    library = subscriptionLibrary,
                                                    url = requestedUrl,
                                                    userAgent = subscriptionUserAgent,
                                                    name = update.metadata.profileTitle.orEmpty(),
                                                    metadata = update.metadata,
                                                )
                                            }.getOrElse { error ->
                                                subscriptionMessage = "Список получен, но подписка не сохранена: ${error.message.orEmpty()}"
                                                return@launch
                                            }
                                            DesktopSubscriptionLibraries.saveDefault(metadataOnlyLibrary).onSuccess {
                                                subscriptionLibrary = metadataOnlyLibrary
                                                subscriptionUrl = requestedUrl
                                                subscriptionUpdate = update
                                            }.onFailure { error ->
                                                subscriptionMessage = "Список получен, но подписка не сохранена: ${error.message.orEmpty()}"
                                                return@launch
                                            }
                                            val diagnostics = update.importDiagnostics.take(2).joinToString(" ")
                                            subscriptionMessage = "Подписка не изменила список: не найдено поддерживаемых серверов. " +
                                                "Существующая группа сохранена.${diagnostics.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()}"
                                            return@launch
                                        }

                                        val resolvedEmbeddedConfig = runCatching {
                                            withContext(Dispatchers.IO) {
                                                subscriptionFetcher.resolveEmbeddedConfig(
                                                    metadata = update.metadata,
                                                    userAgent = subscriptionUserAgent,
                                                    timeout = Duration.ofSeconds(
                                                        desktopSettings.subscriptionFetchTimeoutSeconds.toLong(),
                                                    ),
                                                )
                                            }
                                        }

                                        val refreshCommit = runCatching {
                                            DesktopSubscriptionRefreshCommitter.rebaseServers(
                                                baseline = refreshBaseline,
                                                latestSubscriptions = subscriptionLibrary,
                                                latestServers = serverLibrary,
                                                url = requestedUrl,
                                                userAgent = subscriptionUserAgent,
                                                name = update.metadata.profileTitle.orEmpty(),
                                                metadata = update.metadata,
                                                importedServers = update.importResult.servers,
                                            )
                                        }.getOrElse { error ->
                                            subscriptionMessage = "Список получен, но ссылка некорректна: ${error.message.orEmpty()}"
                                            return@launch
                                        }
                                        val readyCommit = when (refreshCommit) {
                                            is DesktopSubscriptionRefreshServerCommit.Conflict -> {
                                                subscriptionMessage = "Подписка не применена: ${refreshCommit.message} Повторите обновление."
                                                return@launch
                                            }

                                            is DesktopSubscriptionRefreshServerCommit.Ready -> refreshCommit
                                        }
                                        DesktopSubscriptionLibraries.saveDefault(readyCommit.subscriptions).getOrElse { error ->
                                            subscriptionMessage = "Список получен, но подписка не сохранена: ${error.message.orEmpty()}"
                                            return@launch
                                        }

                                        val saveServers = if (readyCommit.serverGroupWasReplaced) {
                                            DesktopServerLibraries.saveDefault(readyCommit.servers)
                                        } else {
                                            Result.success(Unit)
                                        }
                                        saveServers.onSuccess {
                                            subscriptionLibrary = readyCommit.subscriptions
                                            serverLibrary = readyCommit.servers
                                            subscriptionUrl = requestedUrl
                                            subscriptionUpdate = update
                                            subscriptionMessage = if (readyCommit.serverGroupWasReplaced) {
                                                "Подписка и её группа серверов обновлены. Получено: " +
                                                    "${update.importResult.servers.size}; отклонено: " +
                                                    "${update.importResult.rejectedUrlCount}."
                                            } else {
                                                "Подписка обновлена, но её группа серверов была изменена во время загрузки и сохранена без замены."
                                            }
                                            update.importDiagnostics.take(2).joinToString(" ")
                                                .takeIf(String::isNotBlank)
                                                ?.let { diagnostics -> subscriptionMessage += " $diagnostics" }
                                            val embeddedConfigMessage = resolvedEmbeddedConfig.fold(
                                                onSuccess = { embedded ->
                                                    embedded?.let { resolved ->
                                                        when (val profileCommit = DesktopSubscriptionRefreshCommitter.rebaseEmbeddedProfile(
                                                            baseline = refreshBaseline,
                                                            latestConfigs = configLibrary,
                                                            subscription = readyCommit.subscription,
                                                            metadata = update.metadata,
                                                            resolved = resolved,
                                                            nowMillis = System.currentTimeMillis(),
                                                        )) {
                                                            is DesktopSubscriptionRefreshProfileCommit.Applied -> {
                                                                val activeBefore = configLibrary.selectedConfigId
                                                                DesktopConfigLibraries.saveDefault(profileCommit.configs).fold(
                                                                    onSuccess = {
                                                                        configLibrary = profileCommit.configs
                                                                        if (xrayProcessState.isRunning && activeBefore != profileCommit.configs.selectedConfigId) {
                                                                            requestTunnelReconnect("Профиль из подписки активирован.")
                                                                        }
                                                                        " Маршрутный профиль ${if (profileCommit.added) "добавлен" else "обновлён"}."
                                                                    },
                                                                    onFailure = { error ->
                                                                        " Серверы обновлены, но профиль из подписки не сохранён: ${error.message.orEmpty()}."
                                                                    },
                                                                )
                                                            }

                                                            DesktopSubscriptionRefreshProfileCommit.Conflict ->
                                                                " Серверы обновлены, но профиль из подписки был изменён во время загрузки и сохранён без замены."

                                                            DesktopSubscriptionRefreshProfileCommit.Locked ->
                                                                " Серверы обновлены, но обновление маршрутного профиля заблокировано."

                                                            is DesktopSubscriptionRefreshProfileCommit.Invalid ->
                                                                " Серверы обновлены, но профиль из подписки не применён: ${profileCommit.message}."
                                                        }
                                                    }
                                                },
                                                onFailure = { error ->
                                                    " Серверы обновлены, но профиль из подписки не загружен: ${error.message.orEmpty()}."
                                                },
                                            ).orEmpty()
                                            subscriptionMessage += embeddedConfigMessage
                                            if (xrayProcessState.isRunning) {
                                                requestTunnelReconnect("Подписка обновлена.")
                                            }
                                        }.onFailure { error ->
                                            subscriptionLibrary = readyCommit.subscriptions
                                            subscriptionUrl = requestedUrl
                                            subscriptionMessage = "Подписка сохранена, но серверы не обновлены: ${error.message.orEmpty()}"
                                        }
                                        } finally {
                                            subscriptionUpdateInProgress = false
                                        }
                                    }
                                }
                            },
                            scheduledSubscriptionId = scheduledSubscriptionId,
                            onScheduledSubscriptionConsumed = {
                                scheduledSubscriptionId = null
                                subscriptionSchedulerTick += 1
                            },
                            onUpdateSubscriptionProvider = { subscriptionId, edit ->
                                if (subscriptionUpdateInProgress) {
                                    Result.failure(IllegalStateException("Дождитесь завершения обновления подписки."))
                                } else {
                                    runCatching {
                                        val previousUrl = subscriptionLibrary.subscriptions
                                            .firstOrNull { subscription -> subscription.id == subscriptionId }
                                            ?.url
                                        val updated = DesktopSubscriptionLibraries.updateProvider(
                                            subscriptionLibrary,
                                            subscriptionId,
                                            edit,
                                        )
                                        DesktopSubscriptionLibraries.saveDefault(updated).getOrThrow()
                                        subscriptionLibrary = updated
                                        if (subscriptionUrl == previousUrl) subscriptionUrl = edit.url.trim()
                                        subscriptionMessage = "Параметры подписки сохранены."
                                    }
                                }
                            },
                            onDeleteSubscription = { subscriptionId ->
                                if (subscriptionUpdateInProgress) {
                                    subscriptionMessage = "Дождитесь завершения обновления подписки."
                                } else {
                                    val deletedSelectedServer = serverLibrary.servers
                                        .firstOrNull { stored -> stored.id == serverLibrary.selectedServerId }
                                        ?.subscriptionId == subscriptionId
                                    val updatedServers = DesktopServerLibraries.removeSubscriptionServers(serverLibrary, subscriptionId)
                                    val updatedSubscriptions = DesktopSubscriptionLibraries.remove(subscriptionLibrary, subscriptionId)
                                    DesktopServerLibraries.saveDefault(updatedServers).onSuccess {
                                        DesktopSubscriptionLibraries.saveDefault(updatedSubscriptions).onSuccess {
                                            serverLibrary = updatedServers
                                            subscriptionLibrary = updatedSubscriptions
                                            subscriptionUrl = ""
                                            subscriptionMessage = "Подписка и её серверы удалены."
                                            if (deletedSelectedServer && xrayProcessState.isRunning) {
                                                requestTunnelReconnect("Активная подписка удалена.")
                                            }
                                        }.onFailure { error ->
                                            subscriptionMessage = "Серверы удалены, но не удалось удалить подписку: ${error.message.orEmpty()}"
                                        }
                                    }.onFailure { error ->
                                        subscriptionMessage = "Не удалось удалить серверы подписки: ${error.message.orEmpty()}"
                                    }
                                }
                            },
                            contentPadding = contentPadding,
                        )

                        DesktopSection.Configs -> DesktopConfigsScreen(
                            configLibrary = configLibrary,
                            onConfigLibraryChange = { updated ->
                                val activeBefore = configLibrary.selectedConfigId
                                    ?.let { selectedId -> configLibrary.configs.firstOrNull { profile -> profile.id == selectedId } }
                                val activeAfter = updated.selectedConfigId
                                    ?.let { selectedId -> updated.configs.firstOrNull { profile -> profile.id == selectedId } }
                                configLibrary = updated
                                if (xrayProcessState.isRunning && (
                                    activeBefore?.id != activeAfter?.id || activeBefore?.content != activeAfter?.content
                                    )) {
                                    requestTunnelReconnect("Активный профиль изменён.")
                                }
                            },
                            fetchUserAgent = desktopSettings.subscriptionUserAgent,
                            fetchTimeoutSeconds = desktopSettings.subscriptionFetchTimeoutSeconds,
                            contentPadding = contentPadding,
                        )

                        DesktopSection.Settings -> DesktopSettingsScreen(
                            settings = desktopSettings,
                            onSettingsChange = { updated ->
                                val restartRequired = desktopSettings.localProxyPort != updated.localProxyPort ||
                                    desktopSettings.localHttpProxyPort != updated.localHttpProxyPort ||
                                    desktopSettings.useWindowsSystemProxy != updated.useWindowsSystemProxy ||
                                    desktopSettings.localProxyListenAddress != updated.localProxyListenAddress ||
                                    desktopSettings.coreLogLevel != updated.coreLogLevel
                                desktopSettings = updated
                                if (restartRequired && xrayProcessState.isRunning) {
                                    requestTunnelReconnect("Параметры локального прокси изменены.")
                                }
                            },
                            contentPadding = contentPadding,
                            isTunnelRunning = xrayProcessState.isRunning,
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun DesktopBottomNavigation(
    selected: DesktopSection,
    onSelect: (DesktopSection) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().background(SkipiBackground).padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp).height(82.dp),
            color = SkipiCard,
            shape = RoundedCornerShape(38.dp),
            border = BorderStroke(1.dp, Color(0xFF35373E)),
            shadowElevation = 10.dp,
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                DesktopSection.entries.forEach { section ->
                    val isSelected = section == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (isSelected) Color(0xFF747474) else Color.Transparent)
                            .clickable { onSelect(section) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            section.icon(),
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFFF4F4F6) else SkipiMuted,
                        )
                        Text(
                            section.title,
                            color = if (isSelected) Color(0xFFF4F4F6) else SkipiMuted,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

private enum class DesktopSection(val title: String) {
    Proxy("Прокси"),
    Configs("Конфиги"),
    Settings("Настройки");

    fun icon() = when (this) {
        Proxy -> Icons.AutoMirrored.Outlined.List
        Configs -> Icons.Outlined.Tune
        Settings -> Icons.Outlined.Hexagon
    }
}

private val SkipiBackground: Color
    @Composable get() = DesktopContentBackground
private val SkipiCard = Color(0xFF202126)
private val SkipiMuted = Color(0xFF9A9DA8)
private const val MaxHomeLatencyChecks = 24
private const val DesktopSubscriptionSchedulerMinimumDelayMillis = 1_000L
private const val DesktopSubscriptionSchedulerIdleDelayMillis = 60L * 60L * 1_000L
