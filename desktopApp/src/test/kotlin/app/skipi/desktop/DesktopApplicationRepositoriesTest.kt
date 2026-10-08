// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ThemeMode
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.model.ProxySelectionSettings
import app.skipi.app.model.TrafficSelectionSettings
import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import features.proxy.server.model.ProxyServer
import features.subscription.StoredSubscription
import features.subscription.StoredSubscriptionMetadata
import features.subscription.ExpiryReminderUnit
import features.subscription.SubscriptionEmbeddedConfig
import features.subscription.SubscriptionExpiryReminder
import features.subscription.SubscriptionMetadata
import app.skipi.app.subscription.SubscriptionCatalogOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
            subscriptionRecord(
                id = 3,
                title = "Renamed",
                url = "https://example.com/sub",
                enabled = false,
                ageSecretKey = "secret",
                updateViaProxy = true,
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
            subscriptionRecord(
                id = 4,
                title = "New provider",
                url = "https://example.com/new",
                metadata = SubscriptionMetadata(profileDescription = "created metadata"),
            ),
        )
        assertEquals("created metadata", library.subscriptions.single { it.id == 4 }.metadata.description)
    }

    @Test
    fun subscriptionUpsertEditsExistingRecordInPlaceWithoutChangingLibraryOrder(): Unit = runBlocking {
        var library = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                StoredSubscription(id = 3, url = "https://example.com/first", name = "First"),
                StoredSubscription(id = 8, url = "https://example.com/second", name = "Second"),
            ),
        )
        val repository = DesktopSubscriptionRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { library = it; Result.success(Unit) },
            publishLibrary = { library = it },
        )

        repository.upsert(
            subscriptionRecord(
                id = 3,
                title = "First edited",
                url = "https://example.com/first-updated",
                enabled = false,
            ),
        )

        assertEquals(listOf(3, 8), library.subscriptions.map { it.id })
        assertEquals("First edited", library.subscriptions[0].name)
        assertEquals("https://example.com/first-updated", library.subscriptions[0].url)
        assertFalse(library.subscriptions[0].enabled)
        assertEquals("Second", library.subscriptions[1].name)
        assertEquals("https://example.com/second", library.subscriptions[1].url)
    }

    @Test
    fun subscriptionRepositoryPreservesProfileTitleSeparatelyFromDisplayTitle(): Unit = runBlocking {
        var library = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                StoredSubscription(id = 3, url = "https://example.com/sub", name = "Old display title"),
            ),
        )
        val repository = DesktopSubscriptionRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { library = it; Result.success(Unit) },
            publishLibrary = { library = it },
        )

        repository.upsert(
            subscriptionRecord(
                id = 3,
                title = "Display title",
                url = "https://example.com/sub",
                metadata = SubscriptionMetadata(profileTitle = "Provider profile title"),
            ),
        )

        assertEquals("Provider profile title", repository.subscriptions.value.single().metadata?.profileTitle)
        assertEquals("Provider profile title", library.subscriptions.single().metadata.profileTitle)
        assertEquals("Display title", library.subscriptions.single().name)
    }

    @Test
    fun subscriptionRepositoryPreservesExplicitEmptyProfileTitle(): Unit = runBlocking {
        var library = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                StoredSubscription(id = 3, url = "https://example.com/sub", name = "Display title"),
            ),
        )
        val repository = DesktopSubscriptionRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { library = it; Result.success(Unit) },
            publishLibrary = { library = it },
        )

        repository.upsert(
            subscriptionRecord(
                id = 3,
                title = "Display title",
                url = "https://example.com/sub",
                metadata = SubscriptionMetadata(profileTitle = ""),
            ),
        )

        assertEquals(null, repository.subscriptions.value.single().metadata?.profileTitle)
        assertEquals("", library.subscriptions.single().metadata.profileTitle)
        assertEquals("Display title", library.subscriptions.single().name)
    }

    @Test
    fun desktopRepositoryMigratesMissingLegacyCounterAndPersistsCreateCounter() = runBlocking {
        val path = Files.createTempFile("skipi-subscriptions-counter", ".json")
        try {
            Files.writeString(
                path,
                """{"subscriptions":[{"id":1,"url":"https://one.example/sub","name":"ID one"},{"id":4,"url":"https://four.example/sub","name":"ID four"}]}""",
            )
            var library = DesktopSubscriptionLibraries.load(path).getOrThrow()
            assertEquals(null, library.nextSubscriptionId)
            val repository = DesktopSubscriptionRepository(
                initialLibrary = library,
                readLibrary = { library },
                saveLibrary = { updated ->
                    DesktopSubscriptionLibraries.save(path, updated).onSuccess { library = updated }
                },
                publishLibrary = { library = it },
            )

            assertEquals(5, repository.catalog.value.nextSubscriptionId)
            repository.updateCatalog { catalog ->
                SubscriptionCatalogOperations.createEditor(
                    catalog = catalog,
                    proxyCatalog = ProxyServerCatalog(),
                    draft = subscriptionRecord(
                        id = 1,
                        title = "Manual folder",
                        url = "",
                    ),
                ).catalog
            }

            assertEquals(listOf(1, 4, 5), library.subscriptions.map { it.id })
            assertEquals(6, repository.catalog.value.nextSubscriptionId)
            assertEquals(6, library.nextSubscriptionId)
            assertEquals(6, DesktopSubscriptionLibraries.load(path).getOrThrow().nextSubscriptionId)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun desktopRemoveMigratesLegacyCounterBeforeTheNextCreate() = runBlocking {
        var library = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                StoredSubscription(id = 1, url = "", name = "Manual"),
                StoredSubscription(id = 4, url = "https://four.example/sub", name = "Removed high ID"),
            ),
        )
        val repository = DesktopSubscriptionRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { library = it; Result.success(Unit) },
            publishLibrary = { library = it },
            removeLinkedServers = {},
        )

        repository.remove(4)
        assertEquals(5, library.nextSubscriptionId)

        repository.updateCatalog { current ->
            SubscriptionCatalogOperations.createEditor(
                catalog = current,
                proxyCatalog = ProxyServerCatalog(),
                draft = subscriptionRecord(id = 1, title = "After deletion", url = ""),
            ).catalog
        }

        assertEquals(listOf(1, 5), library.subscriptions.map { it.id })
        assertEquals(6, library.nextSubscriptionId)
    }

    @Test
    fun desktopRepositoryRejectsRemovingBuiltInSubscriptionBeforeHostCleanup() = runBlocking {
        var library = DesktopSubscriptionLibrary(
            subscriptions = listOf(StoredSubscription(id = 1, url = "", name = "Default", builtIn = true)),
        )
        var removeLinkedCalls = 0
        var saveCalls = 0
        val repository = DesktopSubscriptionRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { saveCalls++; library = it; Result.success(Unit) },
            publishLibrary = { library = it },
            removeLinkedServers = { removeLinkedCalls++ },
        )

        val failure = runCatching { repository.remove(1) }.exceptionOrNull()

        assertIs<IllegalArgumentException>(failure)
        assertEquals(0, removeLinkedCalls)
        assertEquals(0, saveCalls)
        assertEquals(1, library.subscriptions.single().id)
    }

    @Test
    fun concurrentSubscriptionCreatesUseLatestCounterWithoutLosingRows() = runBlocking {
        val initialLibrary = DesktopSubscriptionLibrary(
            subscriptions = listOf(StoredSubscription(id = 1, url = "", name = "Manual")),
            nextSubscriptionId = 2,
        )
        val library = AtomicReference(initialLibrary)
        val ready = CountDownLatch(2)
        val startTogether = CountDownLatch(1)
        val repository = DesktopSubscriptionRepository(
            initialLibrary = initialLibrary,
            readLibrary = {
                val snapshot = library.get()
                Thread.sleep(100)
                snapshot
            },
            saveLibrary = { updated -> library.set(updated); Result.success(Unit) },
            publishLibrary = library::set,
        )

        val creates = listOf("First", "Second").map { title ->
            async(Dispatchers.IO) {
                ready.countDown()
                check(startTogether.await(5, TimeUnit.SECONDS))
                repository.updateCatalog { current ->
                    SubscriptionCatalogOperations.createEditor(
                        catalog = current,
                        proxyCatalog = ProxyServerCatalog(),
                        draft = subscriptionRecord(id = 1, title = title, url = ""),
                    ).catalog
                }
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        startTogether.countDown()
        creates.awaitAll()

        val saved = library.get()
        assertEquals(listOf(1, 2, 3), saved.subscriptions.map { it.id })
        assertEquals(4, saved.nextSubscriptionId)
        assertEquals(setOf("First", "Second"), saved.subscriptions.drop(1).map { it.name }.toSet())
    }

    @Test
    fun subscriptionRepositoryRoundTripsProviderOptionsAndDoesNotReserveDesktopIdOne(): Unit = runBlocking {
        val reminders = listOf(SubscriptionExpiryReminder(12, ExpiryReminderUnit.Hours))
        val metadata = StoredSubscriptionMetadata(
            description = "Premium nodes",
            announce = "Maintenance tonight",
            supportUrl = "https://example.com/support",
            supportEmail = "support@example.com",
            profileWebPageUrl = "https://example.com/home",
            announceUrl = "https://example.com/announce",
            trafficUploadBytes = 100,
            trafficDownloadBytes = 200,
            trafficTotalBytes = 1_000,
            trafficExpireAtSeconds = 2_000,
            profileUpdateIntervalHours = "6",
            embeddedConfigPayload = "https://example.com/routing.yaml",
            embeddedConfigActivate = true,
            embeddedConfigIsUrl = true,
            lastUpdatedAtMillis = 1_700_000_000_000,
        )
        var library = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                StoredSubscription(id = 1, url = "https://example.com/ordinary-id-one", name = "ID one"),
                StoredSubscription(
                    id = 42,
                    url = "https://example.com/provider",
                    userAgent = "Provider agent",
                    name = "Provider",
                    metadata = metadata,
                    enabled = false,
                    updateInterval = "6",
                    hwid = "legacy-group-hwid",
                    ageSecretKey = "AGE-SECRET-KEY-1EXAMPLE",
                    updateViaProxy = true,
                    autoOverrideRules = false,
                    builtIn = true,
                    notifyOnExpiry = false,
                    customExpiryReminders = reminders,
                ),
            ),
        )
        val repository = DesktopSubscriptionRepository(
            initialLibrary = library,
            readLibrary = { library },
            saveLibrary = { library = it; Result.success(Unit) },
            publishLibrary = { library = it },
        )

        val ordinary = repository.subscriptions.value.first { it.id == 1 }
        val provider = repository.subscriptions.value.first { it.id == 42 }

        assertFalse(ordinary.builtIn)
        assertEquals("Provider agent", provider.userAgent)
        assertEquals("6", provider.updateInterval)
        assertEquals("legacy-group-hwid", provider.hwid)
        assertEquals("AGE-SECRET-KEY-1EXAMPLE", provider.ageSecretKey)
        assertTrue(provider.updateViaProxy)
        assertFalse(provider.autoOverrideRules)
        assertTrue(provider.builtIn)
        assertFalse(provider.notifyOnExpiry)
        assertEquals(reminders, provider.customExpiryReminders)
        assertEquals(
            SubscriptionMetadata(
                profileTitle = "Provider",
                profileDescription = "Premium nodes",
                announce = "Maintenance tonight",
                supportUrl = "https://example.com/support",
                supportEmail = "support@example.com",
                profileWebPageUrl = "https://example.com/home",
                announceUrl = "https://example.com/announce",
                userInfoReceived = true,
                trafficUploadBytes = 100,
                trafficDownloadBytes = 200,
                trafficTotalBytes = 1_000,
                trafficExpireAtSeconds = 2_000,
                profileUpdateIntervalHours = "6",
                embeddedConfig = SubscriptionEmbeddedConfig(
                    payload = "https://example.com/routing.yaml",
                    activate = true,
                    isUrl = true,
                ),
            ),
            provider.metadata,
        )
        assertEquals(1_700_000_000_000, provider.lastUpdatedAtMillis)

        repository.upsert(provider.copy(updateViaProxy = false))

        val storedProvider = library.subscriptions.first { it.id == 42 }
        assertEquals(listOf(1, 42), library.subscriptions.map { it.id })
        assertEquals("legacy-group-hwid", storedProvider.hwid)
        assertEquals("AGE-SECRET-KEY-1EXAMPLE", storedProvider.ageSecretKey)
        assertFalse(storedProvider.updateViaProxy)
        assertFalse(storedProvider.autoOverrideRules)
        assertTrue(storedProvider.builtIn)
        assertFalse(storedProvider.notifyOnExpiry)
        assertEquals(reminders, storedProvider.customExpiryReminders)
        assertEquals(metadata, storedProvider.metadata)
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

    private fun subscriptionRecord(
        id: Int,
        title: String,
        url: String,
        userAgent: String = "",
        updateInterval: String = "",
        hwid: String = "",
        ageSecretKey: String = "",
        updateViaProxy: Boolean = false,
        autoOverrideRules: Boolean = true,
        enabled: Boolean = true,
        builtIn: Boolean = false,
        metadata: SubscriptionMetadata? = null,
        lastUpdatedAtMillis: Long? = null,
        notifyOnExpiry: Boolean = true,
        customExpiryReminders: List<SubscriptionExpiryReminder>? = null,
    ) = SubscriptionRecord(
        id = id,
        title = title,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        enabled = enabled,
        builtIn = builtIn,
        metadata = metadata,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    )
}
