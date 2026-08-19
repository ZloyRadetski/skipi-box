// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import desktop.DesktopSubscriptionManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DesktopSubscriptionScreen(
    appState: AppState,
    onUpdateAppState: ((AppState) -> AppState) -> Unit,
    subscriptionManager: DesktopSubscriptionManager,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Add Subscription Card
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Добавить подписку",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = "URL подписки (http/https)",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = "Название подписки (необязательно)",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (statusMessage.isNotBlank()) {
                        Text(
                            text = statusMessage,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        enabled = !isLoading && urlInput.isNotBlank(),
                        onClick = {
                            val url = urlInput.trim()
                            if (url.isBlank()) return@Button

                            scope.launch {
                                isLoading = true
                                statusMessage = "Загрузка подписки..."
                                runCatching {
                                    val parsedServers = subscriptionManager.fetchAndParse(url)
                                    val groupId = appState.nextSubscriptionGroupId
                                    val groupName = nameInput.trim().ifBlank { "Подписка $groupId" }

                                    val newGroup = SubscriptionGroupState(
                                        id = groupId,
                                        name = groupName,
                                        url = url,
                                        userAgent = "SKIPI/0.1.0/Desktop",
                                        updateInterval = "",
                                        enabled = true,
                                    )

                                    var nextServerId = appState.nextProxyServerId
                                    val newServerStates = parsedServers.map { server ->
                                        ProxyServerState(
                                            id = nextServerId++,
                                            server = server,
                                            groupId = groupId,
                                        )
                                    }

                                    onUpdateAppState { current ->
                                        current.copy(
                                            subscriptionGroups = current.subscriptionGroups + newGroup,
                                            nextSubscriptionGroupId = groupId + 1,
                                            proxyServers = current.proxyServers + newServerStates,
                                            nextProxyServerId = nextServerId,
                                            selectedProxyServerId = current.selectedProxyServerId.takeIf { it > 0 }
                                                ?: newServerStates.firstOrNull()?.id
                                                ?: 0,
                                        )
                                    }

                                    urlInput = ""
                                    nameInput = ""
                                    statusMessage = "Успешно добавлено ${newServerStates.size} серверов"
                                }.onFailure { error ->
                                    statusMessage = "Ошибка: ${error.message ?: "Не удалось загрузить"}"
                                }
                                isLoading = false
                            }
                        }
                    ) {
                        Text(
                            text = if (isLoading) "Загрузка..." else "Импортировать",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Subscription Groups Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Группы подписок (${appState.subscriptionGroups.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )

            TextButton(
                text = if (isLoading) "Обновление..." else "Обновить все",
                enabled = !isLoading && appState.subscriptionGroups.any { it.url.isNotBlank() },
                onClick = {
                    scope.launch {
                        isLoading = true
                        var totalImported = 0
                        for (group in appState.subscriptionGroups) {
                            if (group.url.isBlank()) continue
                            runCatching {
                                val servers = subscriptionManager.fetchAndParse(group.url, group.userAgent)
                                var nextServerId = appState.nextProxyServerId
                                val newServerStates = servers.map { server ->
                                    ProxyServerState(
                                        id = nextServerId++,
                                        server = server,
                                        groupId = group.id,
                                    )
                                }
                                totalImported += newServerStates.size
                                onUpdateAppState { current ->
                                    val otherServers = current.proxyServers.filter { it.groupId != group.id }
                                    current.copy(
                                        proxyServers = otherServers + newServerStates,
                                        nextProxyServerId = nextServerId,
                                    )
                                }
                            }
                        }
                        statusMessage = "Обновлено $totalImported серверов"
                        isLoading = false
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subscription Groups List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(appState.subscriptionGroups, key = { it.id }) { group ->
                val serverCount = appState.proxyServers.count { it.groupId == group.id }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = group.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$serverCount серверов",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }

                            if (group.url.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = group.url,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                )
                            }
                        }

                        if (!group.builtIn) {
                            TextButton(
                                text = "Удалить",
                                onClick = {
                                    onUpdateAppState { current ->
                                        current.copy(
                                            subscriptionGroups = current.subscriptionGroups.filter { it.id != group.id },
                                            proxyServers = current.proxyServers.filter { it.groupId != group.id },
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
