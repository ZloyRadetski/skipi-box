// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeSortMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android filters page rows by search before rendering, then enables drag only for one column,
 * default sort, and more than one visible row. Search itself does not disable drag. This does not
 * characterize the separate shared move-up/down card-menu interaction.
 */
class ProxyHomeServerReorderAvailabilityTest {
    @Test
    fun androidReorderAvailabilityRequiresSingleColumnAndDefaultSortButAllowsSearch() {
        assertTrue(
            proxyHomeCanReorderServers(
                columns = 1,
                sortMode = ProxyHomeSortMode.Default,
                searchQuery = "matching server",
                visibleServerCount = 2,
            ),
        )
        assertFalse(
            proxyHomeCanReorderServers(
                columns = 2,
                sortMode = ProxyHomeSortMode.Default,
                searchQuery = "",
                visibleServerCount = 2,
            ),
        )
        assertFalse(
            proxyHomeCanReorderServers(
                columns = 1,
                sortMode = ProxyHomeSortMode.Name,
                searchQuery = "",
                visibleServerCount = 2,
            ),
        )
        assertFalse(
            proxyHomeCanReorderServers(
                columns = 1,
                sortMode = ProxyHomeSortMode.Latency,
                searchQuery = "matching server",
                visibleServerCount = 2,
            ),
        )
        assertFalse(
            proxyHomeCanReorderServers(
                columns = 1,
                sortMode = ProxyHomeSortMode.Default,
                searchQuery = "",
                visibleServerCount = 1,
            ),
        )
    }
}
