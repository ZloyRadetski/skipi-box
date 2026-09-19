// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.engine

/**
 * Tracks the physical networks reported as available by the active
 * ConnectivityManager callback. It deliberately does not rely on a
 * synchronous ConnectivityManager snapshot, which can briefly be stale after
 * every network has been lost.
 */
internal class PhysicalNetworkAvailabilityTracker<T> {
    private val lock = Any()
    private val availableNetworks = mutableSetOf<T>()

    fun markAvailable(network: T) {
        synchronized(lock) {
            availableNetworks += network
        }
    }

    fun markLost(network: T) {
        synchronized(lock) {
            availableNetworks -= network
        }
    }

    fun hasAvailableNetwork(): Boolean = synchronized(lock) {
        availableNetworks.isNotEmpty()
    }

    fun clear() {
        synchronized(lock) {
            availableNetworks.clear()
        }
    }
}
