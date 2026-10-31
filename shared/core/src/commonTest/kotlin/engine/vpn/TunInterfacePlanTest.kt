// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import engine.network.NetworkCidrAddress
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The interface a desktop host brings up for the shared TUN settings. Addresses, MTU and IPv6
 * handling are exactly the Android ones; the full-tunnel routes are the split default route that
 * overrides the system default without removing it.
 */
class TunInterfacePlanTest {
    @Test
    fun defaultSettingsGiveAnIpv4OnlyFullTunnel() {
        val plan = AppState().toTunInterfacePlan()

        assertEquals("skipi0", plan.name)
        assertEquals(1400, plan.mtu)
        assertEquals(listOf(NetworkCidrAddress("172.19.0.1", 30)), plan.addresses)
        assertEquals(
            listOf(NetworkCidrAddress("0.0.0.0", 1), NetworkCidrAddress("128.0.0.0", 1)),
            plan.routes,
        )
    }

    @Test
    fun enabledIpv6AddsTheAddressAndTheIpv6HalfRoutes() {
        val plan = AppState(enableIpv6 = true).toTunInterfacePlan()

        assertEquals(
            listOf(NetworkCidrAddress("172.19.0.1", 30), NetworkCidrAddress("fdfe:dcba:9876::1", 126)),
            plan.addresses,
        )
        assertEquals(
            listOf(
                NetworkCidrAddress("0.0.0.0", 1),
                NetworkCidrAddress("128.0.0.0", 1),
                NetworkCidrAddress("::", 1),
                NetworkCidrAddress("8000::", 1),
            ),
            plan.routes,
        )
    }

    @Test
    fun configuredValuesAreUsed() {
        val plan = AppState(
            tunMtu = "1500",
            tunIpv4Cidr = "10.20.30.1/24",
            tunIpv6Cidr = "fd00::1/64",
            enableIpv6 = true,
        ).toTunInterfacePlan()

        assertEquals(1500, plan.mtu)
        assertEquals(
            listOf(NetworkCidrAddress("10.20.30.1", 24), NetworkCidrAddress("fd00::1", 64)),
            plan.addresses,
        )
    }

    @Test
    fun invalidValuesFallBackToTheAndroidDefaults() {
        val plan = AppState(
            tunMtu = "70000",
            tunIpv4Cidr = "fd00::1/64",
            tunIpv6Cidr = "10.0.0.1/24",
            enableIpv6 = true,
        ).toTunInterfacePlan()

        assertEquals(1400, plan.mtu)
        assertEquals(
            listOf(NetworkCidrAddress("172.19.0.1", 30), NetworkCidrAddress("fdfe:dcba:9876::1", 126)),
            plan.addresses,
        )
    }

    @Test
    fun mtuBelowTheMinimumFallsBackToTheDefault() {
        assertEquals(1400, AppState(tunMtu = "1279").toTunInterfacePlan().mtu)
        assertEquals(1280, AppState(tunMtu = "1280").toTunInterfacePlan().mtu)
    }
}
