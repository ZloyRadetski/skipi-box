// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.backup.AppBackupWarning
import features.backup.decodeAppBackup
import features.backup.encodeAppBackup
import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleType
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.model.toPersistedProxyServer
import features.routing.model.RouteRule
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.StoredSubscription
import features.subscription.SubscriptionProviderLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Desktop user data written to and restored from the shared SKIPI backup document. */
class DesktopBackupTest {
    private fun socks(name: String) = Socks(remarks = name, server = "$name.example", port = "1080")

    private fun userData(): DesktopUserData = DesktopUserData(
        settings = DesktopAppSettings(
            localProxyPort = 20_480,
            localProxyPortRaw = "20480",
            enableDynamicLocalProxyPort = true,
            localProxyListenAddress = "0.0.0.0",
            enableLocalProxyAuth = true,
            localProxyUsername = "alice",
            localProxyPassword = "secret",
            subscriptionFetchTimeoutSeconds = 45,
            subscriptionPingUrl = "http://ping.example/generate_204",
            subscriptionUserAgents = listOf("agent/1"),
            sendDeviceHeaders = false,
            coreLogLevel = "debug",
            enableAccessLog = true,
            logRetentionDays = 14,
            enableMux = true,
            muxConcurrency = "7",
            enableFragment = true,
            fragmentLength = "5-10",
            enableSniffing = false,
            enableNetworkAutomation = true,
            networkAutomationRules = listOf(NetworkAutomationRule(id = "r1", type = NetworkRuleType.CELLULAR)),
            routeDomainStrategy = 2,
            defaultRouteOutboundTag = "direct",
            routeRules = listOf(RouteRule(id = 4, remarks = "rule", outboundTag = "direct", domain = listOf("full:a.example"))),
            confirmDeletion = false,
            hapticsEnabled = false,
            languageTag = "ru",
            customColorsEnabled = true,
            customAccentColor = 0xFF112233,
            installationUuid = "device-uuid",
            onboardingCompleted = true,
        ),
        servers = DesktopServerLibrary(
            selectedServerId = 12,
            servers = listOf(
                DesktopStoredProxyServer(10, socks("manual").encodePersistedProxyServer(), subscriptionId = null),
                // Desktop subscription id 1 collides with Android's built-in manual group id.
                DesktopStoredProxyServer(11, socks("sub-one").encodePersistedProxyServer(), subscriptionId = 1),
                DesktopStoredProxyServer(12, socks("sub-seven").encodePersistedProxyServer(), subscriptionId = 7),
                DesktopStoredProxyServer(13, socks("auto").encodePersistedProxyServer(), subscriptionId = -2),
            ),
        ),
        subscriptions = SubscriptionProviderLibrary(
            subscriptions = listOf(
                StoredSubscription(id = 1, url = "https://one.example/sub", name = "One", userAgent = "ua1", updateViaProxy = true),
                StoredSubscription(id = 7, url = "https://seven.example/sub", name = "Seven", enabled = false, hwid = "h7"),
            ),
        ),
        configs = ConfigProfileLibrary(
            selectedConfigId = 2,
            configs = listOf(
                ConfigProfile(id = 1, name = "Direct", content = "[Rule]\nFINAL,DIRECT\n"),
                ConfigProfile(id = 2, name = "Proxy", content = "[Rule]\nFINAL,PROXY\n", sourceUrl = "https://p.example/c", lastUpdatedAtMillis = 99L),
            ),
        ),
    )

    private fun roundTrip(data: DesktopUserData, current: DesktopUserData = DesktopUserData()): DesktopRestorePreview {
        val json = encodeAppBackup(data.toAppBackupFile(createdAtMillis = 1L, appVersionName = "0.4.1", appVersionCode = 180))
        return decodeAppBackup(json).toDesktopRestorePreview(current)
    }

    @Test
    fun exportMapsDesktopGroupsToAndroidGroupIds() {
        val backup = userData().toAppBackupFile(createdAtMillis = 5L, appVersionName = "0.4.1", appVersionCode = 180)

        val groups = backup.data.subscriptionGroups
        val manual = groups.single { it.builtIn }
        assertEquals(DefaultSubscriptionGroupId, manual.id)
        val oneBackupId = groups.single { it.url == "https://one.example/sub" }.id
        assertNotEquals(DefaultSubscriptionGroupId, oneBackupId, "Desktop subscription 1 must not land on the manual group")
        assertEquals(7, groups.single { it.url == "https://seven.example/sub" }.id)

        val byId = backup.data.proxyServers.associateBy { it.id }
        assertEquals(DefaultSubscriptionGroupId, byId.getValue(10).groupId)
        assertEquals(oneBackupId, byId.getValue(11).groupId)
        assertEquals(7, byId.getValue(12).groupId)
        assertEquals(-2, byId.getValue(13).groupId)
        assertEquals(12, backup.data.settings.selectedProxyServerId)
    }

    @Test
    fun roundTripRestoresEveryLibraryAndMappedSetting() {
        val original = userData()

        val restored = roundTrip(original).userData

        // A Desktop subscription numbered 1 is renumbered, so servers are compared through their provider URL.
        fun DesktopUserData.serversByProvider() = servers.servers.map { server ->
            Triple(server.id, subscriptions.subscriptions.firstOrNull { it.id == server.subscriptionId }?.url ?: server.subscriptionId, server.serverJson)
        }
        assertEquals(original.serversByProvider(), restored.serversByProvider())
        assertEquals(12, restored.servers.selectedServerId)
        assertEquals(
            original.subscriptions.subscriptions.map { it.url }.toSet(),
            restored.subscriptions.subscriptions.map { it.url }.toSet(),
        )
        val one = restored.subscriptions.subscriptions.first { it.url == "https://one.example/sub" }
        assertEquals("ua1", one.userAgent)
        assertTrue(one.updateViaProxy)
        assertFalse(restored.subscriptions.subscriptions.first { it.id == 7 }.enabled)
        assertEquals(listOf("Direct", "Proxy"), restored.configs.configs.map { it.name })
        assertEquals(2, restored.configs.selectedConfigId)
        assertEquals("https://p.example/c", restored.configs.configs[1].sourceUrl)

        val s = restored.settings
        val o = original.settings
        assertEquals(o.localProxyPort, s.localProxyPort)
        assertEquals("0.0.0.0", s.localProxyListenAddress)
        assertTrue(s.enableDynamicLocalProxyPort)
        assertEquals("alice", s.localProxyUsername)
        assertEquals("secret", s.localProxyPassword)
        assertEquals(45, s.subscriptionFetchTimeoutSeconds)
        assertEquals("http://ping.example/generate_204", s.subscriptionPingUrl)
        assertEquals(listOf("agent/1"), s.subscriptionUserAgents)
        assertFalse(s.sendDeviceHeaders)
        assertEquals("debug", s.coreLogLevel)
        assertTrue(s.enableAccessLog)
        assertEquals(14, s.logRetentionDays)
        assertTrue(s.enableMux)
        assertEquals("7", s.muxConcurrency)
        assertEquals("5-10", s.fragmentLength)
        assertFalse(s.enableSniffing)
        assertTrue(s.enableNetworkAutomation)
        assertEquals(o.networkAutomationRules, s.networkAutomationRules)
        assertEquals(2, s.routeDomainStrategy)
        assertEquals("direct", s.defaultRouteOutboundTag)
        assertEquals(o.routeRules, s.routeRules)
        assertFalse(s.confirmDeletion)
        assertFalse(s.hapticsEnabled)
        assertEquals("ru", s.languageTag)
        assertTrue(s.customColorsEnabled)
        assertEquals(0xFF112233, s.customAccentColor)
    }

    @Test
    fun tunSettingsSurviveTheBackupButHostSwitchesDoNot() {
        val original = userData().copy(
            settings = userData().settings.copy(
                enableTunMode = true,
                launchOnLogin = true,
                tunMtu = "1500",
                tunIpv4Cidr = "10.20.30.1/24",
                tunIpv6Cidr = "fd00::1/64",
                tunVpnDns = "1.1.1.1",
                tunTcpKeepAliveInterval = "30",
                tunTcpUserTimeout = "20000",
                enableStrictFullTunnel = true,
            ),
        )

        val restored = roundTrip(original).userData.settings

        assertEquals("1500", restored.tunMtu)
        assertEquals("10.20.30.1/24", restored.tunIpv4Cidr)
        assertEquals("fd00::1/64", restored.tunIpv6Cidr)
        assertEquals("1.1.1.1", restored.tunVpnDns)
        assertEquals("30", restored.tunTcpKeepAliveInterval)
        assertEquals("20000", restored.tunTcpUserTimeout)
        assertTrue(restored.enableStrictFullTunnel)
        // Turning TUN on and registering an autostart entry are this machine's decisions.
        assertFalse(restored.enableTunMode)
        assertFalse(restored.launchOnLogin)
    }

    @Test
    fun restoringLeavesThisMachinesTunSwitchesAsTheyAre() {
        val current = DesktopUserData(
            settings = DesktopAppSettings(enableTunMode = true, launchOnLogin = true, autoConnectOnAppOpen = true),
        )

        val restored = roundTrip(userData(), current).userData.settings

        assertTrue(restored.enableTunMode)
        assertTrue(restored.launchOnLogin)
        assertTrue(restored.autoConnectOnAppOpen)
    }

    @Test
    fun restoreKeepsDeviceIdentityAndOnboardingOfTheCurrentInstall() {
        val current = DesktopUserData(
            settings = DesktopAppSettings(installationUuid = "this-device", onboardingCompleted = true),
        )

        val restored = roundTrip(userData(), current).userData.settings

        assertEquals("this-device", restored.installationUuid)
        assertTrue(restored.onboardingCompleted)
    }

    @Test
    fun androidBackupMapsBuiltInGroupToManualAndUnknownGroupsToManual() {
        val json = """
        {"format":"skipi-backup","version":1,"data":{
          "settings":{"selectedProxyServerId":2,"enableVpnHevTun":false,"tunMtu":"1280","localProxyPort":"1081"},
          "subscriptionGroups":[
            {"id":1,"name":"Default","builtIn":true},
            {"id":5,"name":"Provider","url":"https://p.example/s"}],
          "proxyServers":[
            {"id":1,"groupId":1,"protocol":"socks","payload":${socksPayload("a")}},
            {"id":2,"groupId":5,"protocol":"socks","payload":${socksPayload("b")}},
            {"id":3,"groupId":-2,"protocol":"socks","payload":${socksPayload("c")}},
            {"id":4,"groupId":99,"protocol":"socks","payload":${socksPayload("d")}}]}}
        """.trimIndent()

        val restored = decodeAppBackup(json).toDesktopRestorePreview(DesktopUserData()).userData

        assertEquals(listOf(null, 5, -2, null), restored.servers.servers.map { it.subscriptionId })
        assertEquals(2, restored.servers.selectedServerId)
        assertEquals(listOf(5), restored.subscriptions.subscriptions.map { it.id })
        assertEquals(1081, restored.settings.localProxyPort)
    }

    private fun socksPayload(name: String): String = socks(name).toPersistedProxyServer().payload.toString()

    @Test
    fun missingChainMembersAreReportedAsWarnings() {
        val chain = ChainProxy(remarks = "chain", proxyServerIds = listOf(10, 77, 78))
        val data = userData().copy(
            servers = userData().servers.copy(
                servers = userData().servers.servers + DesktopStoredProxyServer(20, chain.encodePersistedProxyServer()),
            ),
        )

        val preview = roundTrip(data)

        assertEquals(listOf(AppBackupWarning.MissingChainProxyMembers(2)), preview.warnings)
        assertEquals(5, preview.serverCount)
        assertEquals(2, preview.subscriptionCount)
        assertEquals(2, preview.profileCount)
    }

    @Test
    fun emptyBackupYieldsFreshLibrariesAndStockSettings() {
        val preview = decodeAppBackup("""{"format":"skipi-backup","version":1}""").toDesktopRestorePreview(DesktopUserData())

        assertTrue(preview.userData.servers.servers.isEmpty())
        assertTrue(preview.userData.subscriptions.subscriptions.isEmpty())
        assertTrue(preview.userData.configs.configs.isEmpty())
        // Settings absent from a backup take the shared backup defaults (Android's), not Desktop's.
        assertEquals("error", preview.userData.settings.coreLogLevel)
        assertEquals(7, preview.userData.settings.logRetentionDays)
    }

    @Test
    fun invalidFormatAndNewerVersionsAreRejected() {
        val badFormat = runCatching { decodeAppBackup("""{"format":"other","version":1}""").toDesktopRestorePreview(DesktopUserData()) }
        val newer = runCatching { decodeAppBackup("""{"format":"skipi-backup","version":99}""").toDesktopRestorePreview(DesktopUserData()) }

        assertTrue(badFormat.isFailure)
        assertEquals("Backup file was created by a newer version", newer.exceptionOrNull()?.message)
    }

    @Test
    fun factoryResetKeepsOnlyDeviceIdentityAndOnboarding() {
        val reset = userData().factoryReset()

        assertEquals(DesktopUserData().servers, reset.servers)
        assertEquals(DesktopUserData().subscriptions, reset.subscriptions)
        assertEquals(DesktopUserData().configs, reset.configs)
        assertEquals("device-uuid", reset.settings.installationUuid)
        assertEquals(DesktopAppSettings().copy(installationUuid = "device-uuid", onboardingCompleted = true), reset.settings)
    }

    @Test
    fun backupFileSurvivesDiskAndKeepsMetadataForThePreview() {
        val directory = java.nio.file.Files.createTempDirectory("skipi-backup-")
        try {
            val target = directory.resolve("nested").resolve(defaultDesktopBackupFileName())
            writeDesktopBackup(target, userData().toAppBackupFile(createdAtMillis = 1234L, appVersionName = "0.4.1", appVersionCode = 180))

            val preview = readDesktopBackup(target).toDesktopRestorePreview(DesktopUserData())

            assertEquals("0.4.1", preview.appVersionName)
            assertEquals(1234L, preview.createdAtMillis)
            assertEquals(1, preview.backupVersion)
            assertEquals(1, preview.routeRuleCount)
            assertTrue(target.fileName.toString().matches(Regex("""skipi-backup-\d{8}-\d{6}\.json""")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
