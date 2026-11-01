// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_editor_custom_override_inbound_dns
import app.skipi.ui.resources.proxy_editor_remarks
import app.skipi.ui.server.editor.SkipiCustomProxyServerEditorPresentation
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The custom JSON editor must leave a phone screen to the JSON, not to the form above it. */
class DesktopCustomEditorPhoneLayoutTest {
    private val width = 400
    private val height = 640

    @Test
    fun theJsonEditorTakesMostOfAPhoneScreenWithTheKeyboardClosed() = runBlocking {
        val layout = render(imeVisible = false)
        assertTrue(layout.remarksShown, "the name field is shown without a keyboard")
        assertTrue(layout.overrideShown, "the override switch is shown without a keyboard")
        assertTrue(
            layout.editor.height >= height * 0.65f,
            "the editor is ${layout.editor.height} px of $height px; the form above it takes too much",
        )
    }

    @Test
    fun theJsonEditorTakesTheWholeScreenWhileTheKeyboardServesIt() = runBlocking {
        val layout = render(imeVisible = true)
        assertTrue(!layout.remarksShown, "the name field steps aside while the JSON is typed")
        assertTrue(!layout.overrideShown, "the override switch steps aside while the JSON is typed")
        assertTrue(
            layout.editor.height >= height * 0.9f,
            "the editor is ${layout.editor.height} px of $height px",
        )
    }

    private class Layout(val editor: androidx.compose.ui.geometry.Rect, val remarksShown: Boolean, val overrideShown: Boolean)

    @OptIn(ExperimentalComposeUiApi::class)
    private suspend fun render(imeVisible: Boolean): Layout {
        val remarksLabel = getString(Res.string.proxy_editor_remarks)
        val overrideTitle = getString(Res.string.proxy_editor_custom_override_inbound_dns)
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f))
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null, fontSizeMode = null, fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    SkipiCustomProxyServerEditorPresentation(
                        draftIdentity = Unit,
                        remarks = "NL Amsterdam 1",
                        onRemarksChange = {},
                        overrideInboundAndDns = true,
                        onOverrideChange = {},
                        contentPadding = PaddingValues(),
                        formatJsonContentDescription = "Format JSON",
                        formatButtonBackground = Color.Gray,
                        formatButtonTint = Color.White,
                        onFormatJson = {},
                        imeVisible = imeVisible,
                        jsonEditor = { editorModifier -> Box(editorModifier.testTag("json-editor")) },
                    )
                }
            }
            // Let the collapse animation settle.
            var frame = 1_000_000_000L
            repeat(30) { scene.render(frame); frame += 50_000_000L }
            fun SemanticsNode.all(): Sequence<SemanticsNode> = sequenceOf(this) + children.asSequence().flatMap { it.all() }
            val nodes = scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.all().toList() }
            fun shows(text: String) = nodes.any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true ||
                    node.config.getOrNull(SemanticsProperties.EditableText)?.text == text
            }
            val editor = assertNotNull(
                nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == "json-editor" },
                "the JSON editor is composed",
            )
            return Layout(editor.boundsInRoot, remarksShown = shows(remarksLabel) || shows("NL Amsterdam 1"), overrideShown = shows(overrideTitle))
        } finally {
            scene.close()
        }
    }
}
