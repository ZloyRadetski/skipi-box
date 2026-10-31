// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectedLatencyActionsTest {
    @Test
    fun testingTheConnectionIsOneEffectForTheGivenServerWhenTheHostOffersIt() = runTest {
        val effects = mutableListOf<ProxyHomeEffect>()
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
                availableActions = setOf(ProxyHomeActionId.TestConnection),
            ),
            scope = this,
            effectHandler = ProxyHomeEffectHandler { effect -> effects += effect; Result.success(Unit) },
        )

        store.dispatch(ProxyHomeAction.TestConnection("a"))
        runCurrent()

        assertEquals(listOf<ProxyHomeEffect>(ProxyHomeEffect.TestConnection("a")), effects)
    }

    @Test
    fun testingTheConnectionIsIgnoredWhenTheHostDoesNotOfferIt() = runTest {
        val effects = mutableListOf<ProxyHomeEffect>()
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(),
            scope = this,
            effectHandler = ProxyHomeEffectHandler { effect -> effects += effect; Result.success(Unit) },
        )

        store.dispatch(ProxyHomeAction.TestConnection("a"))
        runCurrent()

        assertTrue(effects.isEmpty())
    }
}
