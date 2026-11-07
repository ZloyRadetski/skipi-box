// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemPathDnsTxtResolverTest {
    private class Recording(private val label: String) : DnsTxtResolver {
        val servers = mutableListOf<String>()
        override suspend fun queryTxt(server: String, domain: String): List<String>? {
            servers += server
            return listOf(label)
        }
    }

    @Test
    fun systemServersAreAskedTheWayTheOsWouldAndTheRestThroughTheTunnelRoute() = runTest {
        val system = Recording("system-path")
        val tunnel = Recording("tunnel-route")
        val resolver = SystemPathDnsTxtResolver(
            systemServers = listOf("192.168.1.1", " fe80::1 "),
            systemPath = system,
            otherwise = tunnel,
        )

        assertEquals(listOf("system-path"), resolver.queryTxt("192.168.1.1", "o-o.myaddr.l.google.com"))
        assertEquals(listOf("system-path"), resolver.queryTxt("fe80::1", "o-o.myaddr.l.google.com"))
        assertEquals(listOf("tunnel-route"), resolver.queryTxt("8.8.8.8", "o-o.myaddr.l.google.com"))
        assertEquals(listOf("192.168.1.1", "fe80::1"), system.servers)
        assertEquals(listOf("8.8.8.8"), tunnel.servers)
    }

    @Test
    fun withoutSystemServersEverythingUsesTheRoute() = runTest {
        val system = Recording("system-path")
        val tunnel = Recording("tunnel-route")

        val resolver = SystemPathDnsTxtResolver(emptyList(), system, tunnel)

        assertEquals(listOf("tunnel-route"), resolver.queryTxt("1.1.1.1", "x"))
        assertEquals(emptyList(), system.servers)
    }
}
