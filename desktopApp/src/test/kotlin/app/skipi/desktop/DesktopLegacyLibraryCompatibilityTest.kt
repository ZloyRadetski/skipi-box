// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.VLESS
import features.subscription.ExpiryReminderUnit
import features.subscription.SubscriptionExpiryReminder
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Raw persisted-file fixtures guard compatibility independently of current writers. */
class DesktopLegacyLibraryCompatibilityTest {
    @Test
    fun readsAndRoundTripsLegacySettingsWithDefaultsAndUnknownFields() {
        withFixture("settings.json") { fixture ->
            val path = fixture.resolve("settings.json")
            val loaded = DesktopSettingsLibraries.load(path).getOrThrow()

            assertEquals(23_456, loaded.localProxyPort)
            assertEquals(23_457, loaded.localHttpProxyPort)
            assertEquals("0.0.0.0", loaded.localProxyListenAddress)
            assertEquals("info", loaded.coreLogLevel)
            assertEquals("Legacy desktop agent", loaded.subscriptionUserAgent)
            assertEquals(45, loaded.subscriptionFetchTimeoutSeconds)
            assertEquals(DesktopThemeMode.Amoled, loaded.themeMode)
            assertTrue(loaded.compactHome)
            assertFalse(loaded.showTunnelMemory)
            assertTrue(loaded.confirmDeletion)
            assertTrue(loaded.sendDeviceHeaders)
            assertEquals("", loaded.installationUuid)

            val roundTripPath = fixture.resolve("round-trip-settings.json")
            DesktopSettingsLibraries.save(roundTripPath, loaded).getOrThrow()
            assertEquals(loaded, DesktopSettingsLibraries.load(roundTripPath).getOrThrow())
        }
    }

    @Test
    fun readsAndRoundTripsLegacyServersWithSelectionOrderAndSharedPayloads() {
        withFixture("servers.json") { fixture ->
            val loaded = DesktopServerLibraries.load(fixture.resolve("servers.json")).getOrThrow()

            assertEquals(4, loaded.selectedServerId)
            assertEquals(listOf(12, 4), loaded.servers.map { it.id })
            assertEquals(3, loaded.servers.first().subscriptionId)
            assertNull(loaded.servers.last().subscriptionId)

            val subscribed = assertIs<VLESS>(loaded.servers[0].decode().getOrThrow())
            assertEquals("Legacy subscribed", subscribed.remarks)
            assertEquals("edge.example", subscribed.server)
            assertEquals("443", subscribed.port)
            assertEquals("ws", subscribed.parms.type)
            assertEquals("tls", subscribed.parms.security)
            assertEquals("/legacy", subscribed.parms.path)
            assertEquals("cdn.example", subscribed.parms.host)

            val manual = assertIs<VLESS>(loaded.servers[1].decode().getOrThrow())
            assertEquals("Manual legacy", manual.remarks)
            assertEquals("manual.example", manual.server)

            val roundTripPath = fixture.resolve("round-trip-servers.json")
            DesktopServerLibraries.save(roundTripPath, loaded).getOrThrow()
            val restored = DesktopServerLibraries.load(roundTripPath).getOrThrow()
            assertEquals(4, restored.selectedServerId)
            assertEquals(listOf(12, 4), restored.servers.map { it.id })
            assertEquals(
                loaded.servers.map { it.decode().getOrThrow() },
                restored.servers.map { it.decode().getOrThrow() },
            )
            assertEquals(loaded.servers.map { it.subscriptionId }, restored.servers.map { it.subscriptionId })
        }
    }

    @Test
    fun readsAndRoundTripsLegacyConfigsWithSelectionOrderAndFieldDefaults() {
        withFixture("configs.json") { fixture ->
            val loaded = DesktopConfigLibraries.load(fixture.resolve("configs.json")).getOrThrow()

            assertEquals(41, loaded.selectedConfigId)
            assertEquals(listOf(30, 41), loaded.configs.map { it.id })
            assertEquals("[General]\nskipi-show-on-home = true", loaded.configs[0].content)
            assertEquals("", loaded.configs[0].sourceUrl)
            assertFalse(loaded.configs[0].updateLocked)
            assertEquals(0L, loaded.configs[0].lastUpdatedAtMillis)
            assertEquals("https://example.com/work.conf", loaded.configs[1].sourceUrl)
            assertTrue(loaded.configs[1].updateLocked)
            assertEquals(1_700_000_000_123L, loaded.configs[1].lastUpdatedAtMillis)

            val roundTripPath = fixture.resolve("round-trip-configs.json")
            DesktopConfigLibraries.save(roundTripPath, loaded).getOrThrow()
            assertEquals(loaded, DesktopConfigLibraries.load(roundTripPath).getOrThrow())
        }
    }

    @Test
    fun readsAndRoundTripsLegacySubscriptionsWithDefaultsAndOrder() {
        withFixture("subscriptions.json") { fixture ->
            val loaded = DesktopSubscriptionLibraries.load(fixture.resolve("subscriptions.json")).getOrThrow()

            assertEquals(listOf(9, 4), loaded.subscriptions.map { it.id })
            val legacy = loaded.subscriptions[0]
            assertEquals("Legacy provider", legacy.name)
            assertEquals("Fetched before newer metadata fields existed", legacy.metadata.description)
            assertEquals(100L, legacy.metadata.trafficUploadBytes)
            assertEquals(250L, legacy.metadata.trafficDownloadBytes)
            assertEquals(1_000L, legacy.metadata.trafficTotalBytes)
            assertTrue(legacy.enabled)
            assertEquals("", legacy.updateInterval)
            assertEquals("", legacy.ageSecretKey)
            assertFalse(legacy.updateViaProxy)
            assertTrue(legacy.autoOverrideRules)
            assertTrue(legacy.notifyOnExpiry)
            assertNull(legacy.customExpiryReminders)

            val current = loaded.subscriptions[1]
            assertFalse(current.enabled)
            assertEquals("0.25", current.updateInterval)
            assertEquals("AGE-SECRET-KEY-1EXAMPLE", current.ageSecretKey)
            assertTrue(current.updateViaProxy)
            assertFalse(current.autoOverrideRules)
            assertFalse(current.notifyOnExpiry)
            assertEquals(
                listOf(
                    SubscriptionExpiryReminder(12, ExpiryReminderUnit.Hours),
                    SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration),
                ),
                current.customExpiryReminders,
            )
            assertEquals("Maintenance window", current.metadata.announce)
            assertEquals(1_900_000_000L, current.metadata.trafficExpireAtSeconds)
            assertTrue(current.metadata.embeddedConfigActivate)

            val roundTripPath = fixture.resolve("round-trip-subscriptions.json")
            DesktopSubscriptionLibraries.save(roundTripPath, loaded).getOrThrow()
            val restored = DesktopSubscriptionLibraries.load(roundTripPath).getOrThrow()
            assertEquals(loaded, restored)
            assertEquals(listOf(9, 4), restored.subscriptions.map { it.id })
        }
    }

    private fun withFixture(name: String, block: (java.nio.file.Path) -> Unit) {
        val resource = checkNotNull(javaClass.getResource("/fixtures/desktop-legacy/$name")) {
            "Missing desktop legacy fixture: $name"
        }
        val directory = Files.createTempDirectory("skipi-legacy-$name-")
        try {
            val fixture = directory.resolve(name)
            Files.writeString(fixture, resource.readText())
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
