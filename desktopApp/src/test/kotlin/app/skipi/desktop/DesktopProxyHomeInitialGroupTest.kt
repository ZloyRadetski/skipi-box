// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.HTTP
import features.proxy.server.model.encodePersistedProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopProxyHomeInitialGroupTest {
    @Test
    fun initialGroupSeedFollowsPersistedSelectedServerInsteadOfEarlierSubscription() {
        val serverLibrary = DesktopServerLibrary(
            selectedServerId = 8,
            servers = listOf(
                storedServer(id = 7, subscriptionId = 7, remarks = "Earlier group"),
                storedServer(id = 8, subscriptionId = 8, remarks = "Selected group"),
            ),
        )
        val subscriptionLibrary = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                DesktopStoredSubscription(id = 7, url = "https://seven.example/sub", enabled = true),
                DesktopStoredSubscription(id = 8, url = "https://eight.example/sub", enabled = true),
            ),
        )

        val catalog = DesktopProxyGroups.create(
            serverLibrary = serverLibrary,
            subscriptionLibrary = subscriptionLibrary,
        )

        // Preserve the catalog's existing default policy while seeding Home from the selected row.
        assertEquals(DesktopProxyGroupIds.subscription(7), catalog.defaultGroupId)
        assertEquals(DesktopProxyGroupIds.subscription(8), initialDesktopProxyHomeGroupId(serverLibrary))
    }

    @Test
    fun hostAliasesCoverManualAutoAndUnknownSubscriptionIdsWithoutNormalization() {
        val library = DesktopServerLibrary(
            selectedServerId = 1,
            servers = listOf(
                storedServer(id = 1, subscriptionId = null, remarks = "Manual"),
                storedServer(id = 2, subscriptionId = -2, remarks = "Auto"),
                storedServer(id = 3, subscriptionId = 99, remarks = "Unknown subscription"),
            ),
        )

        assertEquals(DesktopProxyGroupIds.Manual, initialDesktopProxyHomeGroupId(library))
        assertEquals(DesktopProxyGroupIds.AutoBalancers, initialDesktopProxyHomeGroupId(library.copy(selectedServerId = 2)))
        assertEquals(DesktopProxyGroupIds.subscription(99), initialDesktopProxyHomeGroupId(library.copy(selectedServerId = 3)))
    }

    @Test
    fun absentOrUndecodableSelectedServerFallsBackToManual() {
        val library = DesktopServerLibrary(
            selectedServerId = 4,
            servers = listOf(
                storedServer(id = 1, subscriptionId = 7, remarks = "Valid"),
                DesktopStoredProxyServer(id = 4, serverJson = "{not-json", subscriptionId = 8),
            ),
        )

        assertEquals(DesktopProxyGroupIds.Manual, initialDesktopProxyHomeGroupId(library))
        assertEquals(
            DesktopProxyGroupIds.Manual,
            initialDesktopProxyHomeGroupId(library.copy(selectedServerId = null)),
        )
        assertEquals(
            DesktopProxyGroupIds.Manual,
            initialDesktopProxyHomeGroupId(library.copy(selectedServerId = 99)),
        )
    }

    private fun storedServer(id: Int, subscriptionId: Int?, remarks: String) = DesktopStoredProxyServer(
        id = id,
        serverJson = HTTP(
            remarks = remarks,
            server = "node-$id.example",
            port = "443",
        ).encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )
}
