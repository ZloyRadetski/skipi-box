// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeServerMoveTest {
    private val visible = listOf(10, 20, 30, 40)

    @Test
    fun aStepMovesAServerOverItsNeighbourInTheShownOrder() {
        assertEquals(
            ProxyHomeServerMove.Swap(fromId = 20, toId = 30),
            resolveProxyHomeServerStep(ProxyHomeSortMode.Default, visible, serverId = 20, offset = 1),
        )
        assertEquals(
            ProxyHomeServerMove.Swap(fromId = 20, toId = 10),
            resolveProxyHomeServerStep(ProxyHomeSortMode.Default, visible, serverId = 20, offset = -1),
        )
    }

    @Test
    fun aStepOffTheEndsOfTheGroupOrOfNoDistanceIsRefused() {
        assertEquals(ProxyHomeServerMove.OutOfRange, resolveProxyHomeServerStep(ProxyHomeSortMode.Default, visible, 40, 1))
        assertEquals(ProxyHomeServerMove.OutOfRange, resolveProxyHomeServerStep(ProxyHomeSortMode.Default, visible, 10, -1))
        assertEquals(ProxyHomeServerMove.OutOfRange, resolveProxyHomeServerStep(ProxyHomeSortMode.Default, visible, 20, 0))
    }

    @Test
    fun aServerThatIsNotShownCannotBeStepped() {
        assertEquals(ProxyHomeServerMove.OutOfRange, resolveProxyHomeServerStep(ProxyHomeSortMode.Default, visible, 99, 1))
    }

    @Test
    fun anyOtherSortThanTheDefaultOneRefusesEveryMove() {
        for (mode in ProxyHomeSortMode.entries - ProxyHomeSortMode.Default) {
            assertEquals(ProxyHomeServerMove.NotDefaultSort, resolveProxyHomeServerStep(mode, visible, 20, 1))
            assertEquals(ProxyHomeServerMove.NotDefaultSort, resolveProxyHomeServerDrag(mode, visible, 10, 30))
        }
    }

    @Test
    fun aDragMovesOneShownServerOntoAnother() {
        assertEquals(
            ProxyHomeServerMove.Swap(fromId = 10, toId = 40),
            resolveProxyHomeServerDrag(ProxyHomeSortMode.Default, visible, fromId = 10, toId = 40),
        )
    }

    @Test
    fun aDragOntoItselfIsRefusedAndOneInvolvingAHiddenServerIsToo() {
        assertEquals(ProxyHomeServerMove.SameServer, resolveProxyHomeServerDrag(ProxyHomeSortMode.Default, visible, 20, 20))
        assertEquals(ProxyHomeServerMove.NotVisible, resolveProxyHomeServerDrag(ProxyHomeSortMode.Default, visible, 20, 99))
        assertEquals(ProxyHomeServerMove.NotVisible, resolveProxyHomeServerDrag(ProxyHomeSortMode.Default, visible, 99, 20))
    }
}
