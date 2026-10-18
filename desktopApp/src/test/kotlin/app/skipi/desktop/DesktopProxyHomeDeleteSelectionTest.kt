// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopProxyHomeDeleteSelectionTest {
    @Test
    fun directDeleteOfSelectedSubscriptionRequestsDeleteBeforeSelectingManual() {
        val events = mutableListOf<String>()

        requestDesktopProxyHomeSubscriptionDeletion(
            subscriptionId = 5,
            selectedGroupId = DesktopProxyGroupIds.subscription(5),
            requestDelete = { events += "delete" },
            selectGroup = { events += "select:$it" },
        )

        assertEquals(listOf("delete", "select:${DesktopProxyGroupIds.Manual}"), events)
    }

    @Test
    fun confirmedDeleteOfSelectedSubscriptionRequestsDeleteBeforeSelectingManual() {
        val events = mutableListOf<String>()

        requestDesktopProxyHomeSubscriptionDeletion(
            subscriptionId = 5,
            selectedGroupId = DesktopProxyGroupIds.subscription(5),
            requestDelete = { events += "delete-after-confirm" },
            selectGroup = { events += "select:$it" },
        )

        assertEquals(listOf("delete-after-confirm", "select:${DesktopProxyGroupIds.Manual}"), events)
    }

    @Test
    fun deletingAnotherSubscriptionKeepsCurrentGroupSelected() {
        val events = mutableListOf<String>()

        requestDesktopProxyHomeSubscriptionDeletion(
            subscriptionId = 5,
            selectedGroupId = DesktopProxyGroupIds.subscription(8),
            requestDelete = { events += "delete" },
            selectGroup = { events += "select:$it" },
        )

        assertEquals(listOf("delete"), events)
    }

    @Test
    fun deletingWithNoSelectedGroupDoesNotSelectManual() {
        val events = mutableListOf<String>()

        requestDesktopProxyHomeSubscriptionDeletion(
            subscriptionId = 5,
            selectedGroupId = null,
            requestDelete = { events += "delete" },
            selectGroup = { events += "select:$it" },
        )

        assertEquals(listOf("delete"), events)
    }
}
