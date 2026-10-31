// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Strategy groups are pinged as on Android: through their members, the best answer is the group's. */
class DesktopStrategyGroupLatencyTest {
    private fun server(name: String): ProxyServer<*> = ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@$name.example:443?security=tls#$name",
    )

    private val fast = server("fast")
    private val slow = server("slow")
    private val dead = server("dead")
    private val group = StrategyGroup(remarks = "Balancer")

    private fun tester(members: List<ProxyServer<*>>) = DesktopStrategyGroupAwareTester(
        members = { members },
        concurrency = 4,
        single = { candidate ->
            when (candidate) {
                fast -> DesktopServerLatencyResult.Success(40)
                slow -> DesktopServerLatencyResult.Success(210)
                else -> DesktopServerLatencyResult.Timeout
            }
        },
    )

    @Test
    fun aGroupGetsTheBestLatencyOfItsMembers() = runBlocking {
        assertEquals(DesktopServerLatencyResult.Success(40), tester(listOf(slow, dead, fast)).test(1, group))
    }

    @Test
    fun aGroupWithoutAnyAnsweringMemberIsAFailure() = runBlocking {
        assertTrue(tester(listOf(dead)).test(1, group) is DesktopServerLatencyResult.Error)
        assertTrue(tester(emptyList()).test(1, group) is DesktopServerLatencyResult.Error)
    }

    @Test
    fun anOrdinaryServerIsMeasuredAsBefore() = runBlocking {
        assertEquals(DesktopServerLatencyResult.Success(210), tester(emptyList()).test(2, slow))
    }

    @Test
    fun aGroupCanBePingedButAChainStillCannot() {
        assertTrue(fast.canBePingedOnDesktop())
        assertTrue(group.canBePingedOnDesktop())
        assertFalse(ChainProxy(remarks = "Chain").canBePingedOnDesktop())
    }

    @Test
    fun aGroupWithoutMembersSaysSoInsteadOfLookingLikeATimeout() {
        val result = DesktopServerLatencyResult.Error("No member of the group answered")

        assertEquals("No servers in group", desktopLatencyErrorText(group, result, noMembersText = "No servers in group"))
        assertEquals(null, desktopLatencyErrorText(group.copy(filter = "fast"), result, noMembersText = "No servers in group"))
        assertEquals(null, desktopLatencyErrorText(fast, result, noMembersText = "No servers in group"))
    }
}
