// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.AppState
import app.R
import app.activeTunnelTargetDisplayName
import engine.stats.ProxyTrafficStatsRuntime
import engine.stats.XrayStatsClientSession
import engine.stats.XrayTrafficBytes
import engine.stats.maxTrafficDeltaComparedTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class ActiveTunnelRuntimeSample(
    val runtime: ProxyTrafficStatsRuntime,
    val outboundTag: String?,
)

/**
 * Shows the target frozen into the currently running tunnel. When Xray's
 * outbound counters are available, a strategy group's member is resolved from
 * the outbound that carried traffic in the latest sampling interval.
 */
@Composable
internal fun ActiveTunnelServerHomeLabel(
    appState: AppState,
    proxyRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val sample by produceActiveTunnelRuntimeSample(context, proxyRunning)
    val directName = stringResource(R.string.routing_outbound_direct)
    val blockName = stringResource(R.string.routing_outbound_block)
    val activeName = remember(appState, sample, directName, blockName) {
        appState.activeTunnelTargetDisplayName(
            runtime = sample?.runtime,
            activeOutboundTag = sample?.outboundTag,
            directName = directName,
            blockName = blockName,
        )
    }
    Text(
        text = if (proxyRunning) {
            stringResource(R.string.proxy_active_server_home_value, activeName)
        } else {
            stringResource(R.string.proxy_active_server_home_stopped)
        },
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier,
    )
}

@Composable
internal fun produceActiveTunnelRuntimeSample(
    context: Context,
    proxyRunning: Boolean,
) = produceState<ActiveTunnelRuntimeSample?>(
    initialValue = null,
    context,
    proxyRunning,
) {
    var previousRuntime: ProxyTrafficStatsRuntime? = null
    var previousTotals = emptyMap<String, XrayTrafficBytes>()
    var lastActiveOutboundTag: String? = null
    // One reusable stats channel for the whole sampling loop instead of a new
    // gRPC handshake on every tick.
    XrayStatsClientSession(context).use { session ->
        while (true) {
            if (!proxyRunning) {
                previousRuntime = null
                previousTotals = emptyMap()
                lastActiveOutboundTag = null
                value = null
            } else {
                val totals = withContext(Dispatchers.IO) {
                    runCatching {
                        session.withClient { client -> client.queryOutboundTraffic(reset = false) }
                    }.getOrNull()
                }
                val runtime = session.lastRuntime
                if (totals != null && runtime != null) {
                    if (runtime != previousRuntime) {
                        previousRuntime = runtime
                        previousTotals = emptyMap()
                        lastActiveOutboundTag = null
                    }
                    totals.maxTrafficDeltaComparedTo(previousTotals, currentActiveTag = lastActiveOutboundTag)?.let { tag ->
                        lastActiveOutboundTag = tag
                    }
                    previousTotals = totals
                    value = ActiveTunnelRuntimeSample(runtime, lastActiveOutboundTag)
                } else {
                    // Tunnel stopped or stats unreachable; reset the baseline.
                    previousRuntime = null
                    previousTotals = emptyMap()
                    lastActiveOutboundTag = null
                    value = null
                }
            }
            delay(ActiveTunnelSampleIntervalMillis)
        }
    }
}

private const val ActiveTunnelSampleIntervalMillis = 1_000L
