// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeDisplaySettingsContractTest {
    @Test
    fun serverSearchIsOffByDefault() {
        assertEquals(false, HomeDisplaySettings().showServerSearch)
    }

    @Test
    fun keepsAndroidHomeOrdinalsRawIncludingUnknownValues() {
        val sortValues = listOf(0, 1, 2, 4)

        sortValues.forEach { sort ->
            val settings = HomeDisplaySettings(
                proxyServerListSort = sort,
                enableAllProxyGroup = true,
                showTunnelMemoryOnHome = true,
            )

            assertEquals(sort, settings.proxyServerListSort)
            assertEquals(true, settings.enableAllProxyGroup)
            assertEquals(true, settings.showTunnelMemoryOnHome)
        }
    }

    @Test
    fun absentAndroidHomeFieldsRemainAbsentForHostAdaptersToPreserve() {
        val settings = HomeDisplaySettings()

        assertNull(settings.proxyServerListSort)
        assertNull(settings.enableAllProxyGroup)
        assertNull(settings.showTunnelMemoryOnHome)
    }
}
