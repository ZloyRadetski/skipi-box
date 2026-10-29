// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import engine.stats.CoreTrafficStatsSampler
import engine.stats.ProxyTrafficStatsRuntime
import kotlinx.coroutines.flow.collect

internal data class ActiveTunnelRuntimeSample(
    val runtime: ProxyTrafficStatsRuntime,
    val outboundTag: String?,
)

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
