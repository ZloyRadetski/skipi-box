// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.ProxyServerState
import app.R
import app.collectAppState
import app.navigation.ProxyServerEditResult
import app.navigation.Route
import app.navigation.TrafficConfigEditorSection
import features.proxy.server.editor.editableCopy
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.StrategyGroup
import features.proxy.server.usecase.withDeletedProxyServers
import features.proxy.server.usecase.withSavedProxyServer
import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.normalizeSkipiUserAgent
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SmallTitle
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
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.AppTheme
import ui.clipboard.getPlainText
import ui.clipboard.setPlainText
import ui.components.WarningConfirmDialog
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import utils.encodeBase64

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun TrafficConfigPage(
    padding: PaddingValues,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val context = LocalContext.current.applicationContext
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
    val isWideScreen = LocalIsWideScreen.current
    val clipboard = LocalClipboard.current
    val listState = rememberLazyListState()
    val configProxyGroups = remember(appState.trafficConfigs) {
        appState.trafficConfigs.flatMap { config ->
            config.rawConfig.analyzeShadowrocketConfig().proxyGroups.map { group ->
                TrafficConfigProxyGroupListItem(
                    configId = config.id,
                    configName = config.name,
                    group = group,
                )
            }
        }
    }
    var contextMenuConfig by remember { mutableStateOf<TrafficConfigState?>(null) }
    var pendingConfigDeletion by remember { mutableStateOf<TrafficConfigState?>(null) }
    var pendingUnlockAndUpdateConfig by remember { mutableStateOf<TrafficConfigState?>(null) }
    var contextMenuAutoBalancer by remember { mutableStateOf<ProxyServerState?>(null) }
    var pendingAutoBalancerDeletion by remember { mutableStateOf<ProxyServerState?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var updatingConfigIds by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(navigator) {
        navigator.observeResult<ProxyServerEditResult>(ConfigProxyGroupEditResultKey).collect { result ->
            navigator.clearResult(ConfigProxyGroupEditResultKey)
            updateAppState { state ->
                state.withSavedProxyServer(
                    serverId = result.serverId,
                    server = result.server,
                    groupId = AutoBalancerGroupId,
                ).state
            }
        }
    }

    fun createConfig() {
        var createdId = 0
        updateAppState { state ->
            createdId = state.nextTrafficConfigId
            state.copy(
                trafficConfigs = state.trafficConfigs + TrafficConfigState(
                    id = createdId,
                    name = context.getString(R.string.configs_new_name, createdId),
                    rawConfig = defaultSkipiTrafficConfigRaw(
                        name = context.getString(R.string.configs_new_name, createdId),
                    ),
                ),
                nextTrafficConfigId = createdId + 1,
            )
        }
        navigator.push(Route.TrafficConfigEditor(createdId))
    }

    fun importConfigFromClipboard() {
        services.appScope.launch {
            val text = clipboard.getPlainText().orEmpty().trim()
            runCatching {
                require(text.isNotBlank()) { context.getString(R.string.common_clipboard_empty) }
                val isHttpUrl = text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)
                val content = if (isHttpUrl) {
                    services.subscriptionFetcher.fetch(
                        url = text,
                        userAgent = DefaultSubscriptionUserAgent,
                        options = AndroidSubscriptionFetchOptions(),
                    )
                } else {
                    text
                }
                updateAppState { state ->
                    state.withImportedTrafficConfig(
                        content = content,
                        activate = false,
                        fallbackName = context.getString(R.string.configs_imported_name),
                        sourceUrl = if (isHttpUrl) text else "",
                    )
                }
            }.onSuccess {
                services.tipNotifier.show(context.getString(R.string.configs_imported))
            }.onFailure { error -> services.tipNotifier.showError(error) }
        }
    }

    fun importConfigFromUrl(url: String) {
        services.appScope.launch {
            val normalizedUrl = url.trim()
            runCatching {
                require(normalizedUrl.isNotBlank()) { context.getString(R.string.configs_source_url_empty) }
                services.tipNotifier.show(context.getString(R.string.configs_updating))
                val fetched = services.subscriptionFetcher.fetch(
                    url = normalizedUrl,
                    userAgent = DefaultSubscriptionUserAgent,
                    options = AndroidSubscriptionFetchOptions(),
                )
                val normalized = fetched.trimEnd() + "\n"
                val analysis = normalized.analyzeShadowrocketConfig()
                require(analysis.diagnostics.none { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
                    analysis.diagnostics.first { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }.message
                }
                var newId = 0
                updateAppState { state ->
                    newId = state.nextTrafficConfigId
                    state.copy(
                        trafficConfigs = state.trafficConfigs + TrafficConfigState(
                            id = newId,
                            name = context.getString(R.string.configs_imported_name),
                            rawConfig = normalized,
                            sourceUrl = normalizedUrl,
                            lastUpdatedAtMillis = System.currentTimeMillis(),
                        ).withSkipiSettingsReadFromRawConfig().let { parsed ->
                            parsed.copy(sourceUrl = normalizedUrl.ifBlank { parsed.sourceUrl }).withSkipiSettingsInRawConfig()
                        },
                        nextTrafficConfigId = newId + 1,
                    )
                }
            }.onSuccess {
                services.tipNotifier.show(context.getString(R.string.configs_imported))
            }.onFailure { error -> services.tipNotifier.showError(error) }
        }
    }

    fun updateConfigFromUrl(config: TrafficConfigState, unlock: Boolean) {
        services.appScope.launch {
            val url = config.sourceUrl.trim()
            updatingConfigIds = updatingConfigIds + config.id
            try {
                runCatching {
                    require(url.isNotBlank()) { context.getString(R.string.configs_source_url_empty) }
                    services.tipNotifier.show(context.getString(R.string.configs_updating))
                    val fetched = services.subscriptionFetcher.fetch(
                        url = url,
                        userAgent = normalizeSkipiUserAgent(config.resourceSettings.userAgent),
                        options = AndroidSubscriptionFetchOptions(),
                    )
                    val normalized = fetched.trimEnd() + "\n"
                    val analysis = normalized.analyzeShadowrocketConfig()
                    require(analysis.diagnostics.none { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
                        analysis.diagnostics.first { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }.message
                    }
                    updateAppState { state ->
                        state.withUpdatedTrafficConfig(config.id) { current ->
                            current.copy(
                                rawConfig = normalized,
                                sourceUrl = url,
                                updateLocked = if (unlock) false else current.updateLocked,
                                lastUpdatedAtMillis = System.currentTimeMillis(),
                            ).withSkipiSettingsReadFromRawConfig().let { parsed ->
                                parsed.copy(
                                    sourceUrl = url.ifBlank { parsed.sourceUrl },
                                    updateLocked = if (unlock) false else parsed.updateLocked,
                                ).withSkipiSettingsInRawConfig()
                            }
                        }.withConfigProxyGroupsReflected()
                    }
                }.onSuccess {
                    services.tipNotifier.show(context.getString(R.string.configs_updated))
                }.onFailure { error -> services.tipNotifier.showError(error) }
            } finally {
                updatingConfigIds = updatingConfigIds - config.id
            }
        }
    }

    fun onTriggerConfigUpdate(config: TrafficConfigState) {
        if (config.updateLocked) {
            pendingUnlockAndUpdateConfig = config
        } else {
            updateConfigFromUrl(config, unlock = false)
        }
    }

    fun createProxyGroup() {
        val serverId = appState.nextProxyServerId
        navigator.navigateForResult(
            route = Route.ProxyServerEditor(
                ps = StrategyGroup(),
                serverId = serverId,
                groupId = AutoBalancerGroupId,
                returnGroupId = AutoBalancerGroupId,
                resultKey = ConfigProxyGroupEditResultKey,
            ),
            requestKey = ConfigProxyGroupEditResultKey,
        )
    }

    fun openProxyGroupEditor(
        server: StrategyGroup,
        serverId: Int,
    ) {
        navigator.navigateForResult(
            route = Route.ProxyServerEditor(
                ps = server,
                serverId = serverId,
                groupId = AutoBalancerGroupId,
                returnGroupId = AutoBalancerGroupId,
                resultKey = ConfigProxyGroupEditResultKey,
            ),
            requestKey = ConfigProxyGroupEditResultKey,
        )
    }

    fun duplicateProxyGroup(proxyGroup: ProxyServerState) {
        val original = proxyGroup.server as? StrategyGroup ?: return
        val duplicate = original.editableCopy() as StrategyGroup
        duplicate.remarks = context.getString(
            R.string.configs_copy_name,
            original.remarks.ifBlank { original.getInfo().protocol },
        )
        openProxyGroupEditor(
            server = duplicate,
            serverId = appState.nextProxyServerId,
        )
    }

    fun deleteProxyGroup(proxyGroup: ProxyServerState) {
        updateAppState { state -> state.withDeletedProxyServers(setOf(proxyGroup.id)) }
    }

    fun duplicateConfig(config: TrafficConfigState) {
        var duplicateId = 0
        updateAppState { state ->
            duplicateId = state.nextTrafficConfigId
            state.copy(
                trafficConfigs = state.trafficConfigs + config.copy(
                    id = duplicateId,
                    name = context.getString(R.string.configs_copy_name, config.name),
                    lastUpdatedAtMillis = 0L,
                ).withSkipiSettingsInRawConfig(),
                nextTrafficConfigId = duplicateId + 1,
            ).withConfigProxyGroupsReflected()
        }
        navigator.push(Route.TrafficConfigEditor(duplicateId))
    }

    fun exportConfig(config: TrafficConfigState) {
        services.appScope.launch {
            val fileName = config.name
                .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
                .trim('_')
                .ifBlank { "skipi-config" } + ".conf"
            val uri = services.logFileCreator(fileName) ?: return@launch
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(config.withSkipiSettingsInRawConfig().rawConfig)
                } ?: error("Could not open exported configuration")
            }.onSuccess {
                services.tipNotifier.show(context.getString(R.string.configs_exported))
            }.onFailure { error -> services.tipNotifier.showError(error) }
        }
    }

    fun exportConfigBase64(config: TrafficConfigState) {
        services.appScope.launch {
            val raw = config.withSkipiSettingsInRawConfig().rawConfig
            clipboard.setPlainText(raw.encodeBase64())
            services.tipNotifier.show(context.getString(R.string.configs_exported_base64))
        }
    }

    fun deleteConfig(config: TrafficConfigState) {
        updateAppState { state ->
            if (state.trafficConfigs.size <= 1) return@updateAppState state
            val remaining = state.trafficConfigs.filterNot { it.id == config.id }
            state.copy(
                trafficConfigs = remaining,
                activeTrafficConfigId = state.activeTrafficConfigId.takeIf { it != config.id }
                    ?: remaining.first().id,
            ).withConfigProxyGroupsReflected()
        }
    }

    val contentPadding = pageContentPaddingWithCutout(
        innerPadding = PaddingValues(0.dp),
        outerPadding = padding,
        isWideScreen = isWideScreen,
    )
    val layoutDirection = LocalLayoutDirection.current
    val pagePadding = pageListPadding(contentPadding)
    val listPadding = PaddingValues(
        start = pagePadding.calculateStartPadding(layoutDirection) + 12.dp,
        top = pagePadding.calculateTopPadding() + 8.dp,
        end = pagePadding.calculateEndPadding(layoutDirection) + 12.dp,
        bottom = pagePadding.calculateBottomPadding() + 12.dp,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Section 1: Global Proxy Groups (Auto-balancers)
            item(key = "global_proxy_groups_section_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallTitle(
                        text = stringResource(R.string.configs_global_proxy_groups),
                    )
                    IconButton(onClick = ::createProxyGroup) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = stringResource(R.string.configs_global_proxy_groups_add),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            items(
                items = appState.proxyServers.filter { it.groupId == AutoBalancerGroupId && it.server is StrategyGroup },
                key = { it.id },
            ) { proxyGroup ->
                TrafficConfigGlobalProxyGroupCard(
                    name = proxyGroup.server.getInfo().remarks.ifBlank { proxyGroup.server.getInfo().protocol },
                    summary = proxyGroup.server.getInfo().address,
                    onClick = {
                        openProxyGroupEditor(
                            server = proxyGroup.server as StrategyGroup,
                            serverId = proxyGroup.id,
                        )
                    },
                    onEdit = {
                        openProxyGroupEditor(
                            server = proxyGroup.server as StrategyGroup,
                            serverId = proxyGroup.id,
                        )
                    },
                    onDelete = {
                        if (appState.enableDeletionConfirmation) {
                            pendingAutoBalancerDeletion = proxyGroup
                        } else {
                            deleteProxyGroup(proxyGroup)
                        }
                    },
                    onLongPress = { contextMenuAutoBalancer = proxyGroup },
                )
            }

            // Section 2: Sourced Proxy Groups (from configs)
            if (configProxyGroups.isNotEmpty()) {
                item(key = "sourced_proxy_groups_section_title") {
                    SmallTitle(
                        text = stringResource(R.string.configs_proxy_groups_title),
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(
                    items = configProxyGroups,
                    key = { item -> "config-proxy-group-${item.configId}-${item.group.lineNumber}" },
                ) { item ->
                    TrafficConfigSourcedProxyGroupCard(
                        name = item.group.name,
                        configName = item.configName,
                        groupType = item.group.type,
                        onClick = {
                            navigator.push(
                                Route.TrafficConfigSection(
                                    trafficConfigId = item.configId,
                                    section = TrafficConfigEditorSection.ProxyGroups,
                                ),
                            )
                        },
                    )
                }
            }

            // Visual Separator between Proxy Groups and Config Profiles
            item(key = "configs_separator") {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppTheme.colors.onSurface.copy(alpha = 0.08f)),
                )
                Spacer(Modifier.height(4.dp))
            }

            // Section 3: Configuration Profiles
            item(key = "configs_section_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallTitle(
                        text = stringResource(R.string.configs_profiles),
                    )
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = stringResource(R.string.configs_add),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            items(items = appState.trafficConfigs, key = TrafficConfigState::id) { config ->
                TrafficConfigCard(
                    config = config,
                    selected = config.id == appState.activeTrafficConfigId,
                    isUpdating = config.id in updatingConfigIds,
                    onSelect = {
                        updateAppState { state -> state.copy(activeTrafficConfigId = config.id) }
                    },
                    onUiEdit = { navigator.push(Route.TrafficConfigEditor(config.id)) },
                    onUpdate = { onTriggerConfigUpdate(config) },
                    onLongPress = { contextMenuConfig = config },
                )
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }

    if (showAddMenu) {
        WindowCascadingListPopup(
            show = true,
            entries = listOf(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = stringResource(R.string.configs_add_new),
                            onClick = { showAddMenu = false; createConfig() },
                        ),
                        DropdownItem(
                            text = stringResource(R.string.configs_import_clipboard),
                            onClick = { showAddMenu = false; importConfigFromClipboard() },
                        ),
                        DropdownItem(
                            text = stringResource(R.string.configs_import_url),
                            onClick = { showAddMenu = false; showUrlImportDialog = true },
                        ),
                    ),
                ),
            ),
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { showAddMenu = false },
        )
    }

    if (showUrlImportDialog) {
        var importUrl by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val clipText = clipboard.getPlainText().orEmpty().trim()
            if (clipText.startsWith("http://", ignoreCase = true) || clipText.startsWith("https://", ignoreCase = true)) {
                importUrl = clipText
            }
        }
        WindowDialog(
            show = true,
            title = stringResource(R.string.configs_import_url_dialog_title),
            onDismissRequest = { showUrlImportDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.configs_import_url_dialog_summary),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                TextField(
                    state = rememberTextFieldState(initialText = importUrl),
                    inputTransformation = { importUrl = asCharSequence().toString() },
                    label = stringResource(R.string.configs_source_url),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { showUrlImportDialog = false },
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        text = stringResource(R.string.configs_import_url),
                        enabled = importUrl.isNotBlank(),
                        onClick = {
                            val url = importUrl.trim()
                            showUrlImportDialog = false
                            importConfigFromUrl(url)
                        },
                    )
                }
            }
        }
    }

    contextMenuConfig?.let { config ->
        TrafficConfigContextMenu(
            onDismissRequest = { contextMenuConfig = null },
            onUpdate = if (config.sourceUrl.isNotBlank() && config.id !in updatingConfigIds) {
                {
                    contextMenuConfig = null
                    onTriggerConfigUpdate(config)
                }
            } else null,
            onRawEdit = { contextMenuConfig = null; navigator.push(Route.TrafficConfigRawEditor(config.id)) },
            onUiEdit = { contextMenuConfig = null; navigator.push(Route.TrafficConfigEditor(config.id)) },
            onDuplicate = { contextMenuConfig = null; duplicateConfig(config) },
            onExport = { contextMenuConfig = null; exportConfig(config) },
            onExportBase64 = { contextMenuConfig = null; exportConfigBase64(config) },
            onDelete = { contextMenuConfig = null; pendingConfigDeletion = config },
            onEnable = {
                contextMenuConfig = null
                updateAppState { state -> state.copy(activeTrafficConfigId = config.id) }
                services.appScope.launch { services.tipNotifier.show(context.getString(R.string.configs_enabled)) }
            },
        )
    }

    contextMenuAutoBalancer?.let { proxyGroup ->
        AutoBalancerContextMenu(
            onDismissRequest = { contextMenuAutoBalancer = null },
            onEdit = {
                contextMenuAutoBalancer = null
                openProxyGroupEditor(proxyGroup.server as StrategyGroup, proxyGroup.id)
            },
            onDuplicate = {
                contextMenuAutoBalancer = null
                duplicateProxyGroup(proxyGroup)
            },
            onDelete = {
                contextMenuAutoBalancer = null
                if (appState.enableDeletionConfirmation) {
                    pendingAutoBalancerDeletion = proxyGroup
                } else {
                    deleteProxyGroup(proxyGroup)
                }
            },
        )
    }

    pendingConfigDeletion?.let { config ->
        WarningConfirmDialog(
            show = true,
            title = stringResource(R.string.deletion_confirmation_delete_config),
            summary = stringResource(R.string.deletion_confirmation_summary),
            confirmText = stringResource(R.string.common_delete),
            dismissText = stringResource(R.string.common_cancel),
            onDismissRequest = { pendingConfigDeletion = null },
            onConfirm = {
                pendingConfigDeletion = null
                deleteConfig(config)
            },
        )
    }

    pendingUnlockAndUpdateConfig?.let { config ->
        WarningConfirmDialog(
            show = true,
            title = stringResource(R.string.configs_update_locked_warning_title),
            summary = stringResource(R.string.configs_update_locked_warning_summary),
            confirmText = stringResource(R.string.configs_update_anyway),
            dismissText = stringResource(R.string.common_cancel),
            onDismissRequest = { pendingUnlockAndUpdateConfig = null },
            onConfirm = {
                pendingUnlockAndUpdateConfig = null
                updateConfigFromUrl(config, unlock = true)
            },
        )
    }

    pendingAutoBalancerDeletion?.let { proxyGroup ->
        WarningConfirmDialog(
            show = true,
            title = stringResource(R.string.deletion_confirmation_delete_proxy_server),
            summary = stringResource(R.string.deletion_confirmation_summary),
            confirmText = stringResource(R.string.common_delete),
            dismissText = stringResource(R.string.common_cancel),
            onDismissRequest = { pendingAutoBalancerDeletion = null },
            onConfirm = {
                pendingAutoBalancerDeletion = null
                deleteProxyGroup(proxyGroup)
            },
        )
    }
}

private const val ConfigProxyGroupEditResultKey = "traffic-config-global-proxy-group"

private data class TrafficConfigProxyGroupListItem(
    val configId: Int,
    val configName: String,
    val group: ShadowrocketPolicyGroup,
)

@Composable
private fun ConfigTagChip(
    text: String,
    selected: Boolean = false,
    isWarning: Boolean = false,
) {
    val chipShape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(
                when {
                    isWarning -> MiuixTheme.colorScheme.error.copy(alpha = 0.12f)
                    selected -> AppTheme.colors.onSurface.copy(alpha = 0.12f)
                    else -> AppTheme.colors.onSurface.copy(alpha = 0.06f)
                },
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isWarning) MiuixTheme.colorScheme.error
            else AppTheme.colors.onSurface.copy(alpha = if (selected) 0.95f else 0.70f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrafficConfigCard(
    config: TrafficConfigState,
    selected: Boolean,
    isUpdating: Boolean,
    onSelect: () -> Unit,
    onUiEdit: () -> Unit,
    onUpdate: () -> Unit,
    onLongPress: () -> Unit,
) {
    val analysis = remember(config.rawConfig) { config.rawConfig.analyzeShadowrocketConfig() }
    val selectedShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(selectedShape)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) AppTheme.colors.onSurface.copy(alpha = 0.16f) else Color.Transparent,
                shape = selectedShape,
            ),
        colors = CardDefaults.defaultColors(
            color = if (selected) AppTheme.colors.accent else AppTheme.colors.surface,
        ),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        onClick = onSelect,
        onLongPress = onLongPress,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = config.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (analysis.unsupportedSections.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    ConfigTagChip(
                        text = stringResource(R.string.configs_preserved_unsupported),
                        isWarning = true,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (config.sourceUrl.isNotBlank()) {
                    IconButton(
                        onClick = onUpdate,
                        enabled = !isUpdating,
                    ) {
                        if (isUpdating) {
                            val updatingDescription = stringResource(R.string.configs_updating)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .semantics { contentDescription = updatingDescription },
                                contentAlignment = Alignment.Center,
                            ) {
                                InfiniteProgressIndicator(
                                    color = MiuixTheme.colorScheme.primary,
                                    size = 20.dp,
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.common_refresh),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                IconButton(onClick = onUiEdit) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = stringResource(R.string.configs_ui_edit),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficConfigGlobalProxyGroupCard(
    name: String,
    summary: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
) {
    val selectedShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(selectedShape),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
        insideMargin = PaddingValues(14.dp),
        onClick = onClick,
        onLongPress = onLongPress,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = summary.ifBlank { "Auto-Balancer" },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ConfigTagChip(
                    text = stringResource(R.string.proxy_server_list_auto_balancers),
                    selected = false,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConfigTagChip(
                    text = "StrategyGroup",
                    selected = false,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = MiuixIcons.Edit,
                            contentDescription = stringResource(R.string.common_edit),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficConfigSourcedProxyGroupCard(
    name: String,
    configName: String,
    groupType: String,
    onClick: () -> Unit,
) {
    val selectedShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(selectedShape),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
        insideMargin = PaddingValues(14.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = configName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ConfigTagChip(
                    text = groupType,
                    selected = false,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConfigTagChip(
                    text = configName,
                    selected = false,
                )

                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoBalancerContextMenu(
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    WindowCascadingListPopup(
        show = true,
        entries = listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(text = stringResource(R.string.common_edit), onClick = onEdit),
                    DropdownItem(text = stringResource(R.string.configs_duplicate), onClick = onDuplicate),
                    DropdownItem(text = stringResource(R.string.common_delete), onClick = onDelete),
                ),
            ),
        ),
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun TrafficConfigContextMenu(
    onDismissRequest: () -> Unit,
    onRawEdit: () -> Unit,
    onUiEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onExportBase64: () -> Unit,
    onDelete: () -> Unit,
    onEnable: () -> Unit,
    onUpdate: (() -> Unit)? = null,
) {
    WindowCascadingListPopup(
        show = true,
        entries = listOf(
            DropdownEntry(
                items = buildList {
                    if (onUpdate != null) {
                        add(DropdownItem(text = stringResource(R.string.common_refresh), onClick = onUpdate))
                    }
                    add(DropdownItem(text = stringResource(R.string.configs_raw_edit), onClick = onRawEdit))
                    add(DropdownItem(text = stringResource(R.string.configs_ui_edit), onClick = onUiEdit))
                    add(DropdownItem(text = stringResource(R.string.configs_duplicate), onClick = onDuplicate))
                    add(DropdownItem(text = stringResource(R.string.configs_export), onClick = onExport))
                    add(DropdownItem(text = stringResource(R.string.configs_export_base64), onClick = onExportBase64))
                    add(DropdownItem(text = stringResource(R.string.common_delete), onClick = onDelete))
                    add(DropdownItem(text = stringResource(R.string.configs_activate), onClick = onEnable))
                },
            ),
        ),
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
    )
}
