// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.ThemeMode
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.model.ProxySelectionSettings
import app.skipi.app.model.TrafficSelectionSettings
import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import features.proxy.server.model.ProxyServer
import features.subscription.StoredSubscription
import features.subscription.StoredSubscriptionMetadata
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopApplicationRepositoriesTest {
    @Test
    fun trafficConfigRepositoryMapsAndPersistsWithoutChangingLibraryShape(): Unit = runBlocking {
        var library = DesktopConfigLibrary(
            selectedConfigId = 7,
            configs = listOf(ConfigProfile(7, "Rules", "payload", "https://example.com/rules", true, 42)),
        )
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { library = it; Result.success(Unit) },
            publishLibrary = { library = it },
        )

        assertEquals(
            TrafficConfigRecord(7, "Rules", "payload", "https://example.com/rules", locked = true),
            repository.configs.value.single(),
        )
        repository.upsert(TrafficConfigRecord(7, "Edited", "new document", locked = false))
        assertEquals(7, library.selectedConfigId)
        assertEquals(ConfigProfile(7, "Edited", "new document", "", false, 42), library.configs.single())

        repository.upsert(TrafficConfigRecord(12, "Additional", "another document"))
        assertEquals(7, library.selectedConfigId)
        assertEquals(listOf(7, 12), library.configs.map { it.id })
        assertEquals(42, library.configs.first().lastUpdatedAtMillis)
        val disabledUpdate = runCatching {
            repository.upsert(TrafficConfigRecord(8, "Disabled", "payload", enabled = false))
        }
        assertIs<IllegalArgumentException>(disabledUpdate.exceptionOrNull())

        repository.remove(7)
        assertEquals(12, library.selectedConfigId)
        assertEquals(listOf(12), library.configs.map { it.id })
    }

    @Test
    fun trafficConfigUpsertRetainsLegacyAppendOrderAndActiveId(): Unit = runBlocking {
        var library = DesktopConfigLibrary(
            selectedConfigId = 7,
            configs = listOf(
                ConfigProfile(7, "Active", "original"),
                ConfigProfile(9, "Other", "other"),
            ),
        )
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { library = it; Result.success(Unit) },
            publishLibrary = { library = it },
        )

        repository.upsert(TrafficConfigRecord(7, "Active edited", "updated"))

        assertEquals(listOf(9, 7), library.configs.map { it.id })
        assertEquals(7, library.selectedConfigId)
    }

    @Test
    fun subscriptionRepositoryPreservesHostOnlyFieldsAndRejectsUnconfiguredRefresh(): Unit = runBlocking {
        var library = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                StoredSubscription(
                    id = 3,
                    url = "https://example.com/sub",
                    name = "Provider",
                    updateViaProxy = true,
                    ageSecretKey = "secret",
                    metadata = StoredSubscriptionMetadata(description = "old", lastUpdatedAtMillis = 100),
                ),
            ),
        )
        val repository = DesktopSubscriptionRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { library = it; Result.success(Unit) },
            publishLibrary = { library = it },
        )

        assertEquals("old", repository.subscriptions.value.single().metadata?.profileDescription)
        repository.upsert(
            SubscriptionRecord(
                id = 3,
                title = "Renamed",
                url = "https://example.com/sub",
                enabled = false,
                metadata = SubscriptionMetadata(profileDescription = "new"),
                lastUpdatedAtMillis = 200,
            ),
        )
        val persisted = library.subscriptions.single()
        assertEquals("Renamed", persisted.name)
        assertFalse(persisted.enabled)
        assertTrue(persisted.updateViaProxy)
        assertEquals("secret", persisted.ageSecretKey)
        assertEquals("new", persisted.metadata.description)
        assertEquals(200, persisted.metadata.lastUpdatedAtMillis)

        val refresh = repository.refresh(3)
        assertTrue(refresh.isFailure)
        assertIs<UnsupportedOperationException>(refresh.exceptionOrNull())

        repository.upsert(
            SubscriptionRecord(
                id = 4,
                title = "New provider",
                url = "https://example.com/new",
                metadata = SubscriptionMetadata(profileDescription = "created metadata"),
            ),
        )
        assertEquals("created metadata", library.subscriptions.single { it.id == 4 }.metadata.description)
    }

    @Test
    fun settingsRepositoryMapsThemeAndSelectionsAndPreservesDesktopOnlySettings(): Unit = runBlocking {
        var settings = DesktopAppSettings(
            themeMode = DesktopThemeMode.Sakura,
            localProxyPort = 10808,
            installationUuid = "stable-id",
        )
        var servers = DesktopServerLibrary(selectedServerId = 4)
        var configs = DesktopConfigLibrary(selectedConfigId = 9)
        val proxy = ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@proxy.example:443")
        servers = DesktopServerLibraries.add(servers, proxy).copy(selectedServerId = 4)
        configs = configs.copy(configs = listOf(ConfigProfile(9, "Profile", "rules")))

        val repository = DesktopSettingsRepository(
            readSettings = { settings },
            saveSettings = { settings = it; Result.success(Unit) },
            publishSettings = { settings = it },
            readServers = { servers },
            saveServers = { servers = it; Result.success(Unit) },
            publishServers = { servers = it },
            readConfigs = { configs },
            saveConfigs = { configs = it; Result.success(Unit) },
            publishConfigs = { configs = it },
        )
        assertEquals(ThemeMode.Named, repository.state.value.appearance.themeMode)
        assertEquals(4, repository.state.value.proxy.selectedServerId)
        assertEquals(9, repository.state.value.traffic.activeConfigId)

        repository.update {
            it.copy(
                appearance = it.appearance.copy(themeMode = ThemeMode.Light),
                proxy = ProxySelectionSettings(selectedServerId = servers.servers.single().id),
                traffic = TrafficSelectionSettings(activeConfigId = null),
            )
        }
        assertEquals(DesktopThemeMode.Light, settings.themeMode)
        assertEquals("stable-id", settings.installationUuid)
        assertEquals(servers.servers.single().id, servers.selectedServerId)
        assertEquals(null, configs.selectedConfigId)
        assertEquals(ThemeMode.Light, repository.state.value.appearance.themeMode)
    }

    @Test
    fun appearanceSettingsRoundTripThroughDesktopJsonModel(): Unit = runBlocking {
        var settings = DesktopAppSettings(themeMode = DesktopThemeMode.Sakura)
        var servers = DesktopServerLibrary()
        var configs = DesktopConfigLibrary()
        val repository = DesktopSettingsRepository(
            readSettings = { settings },
            saveSettings = { settings = it; Result.success(Unit) },
            publishSettings = { settings = it },
            readServers = { servers },
            saveServers = { servers = it; Result.success(Unit) },
            publishServers = { servers = it },
            readConfigs = { configs },
            saveConfigs = { configs = it; Result.success(Unit) },
            publishConfigs = { configs = it },
        )

        repository.update { current ->
            current.copy(
                appearance = current.appearance.copy(
                    customColorsEnabled = true,
                    customAccentColor = 0xFF123456,
                    customBackgroundColor = 0xFF223344,
                    backgroundStyle = 1,
                    backgroundPhotoDimPercent = 71,
                    fontFamilyMode = 4,
                    fontSizeMode = 115,
                    fontWeightMode = 5,
                ),
                home = current.home.copy(
                    connectionDisplayMode = 0,
                    pinConnectionPanel = true,
                    subscriptionSwipeEnabled = false,
                    showFloatingPowerButton = true,
                    confirmDeletion = false,
                    showServerSearch = true,
                ),
            )
        }

        assertTrue(settings.customColorsEnabled)
        assertEquals(0xFF123456, settings.customAccentColor)
        assertEquals(0xFF223344, settings.customBackgroundColor)
        assertEquals(1, settings.backgroundStyle)
        assertEquals(71, settings.backgroundPhotoDimPercent)
        assertEquals(4, settings.fontFamilyMode)
        assertEquals(115, settings.fontSizeMode)
        assertEquals(5, settings.fontWeightMode)
        assertEquals(0, settings.connectionDisplayMode)
        assertTrue(settings.pinConnectionPanelOnHome)
        assertFalse(settings.enableSubscriptionSwipe)
        assertTrue(settings.classicShowFloatingPowerButton)
        assertFalse(settings.confirmDeletion)
        assertTrue(settings.showServerSearch)
        assertEquals(settings.customBackgroundColor, repository.state.value.appearance.customBackgroundColor)
        assertEquals(settings.backgroundPhotoDimPercent, repository.state.value.appearance.backgroundPhotoDimPercent)
    }

    @Test
    fun appearanceStateAdapterPreservesDesktopSettingsAndMapsSharedHomeOptions() {
        val original = DesktopAppSettings(
            localProxyPort = 10808,
            localHttpProxyPort = 10809,
            useSystemProxy = false,
            localProxyListenAddress = "0.0.0.0",
            subscriptionUserAgent = "Desktop Agent",
            installationUuid = "stable-device-id",
            themeMode = DesktopThemeMode.Sakura,
            compactHome = true,
            showTunnelMemory = false,
            pinConnectionPanelOnHome = true,
            enableAllProxyGroup = true,
            showServerSearch = true,
            enableSubscriptionSwipe = false,
            proxyServerListColumns = 3,
            confirmDeletion = false,
            customColorsEnabled = true,
            customAccentColor = 0xFF123456,
            connectionDisplayMode = 0,
            routeDomainStrategy = 2,
            defaultRouteOutboundTag = "direct",
        )

        val shared = original.toAppearanceSettingsState()
        val proxy = original.toLocalProxySettingsState()
        assertFalse(proxy.dynamicPort)
        assertTrue(proxy.listenAllInterfaces)
        assertEquals("10808", proxy.port)
        assertEquals("0.0.0.0:10808", proxy.socksEndpoint)
        assertFalse(proxy.authenticationEnabled)

        val subscriptions = original.toSubscriptionSettingsState()
        assertEquals(30, subscriptions.fetchTimeoutSeconds)
        assertTrue(subscriptions.deviceHeadersEnabled)
        assertFalse(subscriptions.deletionConfirmationEnabled)
        assertEquals(45, original.withSubscriptionSettingsState(subscriptions.copy(fetchTimeoutSeconds = 45)).subscriptionFetchTimeoutSeconds)

        assertEquals(8, shared.colorMode)
        assertEquals(0, shared.connectionDisplayMode)
        assertEquals(3, shared.proxyServerListLayout)
        assertFalse(shared.enableMaterialYou)

        val updated = original.withAppearanceState(shared.copy(showServerSearch = false))
        assertEquals(DesktopThemeMode.Sakura, updated.themeMode)
        assertFalse(updated.showServerSearch)
        assertEquals(10808, updated.localProxyPort)
        assertEquals(10809, updated.localHttpProxyPort)
        assertFalse(updated.useSystemProxy)
        assertEquals("0.0.0.0", updated.localProxyListenAddress)
        assertEquals("Desktop Agent", updated.subscriptionUserAgent)
        assertEquals("stable-device-id", updated.installationUuid)
        assertFalse(updated.confirmDeletion)
        assertEquals(2, updated.routeDomainStrategy)
        assertEquals("direct", updated.defaultRouteOutboundTag)
        assertEquals(0xFF123456L, updated.customAccentColor)
    }
    @Test
    fun legacySettingsJsonLoadsWithDefaultsForNewAppearanceKeys() {
        val path = Files.createTempFile("skipi-settings-compat", ".json")
        try {
            Files.writeString(path, """{"localProxyPort":10808,"themeMode":"Sakura"}""")

            val loaded = DesktopSettingsLibraries.load(path).getOrThrow()

            assertEquals(10808, loaded.localProxyPort)
            assertEquals(DesktopThemeMode.Sakura, loaded.themeMode)
            assertFalse(loaded.customColorsEnabled)
            assertEquals(null, loaded.customBackgroundColor)
            assertEquals(null, loaded.backgroundStyle)
            assertEquals(null, loaded.backgroundPhotoDimPercent)
            assertEquals(null, loaded.connectionDisplayMode)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
