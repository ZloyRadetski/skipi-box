// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.getCopyTextOrNull
import features.proxy.server.model.getTransportDisplay
import features.subscription.isValidManualSubscriptionUrl
import java.awt.Toolkit
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HomeBackground: Color
    @Composable get() = DesktopContentBackground
private val HomeSurface = Color(0xFF202126)
private val HomeSurfaceRaised = Color(0xFF292B31)
private val HomeSelected = Color(0xFF737373)
private val HomeBorder = Color(0xFF3C3E45)
private val HomeText = Color(0xFFF4F4F6)
private val HomeMuted = Color(0xFFA2A5AF)
private val HomeGreen = Color(0xFF58D27A)
private val HomeRed = Color(0xFFFF5B62)

private enum class AddMode { Server, Subscription }

/**
 * Desktop adaptation of Android's ProxyServerListPage.
 *
 * It deliberately keeps the Android composition (header, hero connection card,
 * group selector, optional subscription summary and server cards) and only
 * widens the content column for a desktop window.
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
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    var toolsMenuExpanded by remember { mutableStateOf(false) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var addDialogVisible by remember { mutableStateOf(false) }
    var importDialogVisible by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf(AddMode.Server) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf("") }
    var pendingServerDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingSubscriptionDeletion by remember { mutableStateOf<Int?>(null) }
    var editingServerId by remember { mutableStateOf<Int?>(null) }
    var editingSubscriptionProvider by remember { mutableStateOf<DesktopStoredSubscription?>(null) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 980.dp)
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            HomeHeader(
                onLatencyClick = {
                    if (visibleTestServers.isNotEmpty()) {
                        onMeasureServers(visibleTestServers)
                    } else {
                        localMessage = "В текущей группе нет серверов для проверки."
                    }
                },
                onRefreshSubscription = activeSubscription?.let { subscription ->
                    {
                        onSubscriptionUrlChange(subscription.url)
                        onUpdateSubscription()
                    }
                },
                onAddServer = {
                    editingServerId = null
                    onServerLinkChange("")
                    addMode = AddMode.Server
                    addDialogVisible = true
                },
                onAddSubscription = {
                    editingServerId = null
                    onSubscriptionUrlChange("")
                    addMode = AddMode.Subscription
                    addDialogVisible = true
                },
                onImport = { importDialogVisible = true },
                addMenuExpanded = addMenuExpanded,
                onAddMenuExpandedChange = { addMenuExpanded = it },
                toolsMenuExpanded = toolsMenuExpanded,
                onToolsMenuExpandedChange = { toolsMenuExpanded = it },
                searchVisible = searchVisible,
                onSearchVisibleChange = { searchVisible = it },
                testingLatency = testingServerIds.isNotEmpty(),
                refreshingSubscription = updatingSubscription,
            )
            Spacer(Modifier.height(14.dp))

            // Android keeps the app row fixed while the proxy list scrolls beneath it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {

            ConnectionHeroCard(
                running = running,
                enabled = canToggleTunnel,
                selectedTitle = selectedTitle,
                compact = compactConnection,
                activeProfileName = activeProfileName,
                onToggle = onToggleTunnel,
            )

            if (groupCatalog.groups.size > 1) {
                GroupSelector(
                    groups = groupCatalog.groups,
                    selectedGroupId = activeGroupId,
                    onSelect = { groupId -> selectedGroupId = groupId },
                )
            }

            if (searchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text("Поиск серверов") },
                    shape = RoundedCornerShape(16.dp),
                )
            }

            if (activeSubscription != null) {
                SubscriptionSummaryCard(
                    subscription = activeSubscription,
                    serverCount = groupedServers.size,
                    refreshing = updatingSubscription,
                    onRefresh = {
                        onSubscriptionUrlChange(activeSubscription.url)
                        onUpdateSubscription()
                    },
                    onPing = {
                        onMeasureServers(
                            groupedServers.mapNotNull { (stored, server) -> server?.let { stored.id to it } },
                        )
                    },
                    onEdit = { editingSubscriptionProvider = activeSubscription },
                    onToggleEnabled = {
                        onUpdateSubscriptionProvider(
                            activeSubscription.id,
                            activeSubscription.toProviderEdit().copy(enabled = !activeSubscription.enabled),
                        ).onFailure { error ->
                            localMessage = error.message.orEmpty().ifBlank { "Не удалось изменить состояние подписки." }
                        }
                    },
                    onMessage = { localMessage = it },
                )
            }

            if (visibleServers.isEmpty()) {
                EmptyServerList(
                    hasSearch = searchQuery.isNotBlank(),
                    onAdd = {
                        editingServerId = null
                        onServerLinkChange("")
                        addMode = AddMode.Server
                        addDialogVisible = true
                    },
                )
            } else {
                visibleServers.forEach { (stored, server) ->
                    if (server != null) {
                        ServerCard(
                            server = server,
                            selected = stored.id == serverLibrary.selectedServerId,
                            latency = latencyByServerId[stored.id],
                            testingLatency = stored.id in testingServerIds,
                            onSelect = { onSelectServer(stored.id) },
                            onTest = { onMeasureServers(listOf(stored.id to server)) },
                            onCopy = {
                                server.getCopyTextOrNull()?.let { copyText ->
                                    runCatching {
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(copyText), null)
                                    }.onSuccess {
                                        localMessage = "Ссылка сервера скопирована."
                                    }.onFailure { error ->
                                        localMessage = "Не удалось скопировать ссылку: ${error.message.orEmpty()}"
                                    }
                                }
                            },
                            onEdit = {
                                editingServerId = stored.id
                                server.getCopyTextOrNull()?.let(onServerLinkChange)
                                addMode = AddMode.Server
                                addDialogVisible = true
                            },
                            onDelete = {
                                if (confirmDeletion) pendingServerDeletion = stored.id
                                else onDeleteServer(stored.id)
                            },
                        )
                    }
                }
            }

            listOf(tunnelMessage, subscriptionMessage, serverMessage, localMessage)
                .firstOrNull { it.isNotBlank() }
                ?.let { message ->
                    Text(
                        text = message,
                        color = HomeMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
            }
            Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (addDialogVisible) {
        AddProxyDialog(
            mode = addMode,
            serverLink = serverLink,
            subscriptionUrl = subscriptionUrl,
            subscriptionUpdate = subscriptionUpdate,
            subscriptionMessage = subscriptionMessage,
            editingServer = editingServerId != null,
            updatingSubscription = updatingSubscription,
            onModeChange = { mode ->
                if (mode != AddMode.Server) editingServerId = null
                addMode = mode
            },
            onServerLinkChange = onServerLinkChange,
            onSubscriptionUrlChange = onSubscriptionUrlChange,
            onSaveServer = { server ->
                editingServerId?.let { serverId -> onUpdateServer(serverId, server) } ?: onAddServer(server)
                editingServerId = null
                addDialogVisible = false
            },
            onUpdateSubscription = onUpdateSubscription,
            onPrepareSubscription = onPrepareSubscription,
            onDismiss = { addDialogVisible = false },
        )
    }

    if (importDialogVisible) {
        ImportProxyDialog(
            onImport = onImport,
            onPrepareSubscription = onPrepareSubscription,
            onSubscriptionUrlChange = onSubscriptionUrlChange,
            onUpdateSubscription = onUpdateSubscription,
            onDismiss = { importDialogVisible = false },
        )
    }

    editingSubscriptionProvider?.let { subscription ->
        SubscriptionProviderEditDialog(
            subscription = subscription,
            onSave = onUpdateSubscriptionProvider,
            onRequestDelete = {
                editingSubscriptionProvider = null
                pendingSubscriptionDeletion = subscription.id
            },
            onDismiss = { editingSubscriptionProvider = null },
        )
    }

    pendingServerDeletion?.let { serverId ->
        AlertDialog(
            onDismissRequest = { pendingServerDeletion = null },
            title = { Text("Удалить сервер?") },
            text = { Text("Сервер будет удалён только из локальной библиотеки SKIPI.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteServer(serverId)
                    pendingServerDeletion = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingServerDeletion = null }) { Text("Отмена") } },
        )
    }

    pendingSubscriptionDeletion?.let { subscriptionId ->
        AlertDialog(
            onDismissRequest = { pendingSubscriptionDeletion = null },
            title = { Text("Удалить подписку?") },
            text = { Text("Ссылка подписки будет удалена из локального каталога SKIPI.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteSubscription(subscriptionId)
                    pendingSubscriptionDeletion = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingSubscriptionDeletion = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun HomeHeader(
    onLatencyClick: () -> Unit,
    onRefreshSubscription: (() -> Unit)?,
    onAddServer: () -> Unit,
    onAddSubscription: () -> Unit,
    onImport: () -> Unit,
    addMenuExpanded: Boolean,
    onAddMenuExpandedChange: (Boolean) -> Unit,
    toolsMenuExpanded: Boolean,
    onToolsMenuExpandedChange: (Boolean) -> Unit,
    searchVisible: Boolean,
    onSearchVisibleChange: (Boolean) -> Unit,
    testingLatency: Boolean,
    refreshingSubscription: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SKIPI", color = HomeText, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLatencyClick, enabled = !testingLatency) {
                Icon(Icons.Outlined.HourglassEmpty, "Проверить доступность серверов", tint = HomeText, modifier = Modifier.size(27.dp))
            }
            Box {
                IconButton(onClick = { onAddMenuExpandedChange(true) }) {
                    Icon(Icons.Outlined.Add, "Добавить", tint = HomeText, modifier = Modifier.size(34.dp))
                }
                DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { onAddMenuExpandedChange(false) }) {
                    DropdownMenuItem(
                        text = { Text("Добавить сервер") },
                        onClick = { onAddMenuExpandedChange(false); onAddServer() },
                    )
                    DropdownMenuItem(
                        text = { Text("Добавить подписку") },
                        onClick = { onAddMenuExpandedChange(false); onAddSubscription() },
                    )
                    DropdownMenuItem(
                        text = { Text("Импортировать") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = { onAddMenuExpandedChange(false); onImport() },
                    )
                }
            }
            Box {
                IconButton(onClick = { onToolsMenuExpandedChange(true) }) {
                    Icon(Icons.Outlined.MoreVert, "Ещё", tint = HomeText, modifier = Modifier.size(30.dp))
                }
                DropdownMenu(expanded = toolsMenuExpanded, onDismissRequest = { onToolsMenuExpandedChange(false) }) {
                    if (onRefreshSubscription != null) {
                        DropdownMenuItem(
                            text = { Text(if (refreshingSubscription) "Обновление подписки…" else "Обновить подписку") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            enabled = !refreshingSubscription,
                            onClick = {
                                onToolsMenuExpandedChange(false)
                                onRefreshSubscription()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (searchVisible) "Скрыть поиск" else "Поиск") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        onClick = {
                            onToolsMenuExpandedChange(false)
                            onSearchVisibleChange(!searchVisible)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionHeroCard(
    running: Boolean,
    enabled: Boolean,
    selectedTitle: String,
    compact: Boolean,
    activeProfileName: String?,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        border = BorderStroke(1.dp, if (running) HomeGreen.copy(alpha = 0.48f) else HomeBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 20.dp else 26.dp,
                vertical = if (compact) 16.dp else 24.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(if (compact) 78.dp else 96.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 70.dp else 86.dp)
                        .clip(CircleShape)
                        .background(HomeSurfaceRaised)
                        .border(3.dp, if (running) HomeGreen else Color(0xFF555861), CircleShape)
                        .clickable(enabled = enabled, onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PowerSettingsNew,
                        contentDescription = if (running) "Отключить" else "Подключить",
                        tint = if (running) HomeGreen else HomeMuted,
                        modifier = Modifier.size(if (compact) 40.dp else 48.dp),
                    )
                }
            }
            Spacer(Modifier.width(if (compact) 14.dp else 20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (running) "Подключено" else "Отключено",
                    color = HomeText,
                    fontSize = if (compact) 25.sp else 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = when {
                        running -> selectedTitle
                        enabled -> "Нажмите для подключения"
                        else -> "Сначала выберите сервер"
                    },
                    color = HomeMuted,
                    fontSize = if (compact) 16.sp else 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                activeProfileName?.let { profileName ->
                    Text(
                        text = "Профиль: $profileName",
                        color = HomeMuted,
                        fontSize = if (compact) 12.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupSelector(
    groups: List<DesktopProxyGroup>,
    selectedGroupId: String?,
    onSelect: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        border = BorderStroke(1.dp, HomeBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Группы прокси", color = HomeText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (groups.isEmpty()) {
                Text("Добавьте сервер или подписку", color = HomeMuted, fontSize = 14.sp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { group ->
                        val selected = group.id == selectedGroupId
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(13.dp))
                                .clickable { onSelect(group.id) },
                            color = if (selected) HomeSelected else HomeSurfaceRaised,
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, if (selected) Color(0xFFA4A4A4) else Color.Transparent),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                                Text(
                                    group.title,
                                    color = HomeText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("Серверов: ${group.serverCount}", color = HomeMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionSummaryCard(
    subscription: DesktopStoredSubscription,
    serverCount: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val metadata = subscription.metadata
    var expanded by remember(subscription.id) { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        border = BorderStroke(1.dp, HomeBorder),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(HomeSurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) { Text("⌜⌟", color = HomeText, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        subscription.name.ifBlank { "Подписка" },
                        color = HomeText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Серверов: $serverCount", color = HomeMuted, fontSize = 14.sp)
                }
                Switch(
                    checked = subscription.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    enabled = !refreshing,
                )
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    Icon(Icons.Outlined.Refresh, "Обновить", tint = HomeText)
                }
                IconButton(onClick = onPing, enabled = !refreshing && serverCount > 0) {
                    Icon(Icons.Outlined.HourglassEmpty, "Проверить серверы", tint = HomeText)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.MoreVert, "Редактировать подписку", tint = HomeText) }
            }
            Text(
                text = buildString {
                    append(if (subscription.enabled) "Включена" else "Отключена")
                    subscription.updateInterval.trim().takeIf(String::isNotBlank)?.let { interval ->
                        append(" · Автообновление: ")
                        append(interval)
                        append(" ч.")
                    }
                },
                color = if (subscription.enabled) HomeGreen else HomeMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (expanded) {
                val usedBytes = metadata.trafficUploadBytes.coerceAtLeast(0) + metadata.trafficDownloadBytes.coerceAtLeast(0)
                val totalTraffic = metadata.trafficTotalBytes.takeIf { it >= 0 }
                Text(
                    "Трафик: ${formatBytes(usedBytes)} / ${totalTraffic?.let(::formatBytes) ?: "∞"}",
                    color = HomeMuted,
                    fontSize = 14.sp,
                )
                totalTraffic?.takeIf { it > 0 }?.let { total ->
                    LinearProgressIndicator(
                        progress = { (usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = HomeGreen,
                        trackColor = HomeSurfaceRaised,
                    )
                }
                metadata.trafficExpireAtSeconds.takeIf { it > 0 }?.let { expireAtSeconds ->
                    Text(
                        formatSubscriptionExpiry(expireAtSeconds),
                        color = HomeMuted,
                        fontSize = 14.sp,
                    )
                }
                metadata.description.takeIf(String::isNotBlank)?.let { description ->
                    Text(description, color = HomeMuted, fontSize = 14.sp)
                }
                metadata.announce.takeIf(String::isNotBlank)?.let { announce ->
                    val announceUrl = metadata.announceUrl
                    if (announceUrl.isBlank()) {
                        Text(announce, color = HomeMuted, fontSize = 14.sp)
                    } else {
                        TextButton(onClick = {
                            openExternalLink(announceUrl).fold(
                                onSuccess = { onMessage("Открыто объявление подписки.") },
                                onFailure = { error -> onMessage("Не удалось открыть объявление: ${error.message.orEmpty()}") },
                            )
                        }) { Text(announce) }
                    }
                }
                val supportLink = metadata.supportUrl.ifBlank { metadata.supportEmail.takeIf(String::isNotBlank)?.let { "mailto:$it" }.orEmpty() }
                val siteLink = metadata.profileWebPageUrl
                if (supportLink.isNotBlank() || siteLink.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (supportLink.isNotBlank()) {
                            TextButton(onClick = {
                                openExternalLink(supportLink).fold(
                                    onSuccess = { onMessage("Открыта поддержка подписки.") },
                                    onFailure = { error -> onMessage("Не удалось открыть поддержку: ${error.message.orEmpty()}") },
                                )
                            }) { Text("Поддержка") }
                        }
                        if (siteLink.isNotBlank()) {
                            TextButton(onClick = {
                                openExternalLink(siteLink).fold(
                                    onSuccess = { onMessage("Открыт сайт подписки.") },
                                    onFailure = { error -> onMessage("Не удалось открыть сайт: ${error.message.orEmpty()}") },
                                )
                            }) { Text("Сайт") }
                        }
                    }
                }
                metadata.lastUpdatedAtMillis.takeIf { it > 0 }?.let { updatedAt ->
                    Text("Обновлено: ${DesktopDateTimeFormatter.format(Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()))}", color = HomeMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionProviderEditDialog(
    subscription: DesktopStoredSubscription,
    onSave: (Int, DesktopSubscriptionProviderEdit) -> Result<Unit>,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(subscription.id) { mutableStateOf(subscription.toProviderEdit()) }
    var error by remember(subscription.id) { mutableStateOf("") }
    var customRemindersEnabled by remember(subscription.id) {
        mutableStateOf(draft.customExpiryReminders != null)
    }
    var customRemindersText by remember(subscription.id) {
        mutableStateOf(formatDesktopSubscriptionExpiryReminders(draft.customExpiryReminders))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Параметры подписки") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { value -> draft = draft.copy(name = value) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.url,
                    onValueChange = { value -> draft = draft.copy(url = value) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HTTP/HTTPS ссылка") },
                    singleLine = true,
                )
                if (draft.url.trim().startsWith("http://", ignoreCase = true)) {
                    Text("Незащищённая HTTP-ссылка может раскрыть данные подписки.", color = HomeRed, fontSize = 13.sp)
                }
                SubscriptionProviderSwitch(
                    label = "Подписка включена",
                    description = "Отключённая группа остаётся в списке, но не участвует в выборе и автообновлении.",
                    checked = draft.enabled,
                    onCheckedChange = { checked -> draft = draft.copy(enabled = checked) },
                )
                SubscriptionProviderSwitch(
                    label = "Автоматическое переопределение правил",
                    description = "Разрешить подписке применять собственные правила маршрутизации.",
                    checked = draft.autoOverrideRules,
                    onCheckedChange = { checked -> draft = draft.copy(autoOverrideRules = checked) },
                )
                if (draft.url.isNotBlank()) {
                    HorizontalDivider()
                    Text("Обновление", color = HomeText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = draft.ageSecretKey,
                        onValueChange = { value -> draft = draft.copy(ageSecretKey = value) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ключ Age (если подписка зашифрована)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.userAgent,
                        onValueChange = { value -> draft = draft.copy(userAgent = value) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("User-Agent") },
                        singleLine = true,
                    )
                    SubscriptionProviderSwitch(
                        label = "Обновлять через прокси",
                        description = "Использовать локальный HTTP-прокси SKIPI при загрузке подписки.",
                        checked = draft.updateViaProxy,
                        onCheckedChange = { checked -> draft = draft.copy(updateViaProxy = checked) },
                    )
                    OutlinedTextField(
                        value = draft.updateInterval,
                        onValueChange = { value -> draft = draft.copy(updateInterval = value) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Автообновление, часы (пусто или 0 — выкл.)") },
                        supportingText = { Text("Минимум 0,25 часа.") },
                        singleLine = true,
                    )
                    HorizontalDivider()
                    Text("Напоминания об окончании", color = HomeText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    SubscriptionProviderSwitch(
                        label = "Уведомлять об окончании",
                        description = "Показывать напоминания об истечении срока подписки.",
                        checked = draft.notifyOnExpiry,
                        onCheckedChange = { checked -> draft = draft.copy(notifyOnExpiry = checked) },
                    )
                    SubscriptionProviderSwitch(
                        label = "Пользовательские напоминания",
                        description = "Задайте несколько значений через запятую: 3:дни, 12:часы, 0:истечение.",
                        checked = customRemindersEnabled,
                        onCheckedChange = { enabled -> customRemindersEnabled = enabled },
                    )
                    if (customRemindersEnabled) {
                        OutlinedTextField(
                            value = customRemindersText,
                            onValueChange = { value -> customRemindersText = value },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Напоминания") },
                            supportingText = { Text("Единицы: минуты, часы, дни, недели, истечение.") },
                            minLines = 2,
                        )
                    }
                }
                TextButton(onClick = onRequestDelete) { Text("Удалить подписку", color = HomeRed) }
                if (error.isNotBlank()) Text(error, color = HomeRed, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val edited = runCatching {
                    draft.copy(
                        customExpiryReminders = if (customRemindersEnabled) {
                            parseDesktopSubscriptionExpiryReminders(customRemindersText)
                        } else {
                            null
                        },
                    )
                }
                edited.fold(
                    onSuccess = { value -> onSave(subscription.id, value) },
                    onFailure = { failure -> Result.failure(failure) },
                ).fold(
                    onSuccess = { onDismiss() },
                    onFailure = { failure -> error = failure.message.orEmpty().ifBlank { "Не удалось сохранить подписку." } },
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SubscriptionProviderSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
            onCheckedChange(!checked)
        }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = HomeText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = HomeMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ServerCard(
    server: ProxyServer<*>,
    selected: Boolean,
    latency: DesktopServerLatencyResult?,
    testingLatency: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val info = server.getInfo()
    val transport = server.getTransportDisplay().orEmpty()
    val titleParts = splitFlagAndTitle(info.remarks)
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) HomeSelected else HomeSurface),
        border = BorderStroke(1.dp, if (selected) Color(0xFF9A9A9A) else Color.Transparent),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(HomeSurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(titleParts.first ?: "⚡", fontSize = 24.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        titleParts.second,
                        color = HomeText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        info.address,
                        color = HomeMuted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProtocolChip(text = info.protocol, selected = selected)
                if (transport.isNotBlank()) {
                    Spacer(Modifier.width(7.dp))
                    TransportChip(text = transport, selected = selected)
                }
                when (latency) {
                    is DesktopServerLatencyResult.Success -> {
                        Spacer(Modifier.width(10.dp))
                        Text("${latency.milliseconds} ms", color = HomeGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    DesktopServerLatencyResult.Timeout -> {
                        Spacer(Modifier.width(10.dp))
                        Text("Тайм-аут", color = HomeRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    is DesktopServerLatencyResult.Error -> {
                        Spacer(Modifier.width(10.dp))
                        Text("Ошибка", color = HomeRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    null -> Unit
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onTest, enabled = !testingLatency && server.desktopTcpEndpointOrNull() != null) {
                    Icon(Icons.Outlined.HourglassEmpty, "Проверить доступность", tint = HomeText)
                }
                IconButton(onClick = onCopy, enabled = server.getCopyTextOrNull() != null) {
                    Icon(Icons.Outlined.ContentCopy, "Копировать", tint = HomeText)
                }
                IconButton(onClick = onEdit, enabled = server.getCopyTextOrNull() != null) {
                    Icon(Icons.Outlined.Edit, "Изменить", tint = HomeText)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, "Удалить", tint = HomeText)
                }
            }
        }
    }
}

@Composable
private fun ProtocolChip(text: String, selected: Boolean) {
    val chipColor = when (text.lowercase()) {
        "vless", "vmess" -> Color(0xFF5C6DB0)
        "hysteria2", "hy2" -> Color(0xFFB65A3D)
        "shadowsocks", "ss" -> Color(0xFF387D58)
        else -> Color(0xFF426A4B)
    }
    Surface(color = chipColor.copy(alpha = if (selected) 0.85f else 0.45f), shape = RoundedCornerShape(8.dp)) {
        Text(text.uppercase(), color = HomeText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun TransportChip(text: String, selected: Boolean) {
    Surface(color = Color(0xFF40505A).copy(alpha = if (selected) 0.88f else 0.56f), shape = RoundedCornerShape(8.dp)) {
        Text(text, color = HomeText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun EmptyServerList(hasSearch: Boolean, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (hasSearch) "Ничего не найдено" else "Пока нет серверов", color = HomeText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (!hasSearch) {
                Text("Добавьте ссылку сервера или подписку", color = HomeMuted)
                Button(onClick = onAdd) { Text("Добавить сервер") }
            }
        }
    }
}

@Composable
private fun AddProxyDialog(
    mode: AddMode,
    serverLink: String,
    subscriptionUrl: String,
    subscriptionUpdate: DesktopSubscriptionUpdate?,
    subscriptionMessage: String,
    editingServer: Boolean,
    updatingSubscription: Boolean,
    onModeChange: (AddMode) -> Unit,
    onServerLinkChange: (String) -> Unit,
    onSubscriptionUrlChange: (String) -> Unit,
    onSaveServer: (ProxyServer<*>) -> Unit,
    onUpdateSubscription: () -> Unit,
    onPrepareSubscription: (DesktopSubscriptionInstallUri) -> Result<Unit>,
    onDismiss: () -> Unit,
) {
    val parsedServer = remember(serverLink) {
        serverLink.trim().takeIf(String::isNotEmpty)?.let { runCatching { ProxyServer.parse(it) } }
    }
    val subscriptionUrlValid = remember(subscriptionUrl) { subscriptionUrl.isValidManualSubscriptionUrl() }
    var subscriptionPreparationError by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    mode == AddMode.Subscription -> "Добавить подписку"
                    editingServer -> "Изменить сервер"
                    else -> "Добавить сервер"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onModeChange(AddMode.Server) }) { Text("Сервер") }
                    TextButton(onClick = { onModeChange(AddMode.Subscription) }) { Text("Подписка") }
                }
                HorizontalDivider()
                if (mode == AddMode.Server) {
                    OutlinedTextField(
                        value = serverLink,
                        onValueChange = onServerLinkChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("vless://, vmess://, ss:// …") },
                        minLines = 3,
                    )
                    when {
                        parsedServer == null -> Text("Вставьте ссылку сервера.", color = HomeMuted)
                        parsedServer.isFailure -> Text("Ссылка не поддерживается: ${parsedServer.exceptionOrNull()?.message.orEmpty()}", color = HomeRed)
                        else -> {
                            val info = parsedServer.getOrThrow().getInfo()
                            Text("${info.remarks}\n${info.protocol} · ${info.address}", color = HomeMuted)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = subscriptionUrl,
                        onValueChange = onSubscriptionUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("HTTP/HTTPS ссылка подписки") },
                        singleLine = true,
                        enabled = !updatingSubscription,
                    )
                    if (subscriptionUrl.isNotBlank() && !subscriptionUrlValid) {
                        Text("Укажите корректную HTTP/HTTPS ссылку.", color = HomeRed)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val install = subscriptionUrl.toDesktopSubscriptionInstallUriOrNull()
                            if (install == null) {
                                subscriptionPreparationError = "Не удалось подготовить ссылку подписки."
                            } else {
                                onPrepareSubscription(install).fold(
                                    onSuccess = {
                                        subscriptionPreparationError = ""
                                        onUpdateSubscription()
                                    },
                                    onFailure = { error ->
                                        subscriptionPreparationError = error.message.orEmpty()
                                            .ifBlank { "Не удалось сохранить подписку." }
                                    },
                                )
                            }
                        }, enabled = subscriptionUrlValid && !updatingSubscription) {
                            Text(if (updatingSubscription) "Загрузка…" else "Загрузить и применить")
                        }
                    }
                    if (subscriptionPreparationError.isNotBlank()) Text(subscriptionPreparationError, color = HomeRed)
                    if (subscriptionMessage.isNotBlank()) Text(subscriptionMessage, color = HomeMuted)
                    subscriptionUpdate?.let { update ->
                        Text("Найдено серверов: ${update.importResult.servers.size}", color = HomeGreen)
                        Text(
                            if (update.importResult.servers.isNotEmpty()) {
                                "Серверы этой подписки применяются автоматически без дубликатов."
                            } else {
                                "В подписке не найдено поддерживаемых серверов."
                            },
                            color = HomeMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (mode == AddMode.Server) {
                Button(
                    onClick = { parsedServer?.getOrNull()?.let(onSaveServer) },
                    enabled = parsedServer?.isSuccess == true,
                ) { Text(if (editingServer) "Сохранить" else "Добавить") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/**
 * Desktop counterpart of Android's clipboard/file import entries. Parsing and
 * commits are owned by the caller; this dialog only collects local data.
 * There is deliberately no second preview/confirmation step.
 */
@Composable
private fun ImportProxyDialog(
    onImport: (DesktopProxyImportInput) -> Result<String>,
    onPrepareSubscription: (DesktopSubscriptionInstallUri) -> Result<Unit>,
    onSubscriptionUrlChange: (String) -> Unit,
    onUpdateSubscription: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun import(input: DesktopProxyImportInput) {
        val install = input.text.trim().toDesktopSubscriptionInstallUriOrNull()
        if (install != null) {
            onPrepareSubscription(install).fold(
                onSuccess = {
                    onSubscriptionUrlChange(install.url)
                    onUpdateSubscription()
                    message = "Подписка «${install.name}» добавлена и обновляется."
                },
                onFailure = { error ->
                    message = "Не удалось добавить подписку: ${error.message.orEmpty()}"
                },
            )
        } else {
            onImport(input).fold(
                onSuccess = { summary -> message = summary },
                onFailure = { error -> message = "Не удалось импортировать: ${error.message.orEmpty()}" },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импортировать прокси") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Вставьте ссылки серверов, Base64, Mihomo YAML, JSON/Xray или конфиг .conf.",
                    color = HomeMuted,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Данные для импорта") },
                    minLines = 5,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        readDesktopClipboardText().fold(
                            onSuccess = { clipboardText ->
                                text = clipboardText
                                import(DesktopProxyImportInput.Clipboard(clipboardText))
                            },
                            onFailure = { error ->
                                message = "Не удалось прочитать буфер обмена: ${error.message.orEmpty()}"
                            },
                        )
                    }) { Text("Из буфера") }
                    TextButton(onClick = {
                        chooseDesktopImportFile().fold(
                            onSuccess = { file ->
                                if (file == null) return@fold
                                text = file.content
                                import(DesktopProxyImportInput.File(file.name, file.content))
                            },
                            onFailure = { error ->
                                message = "Не удалось прочитать файл: ${error.message.orEmpty()}"
                            },
                        )
                    }) { Text("Выбрать файл") }
                }
                if (message.isNotBlank()) Text(message, color = HomeMuted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = { import(DesktopProxyImportInput.Text(text)) },
                enabled = text.isNotBlank(),
            ) { Text("Импортировать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
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

private fun formatDesktopSubscriptionExpiryReminders(
    reminders: List<DesktopSubscriptionExpiryReminder>?,
): String = reminders.orEmpty().joinToString(", ") { reminder ->
    "${reminder.value}:${reminder.unit.desktopExpiryReminderLabel()}"
}

private fun parseDesktopSubscriptionExpiryReminders(
    text: String,
): List<DesktopSubscriptionExpiryReminder> {
    val entries = text
        .split(',', ';', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
    require(entries.isNotEmpty()) { "Укажите хотя бы одно пользовательское напоминание." }
    return entries.map { entry ->
        val parts = entry.split(':', limit = 2).map(String::trim)
        require(parts.size == 2) { "Напоминание '$entry' должно иметь формат значение:единица." }
        val value = parts.first().toIntOrNull()
            ?: throw IllegalArgumentException("Значение напоминания '$entry' должно быть целым числом.")
        val unit = parts.last().lowercase().let { label ->
            when (label) {
                "m", "min", "minute", "minutes", "минута", "минуты", "минут" ->
                    DesktopSubscriptionExpiryReminderUnit.Minutes

                "h", "hour", "hours", "час", "часа", "часов" ->
                    DesktopSubscriptionExpiryReminderUnit.Hours

                "d", "day", "days", "день", "дня", "дней" ->
                    DesktopSubscriptionExpiryReminderUnit.Days

                "w", "week", "weeks", "неделя", "недели", "недель" ->
                    DesktopSubscriptionExpiryReminderUnit.Weeks

                "expiry", "expiration", "истечение" ->
                    DesktopSubscriptionExpiryReminderUnit.AtExpiration

                else -> throw IllegalArgumentException("Неизвестная единица напоминания '$label'.")
            }
        }
        when (unit) {
            DesktopSubscriptionExpiryReminderUnit.AtExpiration -> require(value == 0) {
                "Для напоминания в момент истечения используйте 0:истечение."
            }

            else -> require(value > 0) { "Значение напоминания должно быть больше нуля." }
        }
        DesktopSubscriptionExpiryReminder(value = value, unit = unit)
    }
}

private fun DesktopSubscriptionExpiryReminderUnit.desktopExpiryReminderLabel(): String = when (this) {
    DesktopSubscriptionExpiryReminderUnit.Minutes -> "минуты"
    DesktopSubscriptionExpiryReminderUnit.Hours -> "часы"
    DesktopSubscriptionExpiryReminderUnit.Days -> "дни"
    DesktopSubscriptionExpiryReminderUnit.Weeks -> "недели"
    DesktopSubscriptionExpiryReminderUnit.AtExpiration -> "истечение"
}

private fun splitFlagAndTitle(value: String): Pair<String?, String> {
    val trimmed = value.trim().ifBlank { "Без названия" }
    val prefix = trimmed.substringBefore(' ')
    val looksLikeEmoji = prefix.any(Char::isSurrogate) || prefix == "⚡"
    return if (looksLikeEmoji && prefix.length <= 5) prefix to trimmed.removePrefix(prefix).trim().ifBlank { trimmed }
    else null to trimmed
}

private fun formatBytes(value: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var amount = value.coerceAtLeast(0).toDouble()
    var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) {
        amount /= 1024
        unit++
    }
    return if (unit == 0) "${amount.toLong()} ${units[unit]}" else "%.2f %s".format(amount, units[unit])
}

private fun formatSubscriptionExpiry(expireAtSeconds: Long): String {
    val remainingDays = ((expireAtSeconds * 1_000L - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    return "Осталось $remainingDays дн."
}

private fun openExternalLink(value: String): Result<Unit> = runCatching {
    check(Desktop.isDesktopSupported()) { "Открытие ссылки не поддерживается системой." }
    Desktop.getDesktop().browse(requireSafeDesktopExternalUri(value))
}

/**
 * Subscription metadata is remote input.  Only web pages and one plain email
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

private val DesktopDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private val SafeDesktopMailtoRecipient = Regex(
    "^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}",
)
