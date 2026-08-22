// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.config

import app.R






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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.AppTheme
import ui.clipboard.setPlainText
import ui.components.BackNavigationIcon
import ui.components.NavigationIcon
import ui.components.StringListEditor
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import engine.network.isIpAddress
import engine.network.isIpv4Address
import engine.vpn.VpnDefaults
import features.settings.sheets.isPort

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
        val trimmedName = name.trim().ifBlank { config.name }
        val trimmedSourceUrl = sourceUrl.trim()
        val trimmedUpdateInterval = updateInterval.trim()
        val trimmedGeoUpdateInterval = geoUpdateInterval.trim()

        val isUnchanged = trimmedName == config.name &&
            trimmedSourceUrl == config.sourceUrl &&
            updateLocked == config.updateLocked &&
            autoUpdate == config.autoUpdate &&
            trimmedUpdateInterval == sanitizeSubscriptionIntervalInput(config.updateInterval) &&
            geoAutoUpdate == config.resourceSettings.autoUpdate &&
            trimmedGeoUpdateInterval == sanitizeSubscriptionIntervalInput(config.resourceSettings.updateInterval)

        if (isUnchanged) return

        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                current.copy(
                    name = trimmedName,
                    sourceUrl = trimmedSourceUrl,
                    updateLocked = updateLocked,
                    autoUpdate = autoUpdate,
                    updateInterval = trimmedUpdateInterval,
                    resourceSettings = current.resourceSettings.copy(
                        autoUpdate = geoAutoUpdate,
                        updateInterval = trimmedGeoUpdateInterval,
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
        containerColor = AppTheme.colors.background,
        modifier = Modifier
            .fillMaxSize(),
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
                current.copy(
                    rawConfig = current.rawConfig
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
    var tunVpnDns by remember(config.id) { mutableStateOf(settings.tunVpnDns) }
    var proxyDns by remember(config.id) { mutableStateOf(settings.proxyDns) }
    var directDns by remember(config.id) { mutableStateOf(settings.directDns) }
    var directDnsDomains by remember(config.id) { mutableStateOf(settings.directDnsDomains) }
    var dnsHosts by remember(config.id) { mutableStateOf(settings.dnsHosts) }

    var showTunDnsDialog by remember { mutableStateOf(false) }
    var showAddProxyDnsDialog by remember { mutableStateOf(false) }
    var showAddDirectDnsDialog by remember { mutableStateOf(false) }
    var showDirectDomainsDialog by remember { mutableStateOf(false) }
    var showDnsHostsDialog by remember { mutableStateOf(false) }

    val proxyDnsPresets = listOf(
        "https://1.1.1.1/dns-query,https://1.0.0.1/dns-query" to "Cloudflare DoH",
        "https://8.8.8.8/dns-query,https://8.8.4.4/dns-query" to "Google DoH",
        "https://dns.adguard-dns.com/dns-query" to "AdGuard DoH",
        "https://dns.quad9.net/dns-query" to "Quad9 DoH",
        "tls://1.1.1.1:853,tls://1.0.0.1:853" to "Cloudflare DoT",
        "tls://8.8.8.8:853,tls://8.8.4.4:853" to "Google DoT",
        "tcp://8.8.8.8:53,tcp://8.8.4.4:53" to "Google TCP",
        "8.8.8.8,8.8.4.4" to "Google DoU",
        "1.1.1.1,1.0.0.1" to "Cloudflare DoU",
    )
    val proxyPresetLabels = proxyDnsPresets.map { it.second } + stringResource(R.string.configs_dns_custom)
    val currentProxyJoined = proxyDns.joinToString(",")
    var proxyPresetIndex by remember(config.id, currentProxyJoined) {
        val idx = proxyDnsPresets.indexOfFirst { it.first == currentProxyJoined }
        mutableIntStateOf(if (idx >= 0) idx else proxyDnsPresets.size)
    }

    val directDnsPresets = listOf(
        "https://77.88.8.8/dns-query" to "Yandex DoH",
        "https://1.1.1.1/dns-query" to "Cloudflare DoH",
        "https://8.8.8.8/dns-query" to "Google DoH",
        "tls://77.88.8.8:853" to "Yandex DoT",
        "77.88.8.8,77.88.8.1" to "Yandex DoU",
        "1.1.1.1,8.8.8.8" to "Cloudflare + Google DoU",
    )
    val directPresetLabels = directDnsPresets.map { it.second } + stringResource(R.string.configs_dns_custom)
    val currentDirectJoined = directDns.joinToString(",")
    var directPresetIndex by remember(config.id, currentDirectJoined) {
        val idx = directDnsPresets.indexOfFirst { it.first == currentDirectJoined }
        mutableIntStateOf(if (idx >= 0) idx else directDnsPresets.size)
    }

    val proxyQuickChips = listOf(
        "DoH (1.1.1.1)" to "https://1.1.1.1/dns-query",
        "DoH (8.8.8.8)" to "https://8.8.8.8/dns-query",
        "DoT (1.1.1.1)" to "tls://1.1.1.1:853",
        "DoT (8.8.8.8)" to "tls://8.8.8.8:853",
        "TCP (8.8.8.8)" to "tcp://8.8.8.8:53",
        "DoU (1.1.1.1)" to "1.1.1.1",
        "DoU (8.8.8.8)" to "8.8.8.8",
    )

    val directQuickChips = listOf(
        "DoH (Yandex)" to "https://77.88.8.8/dns-query",
        "DoT (Yandex)" to "tls://77.88.8.8:853",
        "DoU (Yandex)" to "77.88.8.8",
        "DoH (Cloudflare)" to "https://1.1.1.1/dns-query",
        "DoU (Google)" to "8.8.8.8",
    )

    fun save() {
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { current ->
                current.copy(
                    androidSettings = settings.copy(
                        tunVpnDns = tunVpnDns.trim().ifBlank { VpnDefaults.IPV4_DNS },
                        proxyDns = proxyDns.map(String::trim).filter(String::isNotEmpty).distinct(),
                        directDns = directDns.map(String::trim).filter(String::isNotEmpty).distinct(),
                        directDnsDomains = directDnsDomains.map(String::trim).filter(String::isNotEmpty).distinct(),
                        dnsHosts = dnsHosts.map(String::trim).filter(String::isNotEmpty).distinct(),
                    ),
                    rawConfig = current.rawConfig.withShadowrocketGeneralValue(
                        "dns-server",
                        directDns.firstOrNull()?.trim() ?: "system",
                    ),
                )
            }
        }
    }

    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_dns_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = { save(); navigator.pop() },
        onSave = { save(); navigator.pop() },
    ) { listPadding ->
        LazyColumn(
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "master_switches_title") {
                SmallTitle(text = stringResource(R.string.configs_dns_master_switches))
            }
            item(key = "master_switches_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.configs_local_dns),
                        summary = stringResource(R.string.configs_local_dns_summary),
                        checked = settings.enableVpnLocalDns,
                        onCheckedChange = { settings = settings.copy(enableVpnLocalDns = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_fake_dns),
                        summary = stringResource(R.string.configs_fake_dns_summary),
                        checked = settings.enableFakeDns,
                        enabled = settings.enableVpnLocalDns,
                        onCheckedChange = { settings = settings.copy(enableFakeDns = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_dns_direct_fallback_proxy),
                        summary = stringResource(R.string.configs_dns_direct_fallback_proxy_summary),
                        checked = settings.enableDirectDnsForProxyServerDomains,
                        onCheckedChange = { settings = settings.copy(enableDirectDnsForProxyServerDomains = it) },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.configs_dns_tun_dns),
                        summary = tunVpnDns,
                        onClick = { showTunDnsDialog = true },
                    )
                }
            }

            item(key = "proxy_dns_title") {
                SmallTitle(text = stringResource(R.string.configs_dns_proxy_section))
            }
            item(key = "proxy_dns_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.configs_dns_servers),
                        items = proxyPresetLabels,
                        selectedIndex = proxyPresetIndex,
                        onSelectedIndexChange = { selection ->
                            proxyPresetIndex = selection
                            proxyDnsPresets.getOrNull(selection)?.let { preset ->
                                proxyDns = preset.first.split(',').map(String::trim).filter(String::isNotEmpty)
                            }
                        },
                    )
                    DnsQuickChipsRow(
                        chips = proxyQuickChips,
                        onAdd = { server ->
                            if (server !in proxyDns) {
                                proxyDns = proxyDns + server
                            }
                        },
                    )
                    if (proxyDns.isEmpty()) {
                        Text(
                            text = stringResource(R.string.configs_dns_empty_servers),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    } else {
                        proxyDns.forEach { server ->
                            DnsServerRow(
                                server = server,
                                onDelete = { proxyDns = proxyDns - server },
                            )
                        }
                    }
                    AddServerActionRow(
                        title = stringResource(R.string.configs_dns_add_server),
                        onClick = { showAddProxyDnsDialog = true },
                    )
                }
            }

            item(key = "direct_dns_title") {
                SmallTitle(text = stringResource(R.string.configs_dns_direct_section))
            }
            item(key = "direct_dns_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.configs_dns_servers),
                        items = directPresetLabels,
                        selectedIndex = directPresetIndex,
                        onSelectedIndexChange = { selection ->
                            directPresetIndex = selection
                            directDnsPresets.getOrNull(selection)?.let { preset ->
                                directDns = preset.first.split(',').map(String::trim).filter(String::isNotEmpty)
                            }
                        },
                    )
                    DnsQuickChipsRow(
                        chips = directQuickChips,
                        onAdd = { server ->
                            if (server !in directDns) {
                                directDns = directDns + server
                            }
                        },
                    )
                    if (directDns.isEmpty()) {
                        Text(
                            text = stringResource(R.string.configs_dns_empty_servers),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    } else {
                        directDns.forEach { server ->
                            DnsServerRow(
                                server = server,
                                onDelete = { directDns = directDns - server },
                            )
                        }
                    }
                    AddServerActionRow(
                        title = stringResource(R.string.configs_dns_add_server),
                        onClick = { showAddDirectDnsDialog = true },
                    )
                }
            }

            item(key = "advanced_rules_title") {
                SmallTitle(text = stringResource(R.string.configs_rules_title))
            }
            item(key = "advanced_rules_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.configs_dns_direct_domains_title),
                        summary = if (directDnsDomains.isEmpty()) {
                            stringResource(R.string.configs_dns_direct_domains_empty)
                        } else {
                            "${directDnsDomains.size} — " + directDnsDomains.take(3).joinToString(", ") + if (directDnsDomains.size > 3) "…" else ""
                        },
                        onClick = { showDirectDomainsDialog = true },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.configs_dns_hosts_title),
                        summary = if (dnsHosts.isEmpty()) {
                            stringResource(R.string.configs_dns_hosts_empty)
                        } else {
                            "${dnsHosts.size} — " + dnsHosts.take(2).joinToString(", ") + if (dnsHosts.size > 2) "…" else ""
                        },
                        onClick = { showDnsHostsDialog = true },
                    )
                }
            }
        }
    }

    TunDnsDialog(
        show = showTunDnsDialog,
        currentDns = tunVpnDns,
        onDismissRequest = { showTunDnsDialog = false },
        onConfirm = { tunVpnDns = it },
    )

    AddDnsServerDialog(
        show = showAddProxyDnsDialog,
        title = stringResource(R.string.configs_dns_proxy_section),
        suggestions = proxyQuickChips,
        onDismissRequest = { showAddProxyDnsDialog = false },
        onConfirm = { server ->
            if (server !in proxyDns) proxyDns = proxyDns + server
        },
    )

    AddDnsServerDialog(
        show = showAddDirectDnsDialog,
        title = stringResource(R.string.configs_dns_direct_section),
        suggestions = directQuickChips,
        onDismissRequest = { showAddDirectDnsDialog = false },
        onConfirm = { server ->
            if (server !in directDns) directDns = directDns + server
        },
    )

    DirectDomainsDialog(
        show = showDirectDomainsDialog,
        domains = directDnsDomains,
        onDismissRequest = { showDirectDomainsDialog = false },
        onSave = { directDnsDomains = it },
    )

    DnsHostsDialog(
        show = showDnsHostsDialog,
        hosts = dnsHosts,
        onDismissRequest = { showDnsHostsDialog = false },
        onSave = { dnsHosts = it },
    )
}

private fun dnsProtocolBadge(server: String): String {
    val trimmed = server.trim().lowercase()
    return when {
        trimmed.startsWith("https://") || trimmed.startsWith("h2c://") || trimmed.startsWith("https+local://") -> "DoH"
        trimmed.startsWith("tls://") || trimmed.startsWith("tls+local://") -> "DoT"
        trimmed.startsWith("tcp://") || trimmed.startsWith("tcp+local://") -> "TCP"
        else -> "DoU"
    }
}

@Composable
private fun DnsProtocolBadge(protocol: String) {
    val (bgColor, textColor) = when (protocol) {
        "DoH" -> Color(0xFF6750A4).copy(alpha = 0.22f) to Color(0xFFD0BCFF)
        "DoT" -> Color(0xFF00838F).copy(alpha = 0.22f) to Color(0xFF80DEEA)
        "TCP" -> Color(0xFFE65100).copy(alpha = 0.22f) to Color(0xFFFFB74D)
        else -> Color(0xFF546E7A).copy(alpha = 0.22f) to Color(0xFFCFD8DC)
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = protocol,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DnsServerRow(
    server: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val protocol = dnsProtocolBadge(server)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DnsProtocolBadge(protocol = protocol)
        Spacer(Modifier.width(8.dp))
        Text(
            text = server,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DnsQuickChipsRow(
    chips: List<Pair<String, String>>,
    onAdd: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (label, address) ->
            Box(
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onAdd(address) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.body2,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddServerActionRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Add,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AddDnsServerDialog(
    show: Boolean,
    title: String,
    suggestions: List<Pair<String, String>>,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show) return
    var input by remember { mutableStateOf("") }
    val invalidMessage = stringResource(R.string.configs_dns_server_invalid)
    val error = remember(input) {
        if (input.isBlank()) null else configDnsServerInputError(input, invalidMessage)
    }

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                state = rememberTextFieldState(input),
                inputTransformation = { input = asCharSequence().toString() },
                label = stringResource(R.string.configs_dns_server_address),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            if (suggestions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.color_picker_quick_presets),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                DnsQuickChipsRow(
                    chips = suggestions,
                    onAdd = { input = it },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismissRequest,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.common_add),
                    onClick = {
                        val trimmed = input.trim()
                        if (trimmed.isNotEmpty() && configDnsServerInputError(trimmed, invalidMessage) == null) {
                            onConfirm(trimmed)
                            onDismissRequest()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TunDnsDialog(
    show: Boolean,
    currentDns: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show) return
    var input by remember(currentDns) { mutableStateOf(currentDns) }
    val invalidMessage = stringResource(R.string.settings_tun_dns_invalid)
    val isValid = remember(input) { engine.network.isIpv4Address(input.trim()) }

    WindowDialog(
        show = true,
        title = stringResource(R.string.configs_dns_tun_dns),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                state = rememberTextFieldState(input),
                inputTransformation = { input = asCharSequence().toString() },
                label = stringResource(R.string.configs_dns_tun_dns),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            if (!isValid && input.isNotBlank()) {
                Text(
                    text = invalidMessage,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            ConfigPageHint(stringResource(R.string.configs_dns_tun_dns_summary))
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismissRequest,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        if (isValid) {
                            onConfirm(input.trim())
                            onDismissRequest()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DirectDomainsDialog(
    show: Boolean,
    domains: List<String>,
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    if (!show) return
    var list by remember(domains) { mutableStateOf(domains) }
    val invalidMessage = stringResource(R.string.configs_dns_domain_invalid)

    WindowDialog(
        show = true,
        title = stringResource(R.string.configs_dns_direct_domains_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StringListEditor(
                editorKey = "direct_domains_dialog",
                title = stringResource(R.string.configs_dns_direct_domains_title),
                description = stringResource(R.string.configs_dns_direct_domains_summary),
                values = list,
                onValuesChange = { list = it },
                emptyText = stringResource(R.string.configs_dns_direct_domains_empty),
                validateInput = { configDnsDomainInputError(it, invalidMessage) },
                suggestionContent = { onApplySuggestion ->
                    DomainRuleSuggestions(onSelect = { onApplySuggestion(it, true) })
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismissRequest,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        onSave(list)
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun DnsHostsDialog(
    show: Boolean,
    hosts: List<String>,
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    if (!show) return
    var list by remember(hosts) { mutableStateOf(hosts) }
    val invalidMessage = stringResource(R.string.configs_dns_hosts_invalid)

    WindowDialog(
        show = true,
        title = stringResource(R.string.configs_dns_hosts_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StringListEditor(
                editorKey = "dns_hosts_dialog",
                title = stringResource(R.string.configs_dns_hosts_title),
                description = stringResource(R.string.configs_dns_hosts_summary),
                values = list,
                onValuesChange = { list = it },
                emptyText = stringResource(R.string.configs_dns_hosts_empty),
                validateInput = { configDnsHostInputError(it, invalidMessage) },
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismissRequest,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        onSave(list)
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun DomainRuleSuggestions(onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SuggestionChip(label = "geosite:cn", onClick = { onSelect("geosite:cn") })
        SuggestionChip(label = "geosite:category-gov-ru", onClick = { onSelect("geosite:category-gov-ru") })
        SuggestionChip(label = "domain:ru", onClick = { onSelect("domain:ru") })
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    TextButton(
        text = label,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

private const val ConfigDnsHostSeparator = ':'
private val ConfigXrayDnsUrlSchemes = setOf(
    "https",
    "h2c",
    "https+local",
    "h2c+local",
    "quic+local",
    "tls",
    "tls+local",
    "tcp",
    "tcp+local",
    "udp",
    "udp+local",
)

private fun configDnsServerInputError(input: String, invalidMessage: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return invalidMessage
    return if (isConfigXrayDnsServer(trimmed)) null else invalidMessage
}

private fun isConfigXrayDnsServer(value: String): Boolean {
    if (value.equals("localhost", ignoreCase = true) || value.equals("fakedns", ignoreCase = true)) {
        return true
    }
    val schemeEnd = value.indexOf("://")
    if (schemeEnd >= 0) {
        val scheme = value.substring(0, schemeEnd).lowercase()
        if (scheme !in ConfigXrayDnsUrlSchemes) return false
        val authority = value.substring(schemeEnd + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        return isConfigXrayDnsAuthority(authority)
    }
    return isIpAddress(value) || (!value.contains(":") && isConfigDnsHostDomain(value))
}

private fun isConfigXrayDnsAuthority(authority: String): Boolean {
    val trimmed = authority.trim()
    if (trimmed.isBlank()) return false
    if (trimmed.startsWith("[")) {
        val closeBracketIndex = trimmed.indexOf(']')
        if (closeBracketIndex <= 1) return false
        val host = trimmed.substring(1, closeBracketIndex)
        val rest = trimmed.substring(closeBracketIndex + 1)
        return isIpAddress(host) && (rest.isEmpty() || (rest.startsWith(":") && isPort(rest.drop(1))))
    }
    val colonCount = trimmed.count { it == ':' }
    if (colonCount == 0) {
        return isIpAddress(trimmed) || isConfigDnsHostDomain(trimmed)
    }
    if (colonCount == 1) {
        val host = trimmed.substringBefore(':')
        val port = trimmed.substringAfter(':')
        return (isIpAddress(host) || isConfigDnsHostDomain(host)) && isPort(port)
    }
    return isIpAddress(trimmed)
}

private fun configDnsDomainInputError(input: String, invalidMessage: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return invalidMessage
    if (trimmed.startsWith("regexp:", ignoreCase = true)) {
        return if (trimmed.substringAfter(":").isBlank()) invalidMessage else null
    }
    val supportedPrefix = trimmed.substringBefore(":", missingDelimiterValue = "")
        .lowercase()
        .takeIf { it in setOf("domain", "full", "keyword", "geosite", "ext") }
    if (supportedPrefix != null) {
        return if (trimmed.substringAfter(":").isBlank()) invalidMessage else null
    }
    return if (trimmed.contains("://") || trimmed.contains("/")) invalidMessage else null
}

private fun configDnsHostInputError(input: String, invalidMessage: String): String? {
    val separatorIndex = input.indexOf(ConfigDnsHostSeparator)
    if (separatorIndex <= 0 || separatorIndex == input.lastIndex) return invalidMessage
    val domain = input.substring(0, separatorIndex).trim()
    val addresses = input.substring(separatorIndex + 1)
        .split(",")
        .map { it.trim().trim('[', ']') }
    if (!isConfigDnsHostDomain(domain)) return invalidMessage
    if (addresses.isEmpty() || addresses.any { it.isEmpty() || !isIpAddress(it) }) return invalidMessage
    return null
}

private fun isConfigDnsHostDomain(domain: String): Boolean {
    val normalized = domain.removeSuffix(".")
    if (normalized.isEmpty() || normalized.length > 253) return false
    if (normalized.any { it.isWhitespace() || it == '/' || it == ConfigDnsHostSeparator }) return false
    return normalized.split(".").all { label ->
        label.isNotEmpty() &&
            label.length <= 63 &&
            label.first() != '-' &&
            label.last() != '-' &&
            label.all { it.isLetterOrDigit() || it == '-' }
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
