// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalNetworkAvailabilityTrackerTest {
    @Test
    fun `network remains available until the last callback loss`() {
        val tracker = PhysicalNetworkAvailabilityTracker<String>()

        tracker.markAvailable("wifi")
        tracker.markAvailable("cellular")
        tracker.markLost("wifi")

        assertTrue(tracker.hasAvailableNetwork())

        tracker.markLost("cellular")

        assertFalse(tracker.hasAvailableNetwork())
    }

    @Test
    fun `clear removes state from a previous observer lifecycle`() {
        val tracker = PhysicalNetworkAvailabilityTracker<String>()

        tracker.markAvailable("wifi")
        tracker.clear()

        assertFalse(tracker.hasAvailableNetwork())
    }
}
