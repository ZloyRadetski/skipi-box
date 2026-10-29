// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The address other devices on the LAN use to reach the local proxy. */
class LocalNetworkAddressesTest {
    @Test
    fun hostAddressesAreDistinctNonLoopbackIpv4() {
        val addresses = localNetworkIpAddresses()

        assertEquals(addresses.distinct(), addresses)
        assertTrue(addresses.none { it.startsWith("127.") }, "addresses: $addresses")
        assertTrue(
            addresses.all { address -> Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(address) },
            "addresses: $addresses",
        )
    }

    @Test
    fun interfacesOfTheTunnelItselfAreIgnored() {
        assertTrue(isLocalNetworkInterfaceName("wlan0"))
        assertTrue(isLocalNetworkInterfaceName("Ethernet"))
        assertEquals(false, isLocalNetworkInterfaceName("skipi0"))
        assertEquals(false, isLocalNetworkInterfaceName("tun0"))
    }
}
