// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.effects

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.AppState
import features.config.TrafficConfigNetworkTransportCellular
import features.config.TrafficConfigNetworkTransportWifi

/** Chooses the first explicitly configured profile for the currently active transport. */
internal fun AppState.matchingNetworkConfigId(capabilities: NetworkCapabilities): Int? {
    return trafficConfigs.firstOrNull { config ->
        val activation = config.networkActivation
        activation.enabled && when (activation.transport) {
            TrafficConfigNetworkTransportWifi -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            TrafficConfigNetworkTransportCellular -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            else -> false
        }
    }?.id
}

/** Resolves and switches to the network-activated traffic profile matching current connectivity before starting VPN. */
internal fun AppState.resolveActiveNetworkConfig(context: Context): AppState {
    return runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching this
        val capabilities = features.networkautomation.engine.NetworkAutomationEvaluator.getActivePhysicalCapabilities(context, null)
            ?: return@runCatching this
        val matchingId = matchingNetworkConfigId(capabilities) ?: return@runCatching this
        if (activeTrafficConfigId != matchingId && trafficConfigs.any { it.id == matchingId }) {
            copy(activeTrafficConfigId = matchingId)
        } else {
            this
        }
    }.getOrElse { this }
}
