// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class WidgetSelectionTest {
    @Test
    fun `cycles previous and next with wrap around`() {
        val optionIds = listOf(10, 20, 30)

        assertEquals(30, cycleWidgetOptionId(optionIds, 10, WidgetCycleDirection.Previous))
        assertEquals(20, cycleWidgetOptionId(optionIds, 10, WidgetCycleDirection.Next))
        assertEquals(10, cycleWidgetOptionId(optionIds, 30, WidgetCycleDirection.Next))
    }

    @Test
    fun `uses the appropriate edge when the current option disappeared`() {
        val optionIds = listOf(10, 20, 30)

        assertEquals(30, cycleWidgetOptionId(optionIds, 99, WidgetCycleDirection.Previous))
        assertEquals(10, cycleWidgetOptionId(optionIds, 99, WidgetCycleDirection.Next))
    }

    @Test
    fun `returns null when there are no options`() {
        assertNull(cycleWidgetOptionId(emptyList(), 10, WidgetCycleDirection.Next))
    }
}
