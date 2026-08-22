// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import desktop.DesktopPingTester
import desktop.DesktopProcessProxyEngine
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.ProxyEngineStatus
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.display.ProtocolColorUtils
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DesktopProxyScreen(
    appState: AppState,
    onUpdateAppState: ((AppState) -> AppState) -> Unit,
    proxyEngine: DesktopProcessProxyEngine,
    engineStatus: ProxyEngineStatus,
    onStatusChange: (ProxyEngineStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isPinging by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Top Header with Connect Button and Status
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (engineStatus.running) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (engineStatus.running) "Подключено" else "Отключено",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        val selectedServer = appState.proxyServers.firstOrNull { it.id == appState.selectedProxyServerId }
                        Text(
                            text = if (engineStatus.running && selectedServer != null) {
                                "${selectedServer.server.getInfo().remarks} (${selectedServer.server.getInfo().protocol})"
                            } else {
                                "Выберите сервер и нажмите подключиться"
                            },
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        text = if (isPinging) "Проверка..." else "Проверить пинг",
                        enabled = !isPinging && appState.proxyServers.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                isPinging = true
                                val updatedServers = appState.proxyServers.map { serverState ->
                                    val info = serverState.server.getInfo()
                                    val parts = info.address.split(":")
                                    val host = parts.getOrNull(0).orEmpty()
                                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 443
                                    val latencyMs = if (host.isNotBlank()) {
                                        DesktopPingTester.testTcp(host, port)
                                    } else -1L
                                    val latencyText = if (latencyMs > 0) "${latencyMs}ms" else "timeout"
                                    serverState.copy(latency = latencyText)
                                }
                                onUpdateAppState { it.copy(proxyServers = updatedServers) }
                                isPinging = false
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        colors = if (engineStatus.running) {
                            ButtonDefaults.buttonColors(color = Color(0xFFE53935))
                        } else {
                            ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                        },
                        onClick = {
                            if (engineStatus.running) {
                                val newStatus = proxyEngine.stop()
                                onStatusChange(newStatus)
                            } else {
                                val selectedServer = appState.proxyServers.firstOrNull { it.id == appState.selectedProxyServerId }
                                    ?: appState.proxyServers.firstOrNull()
                                if (selectedServer != null) {
                                    val newStatus = proxyEngine.start(
                                        ProxyEngineStartRequest(
                                            appState = appState,
                                            selectedServer = selectedServer,
                                        )
                                    )
                                    onStatusChange(newStatus)
                                }
                            }
                        }
                    ) {
                        Text(
                            text = if (engineStatus.running) "Отключить" else "Подключить",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Server List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Серверы (${appState.proxyServers.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Server List
        if (appState.proxyServers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Нет доступных серверов",
                        fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Добавьте подписку в разделе «Подписки»",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(appState.proxyServers, key = { it.id }) { serverState ->
                    val isSelected = serverState.id == appState.selectedProxyServerId
                    val info = serverState.server.getInfo()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateAppState { it.copy(selectedProxyServerId = serverState.id) }
                                if (engineStatus.running) {
                                    val newStatus = proxyEngine.start(
                                        ProxyEngineStartRequest(
                                            appState = appState.copy(selectedProxyServerId = serverState.id),
                                            selectedServer = serverState,
                                        )
                                    )
                                    onStatusChange(newStatus)
                                }
                            }
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        2.dp,
                                        MiuixTheme.colorScheme.primary,
                                        RoundedCornerShape(16.dp)
                                    )
                                } else Modifier
                            ),
                        colors = CardDefaults.defaultColors(
                            color = if (isSelected) {
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                            } else {
                                MiuixTheme.colorScheme.surface
                            }
                        ),
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
                                    val countryFlag = CountryFlagUtils.extractLeadingCountryFlag(info.remarks)
                                    if (countryFlag != null) {
                                        Text(text = countryFlag, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    val protocolColor = ProtocolColorUtils.resolveProtocolColor(
                                        protocol = info.protocol,
                                        appState = appState,
                                        isDark = false,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(protocolColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = info.protocol.uppercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = protocolColor,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = CountryFlagUtils.stripLeadingCountryFlag(info.remarks)
                                            .ifBlank { "Сервер #${serverState.id}" },
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }

                                if (info.address.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = info.address,
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }

                            if (serverState.latency.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (serverState.latency == "timeout") {
                                                Color(0xFFE53935).copy(alpha = 0.15f)
                                            } else {
                                                Color(0xFF4CAF50).copy(alpha = 0.15f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = serverState.latency,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (serverState.latency == "timeout") {
                                            Color(0xFFE53935)
                                        } else {
                                            Color(0xFF4CAF50)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
