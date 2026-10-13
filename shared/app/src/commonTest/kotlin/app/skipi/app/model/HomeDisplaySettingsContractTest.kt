// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeDisplaySettingsContractTest {
    @Test
    fun keepsAndroidHomeOrdinalsRawIncludingUnknownValues() {
        val sortValues = listOf(0, 1, 2, 4)

        sortValues.forEach { sort ->
            val settings = HomeDisplaySettings(
                proxyServerListSort = sort,
                proxyServerListLayout = 3,
                enableAllProxyGroup = true,
                showTunnelMemoryOnHome = true,
            )

            assertEquals(sort, settings.proxyServerListSort)
            assertEquals(3, settings.proxyServerListLayout)
            assertEquals(true, settings.enableAllProxyGroup)
            assertEquals(true, settings.showTunnelMemoryOnHome)
        }
    }

    @Test
    fun absentAndroidHomeFieldsRemainAbsentForHostAdaptersToPreserve() {
        val settings = HomeDisplaySettings()

        assertNull(settings.proxyServerListSort)
        assertNull(settings.proxyServerListLayout)
        assertNull(settings.enableAllProxyGroup)
        assertNull(settings.showTunnelMemoryOnHome)
    }
}
