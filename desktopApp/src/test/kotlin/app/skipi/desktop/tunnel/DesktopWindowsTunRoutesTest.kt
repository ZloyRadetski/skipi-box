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
            parallel = false,
            run = { command -> ran += command; Result.success(Unit) },
            adapterExists = { name -> assertEquals("skipi0", name); ++checks >= 3 },
            sleep = {},
            attempts = 10,
        )

        attach.attach(ipv4Plan).getOrThrow()

        assertEquals(3, checks)
        assertEquals(windowsTunAddRouteCommands(ipv4Plan), ran.take(2), "the routes come first")
    }

    @Test
    fun attachFailsWhenTheAdapterNeverAppears() {
        val ran = mutableListOf<List<String>>()
        val attach = DesktopWindowsTunRoutes(
            parallel = false,
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
            parallel = false,
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
            parallel = false,
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
        assertEquals(windowsTunDeleteRouteCommands(ipv4Plan), ran.filter { "delete" in it })
        ran.clear()
        assertTrue(routes.detach().isSuccess)
        assertTrue(ran.isEmpty(), "a second detach has nothing to delete")
    }

    // Windows sends SSDP, LLMNR and mDNS discovery to every adapter, so a new TUN adapter received
    // about a thousand UDP flows within seconds. hev serves at most 1023 sessions at once and then
    // fails every new socket, TCP included: the tunnel was up and carried no traffic.
    @Test
    fun multicastAndBroadcastAreBlockedOnlyOnTheTunnelAdapter() {
        val command = windowsTunMulticastBlockCommand(ipv4Plan)

        assertEquals(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command"), command.take(4))
        val script = command.last()
        assertTrue("-InterfaceAlias 'skipi0'" in script)
        assertTrue("-Direction Outbound" in script && "-Action Block" in script)
        for (range in listOf("224.0.0.0/4", "255.255.255.255", "ff00::/8")) assertTrue(range in script, range)
        assertTrue(script.indexOf("Remove-NetFirewallRule") < script.indexOf("New-NetFirewallRule"), "a stale rule goes first")
    }

    @Test
    fun theBlockRuleIsInstalledAfterTheRoutesAndRemovedOnDetach() {
        val ran = mutableListOf<List<String>>()
        val routes = DesktopWindowsTunRoutes(
            parallel = false,
            run = { command -> ran += command; Result.success(Unit) },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )

        routes.attach(ipv4Plan).getOrThrow()
        assertEquals(windowsTunAddRouteCommands(ipv4Plan) + listOf(windowsTunMulticastBlockCommand(ipv4Plan)), ran)

        ran.clear()
        routes.detach()
        assertEquals(windowsTunDeleteRouteCommands(ipv4Plan) + listOf(windowsTunMulticastUnblockCommand()), ran)
    }

    @Test
    fun aFailedBlockRuleDoesNotFailTheTunnel() {
        val routes = DesktopWindowsTunRoutes(
            parallel = false,
            run = { command ->
                if ("New-NetFirewallRule" in command.last()) Result.failure(IllegalStateException("no firewall service")) else Result.success(Unit)
            },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )

        assertTrue(routes.attach(ipv4Plan).isSuccess)
    }

    // Windows keeps the DNS servers of the physical adapter and sends queries out of the adapter that
    // owns the server, so a tunnel adapter without DNS never resolves through the tunnel (Core's DNS
    // that answers port 53 inside it is never asked). The adapter gets the plan's DNS and the lowest
    // interface metric, which is what the DNS client ranks by.
    private val dnsPlan = ipv4Plan.copy(dnsServers = listOf("1.1.1.1", "8.8.8.8"))

    @Test
    fun theTunnelAdapterIsGivenThePlansDnsAndTheLowestInterfaceMetric() {
        val script = checkNotNull(windowsTunDnsCommand(dnsPlan)).last()

        assertTrue("Set-DnsClientServerAddress -InterfaceAlias 'skipi0' -ServerAddresses ('1.1.1.1','8.8.8.8')" in script, script)
        assertTrue("Set-NetIPInterface -InterfaceAlias 'skipi0'" in script && "-InterfaceMetric 1" in script, script)
        assertTrue("Clear-DnsClientCache" in script, script)
    }

    @Test
    fun aPlanWithoutDnsLeavesTheAdapterDnsAlone() {
        assertEquals(null, windowsTunDnsCommand(ipv4Plan))
    }

    @Test
    fun onlyIpAddressesReachTheDnsScript() {
        val plan = ipv4Plan.copy(dnsServers = listOf("1.1.1.1", "x'; calc; '", "2606:4700:4700::1111"))

        val script = checkNotNull(windowsTunDnsCommand(plan)).last()

        assertTrue("('1.1.1.1','2606:4700:4700::1111')" in script, script)
        assertTrue("calc" !in script, script)
    }

    @Test
    fun dnsIsSetAfterTheRoutesAndBeforeTheBlockRule() {
        val ran = mutableListOf<List<String>>()
        val routes = DesktopWindowsTunRoutes(
            parallel = false,
            run = { command -> ran += command; Result.success(Unit) },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )

        routes.attach(dnsPlan).getOrThrow()

        assertEquals(
            windowsTunAddRouteCommands(dnsPlan) +
                listOf(checkNotNull(windowsTunDnsCommand(dnsPlan)), windowsTunMulticastBlockCommand(dnsPlan)),
            ran,
        )
    }

    @Test
    fun aFailedDnsCommandDoesNotFailTheTunnelAndDetachFlushesTheCache() {
        val ran = mutableListOf<List<String>>()
        val routes = DesktopWindowsTunRoutes(
            parallel = false,
            run = { command ->
                ran += command
                if ("Set-DnsClientServerAddress" in command.last()) Result.failure(IllegalStateException("no dns")) else Result.success(Unit)
            },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )

        assertTrue(routes.attach(dnsPlan).isSuccess)
        ran.clear()
        routes.detach()

        assertEquals(windowsTunDeleteRouteCommands(dnsPlan) + listOf(windowsTunMulticastUnblockCommand(), windowsTunDnsFlushCommand()), ran)
    }

    // Connecting and disconnecting the TUN took seconds: every command was its own process, run one after the
    // other (four netsh calls and two PowerShell starts on the way up, the same on the way down).
    @Test
    fun parallelAttachRunsEveryCommandAndStillRollsBackAFailedRoute() {
        val ran = java.util.Collections.synchronizedList(mutableListOf<List<String>>())
        val routes = DesktopWindowsTunRoutes(
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

        val result = routes.attach(dnsPlan)

        assertTrue(result.isFailure)
        assertTrue(ran.containsAll(windowsTunAddRouteCommands(dnsPlan)), "every route was tried, ran=$ran")
        assertTrue(ran.containsAll(windowsTunDeleteRouteCommands(dnsPlan)), "the routes that went in were removed, ran=$ran")
    }

    @Test
    fun parallelAttachInstallsRoutesDnsAndTheBlockRule() {
        val ran = java.util.Collections.synchronizedList(mutableListOf<List<String>>())
        val routes = DesktopWindowsTunRoutes(
            background = { task -> task() },
            run = { command -> ran += command; Result.success(Unit) },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )

        routes.attach(dnsPlan).getOrThrow()

        val expected = windowsTunAddRouteCommands(dnsPlan) +
            listOf(checkNotNull(windowsTunDnsCommand(dnsPlan)), windowsTunMulticastBlockCommand(dnsPlan))
        assertEquals(expected.toSet(), ran.toSet())
        assertEquals(expected.size, ran.size)
    }

    // Only the routes decide when the system's traffic is back on the real adapter; the firewall rule and the
    // DNS cache are cleaned up behind the disconnect, not in front of it.
    @Test
    fun detachWaitsOnlyForTheRoutesAndLeavesTheRestToTheBackground() {
        val ran = mutableListOf<List<String>>()
        val background = mutableListOf<() -> Unit>()
        val routes = DesktopWindowsTunRoutes(
            parallel = false,
            background = { task -> background += task },
            run = { command -> ran += command; Result.success(Unit) },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )
        routes.attach(dnsPlan).getOrThrow()
        background.clear()
        ran.clear()

        routes.detach()

        assertEquals(windowsTunDeleteRouteCommands(dnsPlan), ran)
        background.forEach { it() }
        assertEquals(
            windowsTunDeleteRouteCommands(dnsPlan) + listOf(windowsTunMulticastUnblockCommand(), windowsTunDnsFlushCommand()),
            ran,
        )
    }

    @Test
    fun attachDoesNotWaitForTheDnsAndTheBlockRule() {
        val ran = mutableListOf<List<String>>()
        val background = mutableListOf<() -> Unit>()
        val routes = DesktopWindowsTunRoutes(
            parallel = false,
            background = { task -> background += task },
            run = { command -> ran += command; Result.success(Unit) },
            adapterExists = { true },
            sleep = {},
            attempts = 1,
        )

        routes.attach(dnsPlan).getOrThrow()

        assertEquals(windowsTunAddRouteCommands(dnsPlan), ran, "only the routes are waited for")
        background.forEach { it() }
        assertEquals(2, ran.size - windowsTunAddRouteCommands(dnsPlan).size)
    }
}
