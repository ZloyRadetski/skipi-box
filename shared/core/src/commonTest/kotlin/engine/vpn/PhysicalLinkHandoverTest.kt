// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PhysicalLinkHandoverTest {
    @Test
    fun sameNetworkWithANewLinkRequestsARuntimeReload() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("cellular", link = "rmnet0|10.1.2.3")

        assertTrue(tracker.observe("cellular", link = "rmnet1|10.9.9.9"))
        assertTrue(tracker.hasPendingRecovery("cellular"))
        tracker.acknowledgeRecovery("cellular")
        assertFalse(tracker.observe("cellular", link = "rmnet1|10.9.9.9"))
    }

    @Test
    fun sameNetworkAndSameLinkDoesNothing() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("cellular", link = "rmnet0|10.1.2.3")

        assertFalse(tracker.observe("cellular", link = "rmnet0|10.1.2.3"))
        assertFalse(tracker.hasPendingRecovery("cellular"))
    }

    @Test
    fun anUnknownLinkIsAdoptedWithoutAReload() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("cellular")

        assertFalse(tracker.observe("cellular", link = "rmnet0|10.1.2.3"))
        assertTrue(tracker.observe("cellular", link = "rmnet0|10.4.4.4"))
    }

    @Test
    fun aMissingLinkReadingNeverTriggersAReload() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("cellular", link = "rmnet0|10.1.2.3")

        assertFalse(tracker.observe("cellular", link = null))
        assertFalse(tracker.observe("cellular", link = "rmnet0|10.1.2.3"))
    }

    @Test
    fun aNewNetworkStillRequestsAReloadRegardlessOfLink() {
        val tracker = PhysicalNetworkHandoverTracker<String>()
        tracker.reset("wifi", link = "wlan0|192.168.1.5")

        assertTrue(tracker.observe("cellular", link = "wlan0|192.168.1.5"))
    }

    @Test
    fun fingerprintIgnoresAddressOrderAndLinkLocalAddresses() {
        val v4 = byteArrayOf(10, 1, 2, 3)
        val v6 = ByteArray(16) { index -> if (index == 0) 0x20 else index.toByte() }
        val linkLocal6 = ByteArray(16).also { it[0] = 0xfe.toByte(); it[1] = 0x80.toByte(); it[15] = 1 }
        val linkLocal4 = byteArrayOf(169.toByte(), 254.toByte(), 1, 1)

        val one = physicalLinkFingerprint("rmnet0", listOf(v4, v6, linkLocal6, linkLocal4))
        val two = physicalLinkFingerprint("rmnet0", listOf(linkLocal4, v6, v4))

        assertEquals(one, two)
    }

    @Test
    fun fingerprintSurvivesIpv6PrivacyAddressRotation() {
        val first = ByteArray(16) { index -> if (index < 8) (index + 1).toByte() else 0x11 }
        val rotated = ByteArray(16) { index -> if (index < 8) (index + 1).toByte() else 0x77 }

        assertEquals(
            physicalLinkFingerprint("rmnet0", listOf(first)),
            physicalLinkFingerprint("rmnet0", listOf(rotated)),
        )
    }

    @Test
    fun fingerprintChangesWithInterfaceIpv4OrIpv6Prefix() {
        val v4 = byteArrayOf(10, 1, 2, 3)
        val base = physicalLinkFingerprint("rmnet0", listOf(v4))

        assertNotEquals(base, physicalLinkFingerprint("rmnet1", listOf(v4)))
        assertNotEquals(base, physicalLinkFingerprint("rmnet0", listOf(byteArrayOf(10, 1, 2, 4))))
        val prefixA = ByteArray(16) { if (it < 8) 1 else 0 }
        val prefixB = ByteArray(16) { if (it < 8) 2 else 0 }
        assertNotEquals(
            physicalLinkFingerprint("rmnet0", listOf(prefixA)),
            physicalLinkFingerprint("rmnet0", listOf(prefixB)),
        )
    }
}
