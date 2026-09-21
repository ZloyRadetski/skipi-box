// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.os.SystemClock
import ui.text.themedFontWeight
import java.util.Locale
import app.effects.resolveActiveNetworkConfig
import app.skipi.ui.home.SkipiConnectionHeroPhase
import app.skipi.ui.home.SkipiProxyHeroClassicCard
import app.skipi.ui.home.SkipiProxyHeroColors
import app.skipi.ui.home.SkipiProxyHeroCompactCard
import app.skipi.ui.home.SkipiProxyHeroLatency
import app.skipi.ui.home.SkipiProxyHeroState
import app.skipi.ui.home.SkipiProxyHeroTypography
import app.skipi.ui.home.SkipiProxyHomeHeader
import app.skipi.ui.home.SkipiProxyHomeHeaderAction
import app.skipi.ui.home.SkipiProxyHomeHeaderColors
import app.skipi.ui.home.SkipiProxyHomeHeaderLabels
import app.skipi.ui.home.SkipiProxyHomeHeaderState
import features.config.withActiveTrafficConfig
import features.networkautomation.engine.NetworkAutomationDecision
import features.networkautomation.engine.NetworkAutomationEvaluator
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.AppState
import app.activeTunnelTargetDisplayName
import app.proxyServerIdFromOutboundTag
import app.ProxyServerListState
import app.ProxyServerLatencyTesting
import app.ProxyServerState
import app.isTestingLatency
import app.modes.ConnectionDisplayModeClassic
import app.modes.ConnectionDisplayModeCompact
import app.modes.ProxyServerListSortDefault
import app.modes.SubscriptionPingModeHttp
import app.R
import app.collectAppState
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.model.StrategyGroup
import kotlinx.coroutines.delay
import ui.KeyColors
import ui.StatusColorDefaults
import ui.isInDarkTheme
import ui.keyColorFor
import ui.resolveSystemAccentColor
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import app.navigation.Navigator
import app.navigation.Route
import data.AndroidAppStateStore
import engine.proxy.latency.ProxyServerLatencyTestMode
import engine.xray.strategyGroupMembers
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import features.proxy.server.usecase.ProxyServerImportFileUseCase
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.createProxyServer
import features.proxy.server.usecase.deleteDuplicateServersInGroup
import features.proxy.server.usecase.deleteInvalidServersInGroup
import features.proxy.server.usecase.importProxyServersFromText
import features.proxy.server.usecase.updatableSubscriptionGroups
import features.proxy.server.usecase.withDeletedProxyServers
import features.proxy.server.usecase.withImportedProxyServers
import features.proxy.server.usecase.withUpdatedSubscriptionServers
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.SubscriptionInstallConfigUseCase
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.runtime.AndroidSubscriptionFetcher
import features.subscription.subscriptionInstallMessage
import features.subscription.usecase.subscriptionUpdateMessage
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import features.subscription.toSubscriptionInstallConfigOrNull
import features.settings.currentTunnelMemoryPssKb
import features.settings.formatTunnelMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.clipboard.getPlainText
import ui.components.DeleteConfirmationDialog
import ui.feedback.AndroidToastTipNotifier
import ui.feedback.LocalAppHaptics
import ui.layout.AdaptiveTopAppBar
import ui.text.formatTemplate

@Composable
internal fun ProxyServerListTopBar(
    groupState: ProxyServerListGroups,
    selectedServer: ProxyServerState?,
    proxyListState: ProxyServerListState,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    qrScanner: suspend () -> String?,
    proxyServerImportFileUseCase: ProxyServerImportFileUseCase,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    proxyServiceUseCase: ProxyServiceUseCase,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
    onTestProxyServerLatency: (List<ProxyServerState>, ProxyServerLatencyTestMode, String, Boolean, (() -> Unit)?) -> Unit,
    onCancelProxyServerLatency: () -> Unit,
) {
    var pendingDeletionAction by remember { mutableStateOf<ProxyServerListToolAction?>(null) }

    fun executeToolAction(action: ProxyServerListToolAction) {
        handleProxyServerListToolAction(
            action = action,
            groupState = groupState,
            selectedServer = selectedServer,
            proxyListState = proxyListState,
            stateStore = stateStore,
            updateAppState = updateAppState,
            subscriptionFetcher = subscriptionFetcher,
            proxyServiceUseCase = proxyServiceUseCase,
            clipboard = clipboard,
            tipNotifier = tipNotifier,
            scope = scope,
            backgroundScope = backgroundScope,
            messages = messages,
            serviceOperationInProgress = serviceOperationInProgress,
            runProxyServiceOperation = runProxyServiceOperation,
            onTestProxyServerLatency = { servers, mode, template, single ->
                onTestProxyServerLatency(servers, mode, template, single, null)
            },
        )
    }

    fun requestToolAction(action: ProxyServerListToolAction) {
        if (action.isDeletion && proxyListState.enableDeletionConfirmation) {
            pendingDeletionAction = action
        } else {
            executeToolAction(action)
        }
    }

    fun handleAddAction(action: ProxyServerListAddAction) {
        handleProxyServerListAddAction(
            action = action,
            groupState = groupState,
            proxyListState = proxyListState,
            stateStore = stateStore,
            updateAppState = updateAppState,
            navigator = navigator,
            qrScanner = qrScanner,
            proxyServerImportFileUseCase = proxyServerImportFileUseCase,
            subscriptionFetcher = subscriptionFetcher,
            clipboard = clipboard,
            tipNotifier = tipNotifier,
            scope = scope,
            backgroundScope = backgroundScope,
            messages = messages,
            resultKey = resultKey,
        )
    }

    val isPinging = remember(groupState.currentFilteredServers) {
        groupState.currentFilteredServers.any { it.isTestingLatency }
    }
    val hapticFeedback = LocalHapticFeedback.current
    fun headerAddAction(
        action: ProxyServerListAddAction,
        title: String,
    ) = SkipiProxyHomeHeaderAction(
        id = action.name,
        title = title,
    )

    fun headerToolAction(
        action: ProxyServerListToolAction,
        title: String,
        selected: Boolean = false,
    ) = SkipiProxyHomeHeaderAction(
        id = action.name,
        title = title,
        selected = selected,
    )

    val addActions = listOf(
        headerAddAction(
            ProxyServerListAddAction.ScanQrCode,
            stringResource(R.string.proxy_server_list_scan_qr_code),
        ),
        headerAddAction(
            ProxyServerListAddAction.Clipboard,
            stringResource(R.string.proxy_server_list_import_clipboard),
        ),
        headerAddAction(
            ProxyServerListAddAction.File,
            stringResource(R.string.proxy_server_list_import_file),
        ),
        SkipiProxyHomeHeaderAction(
            id = "manual_input",
            title = stringResource(R.string.proxy_server_list_manual_input),
            children = listOf(
                headerAddAction(ProxyServerListAddAction.HTTP, stringResource(R.string.proxy_server_list_add_http)),
                headerAddAction(ProxyServerListAddAction.VMess, stringResource(R.string.proxy_server_list_add_vmess)),
                headerAddAction(ProxyServerListAddAction.VLESS, stringResource(R.string.proxy_server_list_add_vless)),
                headerAddAction(ProxyServerListAddAction.Trojan, stringResource(R.string.proxy_server_list_add_trojan)),
                headerAddAction(
                    ProxyServerListAddAction.Shadowsocks,
                    stringResource(R.string.proxy_server_list_add_shadowsocks),
                ),
                headerAddAction(ProxyServerListAddAction.Socks, stringResource(R.string.proxy_server_list_add_socks)),
                headerAddAction(
                    ProxyServerListAddAction.Hysteria2,
                    stringResource(R.string.proxy_server_list_add_hysteria2),
                ),
                headerAddAction(
                    ProxyServerListAddAction.Wireguard,
                    stringResource(R.string.proxy_server_list_add_wireguard),
                ),
                headerAddAction(
                    ProxyServerListAddAction.AmneziaWg,
                    stringResource(R.string.proxy_server_list_add_amnezia_wg),
                ),
                headerAddAction(ProxyServerListAddAction.OlcRtc, stringResource(R.string.proxy_server_list_add_olcrtc)),
            ),
        ),
        headerAddAction(
            ProxyServerListAddAction.StrategyGroup,
            stringResource(R.string.proxy_server_list_add_strategy_group),
        ),
        headerAddAction(
            ProxyServerListAddAction.ChainProxy,
            stringResource(R.string.proxy_server_list_add_chain_proxy),
        ),
        headerAddAction(
            ProxyServerListAddAction.Custom,
            stringResource(R.string.proxy_server_list_add_custom),
        ),
    )
    val toolActions = listOf(
        headerToolAction(
            ProxyServerListToolAction.RestartService,
            stringResource(R.string.proxy_server_list_restart_service),
        ),
        headerToolAction(
            ProxyServerListToolAction.UpdateSubscriptions,
            stringResource(R.string.proxy_server_list_update_subscriptions),
        ),
        SkipiProxyHomeHeaderAction(
            id = "sort",
            title = stringResource(R.string.proxy_server_list_option_sort),
            children = listOf(
                headerToolAction(
                    ProxyServerListToolAction.SetSortDefault,
                    stringResource(R.string.proxy_server_list_option_sort_default),
                    selected = proxyListState.proxyServerListSort == ProxyServerListSortDefault,
                ),
                headerToolAction(
                    ProxyServerListToolAction.SetSortName,
                    stringResource(R.string.proxy_server_list_option_sort_name),
                    selected = proxyListState.proxyServerListSort == ProxyServerListSortName,
                ),
                headerToolAction(
                    ProxyServerListToolAction.SetSortLatency,
                    stringResource(R.string.proxy_server_list_option_sort_latency),
                    selected = proxyListState.proxyServerListSort == ProxyServerListSortLatency,
                ),
            ),
        ),
        SkipiProxyHomeHeaderAction(
            id = "delete_proxy_servers",
            title = stringResource(R.string.proxy_server_list_delete_proxy_servers),
            children = listOf(
                headerToolAction(
                    ProxyServerListToolAction.DeleteDuplicateServers,
                    stringResource(R.string.proxy_server_list_delete_duplicates),
                ),
                headerToolAction(
                    ProxyServerListToolAction.DeleteInvalidServers,
                    stringResource(R.string.proxy_server_list_delete_invalid),
                ),
                headerToolAction(
                    ProxyServerListToolAction.DeleteAllServers,
                    stringResource(R.string.proxy_server_list_delete_all),
                ),
            ),
        ),
    )

    SkipiProxyHomeHeader(
            state = SkipiProxyHomeHeaderState(
                title = stringResource(R.string.app_name),
                latencyTesting = isPinging,
            ),
            labels = SkipiProxyHomeHeaderLabels(
                latencyActionDescription = stringResource(
                    if (isPinging) {
                        R.string.proxy_server_list_ping_in_progress
                    } else {
                        R.string.proxy_server_list_ping_check
                    },
                ),
                addActionDescription = stringResource(R.string.proxy_server_list_add),
                moreActionDescription = stringResource(R.string.proxy_server_list_more),
            ),
            colors = SkipiProxyHomeHeaderColors(
                text = MiuixTheme.colorScheme.onBackground,
                mutedText = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                accent = MiuixTheme.colorScheme.primary,
            ),
            addActions = addActions,
            toolActions = toolActions,
            onTestLatency = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                if (isPinging) {
                    onCancelProxyServerLatency()
                } else {
                    val pingMode = if (proxyListState.subscriptionPingMode == SubscriptionPingModeHttp) {
                        ProxyServerLatencyTestMode.RealConnection
                    } else {
                        ProxyServerLatencyTestMode.TcpConnect
                    }
                    val doneTemplate = if (pingMode == ProxyServerLatencyTestMode.RealConnection) {
                        messages.realConnectionDoneTemplate
                    } else {
                        messages.latencyDoneTemplate
                    }
                    onTestProxyServerLatency(
                        groupState.currentFilteredServers,
                        pingMode,
                        doneTemplate,
                        false,
                        null,
                    )
                }
            },
            onAddAction = { actionId ->
                ProxyServerListAddAction.entries
                    .firstOrNull { action -> action.name == actionId }
                    ?.let(::handleAddAction)
            },
            onToolAction = { actionId ->
                ProxyServerListToolAction.entries
                    .firstOrNull { action -> action.name == actionId }
                    ?.let(::requestToolAction)
            },
            modifier = Modifier.statusBarsPadding(),
    )

    pendingDeletionAction?.let { action ->
        DeleteConfirmationDialog(
            show = true,
            title = androidx.compose.ui.res.stringResource(action.deletionConfirmationTitleResId),
            onDismissRequest = { pendingDeletionAction = null },
            onConfirm = {
                pendingDeletionAction = null
                executeToolAction(action)
            },
        )
    }
}

@Composable
internal fun ProxyHeroConnectionCard(
    appState: AppState,
    proxyRunning: Boolean,
    showTunnelMemoryOnHome: Boolean,
    connectionDisplayMode: Int,
    onToggleProxy: () -> Unit,
    modifier: Modifier = Modifier,
    serviceOperationInProgress: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val sample by produceActiveTunnelRuntimeSample(context, proxyRunning)
    val directName = stringResource(R.string.routing_outbound_direct)
    val blockName = stringResource(R.string.routing_outbound_block)
    val activeName = remember(appState, sample, directName, blockName) {
        appState.activeTunnelTargetDisplayName(
            runtime = sample?.runtime,
            activeOutboundTag = sample?.outboundTag,
            directName = directName,
            blockName = blockName,
        )
    }
    val memoryKb by produceState(initialValue = 0L, context, proxyRunning, showTunnelMemoryOnHome) {
        while (true) {
            value = if (proxyRunning && showTunnelMemoryOnHome) context.currentTunnelMemoryPssKb() else 0L
            delay(3_000)
        }
    }

    val selectedServer = remember(appState.proxyServers, appState.selectedProxyServerId) {
        appState.proxyServers.firstOrNull { it.id == appState.selectedProxyServerId }
    }
    val activeServerId = remember(sample?.outboundTag, sample?.runtime?.startupStrategyMemberId, appState.selectedProxyServerId) {
        sample?.outboundTag?.proxyServerIdFromOutboundTag()
            ?: sample?.runtime?.startupStrategyMemberId
            ?: appState.selectedProxyServerId
    }
    val activeServerState = remember(appState.proxyServers, activeServerId, selectedServer) {
        appState.proxyServers.firstOrNull { it.id == activeServerId } ?: selectedServer
    }
    val rawRemarks = activeServerState?.server?.getInfo()?.remarks ?: activeName
    val selfFlag = remember(rawRemarks) { CountryFlagUtils.extractLeadingCountryFlag(rawRemarks) }
    val effectiveFlag = remember(activeServerState?.server, selfFlag) {
        if (activeServerState?.server is StrategyGroup) selfFlag ?: "\u26A1" else selfFlag
    }
    val cleanTitle = remember(activeName, selfFlag) {
        if (selfFlag != null) CountryFlagUtils.stripLeadingCountryFlag(activeName) else activeName
    }

    val isClassic = connectionDisplayMode == ConnectionDisplayModeClassic
    val phase = when {
        proxyRunning -> SkipiConnectionHeroPhase.Connected
        serviceOperationInProgress -> SkipiConnectionHeroPhase.Connecting
        else -> SkipiConnectionHeroPhase.Disconnected
    }
    val latencyText = remember(selectedServer) { resolveConnectionLatency(selectedServer) }
    val latency = when {
        latencyText == ProxyServerLatencyTesting -> SkipiProxyHeroLatency(
            text = stringResource(R.string.proxy_server_list_ping_in_progress),
            color = MiuixTheme.colorScheme.primary,
            testing = true,
        )

        isClassic -> SkipiProxyHeroLatency(
            text = latencyText ?: "-- ms",
            color = latencyText?.let { proxyServerLatencyColor(it) }
                ?: AppTheme.colors.onSurfaceVariant.copy(alpha = 0.5f),
        )

        else -> latencyText?.let {
            SkipiProxyHeroLatency(
                text = it,
                color = proxyServerLatencyColor(it),
            )
        }
    }
    val memoryText = if (showTunnelMemoryOnHome && memoryKb > 0L) {
        stringResource(R.string.connection_metric_ram_format, formatTunnelMemory(memoryKb))
    } else {
        null
    }
    val title = when (phase) {
        SkipiConnectionHeroPhase.Connected -> stringResource(
            if (isClassic) R.string.connection_status_connected else R.string.proxy_active_server_status_connected,
        )

        SkipiConnectionHeroPhase.Connecting -> stringResource(R.string.connection_status_connecting)
        SkipiConnectionHeroPhase.Disconnected -> stringResource(
            if (isClassic) R.string.connection_status_disconnected else R.string.proxy_active_server_status_stopped,
        )
    }
    val subtitle = if (isClassic) {
        val showActiveServer = phase != SkipiConnectionHeroPhase.Disconnected &&
            cleanTitle.isNotBlank() && cleanTitle != directName
        if (showActiveServer) {
            cleanTitle
        } else {
            when (phase) {
                SkipiConnectionHeroPhase.Connected -> stringResource(R.string.connection_status_secured)
                SkipiConnectionHeroPhase.Connecting -> stringResource(R.string.connection_status_connecting)
                SkipiConnectionHeroPhase.Disconnected -> stringResource(R.string.connection_status_tap_to_connect)
            }
        }
    } else if (phase == SkipiConnectionHeroPhase.Connected) {
        cleanTitle
    } else {
        cleanTitle.ifBlank { stringResource(R.string.proxy_active_server_select_to_connect) }
    }
    val colors = SkipiProxyHeroColors(
        surface = AppTheme.colors.surface,
        raisedSurface = AppTheme.colors.surfaceVariant,
        border = AppTheme.colors.onSurface.copy(alpha = 0.12f),
        accent = AppTheme.colors.accent,
        text = if (isClassic) AppTheme.colors.onSurface else MiuixTheme.colorScheme.onSurface,
        mutedText = if (isClassic) AppTheme.colors.onSurfaceVariant else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        connectedStatus = appState.customStatusRunningColor?.let(::Color)
            ?: StatusColorDefaults.statusRunning(isInDarkTheme()),
    )
    val typography = SkipiProxyHeroTypography(
        title = themedFontWeight(FontWeight.Bold),
        emphasis = themedFontWeight(FontWeight.SemiBold),
        body = themedFontWeight(FontWeight.Medium),
    )
    val state = SkipiProxyHeroState(
        phase = phase,
        title = title,
        subtitle = subtitle,
        flag = effectiveFlag,
        sessionDurationText = if (isClassic && proxyRunning) rememberProxyHeroSessionDuration(proxyRunning) else null,
        latency = latency,
        memoryText = memoryText,
    )

    if (isClassic) {
        SkipiProxyHeroClassicCard(
            state = state,
            colors = colors,
            typography = typography,
            connectContentDescription = stringResource(R.string.connection_status_tap_to_connect),
            disconnectContentDescription = stringResource(R.string.connection_status_connected),
            onToggle = onToggleProxy,
            modifier = modifier,
        )
    } else {
        SkipiProxyHeroCompactCard(
            state = state,
            colors = colors,
            typography = typography,
            fallbackBadgePainter = painterResource(R.drawable.ic_globe),
            modifier = modifier,
        )
    }
}

/**
 * Mirrors exactly what the proxy list shows on the active server's card: the
 * latency stored on the selected server's own state (including the testing
 * placeholder). Reading the same source guarantees the connection widget and
 * the server card always display the same number.
 */
private fun resolveConnectionLatency(
    selectedServer: ProxyServerState?,
): String? {
    return selectedServer?.latency?.takeIf(String::isNotBlank)
}

@Composable
private fun rememberProxyHeroSessionDuration(proxyRunning: Boolean): String {
    val sessionDuration by produceState(initialValue = "00:00:00", proxyRunning) {
        if (!proxyRunning) {
            value = "--:--:--"
            return@produceState
        }
        val startRealtime = SystemClock.elapsedRealtime()
        while (true) {
            val elapsedSeconds = (SystemClock.elapsedRealtime() - startRealtime) / 1000
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            val seconds = elapsedSeconds % 60
            value = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
            delay(1_000)
        }
    }
    return sessionDuration
}

private val ProxyServerListToolAction.isDeletion: Boolean
    get() = when (this) {
        ProxyServerListToolAction.DeleteDuplicateServers,
        ProxyServerListToolAction.DeleteInvalidServers,
        ProxyServerListToolAction.DeleteAllServers,
        -> true

        else -> false
    }

private val ProxyServerListToolAction.deletionConfirmationTitleResId: Int
    get() = when (this) {
        ProxyServerListToolAction.DeleteDuplicateServers -> R.string.proxy_server_list_delete_duplicates
        ProxyServerListToolAction.DeleteInvalidServers -> R.string.proxy_server_list_delete_invalid
        ProxyServerListToolAction.DeleteAllServers -> R.string.proxy_server_list_delete_all
        else -> error("Deletion confirmation is only available for deletion actions")
    }

private fun handleProxyServerListAddAction(
    action: ProxyServerListAddAction,
    groupState: ProxyServerListGroups,
    proxyListState: ProxyServerListState,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    qrScanner: suspend () -> String?,
    proxyServerImportFileUseCase: ProxyServerImportFileUseCase,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
) {
    when (action) {
        ProxyServerListAddAction.ScanQrCode -> {
            scope.launch {
                runCatching { qrScanner() }
                    .onSuccess { scanText ->
                        if (scanText.isNullOrBlank()) return@onSuccess
                        importProxyServersInBackground(
                            text = scanText,
                            source = ProxyServerImportSource.QrCode,
                            groupState = groupState,
                            stateStore = stateStore,
                            subscriptionFetcher = subscriptionFetcher,
                            updateAppState = updateAppState,
                            tipNotifier = tipNotifier,
                            backgroundScope = backgroundScope,
                            messages = messages,
                        )
                    }
                    .onFailure { error -> tipNotifier.showError(error) }
            }
        }

        ProxyServerListAddAction.Clipboard -> {
            scope.launch {
                val text = clipboard.getPlainText().orEmpty()
                importProxyServersInBackground(
                    text = text,
                    source = ProxyServerImportSource.Clipboard,
                    groupState = groupState,
                    stateStore = stateStore,
                    subscriptionFetcher = subscriptionFetcher,
                    updateAppState = updateAppState,
                    tipNotifier = tipNotifier,
                    backgroundScope = backgroundScope,
                    messages = messages,
                )
            }
        }

        ProxyServerListAddAction.File -> {
            scope.launch {
                runCatching { proxyServerImportFileUseCase.readText() }
                    .onSuccess { text ->
                        text?.let {
                            importProxyServersInBackground(
                                text = it,
                                source = ProxyServerImportSource.File,
                                groupState = groupState,
                                stateStore = stateStore,
                                subscriptionFetcher = subscriptionFetcher,
                                updateAppState = updateAppState,
                                tipNotifier = tipNotifier,
                                backgroundScope = backgroundScope,
                                messages = messages,
                            )
                        }
                    }
                    .onFailure { error -> tipNotifier.showError(error) }
            }
        }

        else -> {
            val serverId = proxyListState.nextProxyServerId
            navigator.navigateForResult(
                route = Route.ProxyServerEditor(
                    ps = createProxyServer(action),
                    serverId = serverId,
                    groupId = if (action == ProxyServerListAddAction.StrategyGroup) {
                        AutoBalancerGroupId
                    } else {
                        // A server created from the add menu is always a manual
                        // server. It must not become part of the subscription the
                        // user happened to be viewing when they pressed Add.
                        DefaultSubscriptionGroupId
                    },
                    returnGroupId = if (action == ProxyServerListAddAction.StrategyGroup) {
                        AutoBalancerGroupId
                    } else {
                        DefaultSubscriptionGroupId
                    },
                    resultKey = resultKey,
                ),
                requestKey = resultKey,
            )
        }
    }
}

private fun importProxyServersInBackground(
    text: String,
    source: ProxyServerImportSource,
    groupState: ProxyServerListGroups,
    stateStore: AndroidAppStateStore,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    updateAppState: ((AppState) -> AppState) -> Unit,
    tipNotifier: AndroidToastTipNotifier,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    backgroundScope.launch {
        runCatching {
            if (
                installSubscriptionFromText(
                    text = text,
                    stateStore = stateStore,
                    subscriptionFetcher = subscriptionFetcher,
                    tipNotifier = tipNotifier,
                    messages = messages,
                )
            ) {
                return@runCatching
            }
            val appState = stateStore.state.value
            importProxyServers(
                text = text,
                source = source,
                groupState = groupState,
                subscriptionFetcher = subscriptionFetcher,
                sendDeviceHeaders = appState.enableSubscriptionDeviceHeaders,
                fetchTimeoutSeconds = appState.subscriptionFetchTimeoutSeconds,
                updateAppState = updateAppState,
                tipNotifier = tipNotifier,
                messages = messages,
            )
        }.onFailure { error -> tipNotifier.showError(error) }
    }
}

private suspend fun installSubscriptionFromText(
    text: String,
    stateStore: AndroidAppStateStore,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    tipNotifier: AndroidToastTipNotifier,
    messages: ProxyServerListMessages,
): Boolean {
    val config = text.toSubscriptionInstallConfigOrNull() ?: return false
    runCatching {
        SubscriptionInstallConfigUseCase(
            stateStore = stateStore,
            subscriptionFetcher = subscriptionFetcher,
        ).install(config)
    }.onSuccess { result ->
        tipNotifier.show(
            subscriptionInstallMessage(
                result = result,
                existingUrlTemplate = messages.subscriptionInstallExistingUrlTemplate,
                successTemplate = messages.subscriptionUpdateResultTemplate,
                failedTemplate = messages.subscriptionUpdateResultWithFailedTemplate,
            ),
        )
    }.onFailure { error ->
        tipNotifier.showError(error)
    }
    return true
}

private suspend fun importProxyServers(
    text: String,
    source: ProxyServerImportSource,
    groupState: ProxyServerListGroups,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    sendDeviceHeaders: Boolean,
    fetchTimeoutSeconds: Int,
    updateAppState: ((AppState) -> AppState) -> Unit,
    tipNotifier: AndroidToastTipNotifier,
    messages: ProxyServerListMessages,
) {
    // Clipboard, QR and file imports are all explicit user additions. A
    // subscription is installed through its own flow above; anything reaching
    // this point belongs to the permanent manual-server group.
    val targetGroupId = DefaultSubscriptionGroupId
    val importResult = importProxyServersFromText(
        text = text,
        source = source,
        providerUrlFetcher = { providerUrl ->
            subscriptionFetcher.fetch(
                url = providerUrl,
                userAgent = "",
                options = AndroidSubscriptionFetchOptions(
                    sendDeviceHeaders = sendDeviceHeaders,
                    timeoutSeconds = fetchTimeoutSeconds,
                ),
            )
        },
    )
    if (importResult.servers.isNotEmpty()) {
        updateAppState { state -> state.withImportedProxyServers(importResult, targetGroupId) }
    }
    tipNotifier.show(
        messages.importResultTemplate.formatTemplate(
            "serverCount" to importResult.servers.size,
        ),
    )
}

private fun handleProxyServerListToolAction(
    action: ProxyServerListToolAction,
    groupState: ProxyServerListGroups,
    selectedServer: ProxyServerState?,
    proxyListState: ProxyServerListState,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    proxyServiceUseCase: ProxyServiceUseCase,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
    onTestProxyServerLatency: (List<ProxyServerState>, ProxyServerLatencyTestMode, String, Boolean) -> Unit,
) {
    when (action) {
        ProxyServerListToolAction.RestartService -> {
            restartSelectedProxyService(
                selectedServer = selectedServer,
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }

        ProxyServerListToolAction.TestLatency -> {
            onTestProxyServerLatency(
                groupState.currentFilteredServers,
                ProxyServerLatencyTestMode.TcpConnect,
                messages.latencyDoneTemplate,
                false,
            )
        }

        ProxyServerListToolAction.TestRealConnection -> {
            onTestProxyServerLatency(
                groupState.currentFilteredServers,
                ProxyServerLatencyTestMode.RealConnection,
                messages.realConnectionDoneTemplate,
                false,
            )
        }

        ProxyServerListToolAction.SetSortDefault -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortDefault) }
        }

        ProxyServerListToolAction.SetSortName -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortName) }
        }

        ProxyServerListToolAction.SetSortLatency -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortLatency) }
        }

        ProxyServerListToolAction.UpdateSubscriptions -> {
            updateSubscriptionGroups(
                proxyListState = proxyListState,
                stateStore = stateStore,
                updateAppState = updateAppState,
                subscriptionFetcher = subscriptionFetcher,
                tipNotifier = tipNotifier,
                backgroundScope = backgroundScope,
                messages = messages,
            )
        }

        ProxyServerListToolAction.DeleteDuplicateServers -> {
            deleteDuplicateServers(
                servers = groupState.currentGroupServers,
                updateAppState = updateAppState,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
            )
        }

        ProxyServerListToolAction.DeleteInvalidServers -> {
            deleteInvalidServers(
                servers = groupState.currentGroupServers,
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }

        ProxyServerListToolAction.DeleteAllServers -> {
            deleteAllServers(
                servers = if (groupState.isAllGroupsSelected) {
                    proxyListState.proxyServers
                } else {
                    groupState.currentGroupServers
                },
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }
    }
}

private fun restartSelectedProxyService(
    selectedServer: ProxyServerState?,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    if (serviceOperationInProgress) return
    runProxyServiceOperation {
        when (
            val result = proxyServiceUseCase.restart(
                state = stateStore.state.value,
                selectedServer = selectedServer,
            )
        ) {
            is ProxyServiceResult.Success -> {
                updateAppState { state ->
                    state.copy(
                        proxyRunning = result.proxyRunning,
                        localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                    )
                }
                tipNotifier.show(messages.serviceRestarted)
            }

            ProxyServiceResult.MissingServer -> {
                tipNotifier.show(messages.selectServerFirst)
            }

            is ProxyServiceResult.Failed -> {
                updateAppState { state -> state.copy(proxyRunning = false) }
                tipNotifier.showError(result.error, messages.serviceStopped)
            }
        }
    }
}

private fun updateSubscriptionGroups(
    proxyListState: ProxyServerListState,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    tipNotifier: AndroidToastTipNotifier,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    val subscriptionGroups = proxyListState.subscriptionGroups.updatableSubscriptionGroups()
    backgroundScope.launch {
        if (subscriptionGroups.isEmpty()) {
            tipNotifier.show(messages.noSubscriptionUpdates)
            return@launch
        }
        val result = updateSubscriptions(
            groups = subscriptionGroups,
            subscriptionFetcher = subscriptionFetcher,
            fetchOptions = { group -> stateStore.state.value.toSubscriptionFetchOptions(group) },
        )
        if (result.updates.isNotEmpty()) {
            val nextState = withContext(Dispatchers.Default) {
                stateStore.state.value.withUpdatedSubscriptionServers(
                    updates = result.updates,
                    updatedAtMillis = result.updatedAtMillis,
                )
            }
            updateAppState { nextState }
        }
        tipNotifier.show(
            subscriptionUpdateMessage(
                result = result,
                successTemplate = messages.subscriptionUpdateResultTemplate,
                failedTemplate = messages.subscriptionUpdateResultWithFailedTemplate,
            ),
        )
    }
}

private fun deleteInvalidServers(
    servers: List<ProxyServerState>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    val currentGroupServerIds = servers.map { server -> server.id }.toSet()
    val previewResult = stateStore.state.value.proxyServers.deleteInvalidServersInGroup(currentGroupServerIds)
    deleteServersByIds(
        serverIds = previewResult.removedServerIds,
        stateStore = stateStore,
        updateAppState = updateAppState,
        proxyServiceUseCase = proxyServiceUseCase,
        tipNotifier = tipNotifier,
        scope = scope,
        deletedTemplate = messages.invalidServersDeletedTemplate,
        emptyMessage = messages.noInvalidServers,
        serviceStoppedMessage = messages.serviceStopped,
        serviceOperationInProgress = serviceOperationInProgress,
        runProxyServiceOperation = runProxyServiceOperation,
    )
}

private fun deleteAllServers(
    servers: List<ProxyServerState>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    deleteServersByIds(
        serverIds = servers.map { server -> server.id }.toSet(),
        stateStore = stateStore,
        updateAppState = updateAppState,
        proxyServiceUseCase = proxyServiceUseCase,
        tipNotifier = tipNotifier,
        scope = scope,
        deletedTemplate = messages.allServersDeletedTemplate,
        emptyMessage = messages.noServersToDelete,
        serviceStoppedMessage = messages.serviceStopped,
        serviceOperationInProgress = serviceOperationInProgress,
        runProxyServiceOperation = runProxyServiceOperation,
    )
}

private fun deleteServersByIds(
    serverIds: Set<Int>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    deletedTemplate: String,
    emptyMessage: String,
    serviceStoppedMessage: String,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    val stateSnapshot = stateStore.state.value
    val existingServerIds = stateSnapshot.proxyServers
        .asSequence()
        .map { server -> server.id }
        .filter { serverId -> serverId in serverIds }
        .toSet()
    if (existingServerIds.isEmpty()) {
        scope.launch { tipNotifier.show(emptyMessage) }
        return
    }

    fun applyDeleteAndNotify() {
        var removedCount = 0
        updateAppState { state ->
            val deletedServerIds = state.proxyServers
                .asSequence()
                .map { server -> server.id }
                .filter { serverId -> serverId in serverIds }
                .toSet()
            removedCount = deletedServerIds.size
            state.withDeletedProxyServers(deletedServerIds)
        }
        scope.launch {
            tipNotifier.show(
                if (removedCount > 0) {
                    deletedTemplate.formatTemplate("count" to removedCount)
                } else {
                    emptyMessage
                },
            )
        }
    }

    val selectedServerWillBeDeleted = stateSnapshot.selectedProxyServerId in existingServerIds
    if (!stateSnapshot.proxyRunning || !selectedServerWillBeDeleted) {
        applyDeleteAndNotify()
        return
    }
    if (serviceOperationInProgress) return

    runProxyServiceOperation {
        when (val stopResult = proxyServiceUseCase.stop(stateStore.state.value.runMode)) {
            is ProxyServiceResult.Success -> applyDeleteAndNotify()
            ProxyServiceResult.MissingServer -> applyDeleteAndNotify()
            is ProxyServiceResult.Failed -> {
                updateAppState { state -> state.copy(proxyRunning = false) }
                tipNotifier.showError(stopResult.error, serviceStoppedMessage)
            }
        }
    }
}

private fun deleteDuplicateServers(
    servers: List<ProxyServerState>,
    updateAppState: ((AppState) -> AppState) -> Unit,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    val currentGroupServerIds = servers.map { server -> server.id }.toSet()
    var removedCount = 0
    updateAppState { state ->
        val result = state.proxyServers.deleteDuplicateServersInGroup(
            currentGroupServerIds = currentGroupServerIds,
            selectedProxyServerId = state.selectedProxyServerId,
        )
        removedCount = result.removedCount
        if (removedCount == 0) {
            state
        } else {
            state.copy(proxyServers = result.servers)
        }
    }
    scope.launch {
        tipNotifier.show(
            if (removedCount > 0) {
                messages.duplicatesDeletedTemplate.formatTemplate("count" to removedCount)
            } else {
                messages.noDuplicates
            },
        )
    }
}
