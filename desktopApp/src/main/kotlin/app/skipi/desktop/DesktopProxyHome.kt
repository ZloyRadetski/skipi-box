// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.skipi.app.home.ProxyConnectionPhase
import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeCapabilities
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.home.ProxySubscriptionSummary
import app.skipi.ui.components.DeleteConfirmationDialog
import app.skipi.ui.home.ProxyHomeScreenContent
import app.skipi.ui.home.dialogs.SkipiAddSourceDialog
import app.skipi.ui.home.dialogs.SkipiAddSourceMode
import app.skipi.ui.home.dialogs.SkipiImportDialog
import app.skipi.ui.home.dialogs.SkipiSubscriptionEditData
import app.skipi.ui.home.dialogs.SkipiSubscriptionEditDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.subscription_delete
import app.skipi.ui.server.editor.SkipiProxyServerEditorDialog
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.getCopyTextOrNull
import features.proxy.server.model.getTransportDisplay
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop adaptation of ProxyServerListPage using the shared adaptive ProxyHomeScreenContent.
 */
@Composable
internal fun DesktopProxyHome(
    serverLibrary: DesktopServerLibrary,
    subscriptionLibrary: DesktopSubscriptionLibrary,
    subscriptionUpdate: DesktopSubscriptionUpdate?,
    serverLink: String,
    subscriptionUrl: String,
    updatingSubscription: Boolean,
    running: Boolean,
    connecting: Boolean = false,
    canToggleTunnel: Boolean,
    tunnelMessage: String,
    serverMessage: String,
    subscriptionMessage: String,
    compactConnection: Boolean,
    confirmDeletion: Boolean,
    activeProfileName: String?,
    activeTrafficConfigId: Int?,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    testingServerIds: Set<Int>,
    onServerLinkChange: (String) -> Unit,
    onSubscriptionUrlChange: (String) -> Unit,
    onToggleTunnel: () -> Unit,
    onSelectServer: (Int) -> Unit,
    onDeleteServer: (Int) -> Unit,
    onAddServer: (ProxyServer<*>) -> Unit,
    onUpdateServer: (Int, ProxyServer<*>) -> Unit,
    onMeasureServers: (List<Pair<Int, ProxyServer<*>>>) -> Unit,
    onUpdateSubscription: () -> Unit,
    scheduledSubscriptionId: Int?,
    onScheduledSubscriptionConsumed: () -> Unit,
    onPrepareSubscription: (DesktopSubscriptionInstallUri) -> Result<Unit>,
    onImport: (DesktopProxyImportInput) -> Result<String>,
    onUpdateSubscriptionProvider: (Int, DesktopSubscriptionProviderEdit) -> Result<Unit>,
    onDeleteSubscription: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var addDialogVisible by remember { mutableStateOf(false) }
    var importDialogVisible by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf(SkipiAddSourceMode.Server) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf("") }
    var pendingServerDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingSubscriptionDeletion by remember { mutableStateOf<Int?>(null) }
    var editingServerId by remember { mutableStateOf<Int?>(null) }
    var editingSubscriptionProvider by remember { mutableStateOf<DesktopStoredSubscription?>(null) }
    var editingServerModel by remember { mutableStateOf<Pair<Int, ProxyServer<*>>?>(null) }

    val decodedServers = remember(serverLibrary) {
        serverLibrary.servers.map { stored -> stored to stored.decode().getOrNull() }
    }
    val selectedServer = decodedServers.firstOrNull { (stored, _) -> stored.id == serverLibrary.selectedServerId }?.second
    val selectedTitle = selectedServer?.getInfo()?.remarks.orEmpty().ifBlank { "Выберите сервер" }
    val groupCatalog = remember(serverLibrary, subscriptionLibrary, activeTrafficConfigId) {
        DesktopProxyGroups.create(
            serverLibrary,
            subscriptionLibrary,
            DesktopProxyGroupOptions(activeTrafficConfigId = activeTrafficConfigId),
        )
    }
    val activeGroup = groupCatalog.group(selectedGroupId) ?: groupCatalog.select().group
    val activeGroupId = activeGroup?.id
    val activeSubscription = activeGroup
        ?.takeIf { group -> group.kind == DesktopProxyGroupKind.Subscription }
        ?.id
        ?.removePrefix("subscription:")
        ?.toIntOrNull()
        ?.let { subscriptionId -> subscriptionLibrary.subscriptions.firstOrNull { it.id == subscriptionId } }
    val visibleServerIds = groupCatalog.filter(activeGroupId, searchQuery).serverIds.toSet()
    val groupedServers = decodedServers.filter { (stored, _) -> stored.id in activeGroup?.serverIds.orEmpty() }
    val visibleServers = decodedServers.filter { (stored, _) -> stored.id in visibleServerIds }
    val visibleTestServers = visibleServers.mapNotNull { (stored, server) ->
        server?.takeIf { candidate -> candidate.desktopTcpEndpointOrNull() != null }?.let { stored.id to it }
    }

    LaunchedEffect(scheduledSubscriptionId, updatingSubscription) {
        val subscriptionId = scheduledSubscriptionId ?: return@LaunchedEffect
        val subscription = subscriptionLibrary.subscriptions.firstOrNull { it.id == subscriptionId }
        if (subscription != null && !updatingSubscription) {
            onSubscriptionUrlChange(subscription.url)
            onUpdateSubscription()
        }
        onScheduledSubscriptionConsumed()
    }

    val connectionPhase = when {
        running -> ProxyConnectionPhase.Connected
        connecting -> ProxyConnectionPhase.Connecting
        else -> ProxyConnectionPhase.Disconnected
    }

    val proxyGroups = remember(groupCatalog.groups) {
        groupCatalog.groups.map { group ->
            ProxyGroupSummary(
                id = group.id,
                title = group.title,
                serverCount = group.serverCount,
                enabled = group.enabled,
            )
        }
    }

    val subscriptionSummary = remember(activeSubscription, groupedServers.size, updatingSubscription) {
        activeSubscription?.let { sub ->
            ProxySubscriptionSummary(
                id = sub.id.toString(),
                title = sub.name,
                serverCount = groupedServers.size,
                enabled = sub.enabled,
                refreshing = updatingSubscription,
                updateIntervalHours = sub.updateInterval,
                usedBytes = sub.metadata.trafficUploadBytes.coerceAtLeast(0) + sub.metadata.trafficDownloadBytes.coerceAtLeast(0),
                totalBytes = sub.metadata.trafficTotalBytes.takeIf { it >= 0 },
                expireAtSeconds = sub.metadata.trafficExpireAtSeconds.takeIf { it > 0 },
                description = sub.metadata.description.takeIf(String::isNotBlank),
                announcement = sub.metadata.announce.takeIf(String::isNotBlank),
                announcementUrl = sub.metadata.announceUrl.takeIf(String::isNotBlank),
                supportUrl = sub.metadata.supportUrl.ifBlank { sub.metadata.supportEmail.takeIf(String::isNotBlank)?.let { "mailto:$it" } }.takeIf(String::isNotBlank),
                siteUrl = sub.metadata.profileWebPageUrl.takeIf(String::isNotBlank),
                lastUpdatedAtMillis = sub.metadata.lastUpdatedAtMillis.takeIf { it > 0 },
            )
        }
    }

    val proxyServers = remember(visibleServers, serverLibrary.selectedServerId, latencyByServerId, testingServerIds) {
        visibleServers.mapNotNull { (stored, server) ->
            server?.let { s ->
                val latencyResult = latencyByServerId[stored.id]
                val info = s.getInfo()
                val (flag, title) = splitFlagAndTitle(info.remarks)
                ProxyServerSummary(
                    id = stored.id.toString(),
                    title = title,
                    address = info.address,
                    protocol = info.protocol,
                    transport = s.getTransportDisplay(),
                    flag = flag,
                    selected = stored.id == serverLibrary.selectedServerId,
                    latencyMs = (latencyResult as? DesktopServerLatencyResult.Success)?.milliseconds?.toLong(),
                    latencyTesting = stored.id in testingServerIds,
                    latencyError = latencyResult is DesktopServerLatencyResult.Error || latencyResult is DesktopServerLatencyResult.Timeout,
                    canTest = s.desktopTcpEndpointOrNull() != null,
                )
            }
        }
    }

    val combinedStatusMessage = listOf(tunnelMessage, subscriptionMessage, serverMessage, localMessage)
        .firstOrNull { it.isNotBlank() }
    val isStatusError = combinedStatusMessage?.let { msg ->
        msg.contains("не удалось", ignoreCase = true) ||
            msg.contains("ошибка", ignoreCase = true) ||
            msg.contains("failed", ignoreCase = true) ||
            msg.contains("rejected", ignoreCase = true)
    } ?: false

    val homeUiState = ProxyHomeUiState(
        connectionPhase = connectionPhase,
        selectedServerTitle = selectedTitle,
        activeProfileName = activeProfileName,
        canToggleTunnel = canToggleTunnel,
        groups = proxyGroups,
        selectedGroupId = activeGroupId,
        subscription = subscriptionSummary,
        servers = proxyServers,
        searchQuery = searchQuery,
        isSearchVisible = searchVisible,
        isTestingLatency = testingServerIds.isNotEmpty(),
        statusMessage = combinedStatusMessage,
        isStatusError = isStatusError,
    )

    val capabilities = remember(
        serverLibrary,
        visibleServers,
        visibleTestServers,
        groupedServers,
        activeSubscription,
        searchQuery,
        searchVisible,
        canToggleTunnel,
        confirmDeletion,
    ) {
        ProxyHomeCapabilities(
            onToggleTunnel = onToggleTunnel,
            onSelectServer = { serverIdStr ->
                serverIdStr.toIntOrNull()?.let(onSelectServer)
            },
            onTestServerLatency = { serverIdStr ->
                serverIdStr.toIntOrNull()?.let { id ->
                    visibleServers.firstOrNull { it.first.id == id }?.second?.let { server ->
                        onMeasureServers(listOf(id to server))
                    }
                }
            },
            onTestGroupLatency = { _ ->
                onMeasureServers(
                    groupedServers.mapNotNull { (stored, server) -> server?.let { stored.id to it } },
                )
            },
            onTestAllLatency = {
                if (visibleTestServers.isNotEmpty()) {
                    onMeasureServers(visibleTestServers)
                } else {
                    localMessage = "В текущей группе нет серверов для проверки."
                }
            },
            onAddServer = {
                editingServerId = null
                onServerLinkChange("")
                addMode = SkipiAddSourceMode.Server
                addDialogVisible = true
            },
            onAddSubscription = {
                editingServerId = null
                onSubscriptionUrlChange("")
                addMode = SkipiAddSourceMode.Subscription
                addDialogVisible = true
            },
            onImport = { importDialogVisible = true },
            onEditServer = { serverIdStr ->
                serverIdStr.toIntOrNull()?.let { id ->
                    visibleServers.firstOrNull { it.first.id == id }?.let { (stored, server) ->
                        if (server != null) {
                            editingServerModel = stored.id to server
                        }
                    }
                }
            },
            onDeleteServer = { serverIdStr ->
                serverIdStr.toIntOrNull()?.let { id ->
                    if (confirmDeletion) pendingServerDeletion = id else onDeleteServer(id)
                }
            },
            onCopyServerLink = { serverIdStr ->
                serverIdStr.toIntOrNull()?.let { id ->
                    visibleServers.firstOrNull { it.first.id == id }?.second?.getCopyTextOrNull()?.let { copyText ->
                        runCatching {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(copyText), null)
                        }.onSuccess {
                            localMessage = "Ссылка сервера скопирована."
                        }.onFailure { error ->
                            localMessage = "Не удалось скопировать ссылку: ${error.message.orEmpty()}"
                        }
                    }
                }
            },
            onSelectGroup = { groupId -> selectedGroupId = groupId },
            onRefreshSubscription = { _ ->
                activeSubscription?.let { subscription ->
                    onSubscriptionUrlChange(subscription.url)
                    onUpdateSubscription()
                }
            },
            onEditSubscription = { _ ->
                editingSubscriptionProvider = activeSubscription
            },
            onToggleSubscriptionEnabled = { _ ->
                activeSubscription?.let { subscription ->
                    onUpdateSubscriptionProvider(
                        subscription.id,
                        subscription.toProviderEdit().copy(enabled = !subscription.enabled),
                    ).onFailure { error ->
                        localMessage = error.message.orEmpty().ifBlank { "Не удалось изменить состояние подписки." }
                    }
                }
            },
            onOpenAnnouncement = { url: String ->
                openExternalLink(url).fold(
                    onSuccess = { localMessage = "Открыто объявление подписки." },
                    onFailure = { error -> localMessage = "Не удалось открыть объявление: ${error.message.orEmpty()}" },
                )
            },
            onOpenSupport = { url: String ->
                openExternalLink(url).fold(
                    onSuccess = { localMessage = "Открыта поддержка подписки." },
                    onFailure = { error -> localMessage = "Не удалось открыть поддержку: ${error.message.orEmpty()}" },
                )
            },
            onOpenSite = { url: String ->
                openExternalLink(url).fold(
                    onSuccess = { localMessage = "Открыт сайт подписки." },
                    onFailure = { error -> localMessage = "Не удалось открыть сайт: ${error.message.orEmpty()}" },
                )
            },
            onSearchQueryChange = { searchQuery = it },
            onToggleSearchVisible = { searchVisible = !searchVisible },
        )
    }

    ProxyHomeScreenContent(
        state = homeUiState,
        onAction = { /* state handled in capabilities */ },
        capabilities = capabilities,
        contentPadding = contentPadding,
    )

    fun importFromClipboard() {
        readDesktopClipboardText().onSuccess { text ->
            val install = text.trim().toDesktopSubscriptionInstallUriOrNull()
            if (install != null) {
                onPrepareSubscription(install).fold(
                    onSuccess = {
                        onSubscriptionUrlChange(install.url)
                        onUpdateSubscription()
                        localMessage = "Подписка «${install.name}» добавлена."
                    },
                    onFailure = { error ->
                        localMessage = error.message ?: "Не удалось сохранить подписку."
                    },
                )
            } else {
                onImport(DesktopProxyImportInput.Clipboard(text)).fold(
                    onSuccess = { summary -> localMessage = summary },
                    onFailure = { error -> localMessage = error.message ?: "Не удалось импортировать." },
                )
            }
        }.onFailure { error ->
            localMessage = error.message ?: "Не удалось прочитать буфер обмена."
        }
    }

    fun importFromFile() {
        chooseDesktopImportFile().onSuccess { file ->
            if (file != null) {
                onImport(DesktopProxyImportInput.File(file.name, file.content)).fold(
                    onSuccess = { summary -> localMessage = summary },
                    onFailure = { error -> localMessage = error.message ?: "Не удалось импортировать." },
                )
            }
        }.onFailure { error ->
            localMessage = error.message ?: "Не удалось прочитать файл."
        }
    }

    if (addDialogVisible) {
        SkipiAddSourceDialog(
            show = addDialogVisible,
            mode = addMode,
            serverLink = serverLink,
            subscriptionUrl = subscriptionUrl,
            isEditingServer = editingServerId != null,
            isSubmitting = updatingSubscription,
            onModeChange = { mode ->
                if (mode != SkipiAddSourceMode.Server) editingServerId = null
                addMode = mode
            },
            onServerLinkChange = onServerLinkChange,
            onSubscriptionUrlChange = onSubscriptionUrlChange,
            onSaveServer = { server ->
                editingServerId?.let { serverId -> onUpdateServer(serverId, server) } ?: onAddServer(server)
                editingServerId = null
                addDialogVisible = false
            },
            onSaveSubscription = { url ->
                val uri = DesktopSubscriptionInstallUri.parseOrNull(url)
                if (uri != null) {
                    onPrepareSubscription(uri).fold(
                        onSuccess = {
                            onUpdateSubscription()
                            addDialogVisible = false
                        },
                        onFailure = { error ->
                            localMessage = error.message ?: "Не удалось подготовить подписку."
                        },
                    )
                }
            },
            onClipboardImport = ::importFromClipboard,
            onFileImport = ::importFromFile,
            onDismiss = { addDialogVisible = false },
        )
    }

    var importText by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }

    if (importDialogVisible) {
        SkipiImportDialog(
            show = importDialogVisible,
            importText = importText,
            replaceExisting = replaceExisting,
            onImportTextChange = { importText = it },
            onReplaceExistingChange = { replaceExisting = it },
            onClipboardImport = ::importFromClipboard,
            onFileImport = ::importFromFile,
            onConfirmImport = {
                onImport(DesktopProxyImportInput.Text(importText)).fold(
                    onSuccess = { summary ->
                        localMessage = summary
                        importDialogVisible = false
                        importText = ""
                    },
                    onFailure = { error ->
                        localMessage = error.message ?: "Ошибка импорта."
                    },
                )
            },
            onDismiss = { importDialogVisible = false },
        )
    }

    editingSubscriptionProvider?.let { subscription ->
        SkipiSubscriptionEditDialog(
            show = true,
            initialData = SkipiSubscriptionEditData(
                name = subscription.name,
                url = subscription.url,
                userAgent = subscription.userAgent,
                updateInterval = subscription.updateInterval,
                ageSecretKey = subscription.ageSecretKey,
                updateViaProxy = subscription.updateViaProxy,
                autoOverrideRules = subscription.autoOverrideRules,
                enabled = subscription.enabled,
            ),
            onSave = { draft ->
                onUpdateSubscriptionProvider(
                    subscription.id,
                    subscription.toProviderEdit().copy(
                        name = draft.name,
                        url = draft.url,
                        userAgent = draft.userAgent,
                        updateInterval = draft.updateInterval,
                        ageSecretKey = draft.ageSecretKey,
                        updateViaProxy = draft.updateViaProxy,
                        autoOverrideRules = draft.autoOverrideRules,
                        enabled = draft.enabled,
                    ),
                )
                editingSubscriptionProvider = null
            },
            onDelete = {
                editingSubscriptionProvider = null
                pendingSubscriptionDeletion = subscription.id
            },
            onDismiss = { editingSubscriptionProvider = null },
        )
    }

    editingServerModel?.let { (serverId, server) ->
        SkipiProxyServerEditorDialog(
            show = true,
            server = server,
            onSave = { updatedServer ->
                onUpdateServer(serverId, updatedServer)
                editingServerModel = null
            },
            onDismiss = { editingServerModel = null },
        )
    }

    pendingServerDeletion?.let { serverId ->
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(Res.string.common_delete),
            onDismissRequest = { pendingServerDeletion = null },
            onConfirm = {
                onDeleteServer(serverId)
                pendingServerDeletion = null
            },
        )
    }

    pendingSubscriptionDeletion?.let { subscriptionId ->
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(Res.string.subscription_delete),
            onDismissRequest = { pendingSubscriptionDeletion = null },
            onConfirm = {
                onDeleteSubscription(subscriptionId)
                pendingSubscriptionDeletion = null
            },
        )
    }
}

private data class DesktopImportFile(val name: String, val content: String)

private fun readDesktopClipboardText(): Result<String> = runCatching {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val text = clipboard.getData(DataFlavor.stringFlavor) as? String
        ?: error("Буфер обмена не содержит текст.")
    require(text.toByteArray(StandardCharsets.UTF_8).size <= MaxDesktopImportBytes) {
        "Данные импорта превышают ${MaxDesktopImportBytes / 1024 / 1024} МБ."
    }
    text
}

private fun chooseDesktopImportFile(): Result<DesktopImportFile?> = runCatching {
    val dialog = FileDialog(null as Frame?, "Импортировать прокси", FileDialog.LOAD)
    dialog.isVisible = true
    val fileName = dialog.file ?: return@runCatching null
    val file = Path.of(dialog.directory.orEmpty(), fileName).normalize()
    require(Files.isRegularFile(file)) { "Выбранный путь не является файлом." }
    require(Files.size(file) <= MaxDesktopImportBytes) {
        "Файл импорта превышает ${MaxDesktopImportBytes / 1024 / 1024} МБ."
    }
    DesktopImportFile(name = file.fileName.toString(), content = Files.readString(file, StandardCharsets.UTF_8))
}

private const val MaxDesktopImportBytes = 8 * 1024 * 1024

private fun splitFlagAndTitle(value: String): Pair<String?, String> {
    val trimmed = value.trim().ifBlank { "Без названия" }
    val prefix = trimmed.substringBefore(' ')
    val looksLikeEmoji = prefix.any(Char::isSurrogate) || prefix == "⚡"
    return if (looksLikeEmoji && prefix.length <= 5) prefix to trimmed.removePrefix(prefix).trim().ifBlank { trimmed }
    else null to trimmed
}

private fun openExternalLink(value: String): Result<Unit> = runCatching {
    check(Desktop.isDesktopSupported()) { "Открытие ссылки не поддерживается системой." }
    Desktop.getDesktop().browse(requireSafeDesktopExternalUri(value))
}

/**
 * Subscription metadata is remote input. Only web pages and one plain email
 * recipient may be sent to the operating system's external URI handlers.
 */
internal fun requireSafeDesktopExternalUri(value: String): URI {
    val uri = URI(value.trim())
    when (uri.scheme?.lowercase()) {
        "https" -> {
            require(uri.host?.isNotBlank() == true) { "HTTPS ссылка должна содержать хост." }
            require(uri.userInfo.isNullOrBlank()) { "HTTPS ссылка не должна содержать учётные данные." }
        }

        "mailto" -> {
            val recipient = uri.rawSchemeSpecificPart
            require(!recipient.isNullOrBlank() && !recipient.contains('?') && !recipient.contains('#')) {
                "Mailto ссылка должна содержать один адрес без параметров."
            }
            require(SafeDesktopMailtoRecipient.matches(recipient)) {
                "Mailto ссылка содержит некорректный адрес."
            }
        }

        else -> error("Поддерживаются только HTTPS и корректные mailto ссылки.")
    }
    return uri
}

private val SafeDesktopMailtoRecipient = Regex(
    "^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}",
)
