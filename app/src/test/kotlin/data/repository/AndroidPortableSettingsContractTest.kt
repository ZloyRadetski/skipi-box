// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import app.AppState
import app.modes.ProxyServerListLayoutMultiple
import app.modes.ProxyServerListSortDefault
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AndroidPortableSettingsContractTest {
    @Test
    fun homeDisplayRoundTripKeepsRawSortLayoutAndAndroidFlags() {
        listOf(ProxyServerListSortDefault, 1, 2, 4).forEach { sort ->
            val original = AppState(
                proxyServerListSort = sort,
                proxyServerListLayout = ProxyServerListLayoutMultiple,
                enableAllProxyGroup = true,
                showTunnelMemoryOnHome = true,
            )

            val persisted = original.toPersistedSettings()
            val restored = AppState().applyPersistedSettings(persisted)

            assertEquals(sort, persisted.home.proxyServerListSort)
            assertEquals(ProxyServerListLayoutMultiple, persisted.home.proxyServerListLayout)
            assertEquals(true, persisted.home.enableAllProxyGroup)
            assertEquals(true, persisted.home.showTunnelMemoryOnHome)
            assertEquals(sort, restored.proxyServerListSort)
            assertEquals(ProxyServerListLayoutMultiple, restored.proxyServerListLayout)
            assertTrue(restored.enableAllProxyGroup)
            assertTrue(restored.showTunnelMemoryOnHome)
        }
    }

    @Test
    fun nullHomeFieldsLeaveAndroidHostValuesUntouched() {
        val original = AppState(
            proxyServerListSort = 4,
            proxyServerListLayout = ProxyServerListLayoutMultiple,
            enableAllProxyGroup = true,
            showTunnelMemoryOnHome = true,
        )
        val withoutNewFields = original.toPersistedSettings().copy(
            home = original.toPersistedSettings().home.copy(
                proxyServerListSort = null,
                proxyServerListLayout = null,
                enableAllProxyGroup = null,
                showTunnelMemoryOnHome = null,
            ),
        )

        val restored = original.applyPersistedSettings(withoutNewFields)

        assertEquals(4, restored.proxyServerListSort)
        assertEquals(ProxyServerListLayoutMultiple, restored.proxyServerListLayout)
        assertTrue(restored.enableAllProxyGroup)
        assertTrue(restored.showTunnelMemoryOnHome)
        assertFalse(restored.showServerSearch)
    }
}
