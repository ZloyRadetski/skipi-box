// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.routing

import features.routing.model.RouteRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RouteRuleCollectionOperationsTest {
    @Test
    fun upsertReplacesInPlaceAndAllocatesIdsWithoutReordering() {
        val rules = listOf(RouteRule(id = 2, remarks = "First"), RouteRule(id = 8, remarks = "Second"))

        val replaced = upsertRouteRule(rules, nextRouteRuleId = 9, rule = RouteRule(id = 8, remarks = "Edited"))
        assertEquals(listOf("First", "Edited"), replaced.rules.map(RouteRule::remarks))
        assertEquals(9, replaced.nextRouteRuleId)

        val added = upsertRouteRule(replaced.rules, replaced.nextRouteRuleId, RouteRule(id = 12, remarks = "Third"))
        assertEquals(listOf(2, 8, 12), added.rules.map(RouteRule::id))
        assertEquals(13, added.nextRouteRuleId)
    }

    @Test
    fun routingEditsPreserveOrderAndInvalidOperationsAreNoOps() {
        val rules = listOf(
            RouteRule(id = 1, remarks = "One"),
            RouteRule(id = 2, remarks = "Two"),
            RouteRule(id = 3, remarks = "Three"),
        )

        val moved = moveRouteRule(rules, fromIndex = 0, toIndex = 2)
        assertEquals(listOf(2, 3, 1), moved.map(RouteRule::id))
        assertEquals(listOf(2, 1), removeRouteRule(moved, ruleId = 3).map(RouteRule::id))
        assertEquals(listOf(false, true, true), setRouteRuleEnabled(rules, 1, false).map(RouteRule::enabled))
        assertSame(rules, moveRouteRule(rules, fromIndex = -1, toIndex = 1))
        assertSame(rules, removeRouteRule(rules, ruleId = 99))
        assertSame(rules, setRouteRuleEnabled(rules, ruleId = 99, enabled = false))
    }
}
