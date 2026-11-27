// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import kotlin.test.Test
import kotlin.test.assertEquals

class MultilinkOverviewTest {
    private val eth = MultilinkLink(id = "{eth}", name = "Ethernet 2", state = "up", flows = 8, linkIn = 750_000.0, linkOut = 50_000.0)
    private val wifi = MultilinkLink(id = "{wifi}", name = "Wi-Fi", state = "up", flows = 4, linkIn = 250_000.0, linkOut = 150_000.0)
    private val merging = MultilinkStatus(enabled = true, merging = true, links = listOf(eth, wifi))
    private val on = MultilinkSettings(enabled = true)

    @Test
    fun theStateFollowsTheSwitchTheTunnelAndTheLinks() {
        assertEquals(MultilinkPhase.Off, multilinkOverview(MultilinkSettings(), merging, tunnelRunning = true).phase)
        assertEquals(MultilinkPhase.Waiting, multilinkOverview(on, MultilinkStatus(), tunnelRunning = false).phase)
        assertEquals(MultilinkPhase.Waiting, multilinkOverview(on, MultilinkStatus(), tunnelRunning = true).phase)
        assertEquals(MultilinkPhase.OneLink, multilinkOverview(on, merging.copy(merging = false), tunnelRunning = true).phase)
        assertEquals(MultilinkPhase.Merging, multilinkOverview(on, merging, tunnelRunning = true).phase)
        assertEquals(MultilinkPhase.Unsupported, multilinkOverview(on, merging, tunnelRunning = true, coreSupported = false).phase)
    }

    @Test
    fun totalsAddUpTheUsedLinks() {
        val overview = multilinkOverview(on, merging, tunnelRunning = true)
        assertEquals(8.0, overview.downMbps, 0.001)
        assertEquals(1.6, overview.upMbps, 0.001)
        assertEquals(12, overview.flows)
        assertEquals(2, overview.usedLinks)
    }

    /** Each link's part of the traffic, both directions together, so the bar shows who carries what. */
    @Test
    fun sharesAreEachLinksPartOfTheTraffic() {
        val shares = multilinkOverview(on, merging, tunnelRunning = true).shares
        assertEquals(listOf("{eth}", "{wifi}"), shares.map { it.id })
        assertEquals(0.6666, shares[0].fraction, 0.001)
        assertEquals(0.3333, shares[1].fraction, 0.001)
    }

    @Test
    fun anIdleTunnelSplitsTheBarEvenly() {
        val idle = merging.copy(links = listOf(eth.copy(linkIn = 0.0, linkOut = 0.0), wifi.copy(linkIn = 0.0, linkOut = 0.0)))
        assertEquals(listOf(0.5, 0.5), multilinkOverview(on, idle, tunnelRunning = true).shares.map { it.fraction })
    }

    @Test
    fun excludedAndOfflineLinksTakeNoPartOfTheBar() {
        val status = merging.copy(links = listOf(eth, wifi, MultilinkLink(id = "{usb}", name = "USB", state = "no internet")))
        val overview = multilinkOverview(on.withLinkUsed("{wifi}", used = false), status, tunnelRunning = true)
        assertEquals(listOf("{eth}"), overview.shares.map { it.id })
        assertEquals(1, overview.usedLinks)
    }
}
