// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.subscription.SubscriptionMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopProxyImportCommitterTest {
    @Test
    fun addsDirectServersAsManualAndPreservesTheSelectedExistingServer() {
        val existingManual = proxy("manual.example", "Manual")
        val subscriptionServer = proxy("subscription.example", "Subscription")
        val importedFirst = proxy("first.example", "First import")
        val importedSecond = proxy("second.example", "Second import")
        val withManual = DesktopServerLibraries.add(DesktopServerLibrary(), existingManual)
        val initial = DesktopServerLibraries.replaceSubscriptionServers(
            withManual,
            subscriptionId = 40,
            servers = listOf(subscriptionServer),
        )
        val selected = DesktopServerLibraries.select(initial, serverId = 2)

        val result = DesktopProxyImportCommitter.commit(
            plan = DesktopProxyImportPlan(
                source = DesktopProxyImportSource.Clipboard,
                actions = listOf(DesktopProxyImportAction.AddServers(listOf(importedFirst, importedSecond))),
            ),
            serverLibrary = selected,
            subscriptionLibrary = DesktopSubscriptionLibrary(),
            configLibrary = DesktopConfigLibrary(),
        )

        assertEquals(2, result.counts.addedServers)
        assertEquals(2, result.serverLibrary.selectedServerId)
        assertEquals(4, result.serverLibrary.servers.size)
        assertEquals(listOf("First import", "Second import"), result.serverLibrary.servers.take(2).map { stored ->
            stored.decode().getOrThrow().getInfo().remarks
        })
        assertTrue(result.serverLibrary.servers.take(2).all { stored -> stored.subscriptionId == null })
        assertEquals(40, result.serverLibrary.servers.single { it.id == 2 }.subscriptionId)
    }

    @Test
    fun selectsTheFirstImportedServerWhenTheCurrentSelectionIsAbsent() {
        val existing = proxy("existing.example", "Existing")
        val imported = proxy("imported.example", "Imported")
        val libraryWithMissingSelection = DesktopServerLibraries
            .add(DesktopServerLibrary(), existing)
            .copy(selectedServerId = 999)

        val result = DesktopProxyImportCommitter.commit(
            plan = DesktopProxyImportPlan(
                source = DesktopProxyImportSource.Text,
                actions = listOf(DesktopProxyImportAction.AddServers(listOf(imported))),
            ),
            serverLibrary = libraryWithMissingSelection,
            subscriptionLibrary = DesktopSubscriptionLibrary(),
            configLibrary = DesktopConfigLibrary(),
        )

        assertEquals(2, result.serverLibrary.selectedServerId)
        assertEquals("Imported", result.serverLibrary.servers.first().decode().getOrThrow().getInfo().remarks)
    }

    @Test
    fun appliesMixedActionsAndKeepsUnrelatedDataWhileReportingAddedAndUpdatedCounts() {
        val existingManual = proxy("manual.example", "Existing manual")
        val imported = proxy("imported.example", "Imported")
        val servers = DesktopServerLibraries.add(DesktopServerLibrary(), existingManual)
        val existingSubscription = DesktopSubscriptionLibraries.addOrReplace(
            library = DesktopSubscriptionLibrary(),
            url = "https://provider.example.com/existing",
            userAgent = "Old agent",
            name = "Existing provider",
            metadata = SubscriptionMetadata(
                profileDescription = "Keep this metadata",
                userInfoReceived = true,
                trafficTotalBytes = 1_024L,
            ),
        )
        val existingConfig = DesktopConfigLibraries.put(
            library = DesktopConfigLibrary(),
            name = "Existing config",
            content = "[General]\nold = true",
            sourceUrl = "https://configs.example.com/existing.conf",
            updateLocked = false,
            lastUpdatedAtMillis = 123L,
        )
        val configs = DesktopConfigLibraries.put(
            library = existingConfig,
            name = "Untouched config",
            content = "[Rule]\nFINAL,DIRECT",
        )

        val result = DesktopProxyImportCommitter.commit(
            plan = DesktopProxyImportPlan(
                source = DesktopProxyImportSource.File,
                actions = listOf(
                    DesktopProxyImportAction.AddServers(listOf(imported)),
                    DesktopProxyImportAction.AddSubscription("https://provider.example.com/existing"),
                    DesktopProxyImportAction.AddSubscription("https://provider.example.com/new"),
                    DesktopProxyImportAction.AddConfig(
                        name = "Renamed config",
                        content = "[General]\nnew = true",
                        sourceUrl = "HTTPS://CONFIGS.EXAMPLE.COM/EXISTING.CONF",
                        updateLocked = true,
                    ),
                    DesktopProxyImportAction.AddConfig(
                        name = "New config",
                        content = "[Rule]\nFINAL,PROXY",
                        sourceUrl = "",
                        updateLocked = false,
                    ),
                ),
            ),
            serverLibrary = servers,
            subscriptionLibrary = existingSubscription,
            configLibrary = configs,
            subscriptionUserAgent = "Desktop import agent",
        )

        assertEquals(1, result.counts.addedServers)
        assertEquals(1, result.counts.addedSubscriptions)
        assertEquals(1, result.counts.updatedSubscriptions)
        assertEquals(1, result.counts.addedConfigs)
        assertEquals(1, result.counts.updatedConfigs)
        assertEquals(3, result.addedCount)
        assertEquals(2, result.updatedCount)
        assertEquals(
            "Добавлено: серверов: 1, подписок: 1, конфигов: 1; обновлено: подписок: 1, конфигов: 1.",
            result.summary,
        )

        assertEquals(2, result.serverLibrary.servers.size)
        assertNull(result.serverLibrary.servers.first().subscriptionId)
        assertEquals("Existing manual", result.serverLibrary.servers.last().decode().getOrThrow().getInfo().remarks)

        val retainedSubscription = result.subscriptionLibrary.subscriptions.single { stored ->
            stored.url == "https://provider.example.com/existing"
        }
        assertEquals("Keep this metadata", retainedSubscription.metadata.description)
        assertEquals(1_024L, retainedSubscription.metadata.trafficTotalBytes)
        assertEquals("Desktop import agent", retainedSubscription.userAgent)
        assertEquals(2, result.subscriptionLibrary.subscriptions.size)

        val updatedConfig = result.configLibrary.configs.single { stored ->
            stored.sourceUrl.equals("https://configs.example.com/existing.conf", ignoreCase = true)
        }
        assertEquals("Renamed config", updatedConfig.name)
        assertEquals("[General]\nnew = true", updatedConfig.content)
        assertTrue(updatedConfig.updateLocked)
        assertEquals(123L, updatedConfig.lastUpdatedAtMillis)
        assertEquals("[Rule]\nFINAL,DIRECT", result.configLibrary.configs.single { it.name == "Untouched config" }.content)
        assertEquals(3, result.configLibrary.configs.size)
    }

    private fun proxy(host: String, remarks: String): ProxyServer<*> = ProxyServer.parse(
        "vless://123e4567-e89b-42d3-a456-426614174000@$host:443?security=tls#$remarks",
    )
}
