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
import androidx.compose.runtime.mutableIntStateOf
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
import app.collectAppState
import app.skipi.app.config.withGeneralOptions
import app.skipi.app.config.withNetworkActivation
import app.skipi.app.config.withProfileBasics
import app.skipi.app.config.withDnsOptions
import app.skipi.app.config.withTunnelOptions
import app.navigation.Route
import app.navigation.TrafficConfigEditorSection
import app.skipi.ui.config.SkipiTrafficConfigGeneralOptions
import app.skipi.ui.config.SkipiTrafficConfigNetworkActivation
import app.skipi.ui.config.SkipiTrafficConfigProfileBasics
import app.skipi.ui.config.SkipiTrafficConfigDnsEditor
import app.skipi.ui.config.SkipiTrafficConfigTunnelEditor
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
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var name by remember(config.id) { mutableStateOf(config.name) }
    var sourceUrl by remember(config.id) { mutableStateOf(config.sourceUrl) }
    var updateLocked by remember(config.id) { mutableStateOf(config.updateLocked) }
    var autoUpdate by remember(config.id) { mutableStateOf(config.autoUpdate) }
    var updateInterval by remember(config.id) { mutableStateOf(config.updateInterval) }
    var geoAutoUpdate by remember(config.id) { mutableStateOf(config.resourceSettings.autoUpdate) }
    var geoUpdateInterval by remember(config.id) {
        mutableStateOf(config.resourceSettings.updateInterval)
    }

    fun saveBasics() {
        val updated = config.withProfileBasics(
            name = name,
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            autoUpdate = autoUpdate,
            updateInterval = updateInterval,
            resourceAutoUpdate = geoAutoUpdate,
            resourceUpdateInterval = geoUpdateInterval,
        )
        if (updated == config) return

        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                current.withProfileBasics(
                    name = name,
                    sourceUrl = sourceUrl,
                    updateLocked = updateLocked,
                    autoUpdate = autoUpdate,
                    updateInterval = updateInterval,
                    resourceAutoUpdate = geoAutoUpdate,
                    resourceUpdateInterval = geoUpdateInterval,
                )
            }
        }
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = {
            saveBasics()
            navigator.pop()
        },
    )

    Scaffold(
        containerColor = AppTheme.colors.background,
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.configs_edit),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = { saveBasics(); navigator.pop() }) },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val basePadding = pageListPadding(contentPadding)
        val layoutDirection = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            start = basePadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = basePadding.calculateTopPadding() + 8.dp,
            end = basePadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = basePadding.calculateBottomPadding() + 12.dp,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.pageScrollModifiers(scrollBehavior),
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "profile_basics") {
                    SkipiTrafficConfigProfileBasics(
                        name = name,
                        onNameChange = { name = it },
                        sourceUrl = sourceUrl,
                        onSourceUrlChange = { sourceUrl = it },
                        updateLocked = updateLocked,
                        onUpdateLockedChange = { updateLocked = it },
                        autoUpdate = autoUpdate,
                        onAutoUpdateChange = { autoUpdate = it },
                        updateInterval = updateInterval,
                        onUpdateIntervalChange = { updateInterval = it },
                        resourceAutoUpdate = geoAutoUpdate,
                        onResourceAutoUpdateChange = { geoAutoUpdate = it },
                        resourceUpdateInterval = geoUpdateInterval,
                        onResourceUpdateIntervalChange = { geoUpdateInterval = it },
                    )
                }
                item(key = "sections_title") {
                    SmallTitle(text = stringResource(R.string.configs_editor_sections))
                }
                item(key = "section_general") {
                    ConfigEditorGroupCard(
                        title = stringResource(R.string.configs_general_title),
                        summary = stringResource(R.string.configs_general_summary),
                        onClick = {
                            saveBasics()
                            navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.General))
                        },
                    )
                }
                item(key = "section_dns") {
                    ConfigEditorGroupCard(
                        title = stringResource(R.string.configs_dns_title),
                        summary = stringResource(R.string.configs_dns_summary),
                        onClick = {
                            saveBasics()
                            navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.Dns))
                        },
                    )
                }
                item(key = "section_tunnel") {
                    ConfigEditorGroupCard(
                        title = stringResource(R.string.configs_android_title),
                        summary = stringResource(R.string.configs_tunnel_summary),
                        onClick = {
                            saveBasics()
                            navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.Tunnel))
                        },
                    )
                }
                item(key = "section_network") {
                    ConfigEditorGroupCard(
                        title = stringResource(R.string.configs_network_title),
                        summary = stringResource(R.string.configs_network_summary),
                        onClick = {
                            saveBasics()
                            navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.Network))
                        },
                    )
                }
                item(key = "section_routing") {
                    ConfigEditorGroupCard(
                        title = stringResource(R.string.configs_rules_title),
                        summary = stringResource(R.string.configs_routing_summary),
                        onClick = {
                            saveBasics()
                            navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.Routing))
                        },
                    )
                }
                item(key = "section_per_app") {
                    ConfigEditorGroupCard(
                        title = stringResource(R.string.configs_per_app),
                        summary = stringResource(R.string.configs_per_app_summary),
                        onClick = {
                            saveBasics()
                            navigator.push(Route.ProxyAppList(config.id))
                        },
                    )
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                trackPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun ConfigEditorGroupCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = themedFontWeight(FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = MiuixIcons.Edit,
                contentDescription = null,
                tint = AppTheme.colors.onSurfaceVariant,
            )
        }
    }
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
    fun save(): Boolean {
        val currentText = rawEditorState.snapshotText()
        if (currentText.isBlank()) return false
        val analysis = currentText.analyzeShadowrocketConfig()
        if (analysis.diagnostics.any { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) return false
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                val normalized = currentText.trimEnd() + "\n"
                current.copy(rawConfig = normalized)
            }
        }
        return true
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = {
            save()
            navigator.pop()
        },
    )

    Scaffold(
        containerColor = AppTheme.colors.background,
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = config.name.ifBlank { stringResource(R.string.configs_raw_edit) },
                isWideScreen = isWideScreen,
                scrollBehavior = MiuixScrollBehavior(),
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = {
                            save()
                            navigator.pop()
                        },
                    )
                },
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
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val basePadding = pageListPadding(contentPadding)
        val layoutDirection = LocalLayoutDirection.current
        val pagePadding = PaddingValues(
            start = basePadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = basePadding.calculateTopPadding(),
            end = basePadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = basePadding.calculateBottomPadding() + 12.dp,
        )
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
    val general = remember(config.rawConfig) { config.rawConfig.analyzeShadowrocketConfig().general }
    var ipv6 by remember(config.id, config.rawConfig) { mutableStateOf(general["ipv6"].isConfigEditorBoolean()) }
    var preferIpv6 by remember(config.id, config.rawConfig) { mutableStateOf(general["prefer-ipv6"].isConfigEditorBoolean()) }
    fun save() {
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                current.withGeneralOptions(ipv6 = ipv6, preferIpv6 = preferIpv6)
            }
        }
    }
    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_general_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = { save(); navigator.pop() },
        onSave = { save(); navigator.pop() },
    ) { listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SkipiTrafficConfigGeneralOptions(
                    ipv6 = ipv6,
                    preferIpv6 = preferIpv6,
                    onIpv6Change = { ipv6 = it },
                    onPreferIpv6Change = { preferIpv6 = it },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TrafficConfigDnsSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    var settings by remember(config.id) { mutableStateOf(config.androidSettings) }
    fun save() {
        updateAppState { state -> state.withUpdatedTrafficConfig(config.id) { it.withDnsOptions(settings) } }
    }
    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_dns_title), padding = padding, isWideScreen = isWideScreen,
        onBack = { save(); navigator.pop() }, onSave = { save(); navigator.pop() },
    ) { listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SkipiTrafficConfigDnsEditor(
                    settings = settings,
                    onSettingsChange = { settings = it },
                )
            }
        }
    }
}


@Composable
private fun TrafficConfigTunnelSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    var settings by remember(config.id) { mutableStateOf(config.androidSettings) }
    var muxConcurrency by remember(config.id) { mutableStateOf(settings.muxConcurrency) }
    fun save() {
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                current.withTunnelOptions(settings, muxConcurrency)
            }
        }
    }
    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_android_title), padding = padding, isWideScreen = isWideScreen,
        onBack = { save(); navigator.pop() }, onSave = { save(); navigator.pop() },
    ) { listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SkipiTrafficConfigTunnelEditor(
                    settings = settings,
                    muxConcurrency = muxConcurrency,
                    onSettingsChange = { settings = it },
                    onMuxConcurrencyChange = { muxConcurrency = it },
                )
            }
        }
    }
}


@Composable
private fun TrafficConfigNetworkSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    var enabled by remember(config.id) { mutableStateOf(config.networkActivation.enabled) }
    val transportLabels = listOf(stringResource(R.string.configs_network_wifi), stringResource(R.string.configs_network_cellular))
    var transport by remember(config.id) { mutableIntStateOf(config.networkActivation.transport.coerceIn(transportLabels.indices)) }
    fun save() = updateAppState { state ->
        state.withUpdatedTrafficConfig(config.id) { current ->
            current.withNetworkActivation(enabled = enabled, transport = transport)
        }
    }
    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_network_title), padding = padding, isWideScreen = isWideScreen,
        onBack = { save(); navigator.pop() }, onSave = { save(); navigator.pop() },
    ) { listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SkipiTrafficConfigNetworkActivation(
                    enabled = enabled,
                    transport = transport,
                    onEnabledChange = { enabled = it },
                    onTransportChange = { transport = it },
                )
            }
        }
    }
}

@Composable
private fun TrafficConfigRoutingSectionPage(padding: PaddingValues, trafficConfigId: Int) {
    val appState by LocalAppStateStore.current.collectAppState()
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run { navigator.pop(); return }
    val analysis = remember(config.rawConfig) { config.rawConfig.analyzeShadowrocketConfig() }
    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_rules_title), padding = padding, isWideScreen = isWideScreen,
        onBack = navigator::pop, onSave = navigator::pop,
    ) { listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("resources") {
                ConfigEditorGroupCard(
                    title = stringResource(R.string.configs_resources),
                    summary = stringResource(R.string.configs_resources_summary),
                    onClick = { navigator.push(Route.ResourceManagement(config.id)) },
                )
            }
            item("rules") {
                ConfigEditorGroupCard(
                    title = stringResource(R.string.configs_rules_title),
                    summary = stringResource(R.string.configs_rule_summary, analysis.rules.count { !it.isFinal }),
                    onClick = {
                        navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.RoutingRules))
                    },
                )
            }
            item("groups") {
                ConfigEditorGroupCard(
                    title = stringResource(R.string.configs_proxy_groups_title),
                    summary = stringResource(R.string.configs_proxy_groups_summary),
                    onClick = {
                        navigator.push(Route.TrafficConfigSection(config.id, TrafficConfigEditorSection.ProxyGroups))
                    },
                )
            }
        }
    }
}

@Composable
internal fun TrafficConfigFullScreenScaffold(
    title: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable (listPadding: PaddingValues, scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = onBack,
    )
    Scaffold(
        containerColor = AppTheme.colors.background,
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    actions?.invoke()
                    if (onSave != null) {
                        NavigationIcon(
                            onClick = onSave,
                            imageVector = MiuixIcons.Ok,
                            contentDescription = stringResource(R.string.common_save),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val basePadding = pageListPadding(contentPadding)
        val layoutDirection = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            start = basePadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = basePadding.calculateTopPadding() + 8.dp,
            end = basePadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = basePadding.calculateBottomPadding() + 12.dp,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            content(listPadding, scrollBehavior)
        }
    }
}


@Composable
private fun ConfigEditorSectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title3,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

private fun String?.isConfigEditorBoolean(): Boolean {
    return this?.trim()?.lowercase() in setOf("true", "yes", "1")
}
