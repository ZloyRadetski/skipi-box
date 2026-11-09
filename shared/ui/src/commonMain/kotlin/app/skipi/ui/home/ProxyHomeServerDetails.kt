// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.ProxyServerSummary
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_latency_milliseconds
import app.skipi.ui.resources.proxy_server_list_latency_failed
import app.skipi.ui.resources.home_server_address
import app.skipi.ui.resources.home_server_detail_hint
import app.skipi.ui.resources.home_server_detail_title
import app.skipi.ui.resources.home_server_latency
import app.skipi.ui.resources.home_server_protocol
import app.skipi.ui.resources.home_server_test_latency
import app.skipi.ui.resources.home_server_testing_latency
import app.skipi.ui.resources.home_server_transport
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SelectedServerDetailsPane(
    state: ProxyHomeUiState,
    server: ProxyServerSummary?,
    onTest: (ProxyServerSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (server == null) return
    val latencyMs = server.latencyMs
    val latency = when {
        server.latencyTesting -> stringResource(Res.string.home_server_testing_latency)
        latencyMs != null -> stringResource(Res.string.home_latency_milliseconds, latencyMs)
        server.latencyError -> server.latencyErrorText?.takeIf(String::isNotBlank)
            ?: stringResource(Res.string.proxy_server_list_latency_failed)
        else -> "\u2014"
    }
    Column(modifier = modifier.fillMaxWidth()) {
        FlatDetailRow(stringResource(Res.string.home_server_address), server.address)
        FlatDetailRow(stringResource(Res.string.home_server_protocol), server.protocol)
        server.transport?.takeIf(String::isNotBlank)?.let { transport ->
            FlatDetailRow(stringResource(Res.string.home_server_transport), transport)
        }
        FlatDetailRow(stringResource(Res.string.home_server_latency), latency)
        Spacer(Modifier.height(SkipiTheme.spacing.small))
        val canTest = server.canTest && !server.latencyTesting && canUseHomeAction(state, ProxyHomeActionId.TestServer)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SkipiTheme.colors.surfaceVariant)
                .clickable(enabled = canTest) { onTest(server) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.home_server_test_latency),
                color = if (canTest) SkipiTheme.colors.onSurface else SkipiTheme.colors.onSurfaceVariant,
                style = SkipiTheme.typography.labelLarge,
                fontWeight = themedFontWeight(FontWeight.SemiBold),
            )
        }
    }
}

@Composable
internal fun FlatDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.medium),
    ) {
        Text(
            text = label,
            color = SkipiTheme.colors.onSurfaceVariant,
            style = SkipiTheme.typography.bodySmall,
            modifier = Modifier.widthIn(min = 76.dp),
        )
        Text(
            text = value,
            color = SkipiTheme.colors.onSurface,
            style = SkipiTheme.typography.bodySmall,
            fontWeight = themedFontWeight(FontWeight.Medium),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun LegacyServerDetailsPane(
    state: ProxyHomeUiState,
    server: ProxyServerSummary?,
    onTest: (ProxyServerSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = SkipiTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = SkipiTheme.colors.surface),
        border = BorderStroke(1.dp, SkipiTheme.colors.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SkipiTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(Res.string.home_server_detail_title),
                color = SkipiTheme.colors.onSurface,
                style = SkipiTheme.typography.titleLarge,
                fontWeight = themedFontWeight(FontWeight.Bold),
            )
            if (server == null) {
                Text(
                    text = stringResource(Res.string.home_server_detail_hint),
                    color = SkipiTheme.colors.onSurfaceVariant,
                    style = SkipiTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = server.title,
                    color = SkipiTheme.colors.onSurface,
                    style = SkipiTheme.typography.headlineSmall,
                    fontWeight = themedFontWeight(FontWeight.SemiBold),
                )
                ServerDetailValue(
                    label = stringResource(Res.string.home_server_address),
                    value = server.address,
                )
                ServerDetailValue(
                    label = stringResource(Res.string.home_server_protocol),
                    value = server.protocol,
                )
                server.transport?.takeIf(String::isNotBlank)?.let { transport ->
                    ServerDetailValue(
                        label = stringResource(Res.string.home_server_transport),
                        value = transport,
                    )
                }
                val latencyMs = server.latencyMs
                val latency = when {
                    server.latencyTesting -> stringResource(Res.string.home_server_testing_latency)
                    latencyMs != null -> stringResource(Res.string.home_latency_milliseconds, latencyMs)
                    server.latencyError -> server.latencyErrorText?.takeIf(String::isNotBlank)
                        ?: stringResource(Res.string.proxy_server_list_latency_failed)
                    else -> "—"
                }
                ServerDetailValue(
                    label = stringResource(Res.string.home_server_latency),
                    value = latency,
                )
                OutlinedButton(
                    onClick = { onTest(server) },
                    enabled = server.canTest && !server.latencyTesting && canUseHomeAction(state, ProxyHomeActionId.TestServer),
                ) {
                    Text(stringResource(Res.string.home_server_test_latency))
                }
            }
        }
    }
}

@Composable
internal fun ServerDetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.extraSmall)) {
        Text(
            text = label,
            color = SkipiTheme.colors.onSurfaceVariant,
            style = SkipiTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = SkipiTheme.colors.onSurface,
            style = SkipiTheme.typography.bodyLarge,
        )
    }
}
