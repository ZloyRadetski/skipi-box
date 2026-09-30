// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeActionId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyHomeActionAvailabilityTest {
    @Test
    fun operationIsDispatchableOnlyWhenAvailableAndNotBusy() {
        assertTrue(
            canDispatchHomeAction(
                actionId = ProxyHomeActionId.RefreshSubscription,
                availableActions = setOf(ProxyHomeActionId.RefreshSubscription),
                busyActions = emptySet(),
            ),
        )
        assertFalse(
            canDispatchHomeAction(
                actionId = ProxyHomeActionId.RefreshSubscription,
                availableActions = emptySet(),
                busyActions = emptySet(),
            ),
        )
        assertFalse(
            canDispatchHomeAction(
                actionId = ProxyHomeActionId.RefreshSubscription,
                availableActions = setOf(ProxyHomeActionId.RefreshSubscription),
                busyActions = setOf(ProxyHomeActionId.RefreshSubscription),
            ),
        )
        assertTrue(
            canDispatchHomeAction(
                actionId = ProxyHomeActionId.ToggleTunnel,
                availableActions = setOf(ProxyHomeActionId.ToggleTunnel),
                busyActions = setOf(ProxyHomeActionId.ToggleTunnel),
                allowBusy = true,
            ),
        )
        assertFalse(
            canDispatchHomeAction(
                actionId = ProxyHomeActionId.ToggleTunnel,
                availableActions = setOf(ProxyHomeActionId.ToggleTunnel),
                busyActions = setOf(ProxyHomeActionId.ToggleTunnel),
                allowBusy = false,
            ),
        )
    }
}
