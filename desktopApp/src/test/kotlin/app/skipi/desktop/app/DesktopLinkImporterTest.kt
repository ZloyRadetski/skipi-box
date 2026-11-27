// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.skipi_imported_subscription
import features.config.ConfigProfile
import features.config.SkipiDeepLink
import features.subscription.DefaultSubscriptionUserAgent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLinkImporterTest {
    private val document = "[Rule]\nFINAL,PROXY"
    private val current = ConfigProfile(7, "Current", "[General]\nloglevel = warning\n")

    private var configs = DesktopConfigLibrary(selectedConfigId = 7, configs = listOf(current), nextConfigId = 8)
    private var servers = DesktopServerLibrary()
    private val installs = mutableListOf<DesktopSubscriptionInstallUri>()
    private val fetched = mutableListOf<String>()
    private var fetchResponse = document

    private val importer = DesktopLinkImporter(
        installSubscription = { install -> installs += install; Result.success("installed") },
        servers = DesktopProxyServerRepository(
            initialLibrary = servers,
            readLibrary = { servers },
            saveLibrary = { Result.success(Unit) },
            publishLibrary = { servers = it },
        ),
        fetchConfig = { url -> fetched += url; fetchResponse },
        transformConfigs = { transform -> configs = transform(configs) },
    )

    @Test
    fun aSubscriptionLinkIsInstalledWithAndroidsDefaults() = runTest {
        importer.import(SkipiDeepLink.Subscription("https://example.test/sub")).getOrThrow()

        val install = installs.single()
        assertEquals("https://example.test/sub", install.url)
        assertEquals(DefaultSubscriptionUserAgent, install.userAgent)
        assertEquals(desktopText(Res.string.skipi_imported_subscription), install.name)
    }

    @Test
    fun aServerLinkIsAddedOnTopAndSelected() = runTest {
        importer.import(SkipiDeepLink.ManualServer("vless://123e4567-e89b-42d3-a456-426614174000@one.example.test:443?security=tls#One")).getOrThrow()
        importer.import(SkipiDeepLink.ManualServer("vless://123e4567-e89b-42d3-a456-426614174000@two.example.test:443?security=tls#Two")).getOrThrow()

        assertEquals(
            listOf("Two", "One"),
            servers.servers.map { it.decode().getOrThrow().getInfo().remarks },
        )
        assertEquals(servers.servers.first().id, servers.selectedServerId)
    }

    @Test
    fun anInlineConfigIsAddedWithoutReplacingTheActiveOne() = runTest {
        importer.import(SkipiDeepLink.TrafficConfig(document, activate = false)).getOrThrow()

        assertEquals(emptyList<String>(), fetched)
        assertEquals(listOf(7, 8), configs.configs.map(ConfigProfile::id))
        assertTrue(configs.configs.last().content.contains("FINAL,PROXY"))
        assertEquals(7, configs.selectedConfigId)
    }

    @Test
    fun aConfigUrlIsDownloadedRememberedAsTheSourceAndActivatedOnRequest() = runTest {
        val url = "https://example.test/rules.conf"

        importer.import(SkipiDeepLink.TrafficConfig(url, activate = true, sourceUrl = url)).getOrThrow()

        assertEquals(listOf(url), fetched)
        assertEquals(url, configs.configs.last().sourceUrl)
        assertEquals(8, configs.selectedConfigId)
    }

    @Test
    fun aDownloadThatIsNotAConfigChangesNothing() = runTest {
        fetchResponse = "<html><body>404</body></html>"
        val before = configs

        val result = importer.import(
            SkipiDeepLink.TrafficConfig("https://example.test/missing.conf", activate = true),
        )

        assertTrue(result.isFailure)
        assertEquals(before, configs)
    }

    @Test
    fun aBrokenServerLinkFailsWithoutAddingARow() = runTest {
        val result = importer.import(SkipiDeepLink.ManualServer("nonsense://example.test"))

        assertTrue(result.isFailure)
        assertEquals(0, servers.servers.size)
    }
}
