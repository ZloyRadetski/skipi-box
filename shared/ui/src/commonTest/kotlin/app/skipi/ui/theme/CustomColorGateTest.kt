// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomColorGateTest {
    @Test
    fun aPickedColorCountsOnlyWhileCustomColorsAreOn() {
        assertEquals(0xFF123456L, customColorIfEnabled(0xFF123456L, enabled = true))
        assertNull(customColorIfEnabled(0xFF123456L, enabled = false))
    }

    @Test
    fun noPickedColorStaysNoColorEitherWay() {
        assertNull(customColorIfEnabled(null, enabled = true))
        assertNull(customColorIfEnabled(null, enabled = false))
    }
}
