// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.config

import androidx.compose.foundation.background
import ui.text.themedFontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.AppState
import app.collectAppState
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.app.config.withProfileBasics
import app.navigation.Route
import app.navigation.TrafficConfigEditorSection
import app.skipi.ui.config.SkipiTrafficConfigEditorSectionAction
import app.skipi.ui.config.SkipiTrafficConfigBasicsEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigGeneralEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigDnsEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigNetworkActivationEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigTunnelEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigRoutingSectionScreen
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.AppTheme
import ui.clipboard.setPlainText
import ui.components.BackNavigationIcon
import ui.components.NavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

/** The full-screen Material entry point for a single SKIPI traffic profile. */
@Composable
fun TrafficConfigEditorPage(
    padding: PaddingValues,
    trafficConfigId: Int,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run {
        navigator.pop()
        return
    }
    val editor = rememberTrafficConfigEditorController(updateAppState)
    val onBack = { navigator.pop() }
    SkipiTrafficConfigBasicsEditorScreen(
        title = stringResource(R.string.configs_edit),
        sectionsTitle = stringResource(R.string.configs_editor_sections),
        sections = listOf(
            SkipiTrafficConfigEditorSectionAction(
                key = "general",
                title = stringResource(R.string.configs_general_title),
                summary = stringResource(R.string.configs_general_summary),
                onNavigate = { navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.General)) },
            ),
            SkipiTrafficConfigEditorSectionAction(
                key = "dns",
                title = stringResource(R.string.configs_dns_title),
                summary = stringResource(R.string.configs_dns_summary),
                onNavigate = { navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.Dns)) },
            ),
            SkipiTrafficConfigEditorSectionAction(
                key = "tunnel",
                title = stringResource(R.string.configs_android_title),
                summary = stringResource(R.string.configs_tunnel_summary),
                onNavigate = { navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.Tunnel)) },
            ),
            SkipiTrafficConfigEditorSectionAction(
                key = "network",
                title = stringResource(R.string.configs_network_title),
                summary = stringResource(R.string.configs_network_summary),
                onNavigate = { navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.Network)) },
            ),
            SkipiTrafficConfigEditorSectionAction(
                key = "routing",
                title = stringResource(R.string.configs_rules_title),
                summary = stringResource(R.string.configs_routing_summary),
                onNavigate = { navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.Routing)) },
            ),
            SkipiTrafficConfigEditorSectionAction(
                key = "per-app",
                title = stringResource(R.string.configs_per_app),
                summary = stringResource(R.string.configs_per_app_summary),
                onNavigate = { navigator.push(Route.ProxyAppList(config.id)) },
            ),
        ),
        profile = config,
        controller = editor,
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(R.string.common_save),
        onBack = onBack,
        platformBackHandler = { back -> TrafficConfigEditorBackHandler(back) },
    )
}

@Composable
private fun rememberTrafficConfigEditorController(
    updateAppState: ((AppState) -> AppState) -> Unit,
): TrafficConfigEditorController = remember(updateAppState) {
    TrafficConfigEditorController(
        TrafficConfigEditorProfileStore { profileId, transform ->
            var found = false
            updateAppState { state ->
                if (state.trafficConfigs.none { it.id == profileId }) {
                    state
                } else {
                    found = true
                    state.withUpdatedTrafficConfig(profileId, transform)
                }
            }
            found
        },
    )
}

@Composable
internal fun TrafficConfigEditorBackHandler(onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = onBack,
    )
}

/** Full-screen raw editor for the complete portable Shadowrocket + SKIPI profile. */
@Composable
fun TrafficConfigRawEditorPage(
    padding: PaddingValues,
    trafficConfigId: Int,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
    val clipboard = LocalClipboard.current
    val isWideScreen = LocalIsWideScreen.current
    val scope = rememberCoroutineScope()
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run {
        navigator.pop()
        return
    }
    val rawEditorState = remember(config.id) {
        ConfCodeEditorState(config.withSkipiSettingsInRawConfig().rawConfig)
    }
    val copiedMessage = stringResource(R.string.common_copied)
    val editor = rememberTrafficConfigEditorController(updateAppState)
    val onBack: () -> Unit = {
        editor.leaveRaw(config.id, rawEditorState.snapshotText()) { navigator.pop() }
        Unit
    }

    TrafficConfigFullScreenScaffold(
        title = config.name.ifBlank { stringResource(R.string.configs_raw_edit) },
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = onBack,
        actions = {
            NavigationIcon(
                onClick = {
                    scope.launch {
                        clipboard.setPlainText(rawEditorState.snapshotText())
                        services.tipNotifier.show(copiedMessage)
                    }
                },
                imageVector = MiuixIcons.Copy,
                contentDescription = stringResource(R.string.common_copy),
            )
        },
        topContentInset = 0.dp,
    ) { _, pagePadding, _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background)
                .padding(pagePadding)
                .imePadding(),
        ) {
            ConfCodeEditor(
                state = rawEditorState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun TrafficConfigSectionPage(
    padding: PaddingValues,
    trafficConfigId: Int,
    section: TrafficConfigEditorSection,
) {
    when (section) {
        TrafficConfigEditorSection.General -> TrafficConfigGeneralSectionPage(padding, trafficConfigId)
        TrafficConfigEditorSection.Dns -> TrafficConfigDnsSectionPage(padding, trafficConfigId)
        TrafficConfigEditorSection.Tunnel -> TrafficConfigTunnelSectionPage(padding, trafficConfigId)
        TrafficConfigEditorSection.Network -> TrafficConfigNetworkSectionPage(padding, trafficConfigId)
        TrafficConfigEditorSection.Routing -> TrafficConfigRoutingSectionPage(padding, trafficConfigId)
        TrafficConfigEditorSection.ProxyGroups -> TrafficConfigProxyGroupsPage(padding, trafficConfigId)
        TrafficConfigEditorSection.RoutingRules -> TrafficConfigRulesPage(padding, trafficConfigId)
    }
}

@Composable
private fun TrafficConfigGeneralSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    val editor = rememberTrafficConfigEditorController(updateAppState)
    val onBack = { navigator.pop() }
    SkipiTrafficConfigGeneralEditorScreen(
        title = stringResource(R.string.configs_general_title),
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(R.string.common_save),
        profile = config,
        controller = editor,
        onBack = onBack,
        onSave = onBack,
        platformBackHandler = { back -> TrafficConfigEditorBackHandler(back) },
    )
}

@Composable
private fun TrafficConfigDnsSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    val editor = rememberTrafficConfigEditorController(updateAppState)
    val onBack = { navigator.pop() }
    SkipiTrafficConfigDnsEditorScreen(
        title = stringResource(R.string.configs_dns_title),
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(R.string.common_save),
        profile = config,
        controller = editor,
        onBack = onBack,
        onSave = onBack,
        platformBackHandler = { back -> TrafficConfigEditorBackHandler(back) },
    )
}


@Composable
private fun TrafficConfigTunnelSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    val editor = rememberTrafficConfigEditorController(updateAppState)
    val onBack: () -> Unit = { navigator.pop(); Unit }
    SkipiTrafficConfigTunnelEditorScreen(
        title = stringResource(R.string.configs_android_title),
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(R.string.common_save),
        profile = config,
        controller = editor,
        onBack = onBack,
        onSave = onBack,
        platformBackHandler = { back -> TrafficConfigEditorBackHandler(back) },
    )
}


@Composable
private fun TrafficConfigNetworkSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    val editor = rememberTrafficConfigEditorController(updateAppState)
    val onBack: () -> Unit = { navigator.pop(); Unit }
    SkipiTrafficConfigNetworkActivationEditorScreen(
        title = stringResource(R.string.configs_network_title),
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(R.string.common_save),
        profile = config,
        controller = editor,
        onBack = onBack,
        onSave = onBack,
        platformBackHandler = { back -> TrafficConfigEditorBackHandler(back) },
    )
}

@Composable
private fun TrafficConfigRoutingSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    SkipiTrafficConfigRoutingSectionScreen(
        profile = config,
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onResources = { navigator.push(Route.ResourceManagement(config.id)) },
        onRules = {
            navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.RoutingRules))
        },
        onProxyGroups = {
            navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.ProxyGroups))
        },
        platformBackHandler = { back -> TrafficConfigEditorBackHandler(back) },
    )
}

@Composable
internal fun TrafficConfigFullScreenScaffold(
    title: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    topContentInset: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(contentPadding: PaddingValues, listPadding: PaddingValues, scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior) -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = onBack,
    )
    app.skipi.ui.config.SkipiTrafficConfigFullScreenScaffold(
        title = title,
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(R.string.common_save),
        onBack = onBack,
        onSave = onSave,
        actions = actions,
        topContentInset = topContentInset,
        content = content,
    )
}
