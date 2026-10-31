// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.network.NetworkCidrAddress
import engine.vpn.TunInterfacePlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopWindowsTunRoutesTest {
    private val ipv4Plan = TunInterfacePlan(
        name = "skipi0",
        mtu = 1400,
        addresses = listOf(NetworkCidrAddress("172.19.0.1", 30)),
        routes = listOf(NetworkCidrAddress("0.0.0.0", 1), NetworkCidrAddress("128.0.0.0", 1)),
    )
    private val dualStackPlan = ipv4Plan.copy(
        addresses = ipv4Plan.addresses + NetworkCidrAddress("fdfe:dcba:9876::1", 126),
        routes = ipv4Plan.routes + listOf(NetworkCidrAddress("::", 1), NetworkCidrAddress("8000::", 1)),
    )

    @Test
    fun routesAreAddedOnTheTunnelAdapterOnLinkAndNeverPersisted() {
        assertEquals(
            listOf(
                listOf("netsh", "interface", "ipv4", "add", "route", "prefix=0.0.0.0/1", "interface=skipi0", "nexthop=0.0.0.0", "metric=1", "store=active"),
                listOf("netsh", "interface", "ipv4", "add", "route", "prefix=128.0.0.0/1", "interface=skipi0", "nexthop=0.0.0.0", "metric=1", "store=active"),
            ),
            windowsTunAddRouteCommands(ipv4Plan),
        )
    }

    @Test
    fun ipv6RoutesUseTheIpv6ContextAndAnUnspecifiedNextHop() {
        val commands = windowsTunAddRouteCommands(dualStackPlan)

        assertEquals(4, commands.size)
        assertEquals(
            listOf("netsh", "interface", "ipv6", "add", "route", "prefix=::/1", "interface=skipi0", "nexthop=::", "metric=1", "store=active"),
            commands[2],
        )
        assertTrue(commands[3].contains("prefix=8000::/1"))
    }

    @Test
    fun routesAreRemovedWithMatchingDeleteCommands() {
        assertEquals(
            listOf(
                listOf("netsh", "interface", "ipv4", "delete", "route", "prefix=0.0.0.0/1", "interface=skipi0", "store=active"),
                listOf("netsh", "interface", "ipv4", "delete", "route", "prefix=128.0.0.0/1", "interface=skipi0", "store=active"),
            ),
            windowsTunDeleteRouteCommands(ipv4Plan),
        )
    }

    @Test
    fun attachWaitsForTheAdapterThenAddsEveryRoute() {
        val ran = mutableListOf<List<String>>()
        var checks = 0
        val attach = DesktopWindowsTunRoutes(
            run = { command -> ran += command; Result.success(Unit) },
            adapterExists = { name -> assertEquals("skipi0", name); ++checks >= 3 },
            sleep = {},
            attempts = 10,
        )

        attach.attach(ipv4Plan).getOrThrow()

        assertEquals(3, checks)
        assertEquals(windowsTunAddRouteCommands(ipv4Plan), ran)
    }

    @Test
    fun attachFailsWhenTheAdapterNeverAppears() {
        val ran = mutableListOf<List<String>>()
        val attach = DesktopWindowsTunRoutes(
            run = { command -> ran += command; Result.success(Unit) },
            adapterExists = { false },
            sleep = {},
            attempts = 4,
        )

        val result = attach.attach(ipv4Plan)

        assertTrue(result.isFailure)
        assertTrue("skipi0" in result.exceptionOrNull()?.message.orEmpty())
        assertTrue(ran.isEmpty())
    }

    @Test
    fun aFailedRouteCommandRemovesTheRoutesAlreadyAddedAndReportsTheFailure() {
        val ran = mutableListOf<List<String>>()
        val attach = DesktopWindowsTunRoutes(
            run = { command ->
                ran += command
                if ("prefix=128.0.0.0/1" in command && "add" in command) {
                    Result.failure(IllegalStateException("netsh failed: access denied"))
                } else {
                    Result.success(Unit)
                }
            },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )

        val result = attach.attach(ipv4Plan)

        assertTrue(result.isFailure)
        assertTrue("access denied" in result.exceptionOrNull()?.message.orEmpty())
        assertEquals(windowsTunDeleteRouteCommands(ipv4Plan), ran.filter { "delete" in it })
    }

    @Test
    fun detachDeletesTheRoutesAndIgnoresAlreadyMissingOnes() {
        val ran = mutableListOf<List<String>>()
        var failDeletes = false
        val routes = DesktopWindowsTunRoutes(
            run = { command ->
                ran += command
                if (failDeletes && "delete" in command) Result.failure(IllegalStateException("not found")) else Result.success(Unit)
            },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )
        routes.attach(ipv4Plan).getOrThrow()
        ran.clear()
        failDeletes = true

        assertTrue(routes.detach().isSuccess)
        assertEquals(windowsTunDeleteRouteCommands(ipv4Plan), ran)
        ran.clear()
        assertTrue(routes.detach().isSuccess)
        assertTrue(ran.isEmpty(), "a second detach has nothing to delete")
    }
}
