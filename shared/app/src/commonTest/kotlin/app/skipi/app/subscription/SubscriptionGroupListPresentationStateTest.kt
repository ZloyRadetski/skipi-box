// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubscriptionGroupListPresentationStateTest {
    @Test
    fun updateUsesFreshSavedPayloadBeforeTheListResolverRecomposes() {
        val saved = Group(id = 4, url = "https://new.example")
        val stale = Group(id = 4, url = "https://old.example")
        var resolverCalled = false

        val result = resolveSubscriptionGroupUpdatePayload(
            groupId = saved.id,
            groupOverride = saved,
            resolveGroupPayload = {
                resolverCalled = true
                stale
            },
        )

        assertEquals(saved, result)
        assertFalse(resolverCalled)
    }

    @Test
    fun listUpdateFallsBackToTheCurrentGroupResolver() {
        val current = Group(id = 8, url = "https://current.example")

        val result = resolveSubscriptionGroupUpdatePayload(
            groupId = current.id,
            groupOverride = null,
            resolveGroupPayload = { if (it == current.id) current else null },
        )

        assertEquals(current, result)
    }

    private data class Group(val id: Int, val url: String)
}
