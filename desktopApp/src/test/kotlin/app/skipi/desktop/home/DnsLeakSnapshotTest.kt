// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import app.skipi.app.tools.DnsLeakTestUiState
import app.skipi.ui.diagnostics.SkipiDnsLeakTestScreen
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.tools.dnsleak.DnsLeakExitInfo
import features.tools.dnsleak.DnsLeakResolver
import features.tools.dnsleak.DnsLeakSilentServer
import features.tools.dnsleak.DnsLeakStage
import features.tools.dnsleak.DnsLeakTestOutcome
import features.tools.dnsleak.DnsLeakVerdict
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the DNS leak screen to PNGs so its look can be checked without a running app. */
class DnsLeakSnapshotTest {
    private fun render(name: String, state: DnsLeakTestUiState, width: Int, height: Int, dark: Boolean = true) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f))
        scene.setContent {
            ProvideSkipiTheme(
                colors = resolveBaseAppearanceColors(isDark = dark),
                fontFamily = null,
                fontSizeMode = null,
                fontWeightMode = null,
                visualProfile = SkipiVisualProfile.Desktop,
            ) {
                Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background)) {
                    SkipiDnsLeakTestScreen(
                        state = state,
                        padding = PaddingValues(),
                        isWideScreen = width > 700,
                        onBack = {},
                        onStart = {},
                        onStop = {},
                    )
                }
            }
        }
        var nanos = 0L
        repeat(30) { nanos += 50_000_000L; scene.render(nanos).close() }
        val image = scene.render(nanos + 1)
        val dir = File(System.getProperty("speedgauge.out") ?: "build/speedgauge").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        image.close()
        scene.close()
    }

    private fun resolver(server: String, system: Boolean, country: String, isp: String, ip: String, asn: Int) = DnsLeakResolver(
        server = server, isSystemServer = system, egressIp = ip, clientSubnetIp = null,
        observedCountryCode = country, observedCountryName = country, isp = isp, asn = asn,
    )

    private val exit = DnsLeakExitInfo("203.0.113.9", "FI", "Finland", "Hetzner Online GmbH", 24940)

    @Test
    fun rendersTheScreen() {
        val leak = DnsLeakTestOutcome(
            resolvers = listOf(
                resolver("192.168.1.1", true, "RU", "Rostelecom", "95.24.1.1", 12389),
                resolver("1.1.1.1", false, "FI", "Hetzner Online GmbH", "203.0.113.9", 24940),
                resolver("8.8.8.8", false, "FI", "Hetzner Online GmbH", "203.0.113.10", 24940),
            ),
            verdict = DnsLeakVerdict.SuspectedLeak,
            exit = exit,
            silentServers = listOf(DnsLeakSilentServer("9.9.9.9", false)),
        )
        val clean = leak.copy(resolvers = leak.resolvers.drop(1), verdict = DnsLeakVerdict.NoLeak, silentServers = emptyList())
        render("dns-phone-idle", DnsLeakTestUiState(), 420, 900)
        render("dns-phone-running", DnsLeakTestUiState(running = true, stage = DnsLeakStage.Probing), 420, 900)
        render("dns-phone-leak", DnsLeakTestUiState(outcome = leak), 420, 1100)
        render("dns-desktop-clean", DnsLeakTestUiState(outcome = clean), 1040, 700)
        render("dns-light-leak", DnsLeakTestUiState(outcome = leak), 420, 1100, dark = false)
        assertTrue(File("build/speedgauge/dns-phone-leak.png").length() > 0)
    }
}
