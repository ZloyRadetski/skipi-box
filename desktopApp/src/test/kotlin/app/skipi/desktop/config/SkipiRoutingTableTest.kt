// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import app.skipi.ui.config.SkipiTrafficConfigRoutingTable
import app.skipi.ui.config.rememberSkipiRoutingTableState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.routing_table_complex_open
import app.skipi.ui.resources.routing_table_delete
import app.skipi.ui.resources.routing_table_final
import app.skipi.ui.resources.routing_table_move_down
import app.skipi.ui.resources.routing_table_server_or_group
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ShadowrocketRule
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RoutingSceneWidth = 1180
private const val RoutingSceneHeight = 860

private val RoutingProfile = """
    [General]
    ipv6 = true

    [Rule]
    OR,((DOMAIN-SET,geosite:category-ads-all),(DOMAIN-SET,geosite:win-spy)),REJECT // BLOCK
    AND,((OR,((DOMAIN-KEYWORD,hypixel.net),(DOMAIN-KEYWORD,sessionserver.mojang.com))),(DST-PORT,25565)),DIRECT // Hypixel
    OR,((DOMAIN-SET,geosite:private),(GEOIP,private)),DIRECT // private direct
    OR,((AND,((DOMAIN,a.com),(DST-PORT,1))),(DOMAIN,b.com)),DIRECT // Complex one
    DOMAIN-SUFFIX,steamdb.info,PROXY // proxy
    FINAL,PROXY
""".trimIndent() + "\n"

class SkipiRoutingTableTest {
    private class Harness(raw: String = RoutingProfile, width: Int = RoutingSceneWidth) {
        var profile by mutableStateOf(TrafficConfigState(id = 5, name = "P", rawConfig = raw))
        val controller = TrafficConfigEditorController(
            object : TrafficConfigEditorProfileStore {
                override fun update(profileId: Int, transform: (TrafficConfigState) -> TrafficConfigState): Boolean {
                    if (profileId != profile.id) return false
                    profile = transform(profile)
                    return true
                }
            },
        )
        val edited = mutableListOf<ShadowrocketRule>()
        val policyRequests = mutableListOf<String>()
        var applyPolicy: ((String) -> Unit)? = null
        val scene = ImageComposeScene(width = width, height = RoutingSceneHeight, density = Density(1f))

        init {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    val state = rememberSkipiRoutingTableState(profile.id)
                    applyPolicy = state::applyPolicy
                    Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background).padding(horizontal = 24.dp)) {
                        SkipiTrafficConfigRoutingTable(
                            profile = this@Harness.profile,
                            controller = controller,
                            state = state,
                            contentPadding = PaddingValues(0.dp),
                            onPickPolicy = { current -> policyRequests += current },
                            onEditRule = { edited += it },
                            onImport = {},
                        )
                    }
                }
            }
            settle()
        }

        val rules get() = profile.rawConfig.analyzeShadowrocketConfig().rules.map { it.raw.trim() }

        fun settle() {
            repeat(4) { scene.render().close() }
        }

        @OptIn(ExperimentalComposeUiApi::class)
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.descendants().toList() }

        private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
            yield(this@descendants)
            children.forEach { yieldAll(it.descendants()) }
        }

        fun findText(text: String): SemanticsNode? = nodes().firstOrNull { node ->
            node.boundsInRoot.width > 0f &&
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.equals(text, ignoreCase = true) }
        }

        fun findTag(tag: String): SemanticsNode? = nodes().firstOrNull { node ->
            node.boundsInRoot.width > 0f && node.config.getOrElse(SemanticsProperties.TestTag) { "" } == tag
        }

        fun textOfTag(tag: String): String? {
            val node = findTag(tag) ?: return null
            return if (node.config.contains(SemanticsProperties.EditableText)) node.config[SemanticsProperties.EditableText].text else null
        }

        fun click(node: SemanticsNode) {
            val at: Offset = node.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Move, at, buttons = PointerButtons())
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = PointerButton.Primary)
            settle()
        }

        fun clickText(text: String) = click(assertNotNull(findText(text), "no text '$text' on screen:\n${dump()}"))

        fun clickTag(tag: String) = click(assertNotNull(findTag(tag), "no tag '$tag' on screen:\n${dump()}"))

        fun type(tag: String, value: String) {
            val field = assertNotNull(findTag(tag), "no field '$tag' on screen:\n${dump()}")
            assertTrue(field.config[SemanticsActions.SetText].action?.invoke(AnnotatedString(value)) == true)
            settle()
        }

        /** Waits for the short pause after which an edit is written, as the real window would. */
        fun waitForSave(until: () -> Boolean) {
            val deadline = System.nanoTime() + 6_000_000_000L
            while (!until() && System.nanoTime() < deadline) {
                Thread.sleep(40)
                settle()
            }
        }

        fun dump(): String = nodes().filter { it.boundsInRoot.width > 0f }.take(60).joinToString("\n") { node ->
            val text = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("|") { it.text }
            "tag=${node.config.getOrElse(SemanticsProperties.TestTag) { "" }} text='${text.take(60)}'"
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
    fun everyRuleIsOneRowWithItsFields() = withHarness {
        listOf("BLOCK", "Hypixel", "private direct", "proxy").forEach { name ->
            assertNotNull(findText(name), "row '$name' is missing:\n${dump()}")
        }
        assertNotNull(findText("25565"), "the port of Hypixel is not shown")
        val values = nodes().flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
        assertTrue(values.any { "hypixel.net" in it && "sessionserver.mojang.com" in it }, "domains are not listed: $values")
        assertTrue(values.any { "geosite:private" in it && "geoip:private" in it })
    }

    private fun rowTextHeight(width: Int): Float {
        val harness = Harness(width = width)
        try {
            return assertNotNull(harness.findText("Hypixel")).boundsInRoot.height
        } finally {
            harness.scene.close()
        }
    }

    @Test
    fun theTableGrowsWithTheWindowSoItFillsABigScreen() {
        val atMinimum = rowTextHeight(900)
        val atHalfAMonitor = rowTextHeight(1270)
        val atTheCap = rowTextHeight(1350)
        val wider = rowTextHeight(2000)

        assertTrue(atHalfAMonitor > atMinimum * 1.3f, "text at 1270 px ($atHalfAMonitor) should be about 1.4 times the size at 900 px ($atMinimum)")
        assertTrue(atTheCap >= atHalfAMonitor)
        assertTrue(wider <= atTheCap * 1.2f, "the growth stops at a cap: $wider vs $atTheCap")
    }

    @Test
    fun theTableIsNeverSmallerThanItsDesignSize() {
        assertEquals(rowTextHeight(900), rowTextHeight(1180) / (1180f / 900f).coerceAtMost(1.5f), 1.5f)
    }

    @Test
    fun aRuleThatDoesNotFitTheTableIsMarked() = withHarness {
        val values = nodes().flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
        assertTrue(values.any { "Complex one" == it })
    }

    @Test
    fun clickingARowOpensItsEditor() = withHarness {
        assertNull(findTag("routing-domains"), "the editor must be closed before a row is picked")

        clickText("Hypixel")

        assertEquals("hypixel.net\nsessionserver.mojang.com", textOfTag("routing-domains"))
        assertEquals("25565", textOfTag("routing-port"))
        assertEquals("Hypixel", textOfTag("routing-name"))
    }

    @Test
    fun theCheckboxSwitchesARuleOffAndOn() = withHarness {
        clickTag("routing-toggle-6")
        assertTrue(rules[1].contains("skipi-disabled"), "rules: $rules")

        clickTag("routing-toggle-6")
        assertTrue(!rules[1].contains("skipi-disabled"), "rules: $rules")
    }

    @Test
    fun typingAPortSavesTheRuleAfterAShortPause() = withHarness {
        clickText("Hypixel")
        type("routing-port", "25565, 8080")

        waitForSave { rules[1].contains("8080") }

        assertEquals(
            "AND,((OR,((DOMAIN-KEYWORD,hypixel.net),(DOMAIN-KEYWORD,sessionserver.mojang.com))),(OR,((DST-PORT,25565),(DST-PORT,8080)))),DIRECT // Hypixel",
            rules[1],
        )
    }

    @Test
    fun addingARuleSavesItOnceItHasAConditionAndKeepsItSelected() = withHarness {
        val before = rules.size
        runBlocking { clickText(getString(Res.string.configs_rules_add)) }

        type("routing-name", "Steam")
        type("routing-domains", "geosite:steam\ncdn.example.com")
        waitForSave { rules.size > before }

        assertEquals(before + 1, rules.size)
        assertEquals("OR,((DOMAIN-SET,geosite:steam),(DOMAIN-KEYWORD,cdn.example.com)),PROXY // Steam", rules[before - 1])
        assertEquals("FINAL,PROXY", rules.last())
        assertEquals("geosite:steam\ncdn.example.com", textOfTag("routing-domains"))
    }

    @Test
    fun aRuleWithoutAnyConditionIsNeverWritten() = withHarness {
        val before = profile.rawConfig
        runBlocking { clickText(getString(Res.string.configs_rules_add)) }
        type("routing-name", "Nothing yet")
        Thread.sleep(700)
        settle()

        assertEquals(before, profile.rawConfig)
    }

    @Test
    fun aRuleThatDoesNotFitOpensTheRuleEditor() = withHarness {
        clickText("Complex one")
        runBlocking { clickText(getString(Res.string.routing_table_complex_open)) }

        assertEquals("Complex one", edited.single().remarks)
    }

    @Test
    fun deletingARuleRemovesItsLine() = withHarness {
        clickText("proxy")
        runBlocking { clickText(getString(Res.string.routing_table_delete)) }

        assertTrue(rules.none { it.contains("steamdb.info") }, "rules: $rules")
        assertNull(findTag("routing-domains"), "the editor should close with the rule")
    }

    @Test
    fun theFinalRowChangesWhereUnmatchedTrafficGoes() = withHarness {
        runBlocking { clickText(getString(Res.string.routing_table_final)) }
        clickTag("routing-route-direct")

        assertEquals("FINAL,DIRECT", rules.last())
    }

    @Test
    fun searchHidesTheRulesThatDoNotMatch() = withHarness {
        type("routing-search", "steamdb")

        assertNotNull(findText("proxy"))
        assertNull(findText("Hypixel"), "Hypixel should be filtered out:\n${dump()}")
    }

    @Test
    fun movingARuleDownSwapsItWithTheNext() = withHarness {
        clickText("BLOCK")
        runBlocking { clickText(getString(Res.string.routing_table_move_down)) }

        assertTrue(rules[0].endsWith("// Hypixel"), "rules: $rules")
        assertTrue(rules[1].endsWith("// BLOCK"), "rules: $rules")
        assertEquals("BLOCK", textOfTag("routing-name"), "the moved rule must stay selected")
    }

    @Test
    fun rendersTheTableWithTheEditorOpenForLooking() = withHarness {
        clickText("Hypixel")
        saveFrame("routing-table-open")
        runBlocking { clickText(getString(Res.string.routing_table_final)) }
        saveFrame("routing-table-final")
        close()
        saveFrame("routing-table-closed")
    }

    @Test
    fun rendersTheTableAtHalfAMonitorForLooking() = withHarness(Harness(width = 1270)) {
        clickText("Hypixel")
        saveFrame("routing-table-1270")
    }

    private fun Harness.close() = scene.render().close()

    private fun Harness.saveFrame(name: String) {
        settle()
        val image = scene.render()
        try {
            val bytes = checkNotNull(image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)).bytes
            val dir = java.io.File(System.getProperty("user.dir"), "build/reports/routing-table").also { it.mkdirs() }
            java.io.File(dir, "$name.png").writeBytes(bytes)
        } finally {
            image.close()
        }
    }

    @Test
    fun pickingAServerHandsTheCurrentPolicyToTheHostAndAppliesTheAnswer() = withHarness {
        clickText("Hypixel")
        runBlocking { clickText(getString(Res.string.routing_table_server_or_group)) }

        assertEquals(listOf("DIRECT"), policyRequests)

        applyPolicy!!("Hong Kong")
        waitForSave { rules[1].contains("Hong Kong") }
        assertTrue(rules[1].contains(",Hong Kong // Hypixel"), "rules: $rules")
    }
}
