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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import app.isTestingLatency
import desktop.DesktopPingTester
import desktop.DesktopProcessProxyEngine
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.ProxyEngineStatus
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.display.ProtocolColorUtils
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.isCompositeProxyServer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Design tokens matching the SKIPI dark theme.
private val ScreenBackground = Color(0xFF0D0D10)
private val CardBackground = Color(0xFF1E1C24)
private val AccentPurple = Color(0xFF7C6FE0)
private val ConnectedGreen = Color(0xFF4ADE80)
private val LatencyGreen = Color(0xFF34D399)
private val SubtitleGray = Color(0xFF9CA0A8)
private val ChipBackground = Color(0xFF2A2732)

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
    var isBusy by remember { mutableStateOf(false) }
    var pingAllInProgress by remember { mutableStateOf(false) }
    var selectedGroupFilter by remember { mutableStateOf<Int?>(null) }
    var connectedAtMillis by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    val running = engineStatus.running

    // Connection timer ticks once per second while the tunnel is up.
    LaunchedEffect(running) {
        if (running) {
            if (connectedAtMillis == 0L) connectedAtMillis = System.currentTimeMillis()
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - connectedAtMillis) / 1000L
                kotlinx.coroutines.delay(1000L)
            }
        } else {
            connectedAtMillis = 0L
            elapsedSeconds = 0L
        }
    }

    fun toggleConnection(target: ProxyServerState?) {
        if (isBusy) return
        scope.launch {
            isBusy = true
            try {
                val newStatus = if (running) {
                    proxyEngine.stop()
                } else {
                    val selected = target
                        ?: appState.proxyServers.firstOrNull { it.id == appState.selectedProxyServerId }
                    if (selected == null) {
                        proxyEngine.status()
                    } else {
                        proxyEngine.start(
                            ProxyEngineStartRequest(
                                appState = appState.copy(selectedProxyServerId = selected.id),
                                selectedServer = selected,
                            )
                        )
                    }
                }
                onStatusChange(newStatus)
            } finally {
                isBusy = false
            }
        }
    }

    fun pingServer(serverState: ProxyServerState) {
        val hostPort = resolveHostPort(serverState) ?: run {
            updateLatency(onUpdateAppState, serverState.id, "timeout")
            return
        }
        scope.launch {
            updateLatency(onUpdateAppState, serverState.id, "__TESTING__")
            val ms = DesktopPingTester.testTcp(hostPort.first, hostPort.second)
            updateLatency(onUpdateAppState, serverState.id, if (ms < 0) "timeout" else "$ms ms")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        // ── Connection card ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardBackground)
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PowerButton(
                    running = running,
                    enabled = !isBusy,
                    onClick = { toggleConnection(null) },
                )
                Spacer(modifier = Modifier.width(22.dp))
                Column {
                    Text(
                        text = if (running) "Подключено" else "Отключено",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🛡", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeServerTitle(appState),
                            fontSize = 15.sp,
                            color = SubtitleGray,
                            maxLines = 2,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (running) ConnectedGreen else SubtitleGray.copy(alpha = 0.5f)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = formatTimer(elapsedSeconds),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (running) Color.White else SubtitleGray,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "|", fontSize = 14.sp, color = SubtitleGray.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = selectedLatency(appState),
                    fontSize = 14.sp,
                    color = LatencyGreen,
                )
                Spacer(modifier = Modifier.weight(1f))
                PillButton(
                    label = "⧗  Проверить",
                    enabled = !pingAllInProgress,
                    onClick = {
                        if (!pingAllInProgress) {
                            pingAllInProgress = true
                            scope.launch {
                                appState.proxyServers.forEach { serverState ->
                                    if (!serverState.server.isCompositeProxyServer()) {
                                        pingServer(serverState)
                                    }
                                }
                                pingAllInProgress = false
                            }
                        }
                    },
                )
            }
        }

        // ── Groups selector row ──────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupChip(
                label = "Группы прокси (${appState.subscriptionGroups.size})",
                selected = selectedGroupFilter == null,
                onClick = { selectedGroupFilter = null },
            )
            Spacer(modifier = Modifier.width(10.dp))
            appState.subscriptionGroups.forEach { group ->
                GroupChip(
                    label = group.name.ifBlank { group.profileTitle.ifBlank { "#" + group.id } },
                    selected = selectedGroupFilter == group.id,
                    onClick = { selectedGroupFilter = group.id },
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
        }

        // ── Server cards list ────────────────────────────────────────────
        Spacer(modifier = Modifier.height(14.dp))
        val visibleServers = appState.proxyServers
            .filter { serverState -> selectedGroupFilter == null || serverState.groupId == selectedGroupFilter }

        if (visibleServers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBackground)
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Нет доступных серверов", fontSize = 15.sp, color = SubtitleGray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(visibleServers, key = { it.id }) { serverState ->
                    ServerCard(
                        appState = appState,
                        serverState = serverState,
                        isSelected = serverState.id == appState.selectedProxyServerId,
                        canConnect = !running,
                        onSelect = {
                            onUpdateAppState { it.copy(selectedProxyServerId = serverState.id) }
                        },
                        onConnect = { toggleConnection(serverState) },
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // ── Floating pause/resume button ────────────────────────────────
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AccentPurple)
                    .clickable(enabled = !isBusy) { toggleConnection(null) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (running) "⏸" else "▶",
                    fontSize = 24.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ServerCard(
    appState: AppState,
    serverState: ProxyServerState,
    isSelected: Boolean,
    canConnect: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
) {
    val info = serverState.server.getInfo()
    val strategyGroup = serverState.server as? StrategyGroup
    val countryFlag = CountryFlagUtils.extractLeadingCountryFlag(info.remarks)
    val protocolColor = ProtocolColorUtils.resolveProtocolColor(
        protocol = info.protocol,
        appState = appState,
        isDark = false,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) AccentPurple else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onSelect)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Leading icon: flag for regular servers, lightning for strategy groups.
            Text(
                text = if (strategyGroup != null) "⚡" else (countryFlag ?: "🌐"),
                fontSize = 22.sp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = info.remarks.ifBlank { "Сервер #${serverState.id}" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = strategySubtitle(strategyGroup, appState),
                    fontSize = 13.sp,
                    color = SubtitleGray,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Strategy badge (green) or protocol badge (colored).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LatencyGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = strategyGroup?.let { "Strategy" } ?: info.protocol.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (strategyGroup != null) LatencyGreen else protocolColor,
                )
            }
            if (strategyGroup == null && serverState.latency.isNotBlank() && !serverState.isTestingLatency) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = serverState.latency,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = LatencyGreen,
                )
            }
            if (serverState.isTestingLatency) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "...", fontSize = 13.sp, color = SubtitleGray)
            }
            Spacer(modifier = Modifier.weight(1f))
            ActionGlyph("⧉")
            Spacer(modifier = Modifier.width(16.dp))
            ActionGlyph("✎")
            Spacer(modifier = Modifier.width(16.dp))
            ActionGlyph("🗑")
        }
        if (isSelected && canConnect) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentPurple.copy(alpha = 0.18f))
                    .clickable(onClick = onConnect)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Подключиться",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ActionGlyph(glyph: String) {
    Text(
        text = glyph,
        fontSize = 15.sp,
        color = SubtitleGray,
    )
}

@Composable
private fun PowerButton(
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(4.dp, AccentPurple, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⏻",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = if (running) ConnectedGreen else Color.White,
        )
    }
}

@Composable
private fun PillButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ChipBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else SubtitleGray,
        )
    }
}

@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ChipBackground else CardBackground)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = AccentPurple.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else SubtitleGray,
        )
    }
}


private fun updateLatency(
    onUpdateAppState: ((AppState) -> AppState) -> Unit,
    serverId: Int,
    latency: String,
) {
    onUpdateAppState { state ->
        state.copy(
            proxyServers = state.proxyServers.map { serverState ->
                if (serverState.id == serverId) serverState.copy(latency = latency) else serverState
            },
        )
    }
}

/** Pulls the first address/port pair out of the serialized Xray outbound settings. */
private fun resolveHostPort(serverState: ProxyServerState): Pair<String, Int>? = runCatching {
    val settings = serverState.server.toXrayOutbound("desktop-ping").settings
    var address: String? = null
    var port: Int? = null
    fun walk(element: kotlinx.serialization.json.JsonElement) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("address")) {
                    val a = (element["address"] as? JsonPrimitive)?.contentOrNull
                    val p = (element["port"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                    if (!a.isNullOrBlank() && address == null && p != null) {
                        address = a
                        port = p
                        return
                    }
                }
                element.values.forEach { walk(it) }
            }
            is JsonArray -> element.forEach { walk(it) }
            else -> {}
        }
    }
    walk(settings)
    if (address != null && port != null) address!! to port!! else null
}.getOrNull()



private fun strategySubtitle(strategyGroup: StrategyGroup?, appState: AppState): String {
    if (strategyGroup == null) return ""
    val strategyLabel = when (strategyGroup.strategy) {
        "leastPing" -> "Наименьший пинг (URLTest)"
        "select" -> "Выбор вручную"
        "fallback" -> "Переключение (Fallback)"
        "leastLoad" -> "Наименьшая загрузка (LeastLoad)"
        "roundRobin" -> "По кругу (RoundRobin)"
        else -> strategyGroup.strategy
    }
    val groupName = appState.subscriptionGroups
        .firstOrNull { it.id == strategyGroup.subscriptionGroupId }
        ?.name
    val scopeLabel = if (groupName.isNullOrBlank()) "Все группы" else groupName
    return "$strategyLabel · $scopeLabel"
}

private fun activeServerTitle(appState: AppState): String {
    val selected = appState.proxyServers.firstOrNull { it.id == appState.selectedProxyServerId }
        ?: return "Нет выбранного сервера"
    val info = selected.server.getInfo()
    val remarks = info.remarks.ifBlank { "Сервер #${selected.id}" }
    return "$remarks · ${info.protocol.uppercase()}"
}

private fun selectedLatency(appState: AppState): String {
    val selected = appState.proxyServers.firstOrNull { it.id == appState.selectedProxyServerId }
        ?: return "-- ms"
    val serverImpl = selected.server
    if (serverImpl is StrategyGroup) {
        // Latency of the currently chosen member of the strategy group.
        val memberId = serverImpl.selectedMemberId
        val member = appState.proxyServers.firstOrNull { it.id == memberId }
        return member?.latency?.takeIf { it.isNotBlank() } ?: "-- ms"
    }
    return selected.latency.takeIf { it.isNotBlank() } ?: "-- ms"
}

private fun formatTimer(totalSeconds: Long): String {
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

