// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.AppState
import app.R
import app.activeTunnelTargetDisplayName
import engine.stats.CoreTrafficStatsSampler
import engine.stats.ProxyTrafficStatsRuntime
import kotlinx.coroutines.flow.collect
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
) : State<ActiveTunnelRuntimeSample?> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(
        initialValue = null,
        context,
        proxyRunning,
        lifecycleOwner,
    ) {
        if (!proxyRunning) {
            value = null
        } else {
            // Home can retain pager pages while the activity is stopped. A
            // lifecycle-bound lease makes that invisible composition free: it
            // neither wakes the CPU nor keeps the shared sampler alive.
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                CoreTrafficStatsSampler.acquire(context).use {
                    CoreTrafficStatsSampler.samples.collect { sample ->
                        value = sample?.let { current ->
                            ActiveTunnelRuntimeSample(
                                runtime = current.runtime,
                                outboundTag = current.activeOutboundTag,
                            )
                        }
                    }
                }
            }
        }
    }
}
