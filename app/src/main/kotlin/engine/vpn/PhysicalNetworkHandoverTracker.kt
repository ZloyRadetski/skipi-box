// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

/**
 * Keeps handover detection independent from Android callbacks so duplicate
 * capability events never restart the VPN runtime more than once.
 */
internal class PhysicalNetworkHandoverTracker<T> {
    private var initialized = false
    private var currentNetwork: T? = null
    private var recoveryPending = false

    fun reset(network: T?) {
        initialized = true
        currentNetwork = network
        recoveryPending = false
    }

    fun clear() {
        initialized = false
        currentNetwork = null
        recoveryPending = false
    }

    /**
     * Returns true only after a usable network replaces the previous one. A
     * loss itself does not restart the runtime; recovery waits for an upstream.
     */
    fun observe(network: T?): Boolean {
        if (!initialized) {
            reset(network)
            return false
        }
        if (currentNetwork == network) return false

        val previousNetwork = currentNetwork
        currentNetwork = network
        recoveryPending = network != null && previousNetwork != network
        return recoveryPending
    }

    /**
     * A deferred external VPN restart must not discard a detected handover.
     * The service acknowledges it only after its own recovery has started.
     */
    fun hasPendingRecovery(network: T?): Boolean {
        return network != null && currentNetwork == network && recoveryPending
    }

    fun acknowledgeRecovery(network: T?) {
        if (network != null && currentNetwork == network) {
            recoveryPending = false
        }
    }
}
