// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyServerSummary
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_edit
import app.skipi.ui.resources.home_latency_milliseconds
import app.skipi.ui.resources.home_move_down
import app.skipi.ui.resources.home_move_up
import app.skipi.ui.resources.proxy_server_list_latency_failed
import app.skipi.ui.resources.home_server_testing_latency
import app.skipi.ui.resources.home_show_qr_code
import app.skipi.ui.resources.proxy_editor_strategy_group_select_servers
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.server.display.ProtocolColorUtils
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.StatusColorDefaults
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun serverMenuActionTitle(id: String): String = when {
    id.startsWith("copy:") -> copyFormatTitle(
        ProxyHomeCopyFormat.entries.first { it.name == id.substringAfter("copy:") },
    )
    id == "show-qr" -> stringResource(Res.string.home_show_qr_code)
    id == "edit" -> stringResource(Res.string.common_edit)
    id == "delete" -> stringResource(Res.string.common_delete)
    id == "strategy-members" -> stringResource(Res.string.proxy_editor_strategy_group_select_servers)
    id == "move-up" -> stringResource(Res.string.home_move_up)
    else -> stringResource(Res.string.home_move_down)
}

internal fun handleServerMenuAction(
    actionId: String,
    server: ProxyServerSummary,
    onAction: (ProxyHomeAction) -> Unit,
) {
    when {
        actionId.startsWith("copy:") -> ProxyHomeCopyFormat.entries
            .firstOrNull { it.name == actionId.substringAfter("copy:") }
            ?.let { format -> onAction(ProxyHomeAction.CopyServer(server.id, format)) }
        actionId == "show-qr" -> onAction(ProxyHomeAction.ShowServerQr(server.id))
        actionId == "edit" -> onAction(ProxyHomeAction.EditServer(server.id))
        actionId == "delete" -> onAction(ProxyHomeAction.DeleteServer(server.id))
        actionId == "strategy-members" -> onAction(ProxyHomeAction.OpenStrategyMemberPicker(server.id))
        actionId == "move-up" -> onAction(ProxyHomeAction.MoveServer(server.id, -1))
        actionId == "move-down" -> onAction(ProxyHomeAction.MoveServer(server.id, 1))
    }
}

@Composable
internal fun ServerListItem(
    server: ProxyServerSummary,
    context: ServerRowContext,
    serverIndex: Int,
    totalServers: Int,
    onAction: (ProxyHomeAction) -> Unit,
    inSubscriptionGroup: Boolean = false,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val menuActions = serverMenuActionIds(server, context, serverIndex, totalServers)
    val serverLatencyMs = server.latencyMs
    val latencyText = when {
        server.latencyTesting -> stringResource(Res.string.home_server_testing_latency)
        serverLatencyMs != null -> stringResource(Res.string.home_latency_milliseconds, serverLatencyMs)
        server.latencyError -> server.latencyErrorText?.takeIf(String::isNotBlank)
            ?: stringResource(Res.string.proxy_server_list_latency_failed)
        else -> null
    }
    val latencyColor = serverLatencyColor(server, context.displayOptions)
    // User-chosen protocol colors win; otherwise the theme-aware defaults (readable in light and dark).
    val protocolColor = ProtocolColorUtils.resolveProtocolColor(
        protocol = server.protocol,
        isDark = SkipiTheme.colors.isDark,
        customColors = context.displayOptions.protocolColors,
    )

    run {
        var menuExpanded by remember(server.id) { mutableStateOf(false) }
        SkipiProxyServerCompactListCard(
            state = SkipiProxyServerCompactListCardState(
                flag = server.flag,
                title = server.title,
                summary = server.address,
                protocol = server.protocol,
                protocolColor = protocolColor,
                transport = server.transport,
                transportTextColor = Color.White.copy(alpha = 0.9f),
                transportContainerColor = Color(0xFF40505A).copy(alpha = 0.5f),
                selected = server.selected,
                inSubscriptionGroup = inSubscriptionGroup,
                latencyText = latencyText,
                latencyTesting = server.latencyTesting,
                latencyColor = latencyColor,
                progressColor = SkipiTheme.colors.accent,
                isStrategyGroup = server.isStrategyGroup,
                isDragging = isDragging,
            ),
            colors = SkipiProxyServerCompactListCardColors(
                surface = SkipiTheme.colors.surface,
                selectedSurface = SkipiTheme.colors.surfaceVariant,
                selectedBorder = SkipiTheme.colors.accent,
            ),
            titleFontWeight = themedFontWeight(FontWeight.SemiBold),
            latencyFontWeight = themedFontWeight(FontWeight.Medium),
            onSelect = { onAction(ProxyHomeAction.SelectServer(server.id)) },
            onLongPress = { menuExpanded = true },
            overlay = {
                if (menuActions.isNotEmpty()) {
                    SkipiHomeDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        val moves = menuActions.filter { it == "move-up" || it == "move-down" }
                        (menuActions - moves.toSet()).forEach { actionId ->
                            DropdownMenuItem(
                                text = { Text(serverMenuActionTitle(actionId)) },
                                colors = skipiHomeDropdownItemColors(),
                                onClick = {
                                    menuExpanded = false
                                    handleServerMenuAction(actionId, server, onAction)
                                },
                            )
                        }
                        // Up and down sit side by side. Arrows alone: two labelled items never fit the menu's width.
                        if (moves.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                moves.forEach { actionId ->
                                    val title = serverMenuActionTitle(actionId)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable {
                                                menuExpanded = false
                                                handleServerMenuAction(actionId, server, onAction)
                                            }
                                            .semantics { contentDescription = title },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (actionId == "move-up") "↑" else "↓",
                                            color = SkipiTheme.colors.onSurface,
                                            style = SkipiTheme.typography.titleMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            modifier = modifier,
            dragModifier = dragModifier,
        )
    }
}

@Composable
internal fun serverLatencyColor(
    server: ProxyServerSummary,
    options: ProxyHomeDisplayOptions,
): Color {
    if (server.latencyTesting) return SkipiTheme.colors.accent

    val latencyMs = server.latencyMs
    if (latencyMs == null && !server.latencyError) return SkipiTheme.colors.onSurfaceVariant

    val darkTheme = SkipiTheme.colorScheme.isDark
    val failedColor = options.latencyErrorColor?.let { Color(it) } ?: StatusColorDefaults.statusStopped(darkTheme)
    return when {
        server.latencyError || latencyMs == null || latencyMs >= 400L -> failedColor
        latencyMs < 100L -> options.latencyFastColor?.let { Color(it) } ?: StatusColorDefaults.pingFast(darkTheme)
        latencyMs < 200L -> options.latencyMediumColor?.let { Color(it) } ?: StatusColorDefaults.pingMedium(darkTheme)
        else -> options.latencySlowColor?.let { Color(it) } ?: StatusColorDefaults.pingSlow(darkTheme)
    }
}
