// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import engine.network.NetworkDefaults

internal data class VpnRoute(val address: String, val prefixLength: Int)

/**
 * Routes handed to the system. Both families are always captured: a tunnel
 * without a `::/0` route lets Android send IPv6 (and its DNS) around the VPN
 * through the physical network. When IPv6 is disabled the family has no tunnel
 * address, so the captured packets are dropped instead of leaking.
 */
internal fun planVpnRoutes(): List<VpnRoute> = listOf(
    VpnRoute(NetworkDefaults.IPV4_ANY_ADDRESS, 0),
    VpnRoute(NetworkDefaults.IPV6_ANY_ADDRESS, 0),
)

internal enum class HandoverRecoveryFailureAction {
    /** Tear the VPN down; traffic returns to the physical network. */
    StopVpn,

    /** Keep the TUN established without a core so traffic is dropped, not leaked. */
    HoldBlackhole,
}

internal fun handoverRecoveryFailureAction(enableKillSwitch: Boolean): HandoverRecoveryFailureAction {
    return if (enableKillSwitch) HandoverRecoveryFailureAction.HoldBlackhole else HandoverRecoveryFailureAction.StopVpn
}
