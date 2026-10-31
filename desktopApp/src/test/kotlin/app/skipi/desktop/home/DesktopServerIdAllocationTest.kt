// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopServerIdAllocationTest {
    @Test
    fun manualAddAfterDeletingHighestIdUsesPersistedHighWaterMark() = withTempServerFile { path ->
        val withoutHighest = libraryAfterDeletingHighestId()
        DesktopServerLibraries.save(path, withoutHighest).getOrThrow()

        val restored = DesktopServerLibraries.load(path).getOrThrow()
        val updated = DesktopServerLibraries.add(restored, proxy("next.example", "Next"))

        assertEquals(1, restored.servers.single().id)
        assertEquals(3, updated.servers.single { it.decode().getOrThrow().getInfo().remarks == "Next" }.id)
        assertEquals(3, updated.selectedServerId)
    }

    @Test
    fun subscriptionReplacementUsesHighWaterMarkAndKeepsUnchangedIds() {
        val existing = libraryAfterDeletingHighestId()
        val updated = DesktopServerLibraries.replaceSubscriptionServers(
            library = existing,
            subscriptionId = 10,
            servers = listOf(
                proxy("subscription-one.example", "Subscription one"),
                proxy("subscription-two.example", "Subscription two"),
            ),
        )
        val addedGroup = DesktopServerLibraries.serversForSubscription(updated, 10)

        assertEquals(existing.servers.single(), updated.servers.single { it.subscriptionId == null })
        assertEquals(listOf(3, 4), addedGroup.map { it.id })
        assertEquals(1, updated.selectedServerId)
    }

    @Test
    fun legacyLibraryWithoutCounterLoadsAndAllocatesAboveItsMaximumId() = withTempServerFile { path ->
        val fixture = checkNotNull(javaClass.getResourceAsStream("/fixtures/desktop-legacy/servers.json"))
        fixture.use { input -> Files.copy(input, path, REPLACE_EXISTING) }

        val loaded = DesktopServerLibraries.load(path).getOrThrow()
        assertEquals(4, loaded.selectedServerId)
        assertEquals(listOf(12, 4), loaded.servers.map { it.id })
        assertEquals("Legacy subscribed", loaded.servers.first().decode().getOrThrow().getInfo().remarks)

        val added = DesktopServerLibraries.add(loaded, proxy("legacy-next.example", "Legacy next"))
        assertEquals(13, added.servers.last().id)
        DesktopServerLibraries.save(path, added).getOrThrow()

        val restored = DesktopServerLibraries.load(path).getOrThrow()
        assertEquals(listOf(12, 4, 13), restored.servers.map { it.id })
        assertEquals(13, restored.selectedServerId)
        assertEquals(14, DesktopServerLibraries.add(restored, proxy("after-reload.example", "After reload")).servers.last().id)
    }

    @Test
    fun deletingSelectedServerOrSubscriptionGroupFallsBackToFirstRemainingOrNull() {
        val twoManualServers = DesktopServerLibraries.add(
            DesktopServerLibraries.add(DesktopServerLibrary(), proxy("first.example", "First")),
            proxy("selected-highest.example", "Selected highest"),
        )
        val afterDeletingSelectedHighest = DesktopServerLibraries.remove(twoManualServers, serverId = 2)

        assertEquals(1, afterDeletingSelectedHighest.selectedServerId)
        assertNull(DesktopServerLibraries.remove(afterDeletingSelectedHighest, serverId = 1).selectedServerId)

        val manual = DesktopServerLibraries.add(DesktopServerLibrary(), proxy("manual.example", "Manual"))
        val withSubscription = DesktopServerLibraries.replaceSubscriptionServers(
            library = manual,
            subscriptionId = 20,
            servers = listOf(proxy("subscription.example", "Subscription")),
        )
        val selectedSubscriptionId = DesktopServerLibraries.serversForSubscription(withSubscription, 20).single().id
        val afterDeletingSubscription = DesktopServerLibraries.removeSubscriptionServers(
            DesktopServerLibraries.select(withSubscription, selectedSubscriptionId),
            subscriptionId = 20,
        )
        val onlySubscription = DesktopServerLibraries.replaceSubscriptionServers(
            library = DesktopServerLibrary(),
            subscriptionId = 30,
            servers = listOf(proxy("only-subscription.example", "Only subscription")),
        )
        val empty = DesktopServerLibraries.removeSubscriptionServers(onlySubscription, subscriptionId = 30)

        assertEquals(1, afterDeletingSubscription.selectedServerId)
        assertTrue(afterDeletingSubscription.servers.single().subscriptionId == null)
        assertNull(empty.selectedServerId)
    }

    @Test
    fun manualAddFailsWhenTheLargestPositiveIdIsAlreadyOccupied() {
        val library = DesktopServerLibrary(
            servers = listOf(
                DesktopStoredProxyServer(
                    id = Int.MAX_VALUE,
                    serverJson = proxy("last-id.example", "Last ID").encodePersistedProxyServer(),
                ),
            ),
        )

        assertFailsWith<IllegalStateException> {
            DesktopServerLibraries.add(library, proxy("overflow.example", "Overflow"))
        }
    }

    private fun libraryAfterDeletingHighestId(): DesktopServerLibrary {
        val withTwoServers = DesktopServerLibraries.add(
            DesktopServerLibraries.add(DesktopServerLibrary(), proxy("remaining.example", "Remaining")),
            proxy("removed.example", "Removed"),
        )
        return DesktopServerLibraries.remove(withTwoServers, serverId = 2)
    }

    private fun proxy(host: String, remarks: String): ProxyServer<*> = ProxyServer.parse(
        "vless://123e4567-e89b-42d3-a456-426614174000@$host:443?security=tls#$remarks",
    )

    private inline fun withTempServerFile(block: (Path) -> Unit) {
        val path = Files.createTempFile("skipi-server-ids-", ".json")
        try {
            block(path)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
        }
    }
}
