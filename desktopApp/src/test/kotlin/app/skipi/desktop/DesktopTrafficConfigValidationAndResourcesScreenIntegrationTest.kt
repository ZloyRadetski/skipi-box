// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import app.skipi.app.config.toTrafficConfigState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_add
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_dns_add_server
import app.skipi.ui.resources.configs_dns_direct_domains_title
import app.skipi.ui.resources.configs_dns_direct_section
import app.skipi.ui.resources.configs_dns_domain_invalid
import app.skipi.ui.resources.configs_dns_hosts_invalid
import app.skipi.ui.resources.configs_dns_hosts_title
import app.skipi.ui.resources.configs_dns_server_invalid
import app.skipi.ui.resources.configs_dns_title
import app.skipi.ui.resources.configs_dns_tun_dns
import app.skipi.ui.resources.configs_geo_auto_update
import app.skipi.ui.resources.configs_resources
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.skipiSectionValues
import features.config.withSkipiSettingsInRawConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class DesktopTrafficConfigValidationAndResourcesScreenIntegrationTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun dnsDialogsRejectMalformedDraftsAndPersistOnlyAcceptedValuesOnBack() = runBlocking {
        val labels = labels()
        val fixture = fixture()

        withScreen(fixture, width = 1100, height = 1300) { screen ->
            openInactiveProfileFromMore(screen, labels)
            clickText(screen.scene, labels.dnsTitle, screen.width, screen.height)
            assertEquals(0, screen.libraryTransformCount(), "Navigating into DNS should not write the library")

            // Follow the shared screen's top-to-bottom layout so the harness can scroll forward only.
            clickText(screen.scene, labels.tunDnsTitle, screen.width, screen.height)
            setEditableTextByCurrentValue(screen.scene, "8.8.8.8", "not-an-ip", screen.width, screen.height)
            // The profile TUN-DNS dialog has no invalid-message string; only acceptance is checked.
            clickText(screen.scene, labels.save, screen.width, screen.height)
            clickText(screen.scene, labels.tunDnsTitle, screen.width, screen.height)
            assertNotNull(awaitEditableNode(screen.scene, "8.8.8.8", screen.width, screen.height))
            setEditableTextByCurrentValue(screen.scene, "8.8.8.8", "1.1.1.1", screen.width, screen.height)
            clickText(screen.scene, labels.save, screen.width, screen.height)

            clickAddServerInSection(screen.scene, labels.directDnsSection, labels.addDnsServer, screen.width, screen.height)
            setEditableTextByCurrentValue(
                screen.scene,
                "",
                "tls://dns.example:853",
                screen.width,
                screen.height,
            )
            assertNotNull(awaitText(screen.scene, labels.serverInvalid, screen.width, screen.height))
            clickText(screen.scene, labels.save, screen.width, screen.height)
            assertNotNull(awaitText(screen.scene, "9.9.9.9", screen.width, screen.height))

            clickAddServerInSection(screen.scene, labels.directDnsSection, labels.addDnsServer, screen.width, screen.height)
            setEditableTextByCurrentValue(
                screen.scene,
                "",
                "https://1.1.1.1/dns-query",
                screen.width,
                screen.height,
            )
            clickText(screen.scene, labels.save, screen.width, screen.height)
            assertNotNull(awaitText(screen.scene, "https://1.1.1.1/dns-query", screen.width, screen.height))

            clickText(screen.scene, labels.directDomainsTitle, screen.width, screen.height)
            setEditableTextByCurrentValue(screen.scene, "", "bad domain", screen.width, screen.height)
            assertNotNull(awaitText(screen.scene, labels.domainInvalid, screen.width, screen.height))
            clickText(screen.scene, labels.save, screen.width, screen.height)
            assertNotNull(awaitText(screen.scene, "geosite:cn", screen.width, screen.height))
            // The malformed value stays only in the rejected field draft, never in the saved list.

            clickText(screen.scene, labels.directDomainsTitle, screen.width, screen.height)
            setEditableTextByCurrentValue(
                screen.scene,
                "",
                "domain:accepted.example",
                screen.width,
                screen.height,
            )
            clickContentDescription(screen.scene, labels.add, screen.width, screen.height)
            assertNotNull(awaitText(screen.scene, "domain:accepted.example", screen.width, screen.height))
            clickText(screen.scene, labels.save, screen.width, screen.height)

            clickText(screen.scene, labels.dnsHostsTitle, screen.width, screen.height)
            setEditableTextByCurrentValue(
                screen.scene,
                "",
                "bad.example: not-an-ip",
                screen.width,
                screen.height,
            )
            assertNotNull(awaitText(screen.scene, labels.hostInvalid, screen.width, screen.height))
            clickText(screen.scene, labels.save, screen.width, screen.height)
            assertNotNull(awaitText(screen.scene, "existing.example: 1.2.3.4", screen.width, screen.height))
            // The malformed value stays only in the rejected field draft, never in the saved list.

            clickText(screen.scene, labels.dnsHostsTitle, screen.width, screen.height)
            setEditableTextByCurrentValue(
                screen.scene,
                "",
                "accepted.example: 1.2.3.4",
                screen.width,
                screen.height,
            )
            clickContentDescription(screen.scene, labels.add, screen.width, screen.height)
            assertNotNull(awaitText(screen.scene, "accepted.example: 1.2.3.4", screen.width, screen.height))
            clickText(screen.scene, labels.save, screen.width, screen.height)

            assertEquals(
                fixture.library,
                screen.loadPersistedLibrary(),
                "DNS dialog drafts must stay inside the screen until its Save or Back action",
            )
            assertEquals(0, screen.libraryTransformCount(), "DNS dialog edits should remain in the editor draft")
            clickTopBarBack(screen.scene, screen.width, screen.height)
            assertEquals(1, screen.libraryTransformCount(), "DNS Back should commit the edited profile once")

            val persisted = screen.loadPersistedLibrary()
            assertLibraryEnvelope(fixture.library, persisted, fixture.target.id)
            val savedTarget = persisted.configs.single { it.id == fixture.target.id }
            val settings = savedTarget.toTrafficConfigState(ResourceUserAgent).androidSettings
            val skipi = savedTarget.content.skipiSectionValues()
            assertEquals(listOf("9.9.9.9", "https://1.1.1.1/dns-query"), settings.directDns)
            assertEquals(listOf("geosite:cn", "domain:accepted.example"), settings.directDnsDomains)
            assertEquals(
                listOf("existing.example: 1.2.3.4", "accepted.example: 1.2.3.4"),
                settings.dnsHosts,
            )
            assertEquals("1.1.1.1", settings.tunVpnDns)
            assertEquals("geosite:cn,domain:accepted.example", skipi["direct-dns-domains"]?.last())
            assertEquals("existing.example: 1.2.3.4", skipi["dns-hosts"]?.first())
            assertEquals("accepted.example: 1.2.3.4", skipi["dns-hosts"]?.last())
            assertEquals("9.9.9.9,https://1.1.1.1/dns-query", skipi["direct-dns"]?.last())
            assertTrue(skipi["dns-hosts"]?.none { it.contains("not-an-ip") } == true)
            assertTrue(skipi["direct-dns-domains"]?.none { it.contains("bad domain") } == true)
            assertEquals("1.1.1.1", skipi["tun-dns"]?.last())
            assertDocumentSectionsPreserved(savedTarget)
        }
    }

    @Test
    fun inactiveProfileResourcesRouteBackPersistsAgainstTheLatestRowAndPreservesLibraryEnvelope() = runBlocking {
        val labels = labels()
        val fixture = fixture()

        withScreen(fixture, width = 1100, height = 900) { screen ->
            openInactiveProfileFromMore(screen, labels)
            clickText(screen.scene, labels.routingTitle, screen.width, screen.height)
            clickText(screen.scene, labels.resourcesTitle, screen.width, screen.height)
            assertNotNull(awaitText(screen.scene, labels.geoAutoUpdate, screen.width, screen.height))
            assertEquals(0, screen.libraryTransformCount(), "Opening Resources should not write the profile")

            // Model a newer same-ID document arriving while the Resources screen is open.
            val latestBaseline = screen.transformLatest { library ->
                library.copy(
                    configs = library.configs.map { profile ->
                        if (profile.id == fixture.target.id) {
                            profile.copy(
                                content = profile.content.replace(
                                    "vendor-option = preserve-target",
                                    "vendor-option = preserve-target\nlatest-row-marker = keep",
                                ),
                            )
                        } else {
                            profile
                        }
                    },
                )
            }
            assertTrue(
                latestBaseline.configs.single { it.id == fixture.target.id }.content.contains("latest-row-marker = keep"),
            )
            assertEquals(0, screen.libraryTransformCount(), "External latest-row setup is not a UI action")
            render(screen.scene)
            clickSwitchForLabel(screen.scene, labels.geoAutoUpdate, screen.width, screen.height)
            setEditableTextByCurrentValue(screen.scene, "72", "96", screen.width, screen.height)

            val beforeBack = screen.loadPersistedLibrary()
            val targetBeforeBack = beforeBack.configs.single { it.id == fixture.target.id }
            val resourceSettingsBeforeBack = targetBeforeBack.toTrafficConfigState(ResourceUserAgent).resourceSettings
            assertTrue(resourceSettingsBeforeBack.autoUpdate)
            assertEquals("96", resourceSettingsBeforeBack.updateInterval)
            assertTrue(targetBeforeBack.content.contains("latest-row-marker = keep"))
            assertTrue(targetBeforeBack.content.contains("resource-auto-update = true"))
            assertTrue(targetBeforeBack.content.contains("resource-update-interval = 96"))
            val transformsBeforeBack = screen.libraryTransformCount()
            assertTrue(transformsBeforeBack > 0, "Resource setting edits should already be committed")

            clickTopBarBack(screen.scene, screen.width, screen.height)
            assertEquals(
                transformsBeforeBack,
                screen.libraryTransformCount(),
                "Returning from Resources should not add a profile write",
            )
            assertNotNull(awaitText(screen.scene, labels.routingTitle, screen.width, screen.height))

            val persisted = screen.loadPersistedLibrary()
            assertLibraryEnvelope(latestBaseline, persisted, fixture.target.id)
            val savedTarget = persisted.configs.single { it.id == fixture.target.id }
            val profile = savedTarget.toTrafficConfigState(ResourceUserAgent)
            val skipi = savedTarget.content.skipiSectionValues()
            assertTrue(profile.resourceSettings.autoUpdate)
            assertEquals("96", profile.resourceSettings.updateInterval)
            assertEquals("Profile form fixture/1.0", profile.resourceSettings.userAgent)
            assertEquals(false, profile.autoUpdate, "Profile auto-update is a separate setting")
            assertEquals("true", skipi["resource-auto-update"]?.last())
            assertEquals("96", skipi["resource-update-interval"]?.last())
            assertEquals("Profile form fixture/1.0", skipi["resource-user-agent"]?.last())
            assertTrue(savedTarget.content.contains("latest-row-marker = keep"))
            assertDocumentSectionsPreserved(savedTarget, requireLatestMarker = true)
        }
    }

    private fun openInactiveProfileFromMore(screen: ScreenHarness, labels: Labels) {
        assertNotEquals(screen.fixture.library.selectedConfigId, screen.fixture.target.id)
        clickProfileIcon(screen.scene, screen.fixture.target.name, "Ещё", screen.width, screen.height)
        clickText(screen.scene, labels.uiEdit, screen.width, screen.height)
        assertNotNull(
            awaitEditableNode(screen.scene, screen.fixture.target.name, screen.width, screen.height),
            "More → UI Edit should open the inactive profile in Basics",
        )
    }

    private fun clickAddServerInSection(
        scene: ImageComposeScene,
        sectionTitle: String,
        addTitle: String,
        width: Int,
        height: Int,
    ) {
        val section = requireNotNull(awaitText(scene, sectionTitle, width, height))
        val addText = requireNotNull(awaitNode(scene, width, height) { candidate ->
            candidate.hasText(addTitle) && candidate.ancestorsIncludingSelf().any { it.config.contains(SemanticsActions.OnClick) } &&
                candidate.boundsInRoot.center.y > section.boundsInRoot.center.y
        }) { "Expected the $sectionTitle card's Add DNS server action" }
        click(scene, requireNotNull(addText.ancestorsIncludingSelf().firstOrNull { it.config.contains(SemanticsActions.OnClick) }))
    }

    private fun clickProfileIcon(
        scene: ImageComposeScene,
        profileName: String,
        contentDescription: String,
        width: Int,
        height: Int,
    ) {
        val profile = requireNotNull(awaitText(scene, profileName, width, height))
        val card = allNodes(scene)
            .filter { candidate ->
                candidate.config.contains(SemanticsActions.OnClick) &&
                    candidate.boundsInRoot.contains(profile.boundsInRoot.center)
            }
            .minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
        requireNotNull(card) { "Expected a clickable profile row for $profileName" }
        val icon = requireNotNull(allNodes(scene).firstOrNull { candidate ->
            candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .any { it.equals(contentDescription, ignoreCase = true) } &&
                card.boundsInRoot.contains(candidate.boundsInRoot.center) &&
                isCenterOnScreen(candidate, width, height)
        }) { "Expected $contentDescription inside profile row $profileName" }
        click(scene, icon)
    }

    private fun clickText(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val text = requireNotNull(awaitClickableText(scene, label, width, height)) {
            "Expected visible clickable text '$label' at ${width}x$height"
        }
        val target = requireNotNull(text.ancestorsIncludingSelf().firstOrNull { it.config.contains(SemanticsActions.OnClick) })
        click(scene, target)
    }

    private fun clickContentDescription(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val node = requireNotNull(awaitNode(scene, width, height) { candidate ->
            candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { it == label }
        }) { "Expected visible control '$label'" }
        click(scene, node)
    }

    private fun clickSwitchForLabel(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val title = requireNotNull(awaitText(scene, label, width, height))
        val switch = requireNotNull(title.ancestorsIncludingSelf().firstOrNull { row ->
            row.config.contains(SemanticsActions.OnClick) &&
                row.descendants().count { it.config.contains(SemanticsProperties.ToggleableState) } == 1
        }) { "Expected a public toggle for '$label'" }
        click(scene, switch)
    }

    private fun clickTopBarBack(scene: ImageComposeScene, width: Int, height: Int) {
        val back = requireNotNull(awaitNode(scene, width, height) { candidate ->
            val center = candidate.boundsInRoot.center
            candidate.config.contains(SemanticsActions.OnClick) && center.x in 0f..72f && center.y in 0f..80f
        }) { "Expected the top-left Back control at ${width}x$height" }
        click(scene, back)
    }

    private fun setEditableTextByCurrentValue(
        scene: ImageComposeScene,
        currentValue: String,
        nextValue: String,
        width: Int,
        height: Int,
    ) {
        val field = requireNotNull(awaitEditableNode(scene, currentValue, width, height)) {
            "Expected an editable field with current value '$currentValue'"
        }
        val setText = requireNotNull(field.config[SemanticsActions.SetText].action)
        assertTrue(setText(AnnotatedString(nextValue)), "The public SetText action should accept the draft")
        render(scene)
        assertEquals(nextValue, awaitEditableNode(scene, nextValue, width, height)?.config?.get(SemanticsProperties.EditableText)?.text)
    }

    private fun awaitEditableNode(
        scene: ImageComposeScene,
        value: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitNode(scene, width, height) { candidate ->
        candidate.config.contains(SemanticsActions.SetText) &&
            candidate.config.contains(SemanticsProperties.EditableText) &&
            candidate.config[SemanticsProperties.EditableText].text == value
    }

    private fun awaitText(scene: ImageComposeScene, text: String, width: Int, height: Int): SemanticsNode? =
        awaitNode(scene, width, height) { it.hasText(text) }

    private fun awaitClickableText(
        scene: ImageComposeScene,
        text: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitNode(scene, width, height) { candidate ->
        candidate.hasText(text) && candidate.ancestorsIncludingSelf().any { it.config.contains(SemanticsActions.OnClick) }
    }

    private fun awaitNode(
        scene: ImageComposeScene,
        width: Int,
        height: Int,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + 3_000_000_000L
        var scrollSteps = 0
        do {
            render(scene)
            allNodes(scene).firstOrNull { predicate(it) && isCenterOnScreen(it, width, height) }?.let { return it }
            if (scrollSteps < 12) {
                val scrollable = allNodes(scene)
                    .filter { it.config.contains(SemanticsActions.ScrollBy) && it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                    .maxByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
                scrollable?.config?.get(SemanticsActions.ScrollBy)?.action?.let { scrollBy ->
                    scrollBy(0f, height * 0.65f)
                    scrollSteps++
                }
            }
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)
        return null
    }

    private fun assertLibraryEnvelope(
        baseline: DesktopConfigLibrary,
        persisted: DesktopConfigLibrary,
        targetId: Int,
    ) {
        assertEquals(baseline.selectedConfigId, persisted.selectedConfigId)
        assertEquals(baseline.nextConfigId, persisted.nextConfigId)
        assertEquals(baseline.configs.map(ConfigProfile::id), persisted.configs.map(ConfigProfile::id))
        assertEquals(
            baseline.configs.filterNot { it.id == targetId },
            persisted.configs.filterNot { it.id == targetId },
            "Editing one inactive profile must preserve every sibling row",
        )
        val before = baseline.configs.single { it.id == targetId }
        val after = persisted.configs.single { it.id == targetId }
        assertEquals(before.id, after.id)
        assertEquals(before.name, after.name)
        assertEquals(before.sourceUrl, after.sourceUrl)
        assertEquals(before.updateLocked, after.updateLocked)
        assertEquals(before.lastUpdatedAtMillis, after.lastUpdatedAtMillis)
        assertNotEquals(before.content, after.content)
    }

    private fun assertDocumentSectionsPreserved(profile: ConfigProfile, requireLatestMarker: Boolean = false) {
        assertTrue(profile.content.contains("[General]"))
        assertTrue(profile.content.contains("loglevel = warning"))
        assertTrue(profile.content.contains("dns-server = 9.9.9.9"))
        assertTrue(profile.content.contains("[Proxy]"))
        assertTrue(profile.content.contains("server = target.example:443"))
        assertTrue(profile.content.contains("[Rule]"))
        assertTrue(profile.content.contains("DOMAIN-SUFFIX,target.example,DIRECT"))
        assertTrue(profile.content.contains("[Vendor]"))
        assertTrue(profile.content.contains("vendor-option = preserve-target"))
        if (requireLatestMarker) assertTrue(profile.content.contains("latest-row-marker = keep"))
    }

    private fun labels(): Labels = runBlocking {
        Labels(
            uiEdit = getString(Res.string.configs_ui_edit),
            save = getString(Res.string.common_save),
            add = getString(Res.string.common_add),
            addDnsServer = getString(Res.string.configs_dns_add_server),
            routingTitle = getString(Res.string.configs_rules_title),
            resourcesTitle = getString(Res.string.configs_resources),
            dnsTitle = getString(Res.string.configs_dns_title),
            directDnsSection = getString(Res.string.configs_dns_direct_section),
            directDomainsTitle = getString(Res.string.configs_dns_direct_domains_title),
            dnsHostsTitle = getString(Res.string.configs_dns_hosts_title),
            tunDnsTitle = getString(Res.string.configs_dns_tun_dns),
            serverInvalid = getString(Res.string.configs_dns_server_invalid),
            domainInvalid = getString(Res.string.configs_dns_domain_invalid),
            hostInvalid = getString(Res.string.configs_dns_hosts_invalid),
            geoAutoUpdate = getString(Res.string.configs_geo_auto_update),
        )
    }

    private fun fixture(): Fixture {
        val sibling = ConfigProfile(
            id = 3,
            name = "Selected sibling",
            content = """[General]
loglevel = debug

[Vendor]
vendor-option = preserve-sibling
""",
            lastUpdatedAtMillis = 101L,
        )
        val rawTarget = ConfigProfile(
            id = 7,
            name = "Profile target",
            sourceUrl = "https://target.example/profile.conf",
            updateLocked = false,
            lastUpdatedAtMillis = 456L,
            content = """[General]
loglevel = warning
ipv6 = false
prefer-ipv6 = false
dns-server = 9.9.9.9

[Proxy]
server = target.example:443

[Rule]
DOMAIN-SUFFIX,target.example,DIRECT

[Vendor]
vendor-option = preserve-target

[SKIPI]
profile-name = Profile target
profile-update-url = https://target.example/profile.conf
profile-update-locked = false
profile-auto-update = false
profile-update-interval = 24
resource-auto-update = false
resource-update-interval = 72
resource-user-agent = Profile form fixture/1.0
sniffing = false
sniffing-route-only = false
mux = false
mux-concurrency = 4
fragment = false
vpn-local-dns = false
fake-dns = false
resolve-proxy-server-domain = false
direct-dns-fallback-proxy = false
tun-dns = 8.8.8.8
direct-dns = 9.9.9.9
direct-dns-domains = geosite:cn
dns-hosts = existing.example: 1.2.3.4
network-activation = false
network-transport = wifi
""",
        )
        val target = rawTarget.copy(
            content = rawTarget.toTrafficConfigState(ResourceUserAgent)
                .withSkipiSettingsInRawConfig()
                .rawConfig,
        )
        return Fixture(
            library = DesktopConfigLibrary(
                selectedConfigId = sibling.id,
                configs = listOf(sibling, target),
                nextConfigId = 29,
            ),
            sibling = sibling,
            target = target,
        )
    }

    private fun withScreen(fixture: Fixture, width: Int, height: Int, block: (ScreenHarness) -> Unit) {
        val root = Files.createTempDirectory("desktop-config-validation-resources-").toFile()
        val screen = ScreenHarness(root, fixture, width, height)
        try {
            block(screen)
        } finally {
            screen.close()
            Files.walk(root.toPath()).use { paths ->
                    paths.sorted(Comparator.reverseOrder<Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    private data class Labels(
        val uiEdit: String,
        val save: String,
        val add: String,
        val addDnsServer: String,
        val routingTitle: String,
        val resourcesTitle: String,
        val dnsTitle: String,
        val directDnsSection: String,
        val directDomainsTitle: String,
        val dnsHostsTitle: String,
        val tunDnsTitle: String,
        val serverInvalid: String,
        val domainInvalid: String,
        val hostInvalid: String,
        val geoAutoUpdate: String,
    )

    private data class Fixture(
        val library: DesktopConfigLibrary,
        val sibling: ConfigProfile,
        val target: ConfigProfile,
    )

    private inner class ScreenHarness(
        root: File,
        val fixture: Fixture,
        val width: Int,
        val height: Int,
    ) : AutoCloseable {
        private val configPath: Path = root.toPath().resolve("configs.json")
        private val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
        private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val transformCount = AtomicInteger()
        var latestLibrary by mutableStateOf(fixture.library)
            private set
        private val configRepository by lazy(LazyThreadSafetyMode.NONE) {
            DesktopTrafficConfigRepository(
                initialLibrary = loadPersistedLibrary(),
                readLibrary = ::loadPersistedLibrary,
                saveLibrary = { library -> DesktopConfigLibraries.save(configPath, library) },
                publishLibrary = { latestLibrary = it },
            )
        }
        private val resourceServices by lazy(LazyThreadSafetyMode.NONE, ::createResourceServices)
        val scene = ImageComposeScene(width = width, height = height)

        init {
            DesktopConfigLibraries.save(configPath, fixture.library).getOrThrow()
            latestLibrary = loadPersistedLibrary()
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = false),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    DesktopConfigsScreen(
                        configLibrary = latestLibrary,
                        onTransformConfigLibrary = ::transformLibrary,
                        fetchUserAgent = "Desktop validation and resources test",
                        fetchTimeoutSeconds = 5,
                        fetchProfileDocument = { _, _ -> error("Unexpected profile network refresh in test") },
                        contentPadding = PaddingValues(),
                        resourceActionScope = hostScope,
                        onResourceFileStatusMessage = {},
                        desktopSettings = DesktopAppSettings(),
                        exportProxyServerFullJson = { error("Unexpected proxy export in test") },
                        onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global group apply in test") },
                        proxyServers = emptyList(),
                        subscriptionCatalog = app.skipi.app.model.SubscriptionCatalog(),
                        resourceFiles = resourceFiles,
                        latencyByServerId = emptyMap(),
                        resourceServices = resourceServices,
                        resourceFileStatusMessage = "",
                    )
                }
            }
            render(scene)
        }

        fun loadPersistedLibrary(): DesktopConfigLibrary = DesktopConfigLibraries.load(configPath).getOrThrow()

        fun transformLatest(transform: (DesktopConfigLibrary) -> DesktopConfigLibrary): DesktopConfigLibrary =
            configRepository.transformLatest(transform)

        private fun transformLibrary(transform: (DesktopConfigLibrary) -> DesktopConfigLibrary): DesktopConfigLibrary =
            configRepository.transformLatest(transform).also { updated ->
                transformCount.incrementAndGet()
                latestLibrary = updated
            }

        fun libraryTransformCount(): Int = transformCount.get()

        private fun createResourceServices() = DesktopResourceFileServices(
            scope = hostScope,
            resourceFiles = resourceFiles,
            readSettings = ::DesktopAppSettings,
            readProfile = { id -> latestLibrary.configs.singleOrNull { it.id == id }?.toTrafficConfigState(ResourceUserAgent) },
            activeProxy = { null },
            prepareFeedback = {
                DesktopResourceFileUpdateFeedback(
                    onProgress = { _, _, _ -> },
                    onComplete = {},
                    onFailure = {},
                    mapCancellationError = { it },
                )
            },
            pickFile = { null },
        )

        override fun close() {
            scene.close()
            hostScope.cancel()
        }
    }

    private fun render(scene: ImageComposeScene) {
        scene.render(frameTimeNanos).close()
        frameTimeNanos += 16_666_667L
    }

    private fun SemanticsNode.hasText(text: String): Boolean =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.equals(text, ignoreCase = true) }

    private fun SemanticsNode.ancestorsIncludingSelf(): Sequence<SemanticsNode> = sequence {
        var current: SemanticsNode? = this@ancestorsIncludingSelf
        var depth = 0
        while (current != null && depth++ < 10) {
            yield(current)
            current = current.parent
        }
    }

    private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
        yield(this@descendants)
        children.forEach { yieldAll(it.descendants()) }
    }

    private fun allNodes(scene: ImageComposeScene): Sequence<SemanticsNode> =
        scene.semanticsOwners.asSequence().flatMap { it.unmergedRootSemanticsNode.descendants() }

    private fun isCenterOnScreen(node: SemanticsNode, width: Int, height: Int): Boolean {
        val bounds = node.boundsInRoot
        if (bounds.width <= 0f || bounds.height <= 0f || bounds.left < 0f || bounds.top < 0f ||
            bounds.right > width.toFloat() || bounds.bottom > height.toFloat()
        ) return false
        val center = bounds.center
        return center.x in 0f..width.toFloat() && center.y in 0f..height.toFloat()
    }

    private fun click(scene: ImageComposeScene, node: SemanticsNode) {
        val position = node.boundsInRoot.center
        scene.sendPointerEvent(PointerEventType.Move, position, buttons = PointerButtons())
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = position,
            buttons = PointerButtons(isPrimaryPressed = true),
            button = PointerButton.Primary,
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = position,
            buttons = PointerButtons(),
            button = PointerButton.Primary,
        )
        render(scene)
    }

    private companion object {
        const val ResourceUserAgent = "Desktop profile forms integration test"
    }
}

private fun Rect.contains(point: Offset): Boolean =
    point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
