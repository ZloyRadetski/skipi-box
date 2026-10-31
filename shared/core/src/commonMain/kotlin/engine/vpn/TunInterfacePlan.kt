// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import engine.network.NetworkCidrAddress

/** Interface name Android already gives its TUN; Desktop uses the same one. */
const val TunInterfaceName = "skipi0"

/**
 * What a host brings up for the TUN settings: the interface, its addresses and the routes that send
 * all traffic into it. Android's VpnService does this itself; desktop hosts apply the plan through
 * the operating system's network manager.
 *
 * The full tunnel is two half-routes per family rather than a default route, so the system default
 * route stays in place underneath and disappears with the interface.
 */
data class TunInterfacePlan(
    val name: String,
    val mtu: Int,
    val addresses: List<NetworkCidrAddress>,
    val routes: List<NetworkCidrAddress>,
)

fun AppState.toTunInterfacePlan(): TunInterfacePlan {
    val options = toTunOptions()
    return TunInterfacePlan(
        name = TunInterfaceName,
        mtu = options.mtu,
        addresses = buildList {
            add(options.ipv4Address)
            if (enableIpv6) add(options.ipv6Address)
        },
        routes = buildList {
            add(NetworkCidrAddress("0.0.0.0", 1))
            add(NetworkCidrAddress("128.0.0.0", 1))
            if (enableIpv6) {
                add(NetworkCidrAddress("::", 1))
                add(NetworkCidrAddress("8000::", 1))
            }
        },
    )
}
