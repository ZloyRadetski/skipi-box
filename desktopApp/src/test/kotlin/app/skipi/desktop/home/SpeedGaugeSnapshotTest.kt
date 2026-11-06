// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.skipi.ui.diagnostics.SkipiSpeedTestContent
import app.skipi.ui.diagnostics.SpeedTestUiPhase
import app.skipi.ui.diagnostics.SpeedTestUiResult
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the speed test dial to a PNG so its look can be checked without a running app. */
class SpeedGaugeSnapshotTest {
    private fun render(name: String, phase: SpeedTestUiPhase, mbps: Double?, result: SpeedTestUiResult?) {
        val scene = ImageComposeScene(width = 520, height = 900, density = Density(1f))
        scene.setContent {
            ProvideSkipiTheme(
                colors = resolveBaseAppearanceColors(isDark = true),
                fontFamily = null,
                fontSizeMode = null,
                fontWeightMode = null,
                visualProfile = SkipiVisualProfile.Desktop,
            ) {
                Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background).padding(16.dp)) {
                    SkipiSpeedTestContent(
                        phase = phase,
                        progress = 0.5f,
                        currentMbps = mbps,
                        result = result,
                        errorMessage = null,
                        onStart = {},
                        onStop = {},
                    )
                }
            }
        }
        // The needle springs towards its value, so let the animation run.
        var nanos = 0L
        repeat(80) { nanos += 50_000_000L; scene.render(nanos).close() }
        val image = scene.render(nanos + 1)
        val data = image.encodeToData(EncodedImageFormat.PNG)!!
        val dir = File(System.getProperty("speedgauge.out") ?: "build/speedgauge").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(data.bytes)
        image.close()
        scene.close()
    }

    @Test
    fun rendersTheDial() {
        render("download", SpeedTestUiPhase.Download, 89.36, null)
        render("idle", SpeedTestUiPhase.Idle, null, null)
        render("finished", SpeedTestUiPhase.Finished, 612.4, SpeedTestUiResult(23.0, 4.1, 612.4, 88.2))
        assertTrue(File("build/speedgauge/download.png").length() > 0)
    }
}
