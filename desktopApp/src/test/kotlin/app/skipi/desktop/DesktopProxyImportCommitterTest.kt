// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.subscription.SubscriptionMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopProxyImportCommitterTest {
    @Test
    fun countsPlannedServerBatchesWithoutReturningADetachedCatalog() {
        val importedFirst = proxy("first.example", "First import")
        val importedSecond = proxy("second.example", "Second import")
        val importedThird = proxy("third.example", "Third import")

        val result = DesktopProxyImportCommitter.commit(
            plan = DesktopProxyImportPlan(
                source = DesktopProxyImportSource.Clipboard,
                actions = listOf(
                    DesktopProxyImportAction.AddServers(listOf(importedFirst, importedSecond)),
                    DesktopProxyImportAction.AddServers(listOf(importedThird)),
                ),
            ),
            subscriptionLibrary = DesktopSubscriptionLibrary(),
            configLibrary = DesktopConfigLibrary(),
        )

        assertEquals(3, result.counts.addedServers)
        assertEquals(3, result.addedCount)
    }

    @Test
    fun appliesMixedActionsAndKeepsUnrelatedDataWhileReportingAddedAndUpdatedCounts() {
        val imported = proxy("imported.example", "Imported")
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
