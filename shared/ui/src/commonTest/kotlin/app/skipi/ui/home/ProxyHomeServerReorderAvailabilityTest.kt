// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeSortMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android filters the page rows by search before rendering, but enables drag reorder from only
 * the resolved column count and persisted sort mode. This covers that availability rule; it does
 * not characterize the separate shared move-up/down card-menu interaction.
 */
class ProxyHomeServerReorderAvailabilityTest {
    @Test
    fun androidReorderAvailabilityRequiresSingleColumnAndDefaultSortButAllowsSearch() {
        assertTrue(
            proxyHomeCanReorderServers(
                columns = 1,
                sortMode = ProxyHomeSortMode.Default,
                searchQuery = "matching server",
            ),
        )
        assertFalse(
            proxyHomeCanReorderServers(
                columns = 2,
                sortMode = ProxyHomeSortMode.Default,
                searchQuery = "",
            ),
        )
        assertFalse(
            proxyHomeCanReorderServers(
                columns = 1,
                sortMode = ProxyHomeSortMode.Name,
                searchQuery = "",
            ),
        )
        assertFalse(
            proxyHomeCanReorderServers(
                columns = 1,
                sortMode = ProxyHomeSortMode.Latency,
                searchQuery = "matching server",
            ),
        )
    }
}
