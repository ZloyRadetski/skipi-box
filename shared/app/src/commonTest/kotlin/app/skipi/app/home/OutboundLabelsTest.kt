// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

class OutboundLabelsTest {
    private val labels = OutboundLabels(
        serverNames = mapOf(1 to "Selected", 20 to "Frankfurt", 100 to "Auto"),
        selectedName = "Selected",
    )

    @Test
    fun theSelectedServerAndTheFixedOutboundsKeepTheirTags() {
        assertEquals("proxy", labels.label("proxy"))
        assertEquals("direct", labels.label("direct"))
        assertEquals("block", labels.label("block"))
    }

    @Test
    fun anotherServerIsNamedByItsRemarks() {
        assertEquals("Frankfurt", labels.label("20"))
    }

    @Test
    fun aBalancerMemberReadsGroupDashServer() {
        assertEquals("Auto - Frankfurt", labels.label("100-policy-20"))
    }

    @Test
    fun theMemberOfTheSelectedBalancerNamesTheSelectedGroup() {
        assertEquals("Selected - Frankfurt", labels.label("proxy-policy-20"))
    }

    @Test
    fun anUnknownTagStaysAsCoreReportedIt() {
        assertEquals("77", labels.label("77"))
        assertEquals("100-policy-9", labels.label("100-policy-9"))
        assertEquals("default", labels.label("default"))
    }

    @Test
    fun aProfilesServerAndBalancerMemberAreNamedToo() {
        assertEquals("Frankfurt", labels.label("skipi-server-20"))
        assertEquals("Balancer - Frankfurt", labels.label("skipi-group-member-2-20"))
        assertEquals("skipi-group-member-2-9", labels.label("skipi-group-member-2-9"))
    }
}
