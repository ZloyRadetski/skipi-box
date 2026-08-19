// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.effects

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
