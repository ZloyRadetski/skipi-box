// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteOutboundSelectorStateTest {

    @Test
    fun projectedInitialGroupControlsUnmodifiedExpansion() {
        val state = RouteOutboundSelectorState(initialSelectedTag = "Provider Node")

        assertTrue(state.isExpanded("routing-subscription-7", initiallyExpandedGroupKey = "routing-subscription-7"))
        assertFalse(state.isExpanded("routing-system-targets", initiallyExpandedGroupKey = "routing-subscription-7"))

        // The projection can change while this selector stays open. Groups without a
        // user override follow the new projection's initial selection.
        assertFalse(state.isExpanded("routing-subscription-7", initiallyExpandedGroupKey = "routing-system-targets"))
        assertTrue(state.isExpanded("routing-system-targets", initiallyExpandedGroupKey = "routing-system-targets"))
    }

    @Test
    fun explicitExpansionOverridesSurviveSelectedTagAndProjectionChanges() {
        val state = RouteOutboundSelectorState(initialSelectedTag = "Provider Node")
        state.setExpanded("routing-subscription-7", expanded = false)
        state.setExpanded("routing-system-targets", expanded = true)

        state.updateSelectedTag("DIRECT")

        assertEquals("DIRECT", state.targetTag)
        assertFalse(state.isExpanded("routing-subscription-7", initiallyExpandedGroupKey = "routing-system-targets"))
        assertTrue(state.isExpanded("routing-system-targets", initiallyExpandedGroupKey = "routing-subscription-8"))
    }

    @Test
    fun selectingRowsChangesOnlyThePendingTargetAndExternalTagChangeResetsIt() {
        val state = RouteOutboundSelectorState(initialSelectedTag = "PROXY")

        state.select("Provider Node")
        assertEquals("Provider Node", state.targetTag)

        state.select("DIRECT")
        assertEquals("DIRECT", state.targetTag)

        // Recomposition with the same selectedTag does not replace the local choice.
        state.updateSelectedTag("PROXY")
        assertEquals("DIRECT", state.targetTag)

        // A new host-supplied key matches remember(selectedTag) and resets the target.
        state.updateSelectedTag("REJECT")
        assertEquals("REJECT", state.targetTag)
    }
}
