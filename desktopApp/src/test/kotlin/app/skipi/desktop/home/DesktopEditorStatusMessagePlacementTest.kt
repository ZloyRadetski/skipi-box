// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.proxy.server.model.StrategyGroup
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The editor's notice (for example "choose servers for the group") must sit above the floating bottom
 * bar the host reserves through the content padding; behind the bar it could go unnoticed.
 */
class DesktopEditorStatusMessagePlacementTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun theNoticeStaysAboveTheReservedBottomBar() {
        val width = 400
        val height = 800
        val barHeightDp = 120
        val message = "Choose servers for the group"
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f))
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null, fontSizeMode = null, fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    DesktopProxyServerEditorHost(
                        contentPadding = PaddingValues(bottom = barHeightDp.dp),
                        editResult = ProxyServerEditResult(serverId = null, server = StrategyGroup(remarks = "Auto"), groupId = null),
                        groupOptions = emptyList(),
                        memberOptions = emptyList(),
                        memberGroups = emptyList(),
                        confirmDeletion = false,
                        exportFullJson = { "" },
                        statusMessage = message,
                        onSave = {},
                        onDismiss = {},
                        onMessage = {},
                    )
                }
            }
            scene.render()
            fun SemanticsNode.all(): Sequence<SemanticsNode> = sequenceOf(this) + children.asSequence().flatMap { it.all() }
            val node = assertNotNull(
                scene.semanticsOwners.asSequence().flatMap { it.unmergedRootSemanticsNode.all() }
                    .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == message } == true },
                "the notice is shown",
            )
            val bottom = node.boundsInRoot.bottom
            assertTrue(bottom <= height - barHeightDp, "the notice ends at $bottom px, under the bar that starts at ${height - barHeightDp} px")
        } finally {
            scene.close()
        }
    }
}
