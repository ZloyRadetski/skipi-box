// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyHomePresentationTest {
    @Test
    fun `group selection changes only selected group`() {
        val initial = ProxyHomePresentationState(
            selectedGroupId = "subscription:1",
            searchQuery = "nl",
            searchVisible = true,
        )

        val next = initial.reduce(ProxyHomePresentationAction.SelectGroup("subscription:2"))

        assertEquals("subscription:2", next.selectedGroupId)
        assertEquals("nl", next.searchQuery)
        assertTrue(next.searchVisible)
    }

    @Test
    fun `hiding search preserves its query`() {
        val initial = ProxyHomePresentationState(searchQuery = "wireguard", searchVisible = true)

        val next = initial.reduce(ProxyHomePresentationAction.ToggleSearch)

        assertFalse(next.searchVisible)
        assertEquals("wireguard", next.searchQuery)
    }

    @Test
    fun `explicit search visibility overrides current value`() {
        val next = ProxyHomePresentationState(searchVisible = false)
            .reduce(ProxyHomePresentationAction.SetSearchVisible(true))

        assertTrue(next.searchVisible)
    }
}
