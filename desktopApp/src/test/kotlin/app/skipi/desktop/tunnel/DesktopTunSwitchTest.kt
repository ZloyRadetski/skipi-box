// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopTunSwitchTest {
    @Test
    fun turningTunOnAsANonAdministratorAsksForElevationInsteadOfSaving() {
        assertEquals(TunSwitchChange.RequestElevation, tunSwitchChange(enabled = true, needsElevation = true))
    }

    @Test
    fun turningTunOnWithEnoughRightsJustSaves() {
        assertEquals(TunSwitchChange.Save, tunSwitchChange(enabled = true, needsElevation = false))
    }

    @Test
    fun turningTunOffAlwaysSaves() {
        assertEquals(TunSwitchChange.Save, tunSwitchChange(enabled = false, needsElevation = true))
        assertEquals(TunSwitchChange.Save, tunSwitchChange(enabled = false, needsElevation = false))
    }

    @Test
    fun theSwitchStaysUsableWhenOnlyElevationIsMissing() {
        // Unavailable for another reason: dead switch, unless the user already turned it on and must be able to turn it off.
        assertEquals(true, tunSwitchEnabled(available = false, enabled = false, needsElevation = true))
        assertEquals(false, tunSwitchEnabled(available = false, enabled = false, needsElevation = false))
        assertEquals(true, tunSwitchEnabled(available = false, enabled = true, needsElevation = false))
        assertEquals(true, tunSwitchEnabled(available = true, enabled = false, needsElevation = false))
    }

    @Test
    fun theSwitchShowsOnOnlyWhenTunIsAvailableAndEnabled() {
        assertEquals(false, tunSwitchChecked(available = false, enabled = true))
        assertEquals(true, tunSwitchChecked(available = true, enabled = true))
        assertEquals(false, tunSwitchChecked(available = true, enabled = false))
    }
}
