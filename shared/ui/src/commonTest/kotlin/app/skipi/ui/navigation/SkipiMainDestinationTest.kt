// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class SkipiMainDestinationTest {
    @Test
    fun pageIndicesRemainStableAcrossHosts() {
        assertEquals(SkipiMainDestination.Proxy, SkipiMainDestination.fromPageIndex(0))
        assertEquals(SkipiMainDestination.Configs, SkipiMainDestination.fromPageIndex(1))
        assertEquals(SkipiMainDestination.Settings, SkipiMainDestination.fromPageIndex(2))
    }

    @Test
    fun unknownPageFallsBackToProxy() {
        assertEquals(SkipiMainDestination.Proxy, SkipiMainDestination.fromPageIndex(99))
    }
}
