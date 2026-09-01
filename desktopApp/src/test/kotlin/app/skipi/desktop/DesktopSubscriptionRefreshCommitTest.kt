// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.subscription.SubscriptionMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshCommitTest {
    @Test
    fun rebaseUsesLatestLibraryAndPreservesManualServerAddedDuringFetch() {
        val url = "https://example.com/sub"
        val initialSubscriptions = DesktopSubscriptionLibraries.addOrReplace(
            DesktopSubscriptionLibrary(), url, "SKIPI",
        )
        val subscription = initialSubscriptions.subscriptions.single()
        val oldSubscriptionServer = proxy("old.example", "Old")
        val initialServers = DesktopServerLibraries.replaceSubscriptionServers(
            DesktopServerLibrary(), subscription.id, listOf(oldSubscriptionServer),
        )
        val baseline = DesktopSubscriptionRefreshCommitter.captureBaseline(
            initialSubscriptions,
            initialServers,
            DesktopConfigLibrary(),
            url,
        )

        // This represents a user action that finishes while the HTTP request is in flight.
        val latestServers = DesktopServerLibraries.add(initialServers, proxy("manual.example", "Manual"))
        val commit = DesktopSubscriptionRefreshCommitter.rebaseServers(
            baseline = baseline,
            latestSubscriptions = initialSubscriptions,
            latestServers = latestServers,
            url = url,
            userAgent = "SKIPI",
            name = "Updated provider",
            metadata = SubscriptionMetadata(profileDescription = "fresh"),
            importedServers = listOf(proxy("fresh.example", "Fresh")),
        )

        val ready = assertIs<DesktopSubscriptionRefreshServerCommit.Ready>(commit)
        assertTrue(ready.serverGroupWasReplaced)
        assertEquals(subscription.id, ready.subscription.id)
        assertEquals(latestServers.selectedServerId, ready.servers.selectedServerId)
        assertEquals(
            "Manual",
            ready.servers.servers.single { stored -> stored.subscriptionId == null }.decode().getOrThrow().getInfo().remarks,
        )
        assertEquals(
            "Fresh",
            DesktopServerLibraries.serversForSubscription(ready.servers, subscription.id)
                .single()
                .decode()
                .getOrThrow()
                .getInfo()
                .remarks,
        )
    }

    @Test
    fun rebaseDoesNotReplaceTargetGroupEditedDuringFetch() {
        val url = "https://example.com/sub"
        val subscriptions = DesktopSubscriptionLibraries.addOrReplace(DesktopSubscriptionLibrary(), url, "SKIPI")
        val subscription = subscriptions.subscriptions.single()
        val initial = DesktopServerLibraries.replaceSubscriptionServers(
            DesktopServerLibrary(), subscription.id, listOf(proxy("old.example", "Old")),
        )
        val baseline = DesktopSubscriptionRefreshCommitter.captureBaseline(
            subscriptions,
            initial,
            DesktopConfigLibrary(),
            url,
        )
        val edited = DesktopServerLibraries.update(
            initial,
            initial.servers.single().id,
            proxy("edited.example", "Edited by user"),
        )

        val commit = DesktopSubscriptionRefreshCommitter.rebaseServers(
            baseline = baseline,
            latestSubscriptions = subscriptions,
            latestServers = edited,
            url = url,
            userAgent = "SKIPI",
            name = "Provider",
            metadata = SubscriptionMetadata(),
            importedServers = listOf(proxy("fresh.example", "Fresh")),
        )

        val ready = assertIs<DesktopSubscriptionRefreshServerCommit.Ready>(commit)
        assertEquals(false, ready.serverGroupWasReplaced)
        assertEquals(edited, ready.servers)
    }

    @Test
    fun profileRebaseKeepsUnrelatedProfileAddedDuringFetch() {
        val subscriptions = DesktopSubscriptionLibraries.addOrReplace(
            DesktopSubscriptionLibrary(), "https://example.com/sub", "SKIPI",
        )
        val subscription = subscriptions.subscriptions.single()
        val baseline = DesktopSubscriptionRefreshCommitter.captureBaseline(
            subscriptions,
            DesktopServerLibrary(),
            DesktopConfigLibrary(),
            subscription.url,
        )
        val latestConfigs = DesktopConfigLibraries.put(
            DesktopConfigLibrary(),
            name = "Manual profile",
            content = "[Rule]\nFINAL,DIRECT\n",
            sourceUrl = "",
        )

        val result = DesktopSubscriptionRefreshCommitter.rebaseEmbeddedProfile(
            baseline = baseline,
            latestConfigs = latestConfigs,
            subscription = subscription,
            metadata = SubscriptionMetadata(profileTitle = "From subscription"),
            resolved = DesktopResolvedEmbeddedConfig(
                content = "[Rule]\nFINAL,PROXY\n",
                sourceUrl = "https://example.com/routing.conf",
                activate = false,
            ),
            nowMillis = 42,
        )

        val applied = assertIs<DesktopSubscriptionRefreshProfileCommit.Applied>(result)
        assertEquals(2, applied.configs.configs.size)
        assertEquals("Manual profile", applied.configs.configs.single { it.sourceUrl.isBlank() }.name)
        assertEquals("From subscription", applied.configs.configs.single { it.sourceUrl.isNotBlank() }.name)
    }

    @Test
    fun profileRebaseDoesNotOverwriteProfileEditedDuringFetch() {
        val subscriptions = DesktopSubscriptionLibraries.addOrReplace(
            DesktopSubscriptionLibrary(), "https://example.com/sub", "SKIPI",
        )
        val subscription = subscriptions.subscriptions.single()
        val sourceUrl = "https://example.com/routing.conf"
        val initialConfigs = DesktopConfigLibraries.put(
            DesktopConfigLibrary(),
            name = "Original",
            content = "[Rule]\nFINAL,PROXY\n",
            sourceUrl = sourceUrl,
        )
        val baseline = DesktopSubscriptionRefreshCommitter.captureBaseline(
            subscriptions,
            DesktopServerLibrary(),
            initialConfigs,
            subscription.url,
        )
        val latestConfigs = DesktopConfigLibraries.update(
            initialConfigs,
            id = initialConfigs.configs.single().id,
            name = "Edited by user",
            content = "[Rule]\nFINAL,DIRECT\n",
            sourceUrl = sourceUrl,
            updateLocked = false,
            lastUpdatedAtMillis = 1,
        )

        val result = DesktopSubscriptionRefreshCommitter.rebaseEmbeddedProfile(
            baseline = baseline,
            latestConfigs = latestConfigs,
            subscription = subscription,
            metadata = SubscriptionMetadata(profileTitle = "From subscription"),
            resolved = DesktopResolvedEmbeddedConfig(
                content = "[Rule]\nFINAL,PROXY\n",
                sourceUrl = sourceUrl,
                activate = false,
            ),
            nowMillis = 42,
        )

        assertEquals(DesktopSubscriptionRefreshProfileCommit.Conflict, result)
    }

    private fun proxy(host: String, name: String) = ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@$host:443?security=tls#$name",
    )
}
