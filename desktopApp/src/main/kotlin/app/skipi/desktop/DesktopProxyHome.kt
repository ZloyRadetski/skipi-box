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
import java.awt.datatransfer.StringSelection

private val HomeBackground = Color(0xFF141519)
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
    running: Boolean,
    canToggleTunnel: Boolean,
    tunnelMessage: String,
    serverMessage: String,
    subscriptionMessage: String,
    onServerLinkChange: (String) -> Unit,
    onSubscriptionUrlChange: (String) -> Unit,
    onToggleTunnel: () -> Unit,
    onSelectServer: (Int) -> Unit,
    onDeleteServer: (Int) -> Unit,
    onAddServer: (ProxyServer<*>) -> Unit,
    onUpdateSubscription: () -> Unit,
    onImportSubscriptionServers: () -> Unit,
    onSaveSubscription: () -> Boolean,
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

    val decodedServers = remember(serverLibrary) {
        serverLibrary.servers.map { stored -> stored to stored.decode().getOrNull() }
    }
    val selectedServer = decodedServers.firstOrNull { (stored, _) -> stored.id == serverLibrary.selectedServerId }?.second
    val selectedTitle = selectedServer?.getInfo()?.remarks.orEmpty().ifBlank { "Выберите сервер" }
    val activeSubscription = subscriptionLibrary.subscriptions.firstOrNull { it.url == subscriptionUrl }
        ?: subscriptionLibrary.subscriptions.firstOrNull()
    val visibleServers = decodedServers.filter { (_, server) ->
        val query = searchQuery.trim()
        query.isEmpty() || server?.getInfo()?.let { info ->
            info.remarks.contains(query, ignoreCase = true) ||
                info.address.contains(query, ignoreCase = true) ||
                info.protocol.contains(query, ignoreCase = true)
        } == true
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeHeader(
                onLatencyClick = {
                    if (activeSubscription != null) {
                        onSubscriptionUrlChange(activeSubscription.url)
                        onUpdateSubscription()
                    } else {
                        localMessage = "Добавьте подписку, чтобы обновить список серверов."
                    }
                },
                onAddServer = {
                    addMode = AddMode.Server
                    addDialogVisible = true
                },
                onAddSubscription = {
                    addMode = AddMode.Subscription
                    addDialogVisible = true
                },
                addMenuExpanded = addMenuExpanded,
                onAddMenuExpandedChange = { addMenuExpanded = it },
                toolsMenuExpanded = toolsMenuExpanded,
                onToolsMenuExpandedChange = { toolsMenuExpanded = it },
                searchVisible = searchVisible,
                onSearchVisibleChange = { searchVisible = it },
            )

            ConnectionHeroCard(
                running = running,
                enabled = canToggleTunnel || running,
                selectedTitle = selectedTitle,
                onToggle = onToggleTunnel,
            )

            GroupSelector(
                title = activeSubscription?.name?.ifBlank { "Подписка" } ?: "Группы прокси",
                serverCount = serverLibrary.servers.size,
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
                    serverCount = serverLibrary.servers.size,
                    update = subscriptionUpdate,
                    onRefresh = {
                        onSubscriptionUrlChange(activeSubscription.url)
                        onUpdateSubscription()
                    },
                    onDelete = { onDeleteSubscription(activeSubscription.id) },
                )
            }

            if (visibleServers.isEmpty()) {
                EmptyServerList(
                    hasSearch = searchQuery.isNotBlank(),
                    onAdd = {
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
                            onSelect = { onSelectServer(stored.id) },
                            onCopy = {
                                server.getCopyTextOrNull()?.let { copyText ->
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(copyText), null)
                                    localMessage = "Ссылка сервера скопирована."
                                }
                            },
                            onEdit = {
                                server.getCopyTextOrNull()?.let(onServerLinkChange)
                                addMode = AddMode.Server
                                addDialogVisible = true
                            },
                            onDelete = { onDeleteServer(stored.id) },
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

    if (addDialogVisible) {
        AddProxyDialog(
            mode = addMode,
            serverLink = serverLink,
            subscriptionUrl = subscriptionUrl,
            subscriptionUpdate = subscriptionUpdate,
            subscriptionMessage = subscriptionMessage,
            onModeChange = { addMode = it },
            onServerLinkChange = onServerLinkChange,
            onSubscriptionUrlChange = onSubscriptionUrlChange,
            onAddServer = {
                onAddServer(it)
                addDialogVisible = false
            },
            onUpdateSubscription = onUpdateSubscription,
            onImportSubscriptionServers = onImportSubscriptionServers,
            onSaveSubscription = {
                val saved = onSaveSubscription()
                if (saved) addDialogVisible = false
                saved
            },
            onDismiss = { addDialogVisible = false },
        )
    }
}

@Composable
private fun HomeHeader(
    onLatencyClick: () -> Unit,
    onAddServer: () -> Unit,
    onAddSubscription: () -> Unit,
    addMenuExpanded: Boolean,
    onAddMenuExpandedChange: (Boolean) -> Unit,
    toolsMenuExpanded: Boolean,
    onToolsMenuExpandedChange: (Boolean) -> Unit,
    searchVisible: Boolean,
    onSearchVisibleChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SKIPI", color = HomeText, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLatencyClick) {
                Icon(Icons.Outlined.HourglassEmpty, "Обновить подписку", tint = HomeText, modifier = Modifier.size(27.dp))
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
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        border = BorderStroke(1.dp, if (running) HomeGreen.copy(alpha = 0.48f) else HomeBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
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
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (running) "Подключено" else "Отключено",
                    color = HomeText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = when {
                        running -> selectedTitle
                        enabled -> "Нажмите для подключения"
                        else -> "Сначала выберите сервер"
                    },
                    color = HomeMuted,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GroupSelector(
    title: String,
    serverCount: Int,
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
                text = { Text("Все прокси · $serverCount") },
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
    update: DesktopSubscriptionUpdate?,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val metadata = update?.metadata
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
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Обновить", tint = HomeText) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Удалить", tint = HomeText) }
            }
            metadata?.let {
                val usedBytes = it.trafficUploadBytes.coerceAtLeast(0) + it.trafficDownloadBytes.coerceAtLeast(0)
                if (it.trafficTotalBytes >= 0) {
                    Text("Трафик: ${formatBytes(usedBytes)} / ${formatBytes(it.trafficTotalBytes)}", color = HomeMuted, fontSize = 14.sp)
                }
                it.announce?.takeIf(String::isNotBlank)?.let { announce ->
                    Text(announce, color = HomeMuted, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: ProxyServer<*>,
    selected: Boolean,
    onSelect: () -> Unit,
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
                Spacer(Modifier.weight(1f))
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
    onModeChange: (AddMode) -> Unit,
    onServerLinkChange: (String) -> Unit,
    onSubscriptionUrlChange: (String) -> Unit,
    onAddServer: (ProxyServer<*>) -> Unit,
    onUpdateSubscription: () -> Unit,
    onImportSubscriptionServers: () -> Unit,
    onSaveSubscription: () -> Boolean,
    onDismiss: () -> Unit,
) {
    val parsedServer = remember(serverLink) {
        serverLink.trim().takeIf(String::isNotEmpty)?.let { runCatching { ProxyServer.parse(it) } }
    }
    val subscriptionUrlValid = remember(subscriptionUrl) { subscriptionUrl.isValidManualSubscriptionUrl() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == AddMode.Server) "Добавить сервер" else "Добавить подписку") },
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
                    )
                    if (subscriptionUrl.isNotBlank() && !subscriptionUrlValid) {
                        Text("Укажите корректную HTTP/HTTPS ссылку.", color = HomeRed)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSaveSubscription() }, enabled = subscriptionUrlValid) {
                            Text("Добавить подписку")
                        }
                        Button(onClick = onUpdateSubscription, enabled = subscriptionUrlValid) {
                            Text("Проверить и добавить")
                        }
                    }
                    if (subscriptionMessage.isNotBlank()) Text(subscriptionMessage, color = HomeMuted)
                    subscriptionUpdate?.let { update ->
                        Text("Найдено серверов: ${update.importResult.servers.size}", color = HomeGreen)
                        if (update.importResult.servers.isNotEmpty()) {
                            Button(onClick = onImportSubscriptionServers) { Text("Добавить серверы") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mode == AddMode.Server) {
                Button(
                    onClick = { parsedServer?.getOrNull()?.let(onAddServer) },
                    enabled = parsedServer?.isSuccess == true,
                ) { Text("Добавить") }
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
