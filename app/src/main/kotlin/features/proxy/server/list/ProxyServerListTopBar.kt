// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.os.SystemClock
import java.util.Locale
import app.effects.resolveActiveNetworkConfig
import features.config.withActiveTrafficConfig
import features.networkautomation.engine.NetworkAutomationDecision
import features.networkautomation.engine.NetworkAutomationEvaluator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import ui.KeyColors
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.icons.AnimatedHourglassIcon
import ui.icons.StaticHourglass
import ui.clipboard.getPlainText
import ui.components.DeleteConfirmationDialog
import ui.feedback.AndroidToastTipNotifier
import ui.feedback.LocalAppHaptics
import ui.layout.AdaptiveTopAppBar
import ui.text.formatTemplate

@Composable
internal fun ProxyServerListTopBar(
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    groupState: ProxyServerListGroups,
    pinnedConnectionPanel: (@Composable () -> Unit)?,
    showPinnedGroupSelector: Boolean,
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
    onSelectedGroupIdChange: (Int) -> Unit,
    onMoveSubscriptionGroup: (groupId: Int, offset: Int) -> Unit,
    onTestProxyServerLatency: (List<ProxyServerState>, ProxyServerLatencyTestMode, String, Boolean, (() -> Unit)?) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (isPinging) {
                        scope.launch {
                            tipNotifier.show(messages.pingInProgress)
                        }
                    } else {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
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
            ) {
                if (isPinging) {
                    val pingingDescription = stringResource(R.string.proxy_server_list_ping_in_progress)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = pingingDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedHourglassIcon(
                            color = MiuixTheme.colorScheme.primary,
                            isPinging = true,
                            size = 20.dp,
                        )
                    }
                } else {
                    StaticHourglass(
                        modifier = Modifier.size(24.dp),
                        color = MiuixTheme.colorScheme.onBackground,
                        size = 20.dp,
                    )
                }
            }
            ProxyServerListAddMenu(onAction = ::handleAddAction)
            ProxyServerListToolsMenu(
                sort = proxyListState.proxyServerListSort,
                onAction = ::requestToolAction,
            )
        }

        pinnedConnectionPanel?.invoke()

        AnimatedVisibility(
            visible = showPinnedGroupSelector && groupState.showGroupTabs,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ProxyServerListGroupSelector(
                groups = groupState.groupTabs,
                selectedGroupId = groupState.selectedTabId,
                onGroupSelected = onSelectedGroupIdChange,
                onGroupMove = onMoveSubscriptionGroup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp, bottom = 2.dp),
            )
        }

        AnimatedVisibility(
            visible = proxyListState.showServerSearch,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProxyServerListSearchBar(
                    searchValue = searchValue,
                    onSearchValueChange = onSearchValueChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

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
private fun PowerIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Standard IEC Power Standby symbol: open arc at the top
        drawArc(
            color = color,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Vertical power bar through the top opening
        drawLine(
            color = color,
            start = Offset(center.x, center.y - radius * 1.05f),
            end = Offset(center.x, center.y - radius * 0.05f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SignalBarsIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val barCount = 3
        val spacing = size.width * 0.18f
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount
        for (i in 0 until barCount) {
            val barHeight = size.height * (0.35f + 0.32f * i)
            val left = i * (barWidth + spacing)
            val top = size.height - barHeight
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
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
    val activeServerId = remember(sample?.outboundTag, appState.selectedProxyServerId) {
        sample?.outboundTag?.proxyServerIdFromOutboundTag() ?: appState.selectedProxyServerId
    }
    val activeServerState = remember(appState.proxyServers, activeServerId, selectedServer) {
        appState.proxyServers.firstOrNull { it.id == activeServerId } ?: selectedServer
    }
    val rawRemarks = activeServerState?.server?.getInfo()?.remarks ?: activeName
    val selfFlag = remember(rawRemarks) { CountryFlagUtils.extractLeadingCountryFlag(rawRemarks) }
    val effectiveFlag = remember(activeServerState?.server, selfFlag) {
        if (activeServerState?.server is StrategyGroup) selfFlag ?: "⚡" else selfFlag
    }
    val cleanTitle = remember(activeName, selfFlag) {
        if (selfFlag != null) CountryFlagUtils.stripLeadingCountryFlag(activeName) else activeName
    }

    if (connectionDisplayMode == ConnectionDisplayModeClassic) {
        ProxyHeroConnectionCardClassic(
            appState = appState,
            proxyRunning = proxyRunning,
            showTunnelMemoryOnHome = showTunnelMemoryOnHome,
            memoryKb = memoryKb,
            effectiveFlag = effectiveFlag,
            cleanTitle = cleanTitle,
            directName = directName,
            activeServerState = activeServerState,
            sample = sample,
            onToggleProxy = onToggleProxy,
            modifier = modifier,
        )
    } else {
        ProxyHeroConnectionCardCompact(
            appState = appState,
            proxyRunning = proxyRunning,
            showTunnelMemoryOnHome = showTunnelMemoryOnHome,
            memoryKb = memoryKb,
            effectiveFlag = effectiveFlag,
            cleanTitle = cleanTitle,
            activeServerState = activeServerState,
            sample = sample,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProxyHeroConnectionCardClassic(
    appState: AppState,
    proxyRunning: Boolean,
    showTunnelMemoryOnHome: Boolean,
    memoryKb: Long,
    effectiveFlag: String?,
    cleanTitle: String,
    directName: String,
    activeServerState: ProxyServerState?,
    sample: ActiveTunnelRuntimeSample?,
    onToggleProxy: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            delay(1000)
        }
    }

    val heroShape = RoundedCornerShape(24.dp)
    val context = LocalContext.current
    val accentTone = remember(appState.seedIndex, appState.customMaterialYouSeed, appState.customAccentColor, context) {
        appState.customAccentColor?.let { Color(it) } ?: (keyColorFor(appState.seedIndex, appState.customMaterialYouSeed) ?: resolveSystemAccentColor(context))
    }

    val cardBgColor by animateColorAsState(
        targetValue = if (proxyRunning) AppTheme.colors.accent else AppTheme.colors.surface,
        animationSpec = tween(350),
        label = "classic_card_bg",
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (proxyRunning) accentTone.copy(alpha = 0.35f) else AppTheme.colors.onSurface.copy(alpha = 0.12f),
        animationSpec = tween(350),
        label = "classic_card_border",
    )

    val buttonBgColor by animateColorAsState(
        targetValue = if (proxyRunning) accentTone.copy(alpha = 0.18f) else AppTheme.colors.surfaceVariant,
        animationSpec = tween(300),
        label = "classic_btn_bg",
    )
    val buttonBorderColor by animateColorAsState(
        targetValue = if (proxyRunning) accentTone else AppTheme.colors.onSurface.copy(alpha = 0.2f),
        animationSpec = tween(300),
        label = "classic_btn_border",
    )
    val powerIconTint by animateColorAsState(
        targetValue = if (proxyRunning) AppTheme.colors.onSurface else AppTheme.colors.onSurface.copy(alpha = 0.55f),
        animationSpec = tween(300),
        label = "classic_icon_tint",
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (proxyRunning) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "classic_btn_scale",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "power_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart,
        ),
        label = "power_aura_scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart,
        ),
        label = "power_aura_alpha",
    )

    Card(
        modifier = modifier
            .clip(heroShape)
            .border(
                width = 1.dp,
                color = cardBorderColor,
                shape = heroShape,
            ),
        colors = CardDefaults.defaultColors(
            color = cardBgColor,
        ),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Row: Big Power Button + Header & Server Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Large Circular Power Button with Aura
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (proxyRunning) {
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = pulseAlpha
                                }
                                .clip(CircleShape)
                                .background(accentTone),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .graphicsLayer {
                                scaleX = buttonScale
                                scaleY = buttonScale
                            }
                            .clip(CircleShape)
                            .background(buttonBgColor)
                            .border(
                                width = if (proxyRunning) 4.dp else 2.5.dp,
                                color = buttonBorderColor,
                                shape = CircleShape,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleProxy,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        PowerIcon(
                            color = powerIconTint,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = proxyRunning,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(260)) + slideInVertically(animationSpec = tween(260)) { height -> height / 3 })
                                .togetherWith(fadeOut(animationSpec = tween(160)) + slideOutVertically(animationSpec = tween(160)) { height -> -height / 3 })
                        },
                        label = "classic_status_title",
                    ) { isRunning ->
                        Text(
                            text = if (isRunning) {
                                stringResource(R.string.connection_status_connected)
                            } else {
                                stringResource(R.string.connection_status_disconnected)
                            },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.onSurface,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    AnimatedContent(
                        targetState = proxyRunning && cleanTitle.isNotBlank() && cleanTitle != directName,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(260)) + expandVertically(animationSpec = tween(260)))
                                .togetherWith(fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)))
                        },
                        label = "classic_server_title",
                    ) { showActiveServer ->
                        if (showActiveServer) {
                            Text(
                                text = cleanTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppTheme.colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 19.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = if (proxyRunning) {
                                    stringResource(R.string.connection_status_secured)
                                } else {
                                    stringResource(R.string.connection_status_tap_to_connect)
                                },
                                fontSize = 14.sp,
                                color = AppTheme.colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = proxyRunning,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))

                    val isMemoryVisible = showTunnelMemoryOnHome && memoryKb > 0L
                    val latencyText = remember(activeServerState, appState.proxyServers, sample?.outboundTag) {
                        val directLatency = activeServerState?.latency?.takeIf { it.isNotBlank() && it != ProxyServerLatencyTesting }
                        if (directLatency != null) return@remember directLatency

                        val strategyGroup = activeServerState?.server as? StrategyGroup
                            ?: (appState.proxyServers.firstOrNull { it.id == appState.selectedProxyServerId }?.server as? StrategyGroup)
                        if (strategyGroup != null) {
                            val activeMemberId = sample?.outboundTag?.proxyServerIdFromOutboundTag()
                            if (activeMemberId != null) {
                                val activeMemberLatency = appState.proxyServers.firstOrNull { it.id == activeMemberId }?.latency?.takeIf { it.isNotBlank() && it != ProxyServerLatencyTesting }
                                if (activeMemberLatency != null) return@remember activeMemberLatency
                            }
                            val memberLatencies = appState.proxyServers.filter { member ->
                                member.server !is StrategyGroup && CountryFlagUtils.strategyGroupContainsMember(strategyGroup, member.id, appState.proxyServers)
                            }.mapNotNull { member ->
                                member.latency.takeIf { it.isNotBlank() && it != ProxyServerLatencyTesting }
                            }
                            if (memberLatencies.isNotEmpty()) {
                                return@remember memberLatencies.firstOrNull()
                            }
                        }
                        null
                    } ?: "-- ms"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentTone),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = sessionDuration,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppTheme.colors.onSurfaceVariant,
                        )

                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "|",
                            fontSize = 12.sp,
                            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                        Spacer(Modifier.width(8.dp))

                        SignalBarsIcon(
                            color = accentTone,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))

                        Text(
                            text = latencyText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppTheme.colors.onSurfaceVariant,
                        )

                        if (isMemoryVisible) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "|",
                                fontSize = 12.sp,
                                color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.35f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "RAM ${formatTunnelMemory(memoryKb)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyHeroConnectionCardCompact(
    appState: AppState,
    proxyRunning: Boolean,
    showTunnelMemoryOnHome: Boolean,
    memoryKb: Long,
    effectiveFlag: String?,
    cleanTitle: String,
    activeServerState: ProxyServerState? = null,
    sample: ActiveTunnelRuntimeSample? = null,
    modifier: Modifier = Modifier,
) {
    val heroShape = RoundedCornerShape(18.dp)
    val context = LocalContext.current
    val accentTone = remember(appState.seedIndex, appState.customMaterialYouSeed, appState.customAccentColor, context) {
        appState.customAccentColor?.let { Color(it) } ?: (keyColorFor(appState.seedIndex, appState.customMaterialYouSeed) ?: resolveSystemAccentColor(context))
    }

    val compactCardBgColor by animateColorAsState(
        targetValue = if (proxyRunning) AppTheme.colors.accent else AppTheme.colors.surface,
        animationSpec = tween(350),
        label = "compact_card_bg",
    )
    val compactCardBorderColor by animateColorAsState(
        targetValue = if (proxyRunning) accentTone.copy(alpha = 0.35f) else AppTheme.colors.onSurface.copy(alpha = 0.12f),
        animationSpec = tween(350),
        label = "compact_card_border",
    )

    val dotTransition = rememberInfiniteTransition(label = "dot_pulse")
    val dotScale by dotTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot_scale",
    )

    val latencyText = remember(activeServerState, appState.proxyServers, sample?.outboundTag) {
        val directLatency = activeServerState?.latency?.takeIf { it.isNotBlank() && it != ProxyServerLatencyTesting }
        if (directLatency != null) return@remember directLatency

        val strategyGroup = activeServerState?.server as? StrategyGroup
            ?: (appState.proxyServers.firstOrNull { it.id == appState.selectedProxyServerId }?.server as? StrategyGroup)
        if (strategyGroup != null) {
            val activeMemberId = sample?.outboundTag?.proxyServerIdFromOutboundTag()
            if (activeMemberId != null) {
                val activeMemberLatency = appState.proxyServers.firstOrNull { it.id == activeMemberId }?.latency?.takeIf { it.isNotBlank() && it != ProxyServerLatencyTesting }
                if (activeMemberLatency != null) return@remember activeMemberLatency
            }
            val memberLatencies = appState.proxyServers.filter { member ->
                member.server !is StrategyGroup && CountryFlagUtils.strategyGroupContainsMember(strategyGroup, member.id, appState.proxyServers)
            }.mapNotNull { member ->
                member.latency.takeIf { it.isNotBlank() && it != ProxyServerLatencyTesting }
            }
            if (memberLatencies.isNotEmpty()) {
                return@remember memberLatencies.firstOrNull()
            }
        }
        null
    }

    Card(
        modifier = modifier
            .clip(heroShape)
            .border(
                width = 1.dp,
                color = compactCardBorderColor,
                shape = heroShape,
            ),
        colors = CardDefaults.defaultColors(
            color = compactCardBgColor,
        ),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AnimatedContent(
            targetState = proxyRunning,
            transitionSpec = {
                (fadeIn(animationSpec = tween(280)) + expandVertically(animationSpec = tween(280)))
                    .togetherWith(fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180)))
            },
            label = "compact_card_content",
        ) { isRunning ->
            if (isRunning) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val statusConnectedColor = appState.customStatusRunningColor?.let { Color(it) }
                            ?: if (isInDarkTheme()) Color(0xFF6BD58A) else Color(0xFF128A3C)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer {
                                        scaleX = dotScale
                                        scaleY = dotScale
                                    }
                                    .clip(CircleShape)
                                    .background(statusConnectedColor),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.proxy_active_server_status_connected),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = statusConnectedColor,
                            )
                        }
                        if (showTunnelMemoryOnHome && memoryKb > 0L) {
                            Text(
                                text = "RAM ${formatTunnelMemory(memoryKb)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CountryFlagBadge(
                            flag = effectiveFlag,
                            size = 36.dp,
                            shapeRadius = 8.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = cleanTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!latencyText.isNullOrBlank()) {
                            val latencyColor = proxyServerLatencyColor(latencyText)
                            Spacer(Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(latencyColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            ) {
                                SignalBarsIcon(
                                    color = latencyColor,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = latencyText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = latencyColor,
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CountryFlagBadge(
                        flag = effectiveFlag,
                        size = 36.dp,
                        shapeRadius = 8.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.proxy_active_server_status_stopped),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = cleanTitle.ifBlank { stringResource(R.string.proxy_active_server_select_to_connect) },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!latencyText.isNullOrBlank()) {
                        val latencyColor = proxyServerLatencyColor(latencyText)
                        Spacer(Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(latencyColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            SignalBarsIcon(
                                color = latencyColor,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = latencyText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = latencyColor,
                            )
                        }
                    }
                }
            }
        }
    }
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
