// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.background
import ui.text.themedFontWeight
import ui.components.AppWindowDialog
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
import app.AppState
import app.LocalUpdateAppState
import app.ProxyServerState
import app.R
import app.collectAppState
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.applyProxyServerEditResult
import app.skipi.ui.config.SkipiTrafficConfigScreen
import app.skipi.ui.config.SkipiTrafficConfigContextMenu
import app.skipi.ui.config.SkipiTrafficConfigProxyGroupContextMenu
import app.skipi.ui.config.SkipiTrafficConfigUrlImportDialog
import app.skipi.ui.config.TrafficConfigProfileItem
import app.skipi.ui.config.TrafficConfigProxyGroupItem
import app.navigation.ProxyServerEditResult
import app.navigation.Route
import app.navigation.TrafficConfigEditorSection
import features.proxy.server.editor.editableCopy
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.StrategyGroup
import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.normalizeSkipiUserAgent
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.usecase.toSubscriptionFetchOptions
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
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.AppTheme
import ui.clipboard.getPlainText
import ui.clipboard.setPlainText
import ui.components.AppCascadingListPopup
import ui.components.WarningConfirmDialog
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.text.formatTemplate
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
            when (
                val outcome = applyProxyServerEditResult(
                    result = result,
                    store = services.sharedApplicationStore,
                    defaultGroupId = DefaultSubscriptionGroupId,
                )
            ) {
                is ProxyServerEditApplyOutcome.Saved -> Unit
                ProxyServerEditApplyOutcome.Deleted -> {
                    val remarks = result.server.getInfo().remarks.ifBlank { result.server.getInfo().protocol }
                    services.tipNotifier.show(context.getString(R.string.proxy_server_list_deleted).formatTemplate("name" to remarks))
                }
                is ProxyServerEditApplyOutcome.Failed -> {
                    services.tipNotifier.showError(IllegalStateException(outcome.reason))
                }
            }
        }
    }

    fun createConfig() {
        var createdId = appState.nextTrafficConfigId
        updateAppState { state ->
            val localizedName = context.getString(R.string.configs_new_name, state.nextTrafficConfigId)
            state.withCreatedTrafficConfig(
                name = localizedName,
                rawDocument = defaultSkipiTrafficConfigRaw(name = localizedName),
            ).also { createdId = it.profileId }.state
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
                        options = appState.toSubscriptionFetchOptions(),
                    )
                } else {
                    text
                }
                updateAppState { state ->
                    val imported = state.withImportedTrafficConfig(
                        content = content,
                        activate = false,
                        fallbackName = context.getString(R.string.configs_imported_name),
                        sourceUrl = if (isHttpUrl) text else "",
                    )
                    imported.withProxyCatalogFrom(state)
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
                    options = appState.toSubscriptionFetchOptions(),
                )
                val normalized = fetched.trimEnd() + "\n"
                val analysis = normalized.analyzeShadowrocketConfig()
                require(analysis.diagnostics.none { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
                    analysis.diagnostics.first { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }.message
                }
                var newId = appState.nextTrafficConfigId
                updateAppState { state ->
                    state.withCreatedTrafficConfig(
                        name = context.getString(R.string.configs_imported_name),
                        rawDocument = normalized,
                        sourceUrl = normalizedUrl,
                        lastUpdatedAtMillis = System.currentTimeMillis(),
                        readSkipiSettingsFromRawDocument = true,
                    ).also { newId = it.profileId }.state
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
                        options = appState.toSubscriptionFetchOptions(),
                    )
                    val normalized = fetched.trimEnd() + "\n"
                    val analysis = normalized.analyzeShadowrocketConfig()
                    require(analysis.diagnostics.none { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
                        analysis.diagnostics.first { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }.message
                    }
                    updateAppState { state ->
                        state.withUpdatedTrafficConfigProfile(config.id) { current ->
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
                        }
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
        navigator.navigateForResult(
            route = Route.ProxyServerEditor(
                ps = StrategyGroup(),
                serverId = null,
                groupId = AutoBalancerGroupId,
                returnGroupId = AutoBalancerGroupId,
                resultKey = ConfigProxyGroupEditResultKey,
            ),
            requestKey = ConfigProxyGroupEditResultKey,
        )
    }

    fun openProxyGroupEditor(
        server: StrategyGroup,
        serverId: Int?,
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
            serverId = null,
        )
    }

    fun deleteProxyGroup(proxyGroup: ProxyServerState) {
        services.sharedApplicationStore.dispatch(
            SharedApplicationAction.RemoveProxyServer(proxyGroup.id),
        )
    }

    fun duplicateConfig(config: TrafficConfigState) {
        var duplicateId = 0
        updateAppState { state ->
            state.withDuplicatedTrafficConfigProfile(
                source = config,
                name = context.getString(R.string.configs_copy_name, config.name),
            ).also { duplicateId = it.profileId }.state
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
        updateAppState { state -> state.withDeletedTrafficConfigProfile(config.id) }
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
            .statusBarsPadding(),
    ) {
        SkipiTrafficConfigScreen(
            profiles = appState.trafficConfigs.map { config ->
                TrafficConfigProfileItem(
                    profile = config,
                    active = config.id == appState.activeTrafficConfigId,
                    updating = config.id in updatingConfigIds,
                    canDelete = appState.trafficConfigs.size > 1,
                    hasUnsupportedSections = config.rawConfig.analyzeShadowrocketConfig().unsupportedSections.isNotEmpty(),
                )
            },
            globalProxyGroups = appState.proxyServers.filter { state ->
                state.groupId == AutoBalancerGroupId &&
                    (state.server as? StrategyGroup)?.sourceTrafficConfigId == null
            }.map { state ->
                TrafficConfigProxyGroupItem(
                    key = "global:${state.id}",
                    name = state.server.getInfo().remarks.ifBlank { state.server.getInfo().protocol },
                    subtitle = state.server.getInfo().address.ifBlank { "Auto-Balancer" },
                    badge = "StrategyGroup",
                )
            },
            sourcedProxyGroups = configProxyGroups.map { item ->
                TrafficConfigProxyGroupItem(
                    key = "source:${item.configId}:${item.group.lineNumber}",
                    name = item.group.name,
                    subtitle = item.configName,
                    badge = item.group.type,
                )
            },
            contentPadding = listPadding,
            onAddConfig = { showAddMenu = true },
            onAddGlobalProxyGroup = ::createProxyGroup,
            onSelectProfile = { id ->
                updateAppState { state -> state.withActiveTrafficConfigProfile(id) }
            },
            onEditProfile = { id -> contextMenuConfig = appState.trafficConfigs.firstOrNull { it.id == id } },
            onDeleteProfile = { id ->
                appState.trafficConfigs.firstOrNull { it.id == id }?.let { config ->
                    if (appState.enableDeletionConfirmation) pendingConfigDeletion = config else deleteConfig(config)
                }
            },
            onUpdateProfile = { id -> appState.trafficConfigs.firstOrNull { it.id == id }?.let { onTriggerConfigUpdate(it) } },
            onProfileMenu = { id -> contextMenuConfig = appState.trafficConfigs.firstOrNull { it.id == id } },
            onOpenProxyGroup = { key ->
                if (key.startsWith("global:")) {
                    val id = key.substringAfter(':').toIntOrNull()
                    val state = appState.proxyServers.firstOrNull { it.id == id }
                    (state?.server as? StrategyGroup)?.let { server -> openProxyGroupEditor(server, state.id) }
                } else {
                    val parts = key.split(':')
                    val configId = parts.getOrNull(1)?.toIntOrNull()
                    if (configId != null) navigator.push(Route.TrafficConfigSection(configId, TrafficConfigEditorSection.ProxyGroups))
                }
            },
            onDeleteGlobalProxyGroup = { key ->
                val id = key.substringAfter(':').toIntOrNull()
                appState.proxyServers.firstOrNull { it.id == id }?.let { group ->
                    if (appState.enableDeletionConfirmation) pendingAutoBalancerDeletion = group else deleteProxyGroup(group)
                }
            },
            onGlobalProxyGroupMenu = { key ->
                val id = key.substringAfter(':').toIntOrNull()
                contextMenuAutoBalancer = appState.proxyServers.firstOrNull { it.id == id }
            },
        )
    }

    if (showAddMenu) {
        AppCascadingListPopup(
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
        SkipiTrafficConfigUrlImportDialog(
            show = true,
            url = importUrl,
            onUrlChange = { importUrl = it },
            onDismissRequest = { showUrlImportDialog = false },
            onImport = { url ->
                showUrlImportDialog = false
                importConfigFromUrl(url)
            },
        )
    }

    contextMenuConfig?.let { config ->
        SkipiTrafficConfigContextMenu(
            show = true,
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
                updateAppState { state -> state.withActiveTrafficConfigProfile(config.id) }
                services.appScope.launch { services.tipNotifier.show(context.getString(R.string.configs_enabled)) }
            },
        )
    }

    contextMenuAutoBalancer?.let { proxyGroup ->
        SkipiTrafficConfigProxyGroupContextMenu(
            show = true,
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
