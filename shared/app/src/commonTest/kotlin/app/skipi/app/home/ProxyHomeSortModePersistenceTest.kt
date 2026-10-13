// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import app.modes.ProxyServerListSortDefault
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeSortModePersistenceTest {
    @Test
    fun mapsSharedModesToAndFromExistingAndroidPersistedValues() {
        assertEquals(ProxyServerListSortDefault, ProxyHomeSortMode.Default.toAndroidValue())
        assertEquals(ProxyServerListSortName, ProxyHomeSortMode.Name.toAndroidValue())
        assertEquals(ProxyServerListSortLatency, ProxyHomeSortMode.Latency.toAndroidValue())

        assertEquals(ProxyHomeSortMode.Default, proxyHomeSortModeFromAndroidValue(ProxyServerListSortDefault))
        assertEquals(ProxyHomeSortMode.Name, proxyHomeSortModeFromAndroidValue(ProxyServerListSortName))
        assertEquals(ProxyHomeSortMode.Latency, proxyHomeSortModeFromAndroidValue(ProxyServerListSortLatency))
        assertEquals(ProxyHomeSortMode.Default, proxyHomeSortModeFromAndroidValue(Int.MIN_VALUE))
    }
}
