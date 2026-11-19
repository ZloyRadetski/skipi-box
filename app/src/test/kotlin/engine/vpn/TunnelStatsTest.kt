// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TunnelStatsTest {
    @Test
    fun parsesCoreJsonIntoTheOldLongArrayOrder() {
        val stats = parseTunnelStats("""{"txPackets":1,"txBytes":2,"rxPackets":3,"rxBytes":4}""")
        assertEquals(TunnelStats(txPackets = 1, txBytes = 2, rxPackets = 3, rxBytes = 4), stats)
        // The old TProxyGetStats LongArray order: tx packets, tx bytes, rx packets, rx bytes.
        assertEquals(listOf(1L, 2L, 3L, 4L), stats!!.toLongArray().toList())
    }

    @Test
    fun missingFieldsAreZeroAndUnknownKeysIgnored() {
        assertEquals(TunnelStats(rxBytes = 9), parseTunnelStats("""{"rxBytes":9,"extra":true}"""))
    }

    @Test
    fun blankOrBrokenJsonIsNoStats() {
        assertNull(parseTunnelStats(""))
        assertNull(parseTunnelStats("not json"))
    }
}
