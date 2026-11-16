// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopCustomColorsSwitchTest {
    private val picked = DesktopAppSettings(
        customPingFastColor = 0xFF111111L,
        customPingMediumColor = 0xFF222222L,
        customPingSlowColor = 0xFF333333L,
        customStatusStoppedColor = 0xFF444444L,
        customProtocolVlessColor = 0xFF555555L,
    )

    @Test
    fun theHomeShowsPickedPingAndProtocolColorsOnlyWhileCustomColorsAreOn() {
        val on = picked.copy(customColorsEnabled = true).toProxyHomeDisplayOptions()

        assertEquals(0xFF111111L, on.latencyFastColor)
        assertEquals(0xFF222222L, on.latencyMediumColor)
        assertEquals(0xFF333333L, on.latencySlowColor)
        assertEquals(0xFF444444L, on.latencyErrorColor)
        assertEquals(false, on.protocolColors == DesktopAppSettings().toProxyHomeDisplayOptions().protocolColors)
    }

    @Test
    fun withCustomColorsOffThePickedOnesAreIgnoredAndTheDefaultsShow() {
        val off = picked.copy(customColorsEnabled = false).toProxyHomeDisplayOptions()

        assertNull(off.latencyFastColor)
        assertNull(off.latencyMediumColor)
        assertNull(off.latencySlowColor)
        assertNull(off.latencyErrorColor)
        assertEquals(DesktopAppSettings().toProxyHomeDisplayOptions().protocolColors, off.protocolColors)
    }
}
