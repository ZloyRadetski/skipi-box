// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.ui.config.rememberSkipiRoutingTableState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_merge_import
import app.skipi.ui.resources.common_replace_existing
import app.skipi.ui.resources.routing_import_clipboard_title
import app.skipi.ui.resources.routing_import_no_valid_rules
import app.skipi.ui.resources.routing_table_import_clipboard
import app.skipi.ui.resources.routing_table_import_file
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val Export = """
    [
      {"remarks":"YouTube","outboundTag":"proxy","domain":["geosite:youtube"]},
      {"remarks":"Local","outboundTag":"direct","ip":["geoip:private"],"enabled":false},
      {"remarks":"dns","outboundTag":"direct","domain":["b.com"],"ruleType":2},
      {"remarks":"rest","outboundTag":"direct","port":"0-65535","network":"tcp,udp"}
    ]
""".trimIndent()

class DesktopRoutingTableScreenTest {
    private class Harness(
        val clipboard: String? = Export,
        val file: String? = null,
    ) {
        var profile by mutableStateOf(
            TrafficConfigState(id = 5, name = "P", rawConfig = "[Rule]\nDOMAIN-SUFFIX,old.com,PROXY // Old\nFINAL,PROXY\n"),
        )
        val controller = TrafficConfigEditorController(
            object : TrafficConfigEditorProfileStore {
                override fun update(profileId: Int, transform: (TrafficConfigState) -> TrafficConfigState): Boolean {
                    profile = transform(profile)
                    return true
                }
            },
        )
        val fetched = mutableListOf<String>()
        val scene = ImageComposeScene(width = 1180, height = 860, density = Density(1f))

        init {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background)) {
                        DesktopRoutingTableScreen(
                            profile = this@Harness.profile,
                            controller = controller,
                            state = rememberSkipiRoutingTableState(profile.id),
                            sources = DesktopRoutingImportSources(
                                readFile = { file },
                                readClipboard = { clipboard },
                                fetchUrl = { url -> fetched += url; Export },
                            ),
                            padding = PaddingValues(0.dp),
                            isWideScreen = true,
                            onBack = {},
                            onPickPolicy = {},
                            onEditRule = {},
                        )
                    }
                }
            }
            settle()
        }

        val rules get() = profile.rawConfig.analyzeShadowrocketConfig().rules.map { it.raw.trim() }

        fun settle() = repeat(4) { scene.render().close() }

        @OptIn(ExperimentalComposeUiApi::class)
        fun findText(text: String, contains: Boolean = false): SemanticsNode? {
            val deadline = System.nanoTime() + 5_000_000_000L
            do {
                settle()
                val found = scene.semanticsOwners.asSequence()
                    .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }
                    .firstOrNull { node ->
                        node.boundsInRoot.width > 0f &&
                            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                                .any { if (contains) text in it.text else it.text.equals(text, ignoreCase = true) }
                    }
                if (found != null) return found
                Thread.sleep(30)
            } while (System.nanoTime() < deadline)
            return null
        }

        private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
            yield(this@descendants)
            children.forEach { yieldAll(it.descendants()) }
        }

        fun clickText(text: String) {
            val node = assertNotNull(findText(text), "no '$text' on screen")
            val at: Offset = node.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Move, at, buttons = PointerButtons())
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = PointerButton.Primary)
            settle()
        }
    }

    private fun withHarness(harness: Harness = Harness(), block: Harness.() -> Unit) {
        try {
            harness.block()
        } finally {
            harness.scene.close()
        }
    }

    @Test
    fun importFromTheClipboardAsksHowAndMergesBeforeFinal() = withHarness {
        runBlocking { clickText(getString(Res.string.routing_table_import_clipboard)) }

        assertNotNull(findText(runBlocking { getString(Res.string.routing_import_clipboard_title) }), "the import dialog did not open")
        assertNotNull(findText("DNS", contains = true), "the skipped DNS rule is not mentioned")
        runBlocking { clickText(getString(Res.string.common_merge_import)) }

        assertEquals(
            listOf(
                "DOMAIN-SUFFIX,old.com,PROXY // Old",
                "DOMAIN-SET,geosite:youtube,PROXY // YouTube",
                "GEOIP,private,DIRECT,skipi-disabled // Local",
                "FINAL,PROXY",
            ),
            rules,
        )
    }

    @Test
    fun replacingDropsTheOldRulesAndTakesTheFinalOfTheExport() = withHarness {
        runBlocking { clickText(getString(Res.string.routing_table_import_clipboard)) }
        runBlocking { clickText(getString(Res.string.common_replace_existing)) }

        assertEquals(
            listOf("DOMAIN-SET,geosite:youtube,PROXY // YouTube", "GEOIP,private,DIRECT,skipi-disabled // Local", "FINAL,DIRECT"),
            rules,
        )
    }

    @Test
    fun textThatIsNotAnExportOnlyShowsANotice() = withHarness(Harness(clipboard = "hello")) {
        val before = profile.rawConfig
        runBlocking { clickText(getString(Res.string.routing_table_import_clipboard)) }

        assertNotNull(findText(runBlocking { getString(Res.string.routing_import_no_valid_rules) }), "no notice")
        assertEquals(before, profile.rawConfig)
    }

    @Test
    fun anEmptyClipboardOnlyShowsANotice() = withHarness(Harness(clipboard = null)) {
        val before = profile.rawConfig
        runBlocking { clickText(getString(Res.string.routing_table_import_clipboard)) }

        assertNotNull(findText(runBlocking { getString(Res.string.routing_import_no_valid_rules) }), "no notice")
        assertEquals(before, profile.rawConfig)
    }

    @Test
    fun importFromAFileUsesItsText() = withHarness(Harness(file = Export)) {
        runBlocking { clickText(getString(Res.string.routing_table_import_file)) }
        runBlocking { clickText(getString(Res.string.common_merge_import)) }

        assertTrue(rules.contains("DOMAIN-SET,geosite:youtube,PROXY // YouTube"), "rules: $rules")
    }

    @Test
    fun cancellingTheFilePickerChangesNothing() = withHarness(Harness(file = null)) {
        val before = profile.rawConfig
        runBlocking { clickText(getString(Res.string.routing_table_import_file)) }
        settle()

        assertEquals(before, profile.rawConfig)
    }
}
