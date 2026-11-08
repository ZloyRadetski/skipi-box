// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopTrainingExitTest {
    @Test
    fun theBuildsTrainingRunExitsAfterTheGivenDelay() {
        assertEquals(8_000L, trainingExitDelayMillis("8000"))
    }

    @Test
    fun anOrdinaryStartNeverExitsOnItsOwn() {
        assertNull(trainingExitDelayMillis(null))
        assertNull(trainingExitDelayMillis(""))
        assertNull(trainingExitDelayMillis("soon"))
        assertNull(trainingExitDelayMillis("0"))
        assertNull(trainingExitDelayMillis("-5"))
    }
}
