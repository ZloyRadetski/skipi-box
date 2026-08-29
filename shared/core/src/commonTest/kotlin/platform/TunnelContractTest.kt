// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TunnelContractTest {
    @Test
    fun disconnectedSnapshotIsSafeBeforeAnyPlatformAdapterStarts() {
        val snapshot = TunnelSnapshot()

        assertEquals(TunnelPhase.Disconnected, snapshot.phase)
        assertEquals(TunnelTraffic(), snapshot.traffic)
        assertFalse(snapshot.failure?.recoverable ?: false)
    }
}
