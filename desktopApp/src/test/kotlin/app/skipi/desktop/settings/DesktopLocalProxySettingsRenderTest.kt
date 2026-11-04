// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.dp
import app.skipi.ui.settings.LocalProxySettingsState
import app.skipi.ui.settings.SkipiLocalProxySettingsScreen
import app.skipi.ui.settings.rememberLocalProxySettingsLabels
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import org.jetbrains.skia.EncodedImageFormat
import platform.LocalProxyAuthMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the local proxy settings at phone and desktop widths so the layout can be looked at, not guessed. */
class DesktopLocalProxySettingsRenderTest {
    private var frameTimeNanos = 1_000_000_000L

    private val everythingOn = LocalProxySettingsState(
        dynamicPort = false,
        port = "10808",
        listenAllInterfaces = true,
        enableHttpEndpoint = true,
        httpPort = "10809",
        socksAuthMode = LocalProxyAuthMode.Auto,
        httpAuthMode = LocalProxyAuthMode.Manual,
        username = "skipi_wynzPxFGDBW92TMg",
        password = "skipi_4S7dullokJVAxddx",
        httpUsername = "skipi_yX39SaYrflUGEkVV",
        httpPassword = "skipi_LP3L8IAMwQxD2J60",
        displayIp = "192.168.31.105",
        socksEndpoint = "192.168.31.105:10808",
        httpEndpoint = "192.168.31.105:10809",
        portError = null,
    )

    @Test
    fun rendersTheLocalProxySettingsAtPhoneAndDesktopWidths() {
        render("all-on", everythingOn, width = 390, height = 2400)
        render("all-on", everythingOn, width = 1100, height = 1700)
        render(
            "minimal",
            everythingOn.copy(
                dynamicPort = true,
                listenAllInterfaces = false,
                enableHttpEndpoint = false,
                socksAuthMode = LocalProxyAuthMode.Off,
            ),
            width = 390,
            height = 1100,
        )
    }

    private fun render(name: String, state: LocalProxySettingsState, width: Int, height: Int) {
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
                    SkipiLocalProxySettingsScreen(
                        state = state,
                        labels = rememberLocalProxySettingsLabels(),
                        padding = PaddingValues(0.dp),
                        isWideScreen = width >= 700,
                        onBack = {},
                        onDynamicPortChange = {},
                        onPortChange = {},
                        onHttpEnabledChange = {},
                        onHttpPortChange = {},
                        onListenAllInterfacesChange = {},
                        onSocksAuthModeChange = {},
                        onHttpAuthModeChange = {},
                        onSocksCredentialsChange = { _, _ -> },
                        onHttpCredentialsChange = { _, _ -> },
                        onGenerateSocksCredentials = {},
                        onGenerateHttpCredentials = {},
                        onCopy = { _, _ -> },
                    )
                }
            }
            scene.render(nextFrame()).close()
            val image = scene.render(nextFrame())
            try {
                val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try {
                    val dir = File(System.getProperty("user.dir"), "build/reports/local-proxy-settings")
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
