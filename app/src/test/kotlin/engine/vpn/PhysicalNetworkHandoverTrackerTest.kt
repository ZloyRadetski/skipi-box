// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalNetworkHandoverTrackerTest {
    @Test
    fun `initial network and duplicate callback do not reload runtime`() {
        val tracker = PhysicalNetworkHandoverTracker<String>()

        tracker.reset("wifi")

        assertFalse(tracker.observe("wifi"))
        assertFalse(tracker.observe("wifi"))
    }

    @Test
    fun `wifi to cellular requests exactly one runtime reload`() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("wifi")

        assertTrue(tracker.observe("cellular"))
        tracker.acknowledgeRecovery("cellular")
        assertFalse(tracker.observe("cellular"))
    }

    @Test
    fun `deferred recovery remains pending until the service acknowledges it`() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("wifi")

        assertTrue(tracker.observe("cellular"))
        assertTrue(tracker.hasPendingRecovery("cellular"))
        assertFalse(tracker.observe("cellular"))
        assertTrue(tracker.hasPendingRecovery("cellular"))

        tracker.acknowledgeRecovery("cellular")

        assertFalse(tracker.hasPendingRecovery("cellular"))
    }

    @Test
    fun `losing an upstream clears a deferred recovery`() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("wifi")
        assertTrue(tracker.observe("cellular"))

        assertFalse(tracker.observe(null))
        assertFalse(tracker.hasPendingRecovery(null))
    }

    @Test
    fun `network loss waits for a replacement before reload`() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("wifi")

        assertFalse(tracker.observe(null))
        assertTrue(tracker.observe("cellular"))
    }

    @Test
    fun `upstream arriving after VPN started offline reloads runtime`() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset(null)

        assertTrue(tracker.observe("wifi"))
    }

    @Test
    fun `cleared tracker ignores a late callback from old service lifecycle`() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("wifi")
        tracker.clear()

        assertFalse(tracker.observe("cellular"))
    }
}
