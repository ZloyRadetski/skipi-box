// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteRuleOutboundChoiceTest {

    private val options = listOf(
        RouteRuleOutboundItem("proxy", "Proxy"),
        RouteRuleOutboundItem("direct", "Direct"),
        RouteRuleOutboundItem("block", "Block"),
    )

    @Test
    fun knownTagSelectsItsOptionAndKeepsLabelsInOrder() {
        val choice = RouteRuleOutboundChoice(options, selectedTag = "direct")

        assertEquals(listOf("Proxy", "Direct", "Block"), choice.labels)
        assertEquals(1, choice.selectedIndex)
    }

    @Test
    fun unknownTagStaysVisibleAsRawTagAndIsSelected() {
        // The previous menu showed the raw tag when no option matched, so a rule that
        // points at a removed server keeps displaying (and saving) its tag.
        val choice = RouteRuleOutboundChoice(options, selectedTag = "server-9")

        assertEquals(listOf("Proxy", "Direct", "Block", "server-9"), choice.labels)
        assertEquals(3, choice.selectedIndex)
        assertEquals("server-9", choice.tagAt(3))
    }

    @Test
    fun tagAtMapsIndexesBackToTagsAndIgnoresOutOfRange() {
        val choice = RouteRuleOutboundChoice(options, selectedTag = "proxy")

        assertEquals("block", choice.tagAt(2))
        assertEquals(null, choice.tagAt(-1))
        assertEquals(null, choice.tagAt(3))
    }

    @Test
    fun emptyOptionsStillExposeTheSelectedTag() {
        val choice = RouteRuleOutboundChoice(emptyList(), selectedTag = "proxy")

        assertEquals(listOf("proxy"), choice.labels)
        assertEquals(0, choice.selectedIndex)
    }
}
