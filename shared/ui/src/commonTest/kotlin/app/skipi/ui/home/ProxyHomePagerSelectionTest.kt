// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyHomePagerSelectionTest {
    private val groups = listOf("all", "sub-1", "sub-2")

    @Test
    fun settledPageThatDiffersFromSelectionIsSelected() {
        assertEquals("sub-2", pagerSettledGroupToSelect(groups, settledIndex = 2, viewportWidth = 800, selectedGroupId = "sub-1"))
    }

    @Test
    fun settledPageMatchingSelectionSelectsNothing() {
        assertNull(pagerSettledGroupToSelect(groups, settledIndex = 1, viewportWidth = 800, selectedGroupId = "sub-1"))
    }

    @Test
    fun collapsedViewportNeverOverridesSelection() {
        // A minimized window lays the pager out at zero width; whatever page it reports then is not a user choice.
        assertNull(pagerSettledGroupToSelect(groups, settledIndex = 0, viewportWidth = 0, selectedGroupId = "sub-2"))
    }

    @Test
    fun outOfRangeSettledPageSelectsNothing() {
        assertNull(pagerSettledGroupToSelect(groups, settledIndex = 7, viewportWidth = 800, selectedGroupId = "sub-1"))
    }
}
