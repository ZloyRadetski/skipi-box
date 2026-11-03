// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnLeakProtectionTest {

    @Test
    fun routesCaptureBothIpFamiliesEvenWhenIpv6IsDisabled() {
        // Without a ::/0 route Android sends IPv6 straight out of the physical
        // network, bypassing the tunnel and leaking the real address and DNS.
        val routes = planVpnRoutes()

        assertTrue(routes.contains(VpnRoute("0.0.0.0", 0)))
        assertTrue(routes.contains(VpnRoute("::", 0)))
    }

    @Test
    fun routesContainNoDuplicates() {
        val routes = planVpnRoutes()

        assertEquals(routes.distinct(), routes)
    }

    @Test
    fun failedHandoverRecoveryKeepsTheTunnelBlackholedWhenKillSwitchIsOn() {
        assertEquals(
            HandoverRecoveryFailureAction.HoldBlackhole,
            handoverRecoveryFailureAction(enableKillSwitch = true),
        )
    }

    @Test
    fun failedHandoverRecoveryStopsTheVpnWhenKillSwitchIsOff() {
        assertEquals(
            HandoverRecoveryFailureAction.StopVpn,
            handoverRecoveryFailureAction(enableKillSwitch = false),
        )
    }
}
