// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import app.AppState
import app.CustomResourceFileState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.modes.ColorModeAurora
import app.modes.LanguageModePersian
import app.modes.ColorModeSakura
import app.modes.BackgroundStylePhoto
import app.modes.ConnectionDisplayModeCompact
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ResourceCatalogRecord
import app.skipi.app.model.ResourceDefinition
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.ThemeMode
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.proxy.deleteProxyServerRecords
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.subscription.SubscriptionExpiryReminder
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.ExpiryReminderUnit
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import features.subscription.SubscriptionMetadata
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AndroidAppRepositoryMappingTest {
    @Test
    fun proxyCatalogMappingRoundTripsIdsGroupsServersAndListOrder() {
        val legacyServers = listOf(
            ProxyServerState(
                id = 42,
                server = HTTP(server = "manual.example"),
                groupId = DefaultSubscriptionGroupId,
                latency = "12 ms",
            ),
            ProxyServerState(
                id = 9,
                server = HTTP(server = "subscription.example"),
                groupId = 73,
                latency = "34 ms",
            ),
        )

        val sharedRecords = legacyServers.map { it.toRecord() }
        val restored = sharedRecords.map { it.toAndroidState() }

        assertEquals(listOf(42, 9), sharedRecords.map { it.id })
        assertEquals(listOf(null, 73), sharedRecords.map { it.sourceSubscriptionId })
        assertEquals(legacyServers.map { it.server }, restored.map { it.server })
        assertEquals(listOf(42, 9), restored.map { it.id })
        assertEquals(listOf(DefaultSubscriptionGroupId, 73), restored.map { it.groupId })
    }

    @Test
    fun sharedCatalogWritePreservesLegacyIdsOrderLatencyAndCounters() {
        val original = AppState(
            proxyServers = listOf(
                ProxyServerState(42, HTTP(server = "manual.example"), groupId = DefaultSubscriptionGroupId, latency = "12 ms"),
                ProxyServerState(9, HTTP(server = "provider.example"), groupId = 73, latency = "34 ms"),
            ),
            nextProxyServerId = 88,
            selectedProxyServerId = 9,
        )
        val catalog = original.toProxyServerCatalog().copy(
            servers = listOf(original.proxyServers[1].toRecord(), original.proxyServers[0].toRecord()),
            nextServerId = 91,
            selectedServerId = 42,
        )

        val restored = original.withProxyServerCatalog(catalog)

        assertEquals(listOf(9, 42), restored.proxyServers.map { it.id })
        assertEquals(listOf(73, DefaultSubscriptionGroupId), restored.proxyServers.map { it.groupId })
        assertEquals(listOf("34 ms", "12 ms"), restored.proxyServers.map { it.latency })
        assertEquals(91, restored.nextProxyServerId)
        assertEquals(42, restored.selectedProxyServerId)
    }

    @Test
    fun deletingFromSharedCatalogPreservesAndroidRecordMetadataAndRuntimeState() {
        val original = AppState(
            proxyServers = listOf(
                ProxyServerState(42, HTTP(server = "manual.example"), DefaultSubscriptionGroupId, "12 ms"),
                ProxyServerState(9, HTTP(server = "provider.example"), 73, "34 ms"),
                ProxyServerState(
                    100,
                    StrategyGroup(
                        remarks = "Selected group",
                        proxyServerIds = listOf(42, 9),
                        selectedMemberId = 42,
                        sourceTrafficConfigId = 7,
                        sourcePolicyGroupName = "policy-group",
                    ),
                    groupId = -2,
                    latency = "50 ms",
                ),
                ProxyServerState(
                    101,
                    ChainProxy(remarks = "Chain", proxyServerIds = listOf(42, 9)),
                    groupId = -2,
                    latency = "60 ms",
                ),
            ),
            nextProxyServerId = 188,
            selectedProxyServerId = 42,
            proxyRunning = true,
            localProxyPort = "10991",
        )
        val catalog = original.toProxyServerCatalog()
        val updatedCatalog = deleteProxyServerRecords(catalog, setOf(42))

        val restored = original.withProxyServerCatalog(updatedCatalog)

        assertEquals(listOf(9, 100, 101), restored.proxyServers.map { it.id })
        assertEquals(listOf(73, -2, -2), restored.proxyServers.map { it.groupId })
        assertEquals(listOf("34 ms", "50 ms", "60 ms"), restored.proxyServers.map { it.latency })
        assertEquals(updatedCatalog.servers.map { it.server }, restored.proxyServers.map { it.server })
        assertEquals(188, restored.nextProxyServerId)
        assertEquals(9, restored.selectedProxyServerId)
        assertEquals(original.proxyRunning, restored.proxyRunning)
        assertEquals(original.localProxyPort, restored.localProxyPort)
    }

    @Test
    fun sharedCatalogWriteRejectsDuplicateIdsWithoutChangingLegacyState() {
        val original = AppState(
            proxyServers = listOf(ProxyServerState(4, HTTP(server = "one.example"), groupId = DefaultSubscriptionGroupId)),
            nextProxyServerId = 10,
        )
        val invalid = ProxyServerCatalog(
            servers = listOf(original.proxyServers.single().toRecord(), original.proxyServers.single().toRecord()),
            nextServerId = 11,
            selectedServerId = 4,
        )

        val error = kotlin.runCatching { original.withProxyServerCatalog(invalid) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(listOf(4), original.proxyServers.map { it.id })
        assertEquals(10, original.nextProxyServerId)
    }

    @Test
    fun appearancePreferencesRoundTripWithoutLosingColorsOrBackgroundSettings() {
        val original = AppState(
            colorMode = ColorModeSakura,
            seedIndex = 17,
            customMaterialYouSeed = 0x1020304050607080L,
            enableCustomColors = true,
            customAccentColor = 0xFF123456,
            customBackgroundColor = 0xFF223344,
            customSurfaceColor = 0xFF334455,
            customSurfaceVariantColor = 0xFF445566,
            customTextColor = 0xFF556677,
            customTextSecondaryColor = 0xFF667788,
            customStatusRunningColor = 0xFF778899,
            customStatusStoppedColor = 0xFF8899AA,
            customPingFastColor = 0xFF99AABB,
            customPingMediumColor = 0xFFAABBCC,
            customPingSlowColor = 0xFFBBCCDD,
            customCategoryIconColor = 0xFFCCDDEE,
            customProtocolVlessColor = 0xFFDDEEFF,
            customProtocolVmessColor = 0xFF112233,
            customProtocolHysteria2Color = 0xFF223355,
            customProtocolTrojanColor = 0xFF334466,
            customProtocolShadowsocksColor = 0xFF445577,
            customProtocolWireguardColor = 0xFF556688,
            customProtocolSocksColor = 0xFF667799,
            customProtocolHttpColor = 0xFF7788AA,
            customProtocolStrategyColor = 0xFF8899BB,
            customProtocolChainColor = 0xFF99AACC,
            customProtocolJsonColor = 0xFFAABBCC,
            fontFamilyMode = 4,
            fontSizeMode = 115,
            fontWeightMode = 5,
            backgroundStyle = BackgroundStylePhoto,
            backgroundPhotoDimPercent = 63,
            bottomBarSize = 2,
            connectionDisplayMode = ConnectionDisplayModeCompact,
            pinConnectionPanelOnHome = true,
            enableSubscriptionSwipe = false,
            classicShowFloatingPowerButton = true,
            enableDeletionConfirmation = false,
            showServerSearch = true,
        )

        val shared = original.toPersistedSettings()
        val restored = AppState().applyPersistedSettings(shared)

        assertEquals("sakura", shared.appearance.themeVariant)
        assertEquals(original.toPersistedSettings(), restored.toPersistedSettings())
        assertEquals(0xFF223344, shared.appearance.customBackgroundColor)
        assertEquals(BackgroundStylePhoto, shared.appearance.backgroundStyle)
        assertEquals(63, shared.appearance.backgroundPhotoDimPercent)
        assertEquals(ConnectionDisplayModeCompact, shared.home.connectionDisplayMode)
        assertTrue(shared.home.pinConnectionPanel)
        assertFalse(shared.home.subscriptionSwipeEnabled)
        assertTrue(shared.home.showFloatingPowerButton)
        assertFalse(shared.home.confirmDeletion)
        assertTrue(shared.home.showServerSearch)
    }

    @Test
    fun persistedSettingsUpdateKeepsAndroidOnlyAppearanceAndLanguageChoices() {
        val original = AppState(
            colorMode = ColorModeAurora,
            languageMode = LanguageModePersian,
            enableMaterialYou = true,
            enableHaptics = false,
            hasCompletedOnboarding = true,
        )

        val updated = original.applyPersistedSettings(
            original.toPersistedSettings().copy(
                appearance = AppearanceSettings(themeMode = ThemeMode.Named, dynamicColors = false),
                application = original.toPersistedSettings().application.copy(hapticsEnabled = true),
            ),
        )

        assertEquals(ColorModeAurora, updated.colorMode)
        assertEquals(LanguageModePersian, updated.languageMode)
        assertFalse(updated.enableMaterialYou)
        assertTrue(updated.enableHaptics)
        assertTrue(updated.hasCompletedOnboarding)
    }

    @Test
    fun subscriptionUpdateMapsCompleteOptionsAndPreservesAbsentMetadata() {
        val existing = SubscriptionGroupState(
            id = 8,
            name = "old",
            url = "https://old.example/sub",
            userAgent = "Android custom agent",
            updateInterval = "12",
            hwid = "device-key",
            ageSecretKey = "secret",
            updateViaProxy = true,
            autoOverrideRules = false,
            enabled = true,
            builtIn = false,
            notifyOnExpiry = false,
            customExpiryReminders = listOf(SubscriptionExpiryReminder(3, features.subscription.ExpiryReminderUnit.Days)),
            profileTitle = "Provider title",
            trafficTotalBytes = 9876,
        )

        val mapped = SubscriptionRecord(
            id = 8,
            title = "renamed",
            url = "https://new.example/sub",
            userAgent = existing.userAgent,
            updateInterval = existing.updateInterval,
            hwid = existing.hwid,
            ageSecretKey = existing.ageSecretKey,
            updateViaProxy = existing.updateViaProxy,
            autoOverrideRules = existing.autoOverrideRules,
            enabled = existing.enabled,
            builtIn = existing.builtIn,
            metadata = null,
            lastUpdatedAtMillis = null,
            notifyOnExpiry = existing.notifyOnExpiry,
            customExpiryReminders = existing.customExpiryReminders,
        ).toAndroidGroup(existing)

        assertEquals("renamed", mapped.name)
        assertEquals("https://new.example/sub", mapped.url)
        assertEquals("Android custom agent", mapped.userAgent)
        assertEquals("12", mapped.updateInterval)
        assertEquals("device-key", mapped.hwid)
        assertEquals("secret", mapped.ageSecretKey)
        assertTrue(mapped.updateViaProxy)
        assertFalse(mapped.autoOverrideRules)
        assertFalse(mapped.notifyOnExpiry)
        assertEquals(existing.customExpiryReminders, mapped.customExpiryReminders)
        assertEquals("Provider title", mapped.profileTitle)
        assertEquals(9876, mapped.trafficTotalBytes)
    }

    @Test
    fun subscriptionRecordRoundTripsAllAndroidGroupFields() {
        val original = SubscriptionGroupState(
            id = DefaultSubscriptionGroupId,
            name = "Default",
            url = "https://example.com/default-sub",
            userAgent = "Android custom agent",
            updateInterval = "12",
            hwid = "legacy-group-hwid",
            ageSecretKey = "AGE-SECRET-KEY-1EXAMPLE",
            updateViaProxy = true,
            autoOverrideRules = false,
            enabled = true,
            builtIn = true,
            lastUpdatedAtMillis = 1_700_000_000_000,
            profileTitle = "Provider title",
            announce = "Maintenance tonight",
            supportUrl = "https://example.com/support",
            supportEmail = "support@example.com",
            profileWebPageUrl = "https://example.com/home",
            announceUrl = "https://example.com/announce",
            trafficUploadBytes = 100,
            trafficDownloadBytes = 200,
            trafficTotalBytes = 1_000,
            trafficExpireAtSeconds = 2_000,
            notifyOnExpiry = false,
            customExpiryReminders = listOf(SubscriptionExpiryReminder(3, ExpiryReminderUnit.Days)),
        )

        val expected = SubscriptionRecord(
            id = original.id,
            title = original.name,
            url = original.url,
            userAgent = original.userAgent,
            updateInterval = original.updateInterval,
            hwid = original.hwid,
            ageSecretKey = original.ageSecretKey,
            updateViaProxy = original.updateViaProxy,
            autoOverrideRules = original.autoOverrideRules,
            enabled = original.enabled,
            builtIn = original.builtIn,
            metadata = SubscriptionMetadata(
                profileTitle = original.profileTitle,
                announce = original.announce,
                supportUrl = original.supportUrl,
                supportEmail = original.supportEmail,
                profileWebPageUrl = original.profileWebPageUrl,
                announceUrl = original.announceUrl,
                userInfoReceived = true,
                trafficUploadBytes = original.trafficUploadBytes,
                trafficDownloadBytes = original.trafficDownloadBytes,
                trafficTotalBytes = original.trafficTotalBytes,
                trafficExpireAtSeconds = original.trafficExpireAtSeconds,
            ),
            lastUpdatedAtMillis = original.lastUpdatedAtMillis,
            notifyOnExpiry = original.notifyOnExpiry,
            customExpiryReminders = original.customExpiryReminders,
        )

        val mapped = original.toSubscriptionRecord()

        assertEquals(expected, mapped)
        assertEquals(original, mapped.toAndroidGroup(existing = null))
    }

    @Test
    fun trafficConfigUpdateKeepsAndroidProfileSettings() {
        val existing = TrafficConfigState(
            id = 4,
            name = "old",
            rawConfig = "[General]\nloglevel = warning",
            sourceUrl = "https://provider.example/old",
            updateLocked = false,
            lastUpdatedAtMillis = 1234,
            autoUpdate = true,
            updateInterval = "6",
            androidSettings = TrafficConfigAndroidSettings(enableFakeDns = true),
            resourceSettings = TrafficConfigResourceSettings(userAgent = "profile agent"),
        )

        val mapped = TrafficConfigRecord(
            id = 4,
            name = "renamed",
            rawDocument = "[General]\nloglevel = error",
            sourceUrl = "https://provider.example/new",
            locked = true,
        ).toAndroidTrafficConfig(existing)

        assertEquals("renamed", mapped.name)
        assertEquals("[General]\nloglevel = error", mapped.rawConfig)
        assertEquals("https://provider.example/new", mapped.sourceUrl)
        assertTrue(mapped.updateLocked)
        assertEquals(1234, mapped.lastUpdatedAtMillis)
        assertTrue(mapped.autoUpdate)
        assertEquals("6", mapped.updateInterval)
        assertTrue(mapped.androidSettings.enableFakeDns)
        assertEquals("profile agent", mapped.resourceSettings.userAgent)
    }

    @Test
    fun routingAndResourceCatalogMappingsKeepRepresentableFields() {
        val state = AppState(
            routeDomainStrategy = 3,
            defaultRouteOutboundTag = "proxy",
            resourceFileSource = 2,
            resourceFileUserAgent = "resource agent",
            customResourceFiles = listOf(CustomResourceFileState(5, "custom", "https://example.test/geo")),
        )

        assertEquals(3, state.toRoutingConfigRecord().domainStrategy)
        assertEquals("proxy", state.toRoutingConfigRecord().defaultOutboundTag)
        assertEquals(
            ResourceCatalogRecord(
                sourceId = "2",
                userAgent = "resource agent",
                customResources = listOf(ResourceDefinition(5, "custom", "https://example.test/geo")),
            ),
            state.toResourceCatalogRecord(),
        )
    }
}
