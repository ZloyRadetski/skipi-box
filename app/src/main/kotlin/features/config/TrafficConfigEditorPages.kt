// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
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
import app.navigation.Route
import app.navigation.TrafficConfigEditorSection
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import features.subscription.sanitizeSubscriptionIntervalInput
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    var updateInterval by remember(config.id) {
        mutableStateOf(sanitizeSubscriptionIntervalInput(config.updateInterval))
    }
    var geoAutoUpdate by remember(config.id) { mutableStateOf(config.resourceSettings.autoUpdate) }
    var geoUpdateInterval by remember(config.id) {
        mutableStateOf(sanitizeSubscriptionIntervalInput(config.resourceSettings.updateInterval))
    }

    fun saveBasics() {
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                current.copy(
                    name = name.trim().ifBlank { current.name },
                    sourceUrl = sourceUrl.trim(),
                    updateLocked = updateLocked,
                    autoUpdate = autoUpdate,
                    updateInterval = updateInterval.trim(),
                    resourceSettings = current.resourceSettings.copy(
                        autoUpdate = geoAutoUpdate,
                        updateInterval = geoUpdateInterval.trim(),
                    ),
                ).withSkipiSettingsInRawConfig()
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
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
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
                item(key = "basics_name") {
                    ConfigPageTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.configs_name),
                        summary = stringResource(R.string.configs_name_summary),
                    )
                }
                item(key = "basics_source_url") {
                    ConfigPageTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        label = stringResource(R.string.configs_source_url),
                        summary = stringResource(R.string.configs_source_url_summary),
                    )
                }
                item(key = "basics_lock") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.configs_lock_update),
                            summary = stringResource(R.string.configs_lock_update_summary),
                            checked = updateLocked,
                            onCheckedChange = { updateLocked = it },
                        )
                    }
                }
                if (!updateLocked) {
                    item(key = "basics_config_auto_update") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SwitchPreference(
                                    title = stringResource(R.string.configs_auto_update),
                                    summary = stringResource(R.string.configs_auto_update_summary),
                                    checked = autoUpdate,
                                    onCheckedChange = { autoUpdate = it },
                                )
                                if (autoUpdate) {
                                    TextField(
                                        state = rememberTextFieldState(initialText = updateInterval),
                                        inputTransformation = InputTransformation
                                            .byValue { _, proposed ->
                                                sanitizeSubscriptionIntervalInput(proposed.toString())
                                            }
                                            .then { updateInterval = asCharSequence().toString() },
                                        label = stringResource(R.string.configs_auto_update_interval),
                                        lineLimits = TextFieldLineLimits.SingleLine,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "basics_geo_auto_update") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = stringResource(R.string.configs_geo_auto_update),
                                summary = stringResource(R.string.configs_geo_auto_update_summary),
                                checked = geoAutoUpdate,
                                onCheckedChange = { geoAutoUpdate = it },
                            )
                            if (geoAutoUpdate) {
                                TextField(
                                    state = rememberTextFieldState(initialText = geoUpdateInterval),
                                    inputTransformation = InputTransformation
                                        .byValue { _, proposed ->
                                            sanitizeSubscriptionIntervalInput(proposed.toString())
                                        }
                                        .then { geoUpdateInterval = asCharSequence().toString() },
                                    label = stringResource(R.string.configs_geo_auto_update_interval),
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                )
                            }
                        }
                    }
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
                    fontWeight = FontWeight.SemiBold,
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
    var rawConfig by remember(config.id) {
        mutableStateOf(config.withSkipiSettingsInRawConfig().rawConfig)
    }
    val rawEditorScrollState = rememberScrollState()
    val analysis = remember(rawConfig) { rawConfig.analyzeShadowrocketConfig() }
    val copiedMessage = stringResource(R.string.common_copied)
    fun save(): Boolean {
        if (rawConfig.isBlank() || analysis.diagnostics.any { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) return false
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                val normalized = rawConfig.trimEnd() + "\n"
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
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.configs_raw),
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
                                clipboard.setPlainText(rawConfig)
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
            top = basePadding.calculateTopPadding() + 8.dp,
            end = basePadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = basePadding.calculateBottomPadding() + 12.dp,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(pagePadding)) {
                Text(
                    text = stringResource(R.string.configs_raw),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                BasicTextField(
                    state = rememberTextFieldState(rawConfig),
                    inputTransformation = { rawConfig = asCharSequence().toString() },
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    textStyle = MiuixTheme.textStyles.main.copy(
                        textAlign = TextAlign.Start,
                        color = AppTheme.colors.onSurface,
                    ),
                    cursorBrush = SolidColor(AppTheme.colors.accent),
                    scrollState = rawEditorScrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            color = AppTheme.colors.surface,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(12.dp),
                )
                if (analysis.unsupportedSections.isNotEmpty()) {
                    Text(
                        stringResource(R.string.configs_preserved_unsupported),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
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
    var dns by remember(config.id, config.rawConfig) { mutableStateOf(general["dns-server"] ?: "system") }
    val dnsPresetValues = listOf(
        "1.1.1.1,1.0.0.1,8.8.8.8,8.8.4.4",
        "8.8.8.8,8.8.4.4",
        "1.1.1.1,1.0.0.1",
        "9.9.9.9,149.112.112.112",
        "94.140.14.14,94.140.15.15",
    )
    val dnsPresetLabels = listOf(
        stringResource(R.string.configs_dns_cloudflare_google),
        stringResource(R.string.configs_dns_google),
        stringResource(R.string.configs_dns_cloudflare),
        stringResource(R.string.configs_dns_quad9),
        stringResource(R.string.configs_dns_adblockdns),
        stringResource(R.string.configs_dns_custom),
    )
    var dnsPreset by remember(config.id, config.rawConfig) {
        mutableIntStateOf(dnsPresetValues.indexOf(dns).takeIf { it >= 0 } ?: dnsPresetValues.size)
    }
    var ipv6 by remember(config.id, config.rawConfig) { mutableStateOf(general["ipv6"].isConfigEditorBoolean()) }
    var preferIpv6 by remember(config.id, config.rawConfig) { mutableStateOf(general["prefer-ipv6"].isConfigEditorBoolean()) }
    fun save() {
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                current.copy(
                    rawConfig = current.rawConfig
                        .withShadowrocketGeneralValue("dns-server", dns.trim().ifBlank { "system" })
                        .withShadowrocketGeneralValue("ipv6", ipv6.toString())
                        .withShadowrocketGeneralValue("prefer-ipv6", preferIpv6.toString()),
                )
            }
        }
    }
    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_general_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = { save(); navigator.pop() },
        onSave = { save(); navigator.pop() },
    ) { listPadding ->
        LazyColumn(
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.configs_dns_servers),
                        items = dnsPresetLabels,
                        selectedIndex = dnsPreset,
                        onSelectedIndexChange = { selection ->
                            dnsPreset = selection
                            dnsPresetValues.getOrNull(selection)?.let { preset -> dns = preset }
                        },
                    )
                    ConfigPageHint(stringResource(R.string.configs_dns_servers_summary))
                    SwitchPreference(
                        title = stringResource(R.string.configs_ipv6),
                        summary = stringResource(R.string.configs_ipv6_summary),
                        checked = ipv6,
                        onCheckedChange = { ipv6 = it },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_ipv6_prefer),
                        summary = stringResource(R.string.configs_ipv6_prefer_summary),
                        enabled = ipv6,
                        checked = preferIpv6,
                        onCheckedChange = { preferIpv6 = it },
                    )
                }
                if (dnsPreset == dnsPresetValues.size) {
                    ConfigPageTextField(
                        value = dns,
                        onValueChange = { dns = it },
                        label = stringResource(R.string.configs_dns_custom_value),
                    )
                }
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
                current.copy(androidSettings = settings.copy(
                    muxConcurrency = muxConcurrency.trim(),
                ))
            }
        }
    }
    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_android_title), padding = padding, isWideScreen = isWideScreen,
        onBack = { save(); navigator.pop() }, onSave = { save(); navigator.pop() },
    ) { listPadding ->
        LazyColumn(
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.configs_sniffing),
                        summary = stringResource(R.string.configs_sniffing_summary),
                        checked = settings.enableSniffing,
                        onCheckedChange = { settings = settings.copy(enableSniffing = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_sniffing_route_only),
                        summary = stringResource(R.string.configs_sniffing_route_only_summary),
                        checked = settings.enableSniffingRouteOnly,
                        enabled = settings.enableSniffing,
                        onCheckedChange = { settings = settings.copy(enableSniffingRouteOnly = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_mux),
                        summary = stringResource(R.string.configs_mux_summary),
                        checked = settings.enableMux,
                        onCheckedChange = { settings = settings.copy(enableMux = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_fragment),
                        summary = stringResource(R.string.configs_fragment_summary),
                        checked = settings.enableFragment,
                        onCheckedChange = { settings = settings.copy(enableFragment = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_fake_dns),
                        summary = stringResource(R.string.configs_fake_dns_summary),
                        checked = settings.enableFakeDns,
                        onCheckedChange = { settings = settings.copy(enableFakeDns = it) },
                    )
                }
                if (settings.enableMux) {
                    ConfigPageTextField(
                        value = muxConcurrency,
                        onValueChange = { muxConcurrency = it },
                        label = stringResource(R.string.configs_mux_concurrency),
                        summary = stringResource(R.string.configs_mux_concurrency_summary),
                    )
                }
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
        state.withUpdatedTrafficConfig(config.id) { current -> current.copy(networkActivation = TrafficConfigNetworkActivation(enabled, transport)) }
    }
    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_network_title), padding = padding, isWideScreen = isWideScreen,
        onBack = { save(); navigator.pop() }, onSave = { save(); navigator.pop() },
    ) { listPadding ->
        LazyColumn(
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    SwitchPreference(title = stringResource(R.string.configs_network_enabled), summary = stringResource(R.string.configs_network_enabled_summary), checked = enabled, onCheckedChange = { enabled = it })
                    WindowDropdownPreference(
                        title = stringResource(R.string.configs_network_transport), items = transportLabels,
                        selectedIndex = transport, enabled = enabled, onSelectedIndexChange = { transport = it },
                    )
                    ConfigPageHint(stringResource(R.string.configs_network_transport_summary))
                    Text(stringResource(R.string.configs_network_priority), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
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
    ) { listPadding ->
        LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = onBack,
    )
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
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
            content(listPadding)
        }
    }
}

@Composable
private fun ConfigPageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    summary: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            state = rememberTextFieldState(value),
            inputTransformation = { onValueChange(asCharSequence().toString()) },
            label = label,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        if (summary != null) {
            ConfigPageHint(summary)
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

@Composable
private fun ConfigPageHint(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

private fun String?.isConfigEditorBoolean(): Boolean {
    return this?.trim()?.lowercase() in setOf("true", "yes", "1")
}
