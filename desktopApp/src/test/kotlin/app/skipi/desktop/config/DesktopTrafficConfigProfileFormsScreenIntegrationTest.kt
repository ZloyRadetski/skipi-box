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
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_android_title
import app.skipi.ui.resources.configs_dns_direct_section
import app.skipi.ui.resources.configs_dns_hosts_title
import app.skipi.ui.resources.configs_dns_title
import app.skipi.ui.resources.configs_general_title
import app.skipi.ui.resources.configs_ipv6
import app.skipi.ui.resources.configs_local_dns
import app.skipi.ui.resources.configs_network_enabled
import app.skipi.ui.resources.configs_network_title
import app.skipi.ui.resources.configs_raw_edit
import app.skipi.ui.resources.configs_sniffing
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.TrafficConfigNetworkActivation
import features.config.TrafficConfigNetworkTransportWifi
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.skipiSectionValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Verifies profile-form navigation, persistence, scrolling, and narrow/wide rendering. */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopTrafficConfigProfileFormsScreenIntegrationTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun basicsToolbarBackPersistsTheEditedNameIntoTheTemporaryConfigJson() = runBlocking {
        val labels = labels()
        val fixture = fixture()
        withScreen(fixture, width = 390, height = 844) { screen ->
            openVisualEditor(screen, fixture.target.name, labels)
            setEditableTextByCurrentValue(
                scene = screen.scene,
                currentValue = fixture.target.name,
                nextValue = "Profile saved by Back",
                width = 390,
                height = 844,
            )

            clickTopBarBack(screen.scene, width = 390, height = 844)

            val saved = assertOnlyTargetChanged(screen, fixture) { profile ->
                assertEquals("Profile saved by Back", profile.name)
                assertEquals("Profile saved by Back", profile.toTrafficConfigState(ResourceUserAgent).name)
                assertEquals("Profile saved by Back", profile.content.skipiSectionValues()["profile-name"]?.last())
            }
            assertTrue(saved.content.contains("vendor-option = preserve-target"))
            assertNotNull(awaitVisibleText(screen.scene, "Profile saved by Back", 390, 844))
        }
    }

    @Test
    fun basicsSectionNavigationPersistsItsDraftBeforeOpeningGeneral() = runBlocking {
        val labels = labels()
        val fixture = fixture()
        withScreen(fixture, width = 1100, height = 800) { screen ->
            openVisualEditor(screen, fixture.target.name, labels)
            setEditableTextByCurrentValue(
                scene = screen.scene,
                currentValue = fixture.target.name,
                nextValue = "Profile saved before General",
                width = 1100,
                height = 800,
            )

            clickClickableText(screen.scene, labels.generalTitle, 1100, 800)

            val saved = assertOnlyTargetChanged(screen, fixture) { profile ->
                assertEquals("Profile saved before General", profile.name)
                assertEquals("Profile saved before General", profile.content.skipiSectionValues()["profile-name"]?.last())
            }
            assertTrue(saved.content.contains("vendor-option = preserve-target"))
            assertNotNull(awaitVisibleText(screen.scene, labels.ipv6, 1100, 800), "General should open after Basics is committed")
        }
    }

    @Test
    fun eachDetailSaveActionPersistsItsPublicWidgetEditAndReturnsToBasics() = runBlocking {
        val labels = labels()
        for (form in detailForms(labels)) {
            val fixture = fixture()
            withScreen(fixture, width = 1100, height = 800) { screen ->
                openVisualEditor(screen, fixture.target.name, labels)
                openSection(screen.scene, form.sectionTitle, 1100, 800)
                clickSwitchForLabel(screen.scene, form.controlLabel, 1100, 800)

                clickContentDescription(screen.scene, labels.save, 1100, 800)

                assertOnlyTargetChanged(screen, fixture) { profile -> assertFormResult(profile, form, fixture.target.name) }
                assertNotNull(
                    awaitVisibleClickableText(screen.scene, labels.generalTitle, 1100, 800),
                    "${form.key} Save should return to Basics",
                )
            }
        }
    }

    @Test
    fun eachDetailToolbarBackPersistsItsPublicWidgetEditAndReturnsToBasics() = runBlocking {
        val labels = labels()
        for (form in detailForms(labels)) {
            val fixture = fixture()
            withScreen(fixture, width = 1100, height = 800) { screen ->
                openVisualEditor(screen, fixture.target.name, labels)
                openSection(screen.scene, form.sectionTitle, 1100, 800)
                clickSwitchForLabel(screen.scene, form.controlLabel, 1100, 800)

                clickTopBarBack(screen.scene, width = 1100, height = 800)

                assertOnlyTargetChanged(screen, fixture) { profile -> assertFormResult(profile, form, fixture.target.name) }
                assertNotNull(
                    awaitVisibleClickableText(screen.scene, labels.generalTitle, 1100, 800),
                    "${form.key} Back should return to Basics after saving",
                )
            }
        }
    }

    @Test
    fun rendersBasicsAndPortableSectionsAtNarrowAndWideDesktopViewports() = runBlocking {
        val labels = labels()
        val forms = detailForms(labels)
        for ((width, height) in listOf(390 to 844, 1100 to 800)) {
            renderFormViewport("basics", null, labels, width, height)
            forms.forEach { form -> renderFormViewport(form.key, form, labels, width, height) }
        }
    }

    @Test
    fun rendersRawEditorMetadataAndDocumentFieldAtNarrowAndWideViewports() = runBlocking {
        val labels = labels()
        for ((width, height) in listOf(390 to 844, 1100 to 800)) {
            val fixture = fixture()
            withScreen(fixture, width, height) { screen ->
                openRawEditor(screen, fixture.target.name, labels, width, height)
                assertNotNull(awaitVisibleText(screen.scene, "Название", width, height))
                assertNotNull(awaitVisibleText(screen.scene, "Источник URL (необязательно)", width, height))
                assertNotNull(awaitVisibleClickableText(screen.scene, "Сохранить", width, height))
                assertVisibleTopBarBack(screen.scene, width, height)
                assertTrue(hasVerticalScroll(screen.scene), "Raw editor should expose its scrollable form")
                assertFullyVisible(
                    requireNotNull(awaitVisibleText(screen.scene, "Содержимое .conf", width, height)),
                    width,
                    height,
                    "raw .conf field label",
                )
                saveSnapshot(screen.scene, "raw-metadata", width, height)

                val rawFieldBeforeScroll = requireNotNull(rawEditableFieldNode(screen.scene, fixture.target.content)) {
                    "The raw .conf TextField should expose public SetText semantics before scrolling"
                }
                val rawScrollNode = requireNotNull(outerRawFormScrollNode(screen.scene, rawFieldBeforeScroll)) {
                    "The raw editor should expose a scrollable parent form with remaining vertical range"
                }
                val initialRange = rawScrollNode.config[SemanticsProperties.VerticalScrollAxisRange]
                val initialScrollPosition = initialRange.value()
                val initialScrollMaximum = initialRange.maxValue()
                val requestedScrollDelta = height * 0.65f
                val targetScrollPosition = minOf(
                    initialScrollPosition + requestedScrollDelta,
                    initialScrollMaximum,
                )
                assertTrue(
                    targetScrollPosition > initialScrollPosition,
                    "The raw editor should have room to scroll; " +
                        "before=$initialScrollPosition target=$targetScrollPosition max=$initialScrollMaximum",
                )
                val scrollBy = requireNotNull(rawScrollNode.config[SemanticsActions.ScrollBy].action)
                scrollBy(0f, requestedScrollDelta)

                val scrollDeadline = System.nanoTime() + 3_000_000_000L
                var scrolledPosition = initialScrollPosition
                var scrolledMaximum = initialScrollMaximum
                while (scrolledPosition < targetScrollPosition - 1f && System.nanoTime() < scrollDeadline) {
                    render(screen.scene)
                    Thread.sleep(16L)
                    val scrolledField = requireNotNull(rawEditableFieldNode(screen.scene, fixture.target.content)) {
                        "The raw .conf TextField should remain in the semantics tree while its form scrolls"
                    }
                    val scrolledRange = requireNotNull(outerRawFormScrollNode(screen.scene, scrolledField)) {
                        "The raw editor scrollable parent should remain in the semantics tree after scrolling"
                    }.config[SemanticsProperties.VerticalScrollAxisRange]
                    scrolledPosition = scrolledRange.value()
                    scrolledMaximum = scrolledRange.maxValue()
                }
                val scrollDiagnostic =
                    "before=$initialScrollPosition target=$targetScrollPosition after=$scrolledPosition " +
                        "max=$initialScrollMaximum maxAfter=$scrolledMaximum"
                assertTrue(
                    scrolledPosition > initialScrollPosition,
                    "Raw .conf capture should follow a real vertical scroll; $scrollDiagnostic",
                )
                assertTrue(
                    scrolledPosition >= targetScrollPosition - 1f,
                    "Raw .conf scroll should reach the requested target within 1px; $scrollDiagnostic",
                )
                assertTrue(
                    allNodes(screen.scene).none { candidate ->
                        candidate.hasText("Название") && isCenterOnScreen(candidate, width, height)
                    },
                    "The raw profile-name field should be outside the viewport before the document capture; " +
                        scrollDiagnostic,
                )
                val rawField = requireNotNull(allNodes(screen.scene).firstOrNull { candidate ->
                    candidate.config.contains(SemanticsActions.SetText) &&
                        candidate.config.contains(SemanticsProperties.EditableText) &&
                        candidate.config[SemanticsProperties.EditableText].text == fixture.target.content &&
                        candidate.boundsInRoot.right > 0f && candidate.boundsInRoot.left < width.toFloat() &&
                        candidate.boundsInRoot.bottom > 0f && candidate.boundsInRoot.top < height.toFloat()
                }) {
                    "The scrolled raw .conf TextField should remain reachable through public SetText semantics"
                }
                assertNotNull(rawField.config[SemanticsActions.SetText].action)
                assertVisibleTopBarBack(screen.scene, width, height)
                saveSnapshot(screen.scene, "raw-document", width, height)
            }
        }
    }

    @Test
    fun dnsToolbarBackgroundDoesNotChangeWhenTheFormScrolls() = runBlocking {
        val labels = labels()
        for ((width, height) in listOf(390 to 844, 1100 to 800)) {
            val fixture = fixture()
            withScreen(fixture, width, height) { screen ->
                openVisualEditor(screen, fixture.target.name, labels)
                openSection(screen.scene, labels.dnsTitle, width, height)

                settleScene(screen.scene, "DNS before scrolling at ${width}x$height")
                val toolbarBackgroundBefore = sampleEmptyToolbarPixels(screen.scene, width)

                assertNotNull(awaitVisibleText(screen.scene, labels.dnsDirect, width, height))
                assertNotNull(awaitVisibleText(screen.scene, labels.dnsHosts, width, height))

                settleScene(screen.scene, "DNS after scrolling at ${width}x$height")
                val toolbarBackgroundAfter = sampleEmptyToolbarPixels(screen.scene, width)
                assertEquals(
                    toolbarBackgroundBefore,
                    toolbarBackgroundAfter,
                    "Scrolling the DNS form must not change empty toolbar background pixels at ${width}x$height",
                )
            }
        }
    }

    private fun renderFormViewport(
        key: String,
        form: DetailForm?,
        labels: Labels,
        width: Int,
        height: Int,
    ) {
        val fixture = fixture()
        withScreen(fixture, width, height) { screen ->
            openVisualEditor(screen, fixture.target.name, labels)
            if (form == null) {
                val nameField = requireNotNull(awaitEditableNode(screen.scene, fixture.target.name, width, height))
                assertFullyVisible(nameField, width, height, "Basics profile-name field")
                assertVisibleTopBarBack(screen.scene, width, height)
                assertTrue(hasVerticalScroll(screen.scene), "Basics should expose its scrollable form")
                saveSnapshot(screen.scene, "basics", width, height)
                assertFullyVisible(
                    requireNotNull(awaitVisibleClickableText(screen.scene, labels.generalTitle, width, height)),
                    width,
                    height,
                    "Basics → General card",
                )
                return@withScreen
            }

            openSection(screen.scene, form.sectionTitle, width, height)
            val control = requireNotNull(
                awaitSwitchForLabel(screen.scene, form.controlLabel, width, height),
                { "${form.key} control should be reachable through a visible SwitchPreference" },
            )
            assertFullyVisible(control, width, height, "${form.key} switch")
            assertVisibleTopBarBack(screen.scene, width, height)
            assertFullyVisible(
                requireNotNull(awaitVisibleContentDescription(screen.scene, labels.save, width, height)),
                width,
                height,
                "${form.key} toolbar Save",
            )
            assertTrue(hasVerticalScroll(screen.scene), "${form.key} should expose its scrollable form")

            if (form.key == "dns") {
                assertFullyVisible(
                    requireNotNull(awaitVisibleText(screen.scene, labels.dnsDirect, width, height)),
                    width,
                    height,
                    "DNS direct section",
                )
                assertFullyVisible(
                    requireNotNull(awaitVisibleText(screen.scene, labels.dnsHosts, width, height)),
                    width,
                    height,
                    "DNS hosts section",
                )
            }
            saveSnapshot(screen.scene, form.key, width, height)
        }
    }

    private fun assertFormResult(profile: ConfigProfile, form: DetailForm, expectedName: String) {
        assertEquals(expectedName, profile.name, "Detail-form edits must preserve the profile name")
        val state = profile.toTrafficConfigState(ResourceUserAgent)
        val general = profile.content.analyzeShadowrocketConfig().general
        val skipi = profile.content.skipiSectionValues()
        when (form.key) {
            "general" -> {
                assertEquals("true", general["ipv6"])
                assertEquals("false", general["prefer-ipv6"])
                assertEquals(false, state.androidSettings.enableSniffing)
                assertEquals(false, state.androidSettings.enableVpnLocalDns)
                assertEquals(TrafficConfigNetworkActivation(false, TrafficConfigNetworkTransportWifi), state.networkActivation)
            }
            "dns" -> {
                assertTrue(state.androidSettings.enableVpnLocalDns)
                assertEquals("true", skipi["vpn-local-dns"]?.last())
                assertEquals(listOf("9.9.9.9"), state.androidSettings.directDns)
                assertEquals("9.9.9.9", general["dns-server"])
                assertEquals(false, state.androidSettings.enableSniffing)
                assertEquals(TrafficConfigNetworkActivation(false, TrafficConfigNetworkTransportWifi), state.networkActivation)
            }
            "tunnel" -> {
                assertTrue(state.androidSettings.enableSniffing)
                assertEquals("true", skipi["sniffing"]?.last())
                assertEquals("4", state.androidSettings.muxConcurrency)
                assertEquals(false, state.androidSettings.enableVpnLocalDns)
                assertEquals(TrafficConfigNetworkActivation(false, TrafficConfigNetworkTransportWifi), state.networkActivation)
            }
            "network" -> {
                assertEquals(TrafficConfigNetworkActivation(true, TrafficConfigNetworkTransportWifi), state.networkActivation)
                assertEquals("true", skipi["network-activation"]?.last())
                assertEquals("wifi", skipi["network-transport"]?.last())
                assertEquals(false, state.androidSettings.enableSniffing)
                assertEquals(false, state.androidSettings.enableVpnLocalDns)
            }
            else -> error("Unknown form ${form.key}")
        }
    }

    private fun assertOnlyTargetChanged(
        screen: ProfileFormsScreen,
        fixture: Fixture,
        assertTarget: (ConfigProfile) -> Unit,
    ): ConfigProfile {
        val persisted = screen.loadPersistedLibrary()
        assertEquals(listOf(3, 7), persisted.configs.map(ConfigProfile::id))
        assertEquals(3, persisted.selectedConfigId)
        assertEquals(29, persisted.nextConfigId)
        assertEquals(fixture.library.configs.map(ConfigProfile::id), persisted.configs.map(ConfigProfile::id))
        assertEquals(
            fixture.library.configs.filterNot { it.id == fixture.target.id },
            persisted.configs.filterNot { it.id == fixture.target.id },
            "Editing the target must preserve every non-target ConfigProfile byte-for-byte",
        )
        val savedTarget = persisted.configs.single { it.id == fixture.target.id }
        assertEquals(fixture.target.id, savedTarget.id)
        assertEquals(fixture.target.lastUpdatedAtMillis, savedTarget.lastUpdatedAtMillis)
        assertEquals(fixture.target.sourceUrl, savedTarget.sourceUrl)
        assertEquals(fixture.target.updateLocked, savedTarget.updateLocked)
        assertNotEquals(fixture.target, savedTarget, "The visible widget edit should update the target row")
        assertEquals(persisted, screen.latestLibrary, "The host should publish the JSON value it persisted")
        val typed = savedTarget.toTrafficConfigState(ResourceUserAgent)
        val skipi = savedTarget.content.skipiSectionValues()
        assertEquals(true, typed.autoUpdate)
        assertEquals("24", typed.updateInterval)
        assertEquals(false, typed.resourceSettings.autoUpdate)
        assertEquals("72", typed.resourceSettings.updateInterval)
        assertEquals("Profile form fixture/1.0", typed.resourceSettings.userAgent)
        assertEquals("true", skipi["profile-auto-update"]?.last())
        assertEquals("24", skipi["profile-update-interval"]?.last())
        assertEquals("false", skipi["resource-auto-update"]?.last())
        assertEquals("72", skipi["resource-update-interval"]?.last())
        assertEquals("Profile form fixture/1.0", skipi["resource-user-agent"]?.last())
        assertProfileDocumentIsolation(savedTarget)
        assertTarget(savedTarget)
        return savedTarget
    }

    private fun assertProfileDocumentIsolation(profile: ConfigProfile) {
        assertTrue(profile.content.contains("[Proxy]"))
        assertTrue(profile.content.contains("server = target.example:443"))
        assertTrue(profile.content.contains("[Rule]"))
        assertTrue(profile.content.contains("DOMAIN-SUFFIX,target.example,DIRECT"))
        assertTrue(profile.content.contains("[Vendor]"))
        assertTrue(profile.content.contains("vendor-option = preserve-target"))
    }

    private fun openVisualEditor(screen: ProfileFormsScreen, targetName: String, labels: Labels) {
        clickIconInsideProfileCard(screen.scene, targetName, labels.uiEdit, screen.width, screen.height)
        assertNotNull(awaitEditableNode(screen.scene, targetName, screen.width, screen.height), "The public UI Edit action should open Basics")
    }

    private fun openRawEditor(screen: ProfileFormsScreen, targetName: String, labels: Labels, width: Int, height: Int) {
        clickIconInsideProfileCard(screen.scene, targetName, "Ещё", width, height)
        clickClickableText(screen.scene, labels.rawEdit, width, height)
        assertNotNull(awaitVisibleText(screen.scene, "Название", width, height), "The profile context menu should open Raw Edit")
    }

    private fun openSection(scene: ImageComposeScene, title: String, width: Int, height: Int) {
        clickClickableText(scene, title, width, height)
    }

    private fun clickIconInsideProfileCard(
        scene: ImageComposeScene,
        profileName: String,
        contentDescription: String,
        width: Int,
        height: Int,
    ) {
        val profile = requireNotNull(awaitVisibleText(scene, profileName, width, height)) {
            "Expected the profile row '$profileName' to be visible before choosing its action"
        }
        val card = allNodes(scene)
            .filter { candidate ->
                candidate.config.contains(SemanticsActions.OnClick) &&
                    candidate.boundsInRoot.contains(profile.boundsInRoot.center)
            }
            .minByOrNull { candidate -> candidate.boundsInRoot.width * candidate.boundsInRoot.height }
        requireNotNull(card) { "Expected the public clickable profile card for '$profileName'" }
        val icon = requireNotNull(
            allNodes(scene).firstOrNull { candidate ->
                candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .any { description -> description == contentDescription } &&
                    card.boundsInRoot.contains(candidate.boundsInRoot.center) &&
                    isCenterOnScreen(candidate, width, height)
            },
        ) { "Expected '$contentDescription' inside the '$profileName' row" }
        click(scene, icon)
    }

    private fun clickClickableText(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val text = requireNotNull(awaitVisibleClickableText(scene, label, width, height)) {
            "Expected visible clickable text '$label' at ${width}x$height"
        }
        val clickTarget = requireNotNull(
            text.ancestorsIncludingSelf().firstOrNull { candidate -> candidate.config.contains(SemanticsActions.OnClick) },
        ) { "Expected '$label' to be backed by a clickable Desktop UI element" }
        click(scene, clickTarget)
    }

    private fun clickContentDescription(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val control = requireNotNull(awaitVisibleContentDescription(scene, label, width, height)) {
            "Expected visible control '$label' at ${width}x$height"
        }
        click(scene, control)
    }

    private fun clickTopBarBack(scene: ImageComposeScene, width: Int, height: Int) {
        val back = requireNotNull(awaitVisibleNode(scene, width, height) { candidate ->
            val center = candidate.boundsInRoot.center
            candidate.config.contains(SemanticsActions.OnClick) &&
                center.x in 0f..72f && center.y in 0f..80f
        }) { "Expected the top-left Back control at ${width}x$height" }
        click(scene, back)
    }

    private fun assertVisibleTopBarBack(scene: ImageComposeScene, width: Int, height: Int) {
        val back = requireNotNull(awaitVisibleNode(scene, width, height) { candidate ->
            val center = candidate.boundsInRoot.center
            candidate.config.contains(SemanticsActions.OnClick) &&
                center.x in 0f..72f && center.y in 0f..80f
        }) { "Expected a reachable top-left Back control at ${width}x$height" }
        assertFullyVisible(back, width, height, "top-bar Back")
    }

    private fun clickSwitchForLabel(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val switch = requireNotNull(awaitSwitchForLabel(scene, label, width, height)) {
            "Expected a public clickable Toggleable control associated with '$label'"
        }
        click(scene, switch)
    }

    private fun awaitSwitchForLabel(scene: ImageComposeScene, label: String, width: Int, height: Int): SemanticsNode? {
        val title = awaitVisibleText(scene, label, width, height) ?: return null
        return title.ancestorsIncludingSelf()
            .firstOrNull { row ->
                row.config.contains(SemanticsActions.OnClick) &&
                    row.descendants().count { candidate ->
                        candidate.config.contains(SemanticsProperties.ToggleableState)
                    } == 1 &&
                    isCenterOnScreen(row, width, height)
            }
    }

    private fun setEditableTextByCurrentValue(
        scene: ImageComposeScene,
        currentValue: String,
        nextValue: String,
        width: Int,
        height: Int,
    ) {
        val field = requireNotNull(awaitEditableNode(scene, currentValue, width, height)) {
            "Expected a visible TextField with current value '$currentValue'"
        }
        val setText = requireNotNull(field.config[SemanticsActions.SetText].action)
        assertTrue(setText(AnnotatedString(nextValue)), "The public TextField SetText semantics action should accept input")
        render(scene)
        assertEquals(nextValue, awaitEditableNode(scene, nextValue, width, height)?.config?.get(SemanticsProperties.EditableText)?.text)
    }

    private fun awaitEditableNode(
        scene: ImageComposeScene,
        value: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitVisibleNode(scene, width, height) { candidate ->
        candidate.config.contains(SemanticsActions.SetText) &&
            candidate.config.contains(SemanticsProperties.EditableText) &&
            candidate.config[SemanticsProperties.EditableText].text == value
    }

    private fun awaitVisibleText(scene: ImageComposeScene, label: String, width: Int, height: Int): SemanticsNode? =
        awaitVisibleNode(scene, width, height) { candidate -> candidate.hasText(label) }

    private fun awaitVisibleClickableText(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitVisibleNode(scene, width, height) { candidate ->
        candidate.hasText(label) && candidate.ancestorsIncludingSelf().any { ancestor ->
            ancestor.config.contains(SemanticsActions.OnClick)
        }
    }

    private fun awaitVisibleContentDescription(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitVisibleNode(scene, width, height) { candidate ->
        candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .any { description -> description.equals(label, ignoreCase = true) }
    }

    private fun awaitVisibleNode(
        scene: ImageComposeScene,
        width: Int,
        height: Int,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + 3_000_000_000L
        var scrollSteps = 0
        do {
            render(scene)
            allNodes(scene).firstOrNull { candidate ->
                predicate(candidate) && isCenterOnScreen(candidate, width, height)
            }?.let { return it }

            if (scrollSteps < 12) {
                val scrollable = allNodes(scene)
                    .filter { candidate ->
                        candidate.config.contains(SemanticsActions.ScrollBy) &&
                            candidate.config.contains(SemanticsProperties.VerticalScrollAxisRange)
                    }
                    .maxByOrNull { candidate -> candidate.boundsInRoot.width * candidate.boundsInRoot.height }
                scrollable?.config?.get(SemanticsActions.ScrollBy)?.action?.let { scrollBy ->
                    scrollBy(0f, height * 0.65f)
                    scrollSteps += 1
                }
            }
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)
        return null
    }

    private fun hasVerticalScroll(scene: ImageComposeScene): Boolean = allNodes(scene).any { candidate ->
        candidate.config.contains(SemanticsActions.ScrollBy) &&
            candidate.config.contains(SemanticsProperties.VerticalScrollAxisRange)
    }

    private fun rawEditableFieldNode(scene: ImageComposeScene, content: String): SemanticsNode? =
        allNodes(scene).firstOrNull { candidate ->
            candidate.config.contains(SemanticsActions.SetText) &&
                candidate.config.contains(SemanticsProperties.EditableText) &&
                candidate.config[SemanticsProperties.EditableText].text == content
        }

    private fun outerRawFormScrollNode(scene: ImageComposeScene, rawField: SemanticsNode): SemanticsNode? =
        rawField.ancestorsIncludingSelf().drop(1)
            .filter { candidate ->
                candidate.config.contains(SemanticsActions.ScrollBy) &&
                    candidate.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                    candidate.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f
            }
            .maxByOrNull { candidate -> candidate.boundsInRoot.width * candidate.boundsInRoot.height }

    private fun sampleEmptyToolbarPixels(scene: ImageComposeScene, width: Int): List<Int> {
        val image = scene.render(advanceFrameTime())
        try {
            val pixels = requireNotNull(image.peekPixels())
            try {
                return listOf(20, width / 2, width - 20).map { x -> pixels.getColor(x, 6) }
            } finally {
                pixels.close()
            }
        } finally {
            image.close()
        }
    }

    private fun assertFullyVisible(node: SemanticsNode, width: Int, height: Int, description: String) {
        val bounds = node.boundsInRoot
        assertTrue(
            bounds.width > 0f && bounds.height > 0f &&
                bounds.left >= 0f && bounds.top >= 0f &&
                bounds.right <= width.toFloat() && bounds.bottom <= height.toFloat(),
            "$description bounds $bounds should have positive size and fit inside ${width}x$height",
        )
    }

    private fun isCenterOnScreen(node: SemanticsNode, width: Int, height: Int): Boolean {
        val bounds = node.boundsInRoot
        if (bounds.width <= 0f || bounds.height <= 0f ||
            bounds.left < 0f || bounds.top < 0f ||
            bounds.right > width.toFloat() || bounds.bottom > height.toFloat()
        ) {
            return false
        }
        val center = bounds.center
        return center.x in 0f..width.toFloat() && center.y in 0f..height.toFloat()
    }

    private fun SemanticsNode.hasText(label: String): Boolean =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { text -> text.text.equals(label, ignoreCase = true) }

    private fun SemanticsNode.ancestorsIncludingSelf(): Sequence<SemanticsNode> = sequence {
        var current: SemanticsNode? = this@ancestorsIncludingSelf
        var remaining = 10
        while (current != null && remaining > 0) {
            yield(current)
            current = current.parent
            remaining -= 1
        }
    }

    private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
        yield(this@descendants)
        children.forEach { child -> yieldAll(child.descendants()) }
    }

    private fun allNodes(scene: ImageComposeScene): Sequence<SemanticsNode> =
        scene.semanticsOwners.asSequence().flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }

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

    private fun render(scene: ImageComposeScene) {
        scene.render(advanceFrameTime()).close()
    }

    private fun advanceFrameTime(): Long {
        frameTimeNanos += 16_666_667L
        return frameTimeNanos
    }

    private fun saveSnapshot(scene: ImageComposeScene, page: String, width: Int, height: Int) {
        settleScene(scene, page)
        val image = scene.render(advanceFrameTime())
        try {
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            assertTrue(!image.isEmpty, "$page at ${width}x$height should render visible content")
            val pixels = requireNotNull(image.peekPixels())
            try {
                val sampledColors = buildSet {
                    for (y in 0 until height step 24) for (x in 0 until width step 24) add(pixels.getColor(x, y))
                }
                assertTrue(sampledColors.size >= 3, "$page should include visible controls and surfaces")
            } finally {
                pixels.close()
            }
            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
            try {
                val reportDirectory = File(
                    System.getProperty("user.dir"),
                    "build/reports/desktop-traffic-config-profile-forms",
                )
                assertTrue(reportDirectory.mkdirs() || reportDirectory.isDirectory)
                val file = File(reportDirectory, "$page-${width}x$height.png")
                file.writeBytes(encoded.bytes)
                assertTrue(file.length() > 1_024L, "$page PNG should contain rendered UI")
            } finally {
                encoded.close()
            }
        } finally {
            image.close()
        }
    }

    private fun settleScene(scene: ImageComposeScene, description: String) {
        val minimumFrameCount = 30
        val requiredStableFrames = 3
        val maximumFrameCount = 90
        var firstFramePixels: IntArray? = null
        var secondFramePixels: IntArray? = null
        var previousPixels: IntArray? = null
        var stableFrames = 0

        repeat(maximumFrameCount) { frameIndex ->
            val image = scene.render(advanceFrameTime())
            try {
                val pixelMap = requireNotNull(image.peekPixels())
                try {
                    if (firstFramePixels == null) {
                        firstFramePixels = IntArray(image.width * image.height)
                        secondFramePixels = IntArray(image.width * image.height)
                    }
                    val first = requireNotNull(firstFramePixels)
                    val second = requireNotNull(secondFramePixels)
                    val currentPixels = if (previousPixels === first) second else first
                    var pixelIndex = 0
                    for (y in 0 until image.height) {
                        for (x in 0 until image.width) {
                            currentPixels[pixelIndex++] = pixelMap.getColor(x, y)
                        }
                    }

                    val previous = previousPixels
                    if (previous != null && currentPixels.contentEquals(previous)) {
                        stableFrames++
                    } else {
                        stableFrames = 0
                    }
                    previousPixels = currentPixels
                } finally {
                    pixelMap.close()
                }
            } finally {
                image.close()
            }

            if (frameIndex + 1 >= minimumFrameCount && stableFrames >= requiredStableFrames) return
            Thread.sleep(1L)
        }

        error("The $description screen did not reach stable rendered frames after $maximumFrameCount Compose frames")
    }

    private fun labels(): Labels = runBlocking {
        Labels(
            uiEdit = getString(Res.string.configs_ui_edit),
            save = getString(Res.string.common_save),
            generalTitle = getString(Res.string.configs_general_title),
            dnsTitle = getString(Res.string.configs_dns_title),
            tunnelTitle = getString(Res.string.configs_android_title),
            networkTitle = getString(Res.string.configs_network_title),
            ipv6 = getString(Res.string.configs_ipv6),
            localDns = getString(Res.string.configs_local_dns),
            sniffing = getString(Res.string.configs_sniffing),
            networkEnabled = getString(Res.string.configs_network_enabled),
            dnsDirect = getString(Res.string.configs_dns_direct_section),
            dnsHosts = getString(Res.string.configs_dns_hosts_title),
            rawEdit = getString(Res.string.configs_raw_edit),
        )
    }

    private fun detailForms(labels: Labels): List<DetailForm> = listOf(
        DetailForm("general", labels.generalTitle, labels.ipv6),
        DetailForm("dns", labels.dnsTitle, labels.localDns),
        DetailForm("tunnel", labels.tunnelTitle, labels.sniffing),
        DetailForm("network", labels.networkTitle, labels.networkEnabled),
    )

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
        val target = ConfigProfile(
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
profile-auto-update = true
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
network-activation = false
network-transport = wifi
""",
        )
        val library = DesktopConfigLibrary(
            selectedConfigId = sibling.id,
            configs = listOf(sibling, target),
            nextConfigId = 29,
        )
        return Fixture(library = library, sibling = sibling, target = target)
    }

    private fun withScreen(
        fixture: Fixture,
        width: Int,
        height: Int,
        block: (ProfileFormsScreen) -> Unit,
    ) {
        withTemporaryRoot { root ->
            val screen = ProfileFormsScreen(root, fixture.library, width, height)
            try {
                block(screen)
            } finally {
                screen.close()
            }
        }
    }

    private fun withTemporaryRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("desktop-profile-forms-screen-").toFile()
        try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder<Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    private data class Labels(
        val uiEdit: String,
        val save: String,
        val generalTitle: String,
        val dnsTitle: String,
        val tunnelTitle: String,
        val networkTitle: String,
        val ipv6: String,
        val localDns: String,
        val sniffing: String,
        val networkEnabled: String,
        val dnsDirect: String,
        val dnsHosts: String,
        val rawEdit: String,
    )

    private data class DetailForm(
        val key: String,
        val sectionTitle: String,
        val controlLabel: String,
    )

    private data class Fixture(
        val library: DesktopConfigLibrary,
        val sibling: ConfigProfile,
        val target: ConfigProfile,
    )

    private class ProfileFormsScreen(
        root: File,
        initialLibrary: DesktopConfigLibrary,
        val width: Int,
        val height: Int,
    ) : AutoCloseable {
        private val configPath: Path = root.toPath().resolve("configs.json")
        private val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
        private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val frameTimeNanos = 1_000_000_000L
        var latestLibrary by mutableStateOf(initialLibrary)
            private set
        private val configRepository by lazy(LazyThreadSafetyMode.NONE) {
            DesktopTrafficConfigRepository(
                initialLibrary = loadPersistedLibrary(),
                readLibrary = ::loadPersistedLibrary,
                saveLibrary = { library -> DesktopConfigLibraries.save(configPath, library) },
                publishLibrary = { library -> latestLibrary = library },
            )
        }
        private val desktopResourceServices by lazy(LazyThreadSafetyMode.NONE, ::createResourceServices)
        val scene = ImageComposeScene(width = width, height = height)

        init {
            DesktopConfigLibraries.save(configPath, initialLibrary).getOrThrow()
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
                        fetchUserAgent = "Desktop profile forms screen test",
                        fetchTimeoutSeconds = 5,
                        fetchProfileDocument = { _, _ -> error("Unexpected profile network refresh in form test") },
                        contentPadding = PaddingValues(),
                        resourceActionScope = hostScope,
                        onResourceFileStatusMessage = {},
                        desktopSettings = DesktopAppSettings(),
                        exportProxyServerFullJson = { error("Unexpected proxy-server export in form test") },
                        onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global group apply in form test") },
                        proxyServers = emptyList(),
                        subscriptionCatalog = app.skipi.app.model.SubscriptionCatalog(),
                        resourceFiles = resourceFiles,
                        latencyByServerId = emptyMap(),
                        resourceServices = desktopResourceServices,
                        resourceFileStatusMessage = "",
                    )
                }
            }
            renderScene()
        }

        fun loadPersistedLibrary(): DesktopConfigLibrary = DesktopConfigLibraries.load(configPath).getOrThrow()

        private fun transformLibrary(transform: (DesktopConfigLibrary) -> DesktopConfigLibrary): DesktopConfigLibrary =
            configRepository.transformLatest(transform)

        private fun createResourceServices() = DesktopResourceFileServices(
            scope = hostScope,
            resourceFiles = resourceFiles,
            readSettings = ::DesktopAppSettings,
            readProfile = { profileId ->
                latestLibrary.configs.singleOrNull { profile -> profile.id == profileId }
                    ?.toTrafficConfigState(ResourceUserAgent)
            },
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

        private fun renderScene() {
            scene.render(frameTimeNanos).close()
        }

        override fun close() {
            scene.close()
            hostScope.cancel()
        }
    }

    private companion object {
        const val ResourceUserAgent = "Desktop profile forms integration test"
    }
}

private fun Rect.contains(point: Offset): Boolean =
    point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
