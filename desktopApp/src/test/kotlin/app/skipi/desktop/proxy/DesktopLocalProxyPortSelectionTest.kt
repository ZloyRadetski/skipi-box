// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopLocalProxyPortSelectionTest {
    @Test
    fun staticPortIsKeptWithoutCallingTheHostAllocator() {
        var allocatorCalled = false

        val resolved = resolveDesktopLocalProxyPort(
            currentPort = 10_808,
            dynamicEnabled = false,
            listenAddress = "0.0.0.0",
            excludedPorts = setOf(10_808),
        ) { _, _ ->
            allocatorCalled = true
            null
        }

        assertEquals(10_808, resolved)
        assertFalse(allocatorCalled)
    }

    @Test
    fun dynamicPortForwardsTheListenerAndExcludedPortsToTheHostAllocator() {
        val excludedPorts = setOf(10_808, 10_809)
        var allocationRequest: Pair<String, Set<Int>>? = null

        val resolved = resolveDesktopLocalProxyPort(
            currentPort = 10_808,
            dynamicEnabled = true,
            listenAddress = "192.0.2.12",
            excludedPorts = excludedPorts,
        ) { address, excluded ->
            allocationRequest = address to excluded
            23_456
        }

        assertEquals(23_456, resolved)
        assertEquals("192.0.2.12" to excludedPorts, allocationRequest)
    }

    @Test
    fun failedDynamicAllocationDoesNotSilentlyReuseTheConfiguredPort() {
        val resolved = resolveDesktopLocalProxyPort(
            currentPort = 10_808,
            dynamicEnabled = true,
            listenAddress = "127.0.0.1",
            excludedPorts = emptySet(),
        ) { _, _ -> null }

        assertNull(resolved)
    }

    @Test
    fun dynamicResolutionCanSelectAFreshPortForEachServiceStart() {
        val allocations = ArrayDeque(listOf(23_456, 23_457))
        val allocate: (String, Set<Int>) -> Int? = { _, _ -> allocations.removeFirst() }

        val first = resolveDesktopLocalProxyPort(10_808, true, "127.0.0.1", emptySet(), allocate)
        val second = resolveDesktopLocalProxyPort(10_808, true, "127.0.0.1", emptySet(), allocate)

        assertEquals(23_456, first)
        assertEquals(23_457, second)
    }

    @Test
    fun desktopHostAllocatorReturnsAnEphemeralPortThatCanBeBound() {
        val port = allocateDesktopLocalProxyPort("127.0.0.1", emptySet())

        assertNotNull(port)
        assertTrue(port in 1..65_535)
        ServerSocket(port, 0, java.net.InetAddress.getByName("127.0.0.1")).use { socket ->
            assertEquals(port, socket.localPort)
        }
    }
}
