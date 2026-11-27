// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.dp
import app.skipi.ui.settings.MultilinkSettingsState
import app.skipi.ui.settings.SkipiMultilinkSettingsScreen
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.multilink.MultilinkLink
import features.multilink.MultilinkLinkKind
import features.multilink.MultilinkSettings
import features.multilink.MultilinkSkipped
import features.multilink.MultilinkStatus
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the link merging page in its states so the layout can be looked at, not guessed. */
class DesktopMultilinkSettingsRenderTest {
    private var frameTimeNanos = 1_000_000_000L

    private val links = listOf(
        MultilinkLink(id = "{eth}", name = "Ethernet 2", kind = MultilinkLinkKind.Ethernet, ipv4 = "192.168.1.200",
            state = "up", flows = 8, linkIn = 4_200_000.0, linkOut = 310_000.0),
        MultilinkLink(id = "{wifi}", name = "Беспроводная сеть", kind = MultilinkLinkKind.WiFi, ipv4 = "192.168.168.111",
            state = "up", flows = 4, linkIn = 1_900_000.0, linkOut = 120_000.0),
        MultilinkLink(id = "{usb}", name = "iPhone USB", kind = MultilinkLinkKind.Phone, ipv4 = "172.20.10.2",
            state = "checking", phone = true),
    )
    private val skipped = listOf(
        MultilinkSkipped("Radmin VPN", "virtual/VPN adapter (Famatech Radmin VPN Ethernet Adapter)"),
        MultilinkSkipped("Teredo Tunneling Pseudo-Interface", "tunnel/virtual interface type 131"),
    )
    private val merging = MultilinkSettingsState(
        settings = MultilinkSettings(enabled = true, limits = mapOf("{wifi}" to 50.0), excluded = listOf("{usb}"), phoneCooling = true),
        status = MultilinkStatus(enabled = true, merging = true, links = links, skipped = skipped),
        tunnelRunning = true,
    )

    @Test
    fun rendersTheLinkMergingPage() {
        render("merging", merging, width = 1100, height = 1100)
        render("merging-narrow", merging, width = 420, height = 1500)
        render("off", merging.copy(settings = MultilinkSettings(), status = MultilinkStatus(), tunnelRunning = false), width = 1100, height = 800)
        render("waiting", merging.copy(status = MultilinkStatus(), tunnelRunning = false), width = 1100, height = 800)
    }

    private fun render(name: String, state: MultilinkSettingsState, width: Int, height: Int) {
        val scene = ImageComposeScene(width = width, height = height)
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    SkipiMultilinkSettingsScreen(
                        state = state,
                        padding = PaddingValues(0.dp),
                        isWideScreen = width >= 700,
                        onBack = {},
                        onSettingsChange = {},
                    )
                }
            }
            repeat(30) { scene.render(nextFrame()).close() }
            val image = scene.render(nextFrame())
            try {
                val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try {
                    val dir = File(System.getProperty("user.dir"), "build/reports/multilink-settings")
                    assertTrue(dir.mkdirs() || dir.isDirectory)
                    val png = File(dir, "$name-${width}x$height.png")
                    png.writeBytes(encoded.bytes)
                    assertTrue(png.length() > 1_024, "$name at ${width}x$height should render the screen")
                } finally {
                    encoded.close()
                }
            } finally {
                image.close()
            }
        } finally {
            scene.close()
        }
    }

    private fun nextFrame(): Long {
        frameTimeNanos += 16_666_667L
        return frameTimeNanos
    }
}
