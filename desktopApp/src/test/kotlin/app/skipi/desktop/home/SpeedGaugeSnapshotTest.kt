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
    private fun render(
        name: String,
        phase: SpeedTestUiPhase,
        mbps: Double?,
        result: SpeedTestUiResult?,
        width: Int = 420,
        height: Int = 1000,
        progress: Float = 0.5f,
        down: List<Float>? = null,
        up: List<Float>? = null,
        dark: Boolean = true,
    ) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f))
        scene.setContent {
            ProvideSkipiTheme(
                colors = resolveBaseAppearanceColors(isDark = dark),
                fontFamily = null,
                fontSizeMode = null,
                fontWeightMode = null,
                visualProfile = SkipiVisualProfile.Desktop,
            ) {
                Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background).padding(16.dp)) {
                    SkipiSpeedTestContent(
                        phase = phase,
                        progress = progress,
                        currentMbps = mbps,
                        result = result,
                        errorMessage = null,
                        onStart = {},
                        onStop = {},
                        downloadSamples = down,
                        uploadSamples = up,
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

    private val curveDown = listOf(5f, 40f, 120f, 260f, 380f, 450f, 520f, 560f, 600f, 590f, 612f, 605f, 618f)
    private val curveUp = listOf(4f, 20f, 48f, 70f, 82f, 90f, 86f, 91f, 88f, 89f)

    @Test
    fun rendersTheSpeedTest() {
        val done = SpeedTestUiResult(23.0, 4.1, 612.4, 88.2)
        render("phone-idle", SpeedTestUiPhase.Idle, null, null)
        render("phone-download", SpeedTestUiPhase.Download, 289.4, null, progress = 0.6f, down = curveDown.take(8))
        render("phone-upload", SpeedTestUiPhase.Upload, 61.2, SpeedTestUiResult(23.0, 4.1, 612.4, 0.0), progress = 0.4f, down = curveDown, up = curveUp.take(6))
        render("phone-finished", SpeedTestUiPhase.Finished, 612.4, done, down = curveDown, up = curveUp)
        render("desktop-finished", SpeedTestUiPhase.Finished, 612.4, done, width = 1040, height = 640, down = curveDown, up = curveUp)
        render("desktop-download", SpeedTestUiPhase.Download, 289.4, null, width = 1040, height = 640, progress = 0.6f, down = curveDown.take(8))
        render("light-phone-finished", SpeedTestUiPhase.Finished, 612.4, done, down = curveDown, up = curveUp, dark = false)
        render("light-desktop-download", SpeedTestUiPhase.Download, 289.4, null, width = 1040, height = 640, progress = 0.6f, down = curveDown.take(8), dark = false)
        assertTrue(File("build/speedgauge/phone-finished.png").length() > 0)
    }
}
