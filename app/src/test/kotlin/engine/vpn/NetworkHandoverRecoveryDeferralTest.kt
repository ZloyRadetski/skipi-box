// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkHandoverRecoveryDeferralTest {
    @Test
    fun `an older operation cannot complete a newer handover deferral`() {
        var nowMillis = 100L
        val deferral = NetworkHandoverRecoveryDeferral(
            nowMillis = { nowMillis },
            maxDeferralMillis = 1_000L,
        )

        val first = deferral.begin()
        val second = deferral.begin()
        deferral.complete(first)

        assertTrue(deferral.isPending())

        deferral.complete(second)

        assertFalse(deferral.isPending())
    }

    @Test
    fun `a stalled external operation expires and releases recovery`() {
        var nowMillis = 100L
        val deferral = NetworkHandoverRecoveryDeferral(
            nowMillis = { nowMillis },
            maxDeferralMillis = 1_000L,
        )

        deferral.begin()
        nowMillis += 999L
        assertTrue(deferral.isPending())

        nowMillis += 1L
        assertFalse(deferral.isPending())
    }
}
