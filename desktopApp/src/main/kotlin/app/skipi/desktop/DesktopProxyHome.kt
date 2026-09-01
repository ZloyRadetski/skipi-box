// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import java.awt.datatransfer.StringSelection
import java.net.URI
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
    onDeleteSubscription: (Int) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    var toolsMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var addDialogVisible by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf(AddMode.Server) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf("") }
    var pendingServerDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingSubscriptionDeletion by remember { mutableStateOf<Int?>(null) }
    var editingServerId by remember { mutableStateOf<Int?>(null) }

    val decodedServers = remember(serverLibrary) {
        serverLibrary.servers.map { stored -> stored to stored.decode().getOrNull() }
    }
    val selectedServer = decodedServers.firstOrNull { (stored, _) -> stored.id == serverLibrary.selectedServerId }?.second
    val selectedTitle = selectedServer?.getInfo()?.remarks.orEmpty().ifBlank { "Выберите сервер" }
    val activeSubscription = subscriptionUrl.trim().takeIf(String::isNotBlank)?.let { selectedUrl ->
        subscriptionLibrary.subscriptions.firstOrNull { subscription -> subscription.url == selectedUrl }
    }
    val groupedServers = activeSubscription?.let { subscription ->
        decodedServers.filter { (stored, _) -> stored.subscriptionId == subscription.id }
    } ?: decodedServers
    val visibleServers = groupedServers.filter { (_, server) ->
        val query = searchQuery.trim()
        query.isEmpty() || server?.getInfo()?.let { info ->
            info.remarks.contains(query, ignoreCase = true) ||
                info.address.contains(query, ignoreCase = true) ||
                info.protocol.contains(query, ignoreCase = true)
        } == true
    }
    val visibleTestServers = visibleServers.mapNotNull { (stored, server) ->
        server?.takeIf { candidate -> candidate.desktopTcpEndpointOrNull() != null }?.let { stored.id to it }
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

            GroupSelector(
                title = activeSubscription?.name?.ifBlank { "Подписка" } ?: "Группы прокси",
                serverCount = groupedServers.size,
                totalServerCount = decodedServers.size,
                expanded = groupMenuExpanded,
                onExpandedChange = { groupMenuExpanded = it },
                subscriptions = subscriptionLibrary.subscriptions,
                onSelectAll = { onSubscriptionUrlChange("") },
                onSelectSubscription = { onSubscriptionUrlChange(it.url) },
            )

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
                    onDelete = {
                        if (confirmDeletion) pendingSubscriptionDeletion = activeSubscription.id
                        else onDeleteSubscription(activeSubscription.id)
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
            onDismiss = { addDialogVisible = false },
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
    title: String,
    serverCount: Int,
    totalServerCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    subscriptions: List<DesktopStoredSubscription>,
    onSelectAll: () -> Unit,
    onSelectSubscription: (DesktopStoredSubscription) -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HomeSurface)
                .border(1.dp, HomeBorder, RoundedCornerShape(16.dp))
                .clickable { onExpandedChange(true) }
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = HomeText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Серверов: $serverCount", color = HomeMuted, fontSize = 14.sp)
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = HomeMuted, modifier = Modifier.size(26.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("Все прокси · $totalServerCount") },
                onClick = { onExpandedChange(false); onSelectAll() },
            )
            subscriptions.forEach { subscription ->
                DropdownMenuItem(
                    text = { Text(subscription.name.ifBlank { subscription.url }) },
                    onClick = { onExpandedChange(false); onSelectSubscription(subscription) },
                )
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
    onDelete: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val metadata = subscription.metadata
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    Icon(Icons.Outlined.Refresh, "Обновить", tint = HomeText)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Удалить", tint = HomeText) }
            }
            val usedBytes = metadata.trafficUploadBytes.coerceAtLeast(0) + metadata.trafficDownloadBytes.coerceAtLeast(0)
            if (metadata.trafficTotalBytes >= 0) {
                Text("Трафик: ${formatBytes(usedBytes)} / ${formatBytes(metadata.trafficTotalBytes)}", color = HomeMuted, fontSize = 14.sp)
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
                Text(announce, color = HomeMuted, fontSize = 14.sp)
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
    onDismiss: () -> Unit,
) {
    val parsedServer = remember(serverLink) {
        serverLink.trim().takeIf(String::isNotEmpty)?.let { runCatching { ProxyServer.parse(it) } }
    }
    val subscriptionUrlValid = remember(subscriptionUrl) { subscriptionUrl.isValidManualSubscriptionUrl() }
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
                        Button(onClick = onUpdateSubscription, enabled = subscriptionUrlValid && !updatingSubscription) {
                            Text(if (updatingSubscription) "Загрузка…" else "Загрузить и применить")
                        }
                    }
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
