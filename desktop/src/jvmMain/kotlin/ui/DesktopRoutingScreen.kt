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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppState
import features.routing.model.RouteRule
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DesktopRoutingScreen(
    appState: AppState,
    onUpdateAppState: ((AppState) -> AppState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Правила маршрутизации (${appState.routeRules.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(appState.routeRules, key = { it.id }) { rule ->
                val outboundColor = when (rule.outboundTag.lowercase()) {
                    "direct" -> Color(0xFF4CAF50)
                    "block" -> Color(0xFFE53935)
                    else -> MiuixTheme.colorScheme.primary
                }

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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(outboundColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = rule.outboundTag.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = outboundColor,
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = rule.remarks.ifBlank { "Правило #${rule.id}" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }

                            val conditions = mutableListOf<String>()
                            if (rule.domain.isNotEmpty()) conditions.add("Домены: ${rule.domain.joinToString(", ")}")
                            if (rule.ip.isNotEmpty()) conditions.add("IP: ${rule.ip.joinToString(", ")}")
                            if (rule.port.isNotBlank()) conditions.add("Порт: ${rule.port}")
                            if (rule.network.isNotBlank()) conditions.add("Сеть: ${rule.network}")

                            if (conditions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = conditions.joinToString(" • "),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 2,
                                )
                            }
                        }

                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { checked ->
                                onUpdateAppState { current ->
                                    val updatedRules = current.routeRules.map {
                                        if (it.id == rule.id) it.copy(enabled = checked) else it
                                    }
                                    current.copy(routeRules = updatedRules)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
