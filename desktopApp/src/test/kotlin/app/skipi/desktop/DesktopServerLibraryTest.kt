// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import java.nio.file.Files

class DesktopServerLibraryTest {
    @Test
    fun libraryRoundTripsTheSharedServerPayloadAndSelection() {
        val path = Files.createTempFile("skipi-servers-", ".json")
        try {
            val server = ProxyServer.parse(
                "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@example.com:443#Desktop",
            )
            val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)

            DesktopServerLibraries.save(path, library).getOrThrow()
            val restored = DesktopServerLibraries.load(path).getOrThrow()

            assertEquals(1, restored.selectedServerId)
            assertEquals(1, restored.servers.size)
            assertEquals("Desktop", assertIs<VLESS>(restored.servers.single().decode().getOrThrow()).remarks)
            assertEquals(0, DesktopServerLibraries.remove(restored, 1).servers.size)
            assertEquals(null, DesktopServerLibraries.remove(restored, 1).selectedServerId)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun replacingSubscriptionServersDoesNotTouchManualOrOtherSubscriptionGroups() {
        val manual = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@manual.example:443#Manual",
        )
        val first = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@one.example:443#One",
        )
        val second = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@two.example:443#Two",
        )
        val other = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@other.example:443#Other",
        )

        val withManual = DesktopServerLibraries.add(DesktopServerLibrary(), manual)
        val withFirstGroup = DesktopServerLibraries.replaceSubscriptionServers(withManual, subscriptionId = 10, servers = listOf(first, second))
        val withTwoGroups = DesktopServerLibraries.replaceSubscriptionServers(withFirstGroup, subscriptionId = 20, servers = listOf(other))
        val refreshed = DesktopServerLibraries.replaceSubscriptionServers(withTwoGroups, subscriptionId = 10, servers = listOf(second))

        assertEquals(3, refreshed.servers.size)
        assertEquals(1, DesktopServerLibraries.serversForSubscription(refreshed, 10).size)
        assertEquals(1, DesktopServerLibraries.serversForSubscription(refreshed, 20).size)
        assertEquals(1, refreshed.servers.count { it.subscriptionId == null })

        val removed = DesktopServerLibraries.removeSubscriptionServers(refreshed, 10)
        assertEquals(2, removed.servers.size)
        assertEquals(0, DesktopServerLibraries.serversForSubscription(removed, 10).size)
        assertEquals(1, DesktopServerLibraries.serversForSubscription(removed, 20).size)
    }

    @Test
    fun refreshKeepsStableIdsAndTheCurrentSelectionForUnchangedSubscriptionServers() {
        val first = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@one.example:443#One",
        )
        val second = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@two.example:443#Two",
        )
        val initial = DesktopServerLibraries.replaceSubscriptionServers(
            DesktopServerLibrary(),
            subscriptionId = 10,
            servers = listOf(first, second),
        )
        val selected = DesktopServerLibraries.select(
            initial,
            DesktopServerLibraries.serversForSubscription(initial, 10).single { it.decode().getOrThrow().getInfo().remarks == "Two" }.id,
        )

        val refreshed = DesktopServerLibraries.replaceSubscriptionServers(
            selected,
            subscriptionId = 10,
            servers = listOf(second, first),
        )

        assertEquals(selected.selectedServerId, refreshed.selectedServerId)
        assertEquals(
            DesktopServerLibraries.serversForSubscription(selected, 10).map { it.id }.toSet(),
            DesktopServerLibraries.serversForSubscription(refreshed, 10).map { it.id }.toSet(),
        )
    }

    @Test
    fun updateReplacesTheExistingServerRatherThanAppendingADuplicate() {
        val original = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@one.example:443#One",
        )
        val edited = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@two.example:443#Two",
        )
        val initial = DesktopServerLibraries.add(DesktopServerLibrary(), original)

        val updated = DesktopServerLibraries.update(initial, serverId = 1, server = edited)

        assertEquals(1, updated.servers.size)
        assertEquals(1, updated.selectedServerId)
        assertEquals("Two", updated.servers.single().decode().getOrThrow().getInfo().remarks)
    }
}
