// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import app.skipi.ui.components.AppCascadingListPopup
import app.skipi.ui.components.WarningConfirmDialog
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.resources.common_clipboard_empty
import app.skipi.ui.resources.configs_add_new
import app.skipi.ui.resources.configs_exported_base64
import app.skipi.ui.resources.configs_import_clipboard
import app.skipi.ui.resources.configs_import_file
import app.skipi.ui.resources.configs_import_url
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.deletion_confirmation_delete_config
import app.skipi.ui.resources.deletion_confirmation_summary
import java.util.Base64
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig
import features.config.defaultShadowrocketConfig
import app.skipi.ui.config.SkipiTrafficConfigContextMenu
import app.skipi.ui.config.SkipiTrafficConfigRawEditor
import app.skipi.ui.config.SkipiTrafficConfigRawEditorLabels
import app.skipi.ui.config.SkipiTrafficConfigRawEditorState
import app.skipi.ui.config.SkipiTrafficConfigScreen
import app.skipi.ui.config.SkipiTrafficConfigBasicsEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigRoutingSectionScreen
import app.skipi.ui.config.SkipiTrafficConfigGeneralEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigDnsEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigTunnelEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigNetworkActivationEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigEditorSectionAction
import app.skipi.ui.config.SkipiTrafficConfigUrlImportDialog
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListEditorScreen
import app.skipi.ui.config.TrafficConfigProfileItem
import app.skipi.ui.config.projectTrafficConfigSourcedProxyGroups
import app.skipi.ui.resourcefiles.ResourceManagementScreen
import app.skipi.ui.layout.shouldShowSplitPane
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_copy_name
import app.skipi.ui.resources.configs_imported_name
import app.skipi.ui.resources.configs_new_name
import app.skipi.ui.resources.configs_edit
import app.skipi.ui.resources.configs_editor_sections
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.configs_routing_summary
import app.skipi.ui.resources.configs_general_title
import app.skipi.ui.resources.configs_general_summary
import app.skipi.ui.resources.configs_dns_title
import app.skipi.ui.resources.configs_dns_summary
import app.skipi.ui.resources.configs_android_title
import app.skipi.ui.resources.configs_tunnel_summary
import app.skipi.ui.resources.configs_network_title
import app.skipi.ui.resources.configs_network_summary
import app.skipi.ui.resources.configs_per_app
import app.skipi.ui.resources.configs_per_app_summary
import app.skipi.ui.resources.configs_per_app_desktop_notice
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.resource_file_download_notification_failed
import app.skipi.ui.resources.configs_update_locked_warning_title
import app.skipi.ui.resources.configs_update_locked_warning_summary
import app.skipi.ui.resources.configs_update_anyway
import org.jetbrains.compose.resources.stringResource
import app.ProjectInfo
import app.skipi.app.config.TrafficConfigProfileRefreshOperations
import app.skipi.app.config.TrafficConfigProfileRefreshRequest
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.app.config.toTrafficConfigState
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.text.DateFormat
import java.time.Duration
import java.util.Date
import features.config.ConfigProfile
import app.skipi.ui.theme.SkipiTheme

private val ConfigBackground: Color
    @Composable get() = DesktopContentBackground
private val ConfigSurface = Color(0xFF202126)
private val ConfigRaisedSurface = Color(0xFF292B31)
private val ConfigBorder = Color(0xFF3C3E45)
private val ConfigText = Color(0xFFF4F4F6)
private val ConfigMuted = Color(0xFFA2A5AF)
private val ConfigGreen = Color(0xFF58D27A)

private data class DesktopConfigDraft(
    val id: Int?,
    val name: String,
    val content: String,
    val sourceUrl: String,
    val updateLocked: Boolean,
    val lastUpdatedAtMillis: Long,
)

private enum class DesktopVisualEditorSection {
    Basics,
    PerApp,
    Routing,
    Rules,
    ProxyGroups,
    General,
    Dns,
    Tunnel,
    Network,
    Resources,
}

private enum class DesktopProxyGroupsReturnTarget {
    TrafficConfigCatalog,
    Routing,
}

private data class DesktopVisualEditorSession(
    val configId: Int,
    val section: DesktopVisualEditorSection = DesktopVisualEditorSection.Basics,
    val proxyGroupsReturnTarget: DesktopProxyGroupsReturnTarget = DesktopProxyGroupsReturnTarget.Routing,
)

private class DesktopConfigEditorSaveRejected(message: String) : RuntimeException(message)

/** Desktop counterpart of Android's TrafficConfigPage and raw configuration editor. */
@Composable
internal fun DesktopConfigsScreen(
    configLibrary: DesktopConfigLibrary,
    onTransformConfigLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary,
    fetchUserAgent: String,
    fetchTimeoutSeconds: Int,
    fetchProfileDocument: suspend (TrafficConfigState, String) -> String,
    contentPadding: PaddingValues,
    resourceActionScope: CoroutineScope,
    onResourceFileStatusMessage: (String) -> Unit,
    desktopSettings: DesktopAppSettings,
    exportProxyServerFullJson: suspend (ProxyServerEditResult) -> String,
    onApplyGlobalProxyGroupResult: (ProxyServerEditResult, (ProxyServerEditApplyOutcome) -> Unit) -> Unit,
    copyTextToClipboard: (String) -> Unit = ::copyTextToDesktopClipboard,
    proxyServers: List<app.skipi.app.model.ProxyServerRecord>,
    subscriptionCatalog: app.skipi.app.model.SubscriptionCatalog,
    resourceFiles: DesktopProfileResourceFiles,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    resourceServices: DesktopResourceFileServices,
    resourceFileStatusMessage: String,
    readPerAppClipboardText: suspend () -> String? = {
        Toolkit.getDefaultToolkit().systemClipboard
            .getData(DataFlavor.stringFlavor) as? String
    },
) {
    val scope = rememberCoroutineScope()
    val fetcher = remember { DesktopSubscriptionFetcher() }
    var editorDraft by remember { mutableStateOf<DesktopConfigDraft?>(null) }
    var visualEditor by remember { mutableStateOf<DesktopVisualEditorSession?>(null) }
    val resourceUserAgentFallback = remember { versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME) }
    val trafficConfigProfiles = remember(configLibrary.configs, resourceUserAgentFallback) {
        configLibrary.configs.map { config -> config.toTrafficConfigState(resourceUserAgentFallback) }
    }
    val sourcedProxyGroups = remember(trafficConfigProfiles) {
        projectTrafficConfigSourcedProxyGroups(trafficConfigProfiles)
    }
    var showAddMenu by remember { mutableStateOf(false) }
    var importUrlDialog by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var pendingDeletion by remember { mutableStateOf<DesktopStoredConfig?>(null) }
    var pendingUnlockAndRefresh by remember {
        mutableStateOf<TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh?>(null)
    }
    var contextMenuConfig by remember { mutableStateOf<DesktopStoredConfig?>(null) }
    var message by remember { mutableStateOf("") }
    var editorSaveError by remember { mutableStateOf<String?>(null) }
    val resourceDateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val resourceFailureFallback = stringResource(Res.string.resource_file_download_notification_failed, "")
    val desktopEditorProfileStore = remember(onTransformConfigLibrary, resourceUserAgentFallback) {
        DesktopTrafficConfigEditorProfileStore(
            resourceUserAgentFallback = resourceUserAgentFallback,
            onTransformConfigLibrary = onTransformConfigLibrary,
        )
    }
    val editorController = remember(desktopEditorProfileStore) {
        TrafficConfigEditorController(
            store = TrafficConfigEditorProfileStore { profileId, transform ->
                val saved = desktopEditorProfileStore.update(profileId, transform)
                if (saved) {
                    editorSaveError = null
                    message = "Конфиг сохранён."
                }
                saved
            },
            onSaveFailure = { error ->
                val description = "Не удалось сохранить конфиги: ${error.message.orEmpty()}"
                message = description
                editorSaveError = description
            },
        )
    }
    var updatingConfigIds by remember { mutableStateOf(emptySet<Int>()) }
    val newProfileName = stringResource(Res.string.configs_new_name, configLibrary.nextConfigId)
    val importedProfileName = stringResource(Res.string.configs_imported_name)
    val clipboardEmptyMessage = stringResource(Res.string.common_clipboard_empty)
    val exportedBase64Message = stringResource(Res.string.configs_exported_base64)

    fun openConfigEditor(config: DesktopStoredConfig) {
        editorSaveError = null
        editorDraft = DesktopConfigDraft(
            id = config.id,
            name = config.name,
            content = config.content,
            sourceUrl = config.sourceUrl,
            updateLocked = config.updateLocked,
            lastUpdatedAtMillis = config.lastUpdatedAtMillis,
        )
    }

    fun openVisualEditor(config: DesktopStoredConfig) {
        editorSaveError = null
        editorDraft = null
        visualEditor = DesktopVisualEditorSession(configId = config.id)
    }

    fun openSourcedProxyGroups(key: String) {
        val configId = key.takeIf { value -> value.startsWith("source:") }
            ?.substringAfter(':')
            ?.substringBefore(':')
            ?.toIntOrNull()
            ?: return
        if (configLibrary.configs.any { config -> config.id == configId }) {
            editorSaveError = null
            editorDraft = null
            visualEditor = DesktopVisualEditorSession(
                configId = configId,
                section = DesktopVisualEditorSection.ProxyGroups,
                proxyGroupsReturnTarget = DesktopProxyGroupsReturnTarget.TrafficConfigCatalog,
            )
        }
    }

    fun persist(
        transform: (DesktopConfigLibrary) -> DesktopConfigLibrary,
        successMessage: String,
        failurePrefix: String = "Не удалось сохранить конфиги: ",
        afterSave: (() -> Unit)? = null,
    ): Boolean {
        return runCatching { onTransformConfigLibrary(transform) }.fold(
            onSuccess = {
                message = successMessage
                afterSave?.invoke()
                true
            },
            onFailure = { error ->
                message = if (error is DesktopConfigEditorSaveRejected) {
                    "Конфиг не сохранён: ${error.message.orEmpty()}"
                } else {
                    "$failurePrefix${error.message.orEmpty()}"
                }
                false
            },
        )
    }

    fun deleteConfig(config: DesktopStoredConfig) {
        var removed = false
        persist(
            transform = { latest ->
                val updated = DesktopConfigLibraries.remove(latest, config.id)
                removed = updated.configs != latest.configs
                updated
            },
            successMessage = "",
            afterSave = { if (removed) message = "Конфиг удалён." },
        )
    }

    fun importRawConfig(content: String, name: String, sourceUrl: String = "") {
        persist(
            transform = { latest ->
                DesktopConfigLibraries.importDocument(
                    library = latest,
                    content = content,
                    activate = false,
                    fallbackName = name,
                    sourceUrl = sourceUrl,
                )
            },
            successMessage = "Конфиг добавлен в библиотеку.",
            failurePrefix = "Конфиг не импортирован: ",
        )
    }

    suspend fun fetchImportedDocument(normalizedUrl: String): String = withContext(Dispatchers.IO) {
        fetcher.fetch(
            url = normalizedUrl,
            userAgent = fetchUserAgent,
            timeout = Duration.ofSeconds(fetchTimeoutSeconds.toLong()),
        ).body
    }

    fun fetchConfigFromUrl(
        url: String,
        updatingConfigId: Int? = null,
        fetchDocument: suspend (normalizedUrl: String) -> String,
        onSuccess: (normalizedUrl: String, content: String) -> Unit,
    ) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            message = "Ссылка на источник пуста."
            return
        }
        scope.launch {
            message = "Загрузка конфига…"
            updatingConfigId?.let { updatingConfigIds = updatingConfigIds + it }
            try {
                val fetched = runCatching { fetchDocument(normalizedUrl) }
                fetched.onSuccess { content -> onSuccess(normalizedUrl, content) }
                    .onFailure { error -> message = "Не удалось загрузить конфиг: ${error.message.orEmpty()}" }
            } finally {
                updatingConfigId?.let { updatingConfigIds = updatingConfigIds - it }
            }
        }
    }

    fun importUrlFromClipboard(url: String) {
        fetchConfigFromUrl(
            url = url,
            fetchDocument = ::fetchImportedDocument,
        ) { normalizedUrl, content ->
            persist(
                transform = { latest ->
                    DesktopConfigLibraries.importDocument(
                        library = latest,
                        content = content,
                        activate = false,
                        fallbackName = importedProfileName,
                        sourceUrl = normalizedUrl,
                    )
                },
                successMessage = "Конфиг импортирован.",
                failurePrefix = "Конфиг не импортирован: ",
            )
        }
    }

    fun createConfigFromUrl(url: String) {
        fetchConfigFromUrl(
            url = url,
            fetchDocument = ::fetchImportedDocument,
        ) { normalizedUrl, content ->
            val normalizedContent = content.trimEnd().plus("\n")
            val error = normalizedContent.analyzeShadowrocketConfig().diagnostics.firstOrNull {
                it.severity == ShadowrocketConfigDiagnosticSeverity.Error
            }
            if (error != null) {
                message = "Конфиг не импортирован: ${error.message}"
                return@fetchConfigFromUrl
            }
            val profile = ConfigProfile(
                id = 0,
                name = importedProfileName,
                content = normalizedContent,
                sourceUrl = normalizedUrl,
                lastUpdatedAtMillis = System.currentTimeMillis(),
            )
            persist(
                transform = { latest -> DesktopConfigLibraries.create(latest, profile) },
                successMessage = "Конфиг импортирован.",
                failurePrefix = "Конфиг не импортирован: ",
            )
        }
    }

    fun startProfileRefresh(request: TrafficConfigProfileRefreshRequest.Refresh) {
        val snapshot = request.snapshot
        val capturedUrl = snapshot.sourceUrl.trim()
        fetchConfigFromUrl(
            url = capturedUrl,
            updatingConfigId = snapshot.id,
            fetchDocument = { normalizedUrl -> fetchProfileDocument(snapshot, normalizedUrl) },
        ) { normalizedUrl, content ->
            val normalizedContent = content.trimEnd().plus("\n")
            val error = normalizedContent.analyzeShadowrocketConfig().diagnostics.firstOrNull {
                it.severity == ShadowrocketConfigDiagnosticSeverity.Error
            }
            if (error != null) {
                message = "Конфиг не импортирован: ${error.message}"
                return@fetchConfigFromUrl
            }
            persist(
                transform = { latest ->
                    val current = latest.configs.firstOrNull { it.id == snapshot.id } ?: return@persist latest
                    val refreshed = TrafficConfigProfileRefreshOperations.apply(
                        current = current.toTrafficConfigState(
                            versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME),
                        ),
                        normalizedDocument = normalizedContent,
                        capturedUrl = normalizedUrl,
                        timestamp = System.currentTimeMillis(),
                        mode = request.mode,
                    )
                    DesktopConfigLibraries.update(
                        library = latest,
                        id = snapshot.id,
                        name = refreshed.name,
                        content = refreshed.rawConfig,
                        sourceUrl = refreshed.sourceUrl,
                        updateLocked = refreshed.updateLocked,
                        lastUpdatedAtMillis = refreshed.lastUpdatedAtMillis,
                    )
                },
                successMessage = "Конфиг обновлён.",
                failurePrefix = "Конфиг не обновлён: ",
            )
        }
    }

    fun refreshConfigFromUrl(config: DesktopStoredConfig) {
        when (val request = requestDesktopConfigProfileRefresh(config)) {
            is TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh -> {
                pendingUnlockAndRefresh = request
            }
            is TrafficConfigProfileRefreshRequest.Refresh -> startProfileRefresh(request)
        }
    }

    visualEditor?.let { session ->
        val config = configLibrary.configs.firstOrNull { it.id == session.configId }
        if (config != null) {
            val profile = config.toTrafficConfigState(resourceUserAgentFallback)
            when (session.section) {
                DesktopVisualEditorSection.Basics -> SkipiTrafficConfigBasicsEditorScreen(
                    title = stringResource(Res.string.configs_edit),
                    sectionsTitle = stringResource(Res.string.configs_editor_sections),
                    sections = listOf(
                        SkipiTrafficConfigEditorSectionAction(
                            key = "general",
                            title = stringResource(Res.string.configs_general_title),
                            summary = stringResource(Res.string.configs_general_summary),
                            onNavigate = {
                                visualEditor = session.copy(section = DesktopVisualEditorSection.General)
                            },
                        ),
                        SkipiTrafficConfigEditorSectionAction(
                            key = "dns",
                            title = stringResource(Res.string.configs_dns_title),
                            summary = stringResource(Res.string.configs_dns_summary),
                            onNavigate = {
                                visualEditor = session.copy(section = DesktopVisualEditorSection.Dns)
                            },
                        ),
                        SkipiTrafficConfigEditorSectionAction(
                            key = "tunnel",
                            title = stringResource(Res.string.configs_android_title),
                            summary = stringResource(Res.string.configs_tunnel_summary),
                            onNavigate = {
                                visualEditor = session.copy(section = DesktopVisualEditorSection.Tunnel)
                            },
                        ),
                        SkipiTrafficConfigEditorSectionAction(
                            key = "network",
                            title = stringResource(Res.string.configs_network_title),
                            summary = stringResource(Res.string.configs_network_summary),
                            onNavigate = {
                                visualEditor = session.copy(section = DesktopVisualEditorSection.Network)
                            },
                        ),
                        SkipiTrafficConfigEditorSectionAction(
                            key = "routing",
                            title = stringResource(Res.string.configs_rules_title),
                            summary = stringResource(Res.string.configs_routing_summary),
                            onNavigate = {
                                visualEditor = session.copy(section = DesktopVisualEditorSection.Routing)
                            },
                        ),
                        SkipiTrafficConfigEditorSectionAction(
                            key = "per-app",
                            title = stringResource(Res.string.configs_per_app),
                            summary = stringResource(Res.string.configs_per_app_summary),
                            onNavigate = {
                                visualEditor = session.copy(section = DesktopVisualEditorSection.PerApp)
                            },
                        ),
                    ),
                    profile = profile,
                    controller = editorController,
                    padding = contentPadding,
                    isWideScreen = true,
                    saveLabel = stringResource(Res.string.common_save),
                    onBack = {
                        visualEditor = null
                    },
                    statusContent = editorSaveError?.let { error ->
                        { DesktopEditorStatusContent(error) }
                    },
                )
                DesktopVisualEditorSection.PerApp -> SkipiTrafficConfigProxyAppListEditorScreen(
                    profile = profile,
                    controller = editorController,
                    actionScope = scope,
                    padding = contentPadding,
                    isWideScreen = true,
                    readClipboardText = readPerAppClipboardText,
                    writeClipboardText = { text -> copyTextToClipboard(text) },
                    currentUserId = null,
                    selfPackageName = "",
                    onNotice = { notice -> message = notice },
                    onBack = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    appContent = { appContentPadding, _, _, _, _, _ ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(appContentPadding).padding(28.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.configs_per_app_desktop_notice),
                                color = ConfigMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.widthIn(max = 720.dp),
                            )
                            message.takeIf { text -> text.isNotBlank() && text != editorSaveError }?.let { text ->
                                Text(text, color = ConfigMuted, fontSize = 13.sp)
                            }
                            editorSaveError?.let { error ->
                                DesktopEditorStatusContent(error)
                            }
                        }
                    },
                )
                DesktopVisualEditorSection.Routing -> SkipiTrafficConfigRoutingSectionScreen(
                    profile = profile,
                    padding = contentPadding,
                    isWideScreen = true,
                    onBack = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    onResources = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Resources)
                    },
                    onRules = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Rules)
                    },
                    onProxyGroups = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.ProxyGroups)
                    },
                )
                DesktopVisualEditorSection.Rules -> DesktopTrafficConfigRulesEditor(
                    profile = profile,
                    trafficConfigs = configLibrary.configs.map { config ->
                        config.toTrafficConfigState(resourceUserAgentFallback)
                    },
                    controller = editorController,
                    proxyServers = proxyServers,
                    subscriptionCatalog = subscriptionCatalog,
                    resourceFiles = resourceFiles,
                    latencyByServerId = latencyByServerId,
                    padding = contentPadding,
                    isWideScreen = true,
                    onBack = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Routing)
                    },
                    statusContent = editorSaveError?.let { error ->
                        { DesktopEditorStatusContent(error) }
                    },
                )
                DesktopVisualEditorSection.ProxyGroups -> DesktopTrafficConfigProxyGroupsEditor(
                    profile = profile,
                    controller = editorController,
                    proxyServers = proxyServers,
                    subscriptionCatalog = subscriptionCatalog,
                    padding = contentPadding,
                    isWideScreen = true,
                    confirmDeletion = desktopSettings.confirmDeletion,
                    exportProxyServerFullJson = exportProxyServerFullJson,
                    copyTextToClipboard = copyTextToClipboard,
                    onBack = {
                        visualEditor = when (session.proxyGroupsReturnTarget) {
                            DesktopProxyGroupsReturnTarget.TrafficConfigCatalog -> null
                            DesktopProxyGroupsReturnTarget.Routing -> session.copy(section = DesktopVisualEditorSection.Routing)
                        }
                    },
                    statusContent = editorSaveError?.let { error ->
                        { DesktopEditorStatusContent(error) }
                    },
                )
                DesktopVisualEditorSection.General -> SkipiTrafficConfigGeneralEditorScreen(
                    title = stringResource(Res.string.configs_general_title),
                    padding = contentPadding,
                    isWideScreen = true,
                    saveLabel = stringResource(Res.string.common_save),
                    profile = profile,
                    controller = editorController,
                    onBack = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    onSave = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    statusContent = editorSaveError?.let { error ->
                        { DesktopEditorStatusContent(error) }
                    },
                )
                DesktopVisualEditorSection.Dns -> SkipiTrafficConfigDnsEditorScreen(
                    title = stringResource(Res.string.configs_dns_title),
                    padding = contentPadding,
                    isWideScreen = true,
                    saveLabel = stringResource(Res.string.common_save),
                    profile = profile,
                    controller = editorController,
                    onBack = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    onSave = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    statusContent = editorSaveError?.let { error ->
                        { DesktopEditorStatusContent(error) }
                    },
                )
                DesktopVisualEditorSection.Tunnel -> SkipiTrafficConfigTunnelEditorScreen(
                    title = stringResource(Res.string.configs_android_title),
                    padding = contentPadding,
                    isWideScreen = true,
                    saveLabel = stringResource(Res.string.common_save),
                    profile = profile,
                    controller = editorController,
                    onBack = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    onSave = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    statusContent = editorSaveError?.let { error ->
                        { DesktopEditorStatusContent(error) }
                    },
                )
                DesktopVisualEditorSection.Network -> SkipiTrafficConfigNetworkActivationEditorScreen(
                    title = stringResource(Res.string.configs_network_title),
                    padding = contentPadding,
                    isWideScreen = true,
                    saveLabel = stringResource(Res.string.common_save),
                    profile = profile,
                    controller = editorController,
                    onBack = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    onSave = {
                        visualEditor = session.copy(section = DesktopVisualEditorSection.Basics)
                    },
                    statusContent = editorSaveError?.let { error ->
                        { DesktopEditorStatusContent(error) }
                    },
                )
                DesktopVisualEditorSection.Resources -> Box(Modifier.fillMaxSize()) {
                    key(config.id) {
                        ResourceManagementScreen(
                            profile = profile,
                            padding = contentPadding,
                            isWideScreen = shouldShowSplitPane(),
                            queue = resourceServices.queue,
                            operations = resourceServices.operations,
                            updateOptionsForUserAgent = resourceServices::optionsForUserAgent,
                            userAgentPresets = desktopSettings.subscriptionUserAgents,
                            deletionConfirmationEnabled = desktopSettings.confirmDeletion,
                            actionScope = resourceActionScope,
                            onBack = {
                                visualEditor = session.copy(section = DesktopVisualEditorSection.Routing)
                            },
                            updateSettings = { transform ->
                                check(desktopEditorProfileStore.update(config.id) { current ->
                                    current.copy(
                                        resourceSettings = transform(current.resourceSettings),
                                    ).withSkipiSettingsInRawConfig()
                                }) { "Traffic config profile ${config.id} no longer exists" }
                            },
                            formatTimestamp = { timestamp ->
                                if (timestamp > 0L) resourceDateFormat.format(Date(timestamp)) else "-"
                            },
                            onTip = onResourceFileStatusMessage,
                            onError = { error ->
                                onResourceFileStatusMessage(
                                    error.message?.takeIf(String::isNotBlank) ?: resourceFailureFallback,
                                )
                                DesktopLogger.error("ResourceFiles", "Resource-file screen action failed", error)
                            },
                        )
                    }
                    if (resourceFileStatusMessage.isNotBlank()) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            color = ConfigSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ConfigBorder),
                        ) {
                            Text(
                                resourceFileStatusMessage,
                                color = ConfigText,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }
            return
        }
        visualEditor = null
    }

    if (editorDraft != null) {
        DesktopConfigEditor(
            draft = editorDraft!!,
            contentPadding = contentPadding,
            statusMessage = editorSaveError,
            onBack = { content ->
                val draft = editorDraft ?: return@DesktopConfigEditor
                if (draft.id == null) {
                    editorSaveError = null
                    editorDraft = null
                } else {
                    editorController.leaveRaw(draft.id, content) {
                        editorDraft = null
                    }
                }
            },
            onSave = { name, content, sourceUrl, updateLocked ->
                val draft = editorDraft ?: return@DesktopConfigEditor false
                persist(
                    transform = { latest ->
                        when (
                            val save = applyDesktopConfigEditorSave(
                                library = latest,
                                id = draft.id,
                                lastUpdatedAtMillis = draft.lastUpdatedAtMillis,
                                name = name,
                                content = content,
                                sourceUrl = sourceUrl,
                                updateLocked = updateLocked,
                            )
                        ) {
                            is DesktopConfigEditorSaveResult.Rejected -> throw DesktopConfigEditorSaveRejected(save.message)
                            is DesktopConfigEditorSaveResult.Saved -> save.library
                        }
                    },
                    successMessage = "Конфиг сохранён.",
                    afterSave = {
                        editorSaveError = null
                        editorDraft = null
                    },
                )
            },
        )
        return
    }

    DesktopTrafficConfigGlobalProxyGroupsAdapter(
        proxyServers = proxyServers,
        subscriptionCatalog = subscriptionCatalog,
        contentPadding = contentPadding,
        confirmDeletion = desktopSettings.confirmDeletion,
        exportProxyServerFullJson = exportProxyServerFullJson,
        copyTextToClipboard = copyTextToClipboard,
        onApplyResult = onApplyGlobalProxyGroupResult,
    ) { collection ->
        val profileStatusText = message.takeIf(String::isNotBlank)
        val collectionFailureMessage = collection.failureMessage?.takeIf(String::isNotBlank)
        val pagePadding = pageListPadding(
            pageContentPaddingWithCutout(
                innerPadding = PaddingValues(0.dp),
                outerPadding = contentPadding,
                isWideScreen = true,
            ),
        )
        val layoutDirection = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            start = pagePadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = pagePadding.calculateTopPadding() + 8.dp,
            end = pagePadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = pagePadding.calculateBottomPadding() + 12.dp,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            SkipiTrafficConfigScreen(
                profiles = trafficConfigProfiles.map { profile ->
                    TrafficConfigProfileItem(
                        profile = profile,
                        active = profile.id == configLibrary.selectedConfigId,
                        updating = profile.id in updatingConfigIds,
                        canDelete = configLibrary.configs.size > 1,
                        hasUnsupportedSections = profile.rawConfig.analyzeShadowrocketConfig()
                            .unsupportedSections.isNotEmpty(),
                    )
                },
                globalProxyGroups = collection.groups,
                sourcedProxyGroups = sourcedProxyGroups,
                contentPadding = listPadding,
                onAddConfig = { showAddMenu = true },
                onAddGlobalProxyGroup = collection.onAdd,
                onSelectProfile = { id ->
                    configLibrary.configs.firstOrNull { config -> config.id == id }?.let { config ->
                        persist(
                            transform = { latest -> DesktopConfigLibraries.select(latest, config.id) },
                            successMessage = "Выбран «${config.name}».",
                        )
                    }
                },
                onEditProfile = { id ->
                    configLibrary.configs.firstOrNull { config -> config.id == id }?.let(::openVisualEditor)
                },
                onDeleteProfile = { id ->
                    configLibrary.configs.firstOrNull { config -> config.id == id }?.let { config ->
                        if (desktopSettings.confirmDeletion) pendingDeletion = config else deleteConfig(config)
                    }
                },
                onUpdateProfile = { id ->
                    configLibrary.configs.firstOrNull { config -> config.id == id }?.let(::refreshConfigFromUrl)
                },
                onProfileMenu = { id ->
                    contextMenuConfig = configLibrary.configs.firstOrNull { config -> config.id == id }
                },
                onOpenProxyGroup = { key ->
                    if (key.startsWith("global:")) collection.onOpen(key) else openSourcedProxyGroups(key)
                },
                onDeleteGlobalProxyGroup = collection.onDelete,
                onGlobalProxyGroupMenu = collection.onContextMenu,
                // Mouse users cannot long-press, so the profile menu also gets an explicit button.
                profileTrailingActions = { item ->
                    IconButton(onClick = {
                        contextMenuConfig = configLibrary.configs.firstOrNull { config -> config.id == item.profile.id }
                    }) {
                        Icon(MiuixIcons.More, contentDescription = "Ещё", tint = MiuixTheme.colorScheme.onSurface)
                    }
                },
                statusContent = profileStatusText?.let { text ->
                    { Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
                },
            )
            collectionFailureMessage?.let { failure ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    color = SkipiTheme.colors.errorContainer,
                    contentColor = SkipiTheme.colors.onErrorContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SkipiTheme.colors.error.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = failure,
                        color = SkipiTheme.colors.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    if (showAddMenu) {
        AppCascadingListPopup(
            show = true,
            entries = listOf(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = stringResource(Res.string.configs_add_new),
                            onClick = {
                                showAddMenu = false
                                editorDraft = DesktopConfigDraft(
                                    id = null,
                                    name = newProfileName,
                                    content = defaultShadowrocketConfig(),
                                    sourceUrl = "",
                                    updateLocked = false,
                                    lastUpdatedAtMillis = 0L,
                                )
                            },
                        ),
                        DropdownItem(
                            text = stringResource(Res.string.configs_import_file),
                            onClick = {
                                showAddMenu = false
                                DesktopProfileFiles.chooseProfileToOpen()?.let { path ->
                                    DesktopProfileFiles.read(path).onSuccess { content ->
                                        importRawConfig(content, path.fileName.toString().substringBeforeLast('.'))
                                    }.onFailure { error ->
                                        message = "Не удалось открыть файл: ${error.message.orEmpty()}"
                                    }
                                }
                            },
                        ),
                        DropdownItem(
                            text = stringResource(Res.string.configs_import_clipboard),
                            onClick = {
                                showAddMenu = false
                                val text = runCatching {
                                    Toolkit.getDefaultToolkit().systemClipboard
                                        .getData(DataFlavor.stringFlavor)
                                        .toString()
                                }.getOrDefault("").trim()
                                when {
                                    text.startsWith("http://", true) || text.startsWith("https://", true) -> importUrlFromClipboard(text)
                                    text.isNotBlank() -> importRawConfig(text, importedProfileName)
                                    else -> message = clipboardEmptyMessage
                                }
                            },
                        ),
                        DropdownItem(
                            text = stringResource(Res.string.configs_import_url),
                            onClick = { showAddMenu = false; importUrlDialog = true },
                        ),
                    ),
                ),
            ),
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { showAddMenu = false },
        )
    }
    SkipiTrafficConfigUrlImportDialog(
        show = importUrlDialog,
        url = importUrl,
        onUrlChange = { importUrl = it },
        onDismissRequest = { importUrlDialog = false },
        onImport = { url ->
            importUrl = ""
            importUrlDialog = false
            createConfigFromUrl(url)
        },
    )
    pendingUnlockAndRefresh?.let { pending ->
        WarningConfirmDialog(
            show = true,
            title = stringResource(Res.string.configs_update_locked_warning_title),
            summary = stringResource(Res.string.configs_update_locked_warning_summary),
            confirmText = stringResource(Res.string.configs_update_anyway),
            dismissText = stringResource(Res.string.common_cancel),
            onDismissRequest = {
                TrafficConfigProfileRefreshOperations.dismissUnlockAndRefresh(pending)
                pendingUnlockAndRefresh = null
            },
            onConfirm = {
                pendingUnlockAndRefresh = null
                startProfileRefresh(TrafficConfigProfileRefreshOperations.confirmUnlockAndRefresh(pending))
            },
        )
    }
    contextMenuConfig?.let { config ->
        val duplicateName = stringResource(Res.string.configs_copy_name, config.name)
        SkipiTrafficConfigContextMenu(
            show = true,
            onDismissRequest = { contextMenuConfig = null },
            onUpdate = if (config.sourceUrl.isNotBlank() && config.id !in updatingConfigIds) {
                { contextMenuConfig = null; refreshConfigFromUrl(config) }
            } else null,
            onRawEdit = { contextMenuConfig = null; openConfigEditor(config) },
            onUiEdit = { contextMenuConfig = null; openVisualEditor(config) },
            onDuplicate = {
                contextMenuConfig = null
                var copy: DesktopStoredConfig? = null
                persist(
                    transform = { latest ->
                        DesktopConfigLibraries.duplicate(
                            library = latest,
                            sourceProfile = config,
                            name = duplicateName,
                        ).also { copy = it.configs.last() }
                    },
                    successMessage = "",
                    afterSave = { copy?.let(::openConfigEditor) },
                )
            },
            onExport = {
                contextMenuConfig = null
                DesktopProfileFiles.chooseProfileToSave()?.let { path ->
                    DesktopProfileFiles.write(path, config.content).onSuccess {
                        message = "Конфиг экспортирован: ${path.fileName}"
                    }.onFailure { error -> message = "Не удалось экспортировать конфиг: ${error.message.orEmpty()}" }
                }
            },
            onExportBase64 = {
                contextMenuConfig = null
                val encoded = Base64.getEncoder().encodeToString(config.content.toByteArray(Charsets.UTF_8))
                runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(encoded), null)
                }.onSuccess {
                    message = exportedBase64Message
                }.onFailure { error -> message = "Не удалось скопировать конфиг: ${error.message.orEmpty()}" }
            },
            onDelete = { contextMenuConfig = null; pendingDeletion = config },
            onEnable = {
                contextMenuConfig = null
                persist(
                    transform = { latest -> DesktopConfigLibraries.select(latest, config.id) },
                    successMessage = "Выбран «${config.name}».",
                )
            },
        )
    }
    pendingDeletion?.let { config ->
        WarningConfirmDialog(
            show = true,
            title = stringResource(Res.string.deletion_confirmation_delete_config),
            summary = stringResource(Res.string.deletion_confirmation_summary),
            confirmText = stringResource(Res.string.common_delete),
            dismissText = stringResource(Res.string.common_cancel),
            onDismissRequest = { pendingDeletion = null },
            onConfirm = {
                pendingDeletion = null
                deleteConfig(config)
            },
        )
    }
}

@Composable
private fun DesktopEditorStatusContent(message: String) {
    Text(message, color = ConfigMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
}

@Composable
private fun DesktopConfigEditor(
    draft: DesktopConfigDraft,
    contentPadding: PaddingValues,
    statusMessage: String? = null,
    onBack: (content: String) -> Unit,
    onSave: (name: String, content: String, sourceUrl: String, updateLocked: Boolean) -> Boolean,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var sourceUrl by remember(draft.id) { mutableStateOf(draft.sourceUrl) }
    var updateLocked by remember(draft.id) { mutableStateOf(draft.updateLocked) }
    var content by remember(draft.id) { mutableStateOf(draft.content) }
    val analysis = remember(content) { content.analyzeShadowrocketConfig() }
    SkipiTrafficConfigRawEditor(
        state = SkipiTrafficConfigRawEditorState(
            configId = draft.id,
            name = name,
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            content = content,
            ruleCount = analysis.rules.size,
            proxyGroupCount = analysis.proxyGroups.size,
            diagnostics = analysis.diagnostics,
        ),
        labels = SkipiTrafficConfigRawEditorLabels(
            newConfigTitle = "Новый конфиг",
            editConfigTitle = "Редактор конфига",
            subtitle = "Совместимый Shadowrocket / SKIPI .conf",
            save = "Сохранить",
            name = "Название",
            sourceUrl = "Источник URL (необязательно)",
            lockUpdates = "Блокировать обновление",
            lockUpdatesSummary = "URL-профиль нельзя обновить, пока переключатель включён.",
            parsedTitle = "Конфиг разобран",
            invalidTitle = "В конфиге есть ошибки",
            rules = "Правила",
            proxyGroups = "Группы",
            diagnostics = "Диагностика",
            content = "Содержимое .conf",
        ),
        padding = contentPadding,
        isWideScreen = true,
        onNameChange = { name = it },
        onSourceUrlChange = { sourceUrl = it },
        onUpdateLockedChange = { updateLocked = it },
        onContentChange = { content = it },
        onBack = { onBack(content) },
        onSave = { onSave(name, content, sourceUrl, updateLocked) },
        statusContent = statusMessage?.let { message ->
            { DesktopEditorStatusContent(message) }
        },
    )
}
