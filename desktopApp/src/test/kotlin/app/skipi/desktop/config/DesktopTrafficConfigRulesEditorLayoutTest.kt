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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.configs_rules_edit
import app.skipi.ui.resources.routing_outbound_block
import app.skipi.ui.resources.routing_system_targets
import app.skipi.ui.resources.routing_table_col_targets
import app.skipi.ui.resources.routing_table_complex_open
import app.skipi.ui.resources.routing_table_server_or_group
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTrafficConfigRulesEditorLayoutTest {
    private val raw = """
        [Rule]
        AND,((OR,((DOMAIN-KEYWORD,hypixel.net),(DOMAIN-KEYWORD,mojang.com))),(DST-PORT,25565)),DIRECT // Hypixel
        OR,((AND,((DOMAIN,a.com),(DST-PORT,1))),(DOMAIN,b.com)),DIRECT // Complex one
        FINAL,PROXY
    """.trimIndent() + "\n"

    private class Harness(raw: String, width: Int, root: File) {
        var profile by mutableStateOf(TrafficConfigState(id = 5, name = "P", rawConfig = raw))
        val controller = TrafficConfigEditorController(
            object : TrafficConfigEditorProfileStore {
                override fun update(profileId: Int, transform: (TrafficConfigState) -> TrafficConfigState): Boolean {
                    val updated = transform(profile)
                    if (updated.rawConfig != profile.rawConfig) writes++
                    profile = updated
                    return true
                }
            },
        )
        var writes = 0
        var shown by mutableStateOf(true)
        val scene = ImageComposeScene(width = width, height = 860, density = Density(1f))

        init {
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background)) {
                        if (shown) DesktopTrafficConfigRulesEditor(
                            profile = this@Harness.profile,
                            trafficConfigs = listOf(this@Harness.profile),
                            controller = controller,
                            proxyServers = emptyList(),
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            padding = PaddingValues(0.dp),
                            isWideScreen = true,
                            onBack = {},
                            fetchRoutingDocument = { error("no network in this test") },
                        )
                    }
                }
            }
            settle()
        }

        val rules get() = profile.rawConfig.analyzeShadowrocketConfig().rules.map { it.raw.trim() }

        fun settle() = repeat(4) { scene.render().close() }

        @OptIn(ExperimentalComposeUiApi::class)
        fun findText(text: String, wait: Boolean = true): SemanticsNode? {
            val deadline = System.nanoTime() + if (wait) 5_000_000_000L else 0L
            do {
                settle()
                val found = scene.semanticsOwners.asSequence()
                    .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }
                    .firstOrNull { node ->
                        node.boundsInRoot.width > 0f &&
                            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.equals(text, ignoreCase = true) }
                    }
                if (found != null) return found
                if (wait) Thread.sleep(30)
            } while (System.nanoTime() < deadline)
            return null
        }

        private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
            yield(this@descendants)
            children.forEach { yieldAll(it.descendants()) }
        }

        private fun click(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Move, at, buttons = PointerButtons())
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = PointerButton.Primary)
            settle()
        }

        fun clickText(text: String) = click(assertNotNull(findText(text), "no '$text' on screen").boundsInRoot.center)

        @OptIn(ExperimentalComposeUiApi::class)
        fun findTag(tag: String): SemanticsNode? = scene.semanticsOwners.asSequence()
            .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }
            .firstOrNull { node -> node.boundsInRoot.width > 0f && node.config.getOrElse(SemanticsProperties.TestTag) { "" } == tag }

        fun clickTag(tag: String) = click(assertNotNull(findTag(tag), "no tag '$tag' on screen").boundsInRoot.center)

        fun type(tag: String, value: String) {
            val field = assertNotNull(findTag(tag), "no field '$tag' on screen")
            assertTrue(field.config[SemanticsActions.SetText].action?.invoke(AnnotatedString(value)) == true)
            settle()
        }

        fun apply() = clickTag("routing-apply")

        fun leave() {
            shown = false
            settle()
        }

        @OptIn(ExperimentalComposeUiApi::class)
        fun clickBack() {
            val back = scene.semanticsOwners.asSequence()
                .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }
                .firstOrNull { node ->
                    val center = node.boundsInRoot.center
                    node.config.contains(SemanticsActions.OnClick) && center.x in 1f..72f && center.y in 1f..80f
                }
            click(assertNotNull(back, "no Back control").boundsInRoot.center)
        }
    }

    private fun withHarness(width: Int, block: Harness.() -> Unit) {
        val root = Files.createTempDirectory("rules-layout-").toFile()
        val harness = Harness(raw, width, root)
        try {
            harness.block()
        } finally {
            harness.scene.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun aWideWindowShowsTheTable() = withHarness(width = 1180) {
        assertNotNull(findText(runBlocking { getString(Res.string.routing_table_col_targets) }), "the table header is missing")
        assertNotNull(findText("Hypixel"))
    }

    @Test
    fun aNarrowWindowKeepsTheCards() = withHarness(width = 800) {
        assertNotNull(findText(runBlocking { getString(Res.string.configs_rules_add) }), "the cards' Add button is missing")
        assertNull(findText(runBlocking { getString(Res.string.routing_table_col_targets) }, wait = false), "the table must not show in a narrow window")
    }

    @Test
    fun theServerPickerAnswersTheOpenRuleOfTheTable() = withHarness(width = 1180) {
        clickText("Hypixel")
        runBlocking { clickText(getString(Res.string.routing_table_server_or_group)) }
        assertNotNull(findText(runBlocking { getString(Res.string.routing_system_targets) }), "the server picker did not open")
        runBlocking { clickText(getString(Res.string.routing_outbound_block)) }
        clickBack() // The selector's Back confirms its pending choice.

        assertNotNull(findText(runBlocking { getString(Res.string.routing_table_col_targets) }), "the table should be back after the picker")
        Thread.sleep(600)
        settle()
        assertEquals(0, writes, "the pick stays in the table until it is applied")

        apply()
        assertTrue(rules[0].contains(",REJECT // Hypixel"), "rules: $rules")
        assertEquals(1, writes)
    }

    @Test
    fun editsStayInTheTableUntilTheyAreApplied() = withHarness(width = 1180) {
        val before = profile.rawConfig
        clickTag("routing-toggle-2")
        assertEquals(before, profile.rawConfig, "a switch must not reach the profile by itself")
        assertEquals(0, writes)

        apply()

        assertTrue(rules[0].contains("skipi-disabled"), "rules: $rules")
        assertEquals(1, writes)
    }

    @Test
    fun severalEditsBecomeOneWrite() = withHarness(width = 1180) {
        clickText("Hypixel")
        type("routing-port", "25565, 8080")
        Thread.sleep(700)
        settle()
        clickTag("routing-toggle-2")
        clickTag("routing-route-block")
        assertEquals(0, writes)

        apply()

        assertEquals(1, writes)
        assertTrue(rules[0].contains("8080") && rules[0].contains("skipi-disabled") && rules[0].contains("REJECT"), "rules: $rules")
    }

    @Test
    fun applyingRightAfterTypingKeepsWhatWasTyped() = withHarness(width = 1180) {
        clickText("Hypixel")
        type("routing-port", "8080")

        apply()

        assertTrue(rules[0].contains("DST-PORT,8080"), "rules: $rules")
        assertEquals(1, writes)
    }

    @Test
    fun applyingTwiceWritesOnce() = withHarness(width = 1180) {
        clickTag("routing-toggle-2")
        apply()
        apply()

        assertEquals(1, writes)
    }

    @Test
    fun leavingTheScreenAppliesTheChanges() = withHarness(width = 1180) {
        clickText("Hypixel")
        type("routing-port", "8080")
        clickTag("routing-toggle-2")

        leave()

        assertEquals(1, writes)
        assertTrue(rules[0].contains("DST-PORT,8080") && rules[0].contains("skipi-disabled"), "rules: $rules")
    }

    @Test
    fun leavingWithoutChangesWritesNothing() = withHarness(width = 1180) {
        clickText("Hypixel")

        leave()

        assertEquals(0, writes)
    }

    @Test
    fun openingARuleInTheFormAppliesTheTableFirst() = withHarness(width = 1180) {
        clickTag("routing-toggle-2")
        clickText("Complex one")
        runBlocking { clickText(getString(Res.string.routing_table_complex_open)) }

        assertNotNull(findText(runBlocking { getString(Res.string.configs_rules_edit) }), "the rule form did not open")
        assertEquals(1, writes)
        assertTrue(rules[0].contains("skipi-disabled"), "rules: $rules")
    }

    @Test
    fun aChangeFromOutsideReachesTheTableWhenNothingIsPending() = withHarness(width = 1180) {
        profile = profile.copy(rawConfig = profile.rawConfig.replace("FINAL,PROXY", "DOMAIN,new.example,DIRECT // From outside\nFINAL,PROXY"))

        assertNotNull(findText("From outside"), "the table should follow the profile")
    }

    @Test
    fun aRuleThatDoesNotFitOpensTheRuleFormAndBackReturnsToTheTable() = withHarness(width = 1180) {
        clickText("Complex one")
        runBlocking { clickText(getString(Res.string.routing_table_complex_open)) }
        assertNotNull(findText(runBlocking { getString(Res.string.configs_rules_edit) }), "the rule form did not open")

        clickBack()

        assertNotNull(findText(runBlocking { getString(Res.string.routing_table_col_targets) }), "the table should be back")
        assertEquals(3, rules.size)
    }
}
