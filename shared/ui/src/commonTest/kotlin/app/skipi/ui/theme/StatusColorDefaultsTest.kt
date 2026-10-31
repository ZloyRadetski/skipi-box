// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusColorDefaultsTest {
    private fun saturation(color: Color): Float {
        val top = max(color.red, max(color.green, color.blue))
        val bottom = min(color.red, min(color.green, color.blue))
        return if (top == 0f) 0f else (top - bottom) / top
    }

    private val themes = listOf(false, true)

    @Test
    fun statusColorsAreVividNotPastel() {
        for (dark in themes) {
            val colors = mapOf(
                "running" to StatusColorDefaults.statusRunning(dark),
                "fast" to StatusColorDefaults.pingFast(dark),
                "medium" to StatusColorDefaults.pingMedium(dark),
                "slow" to StatusColorDefaults.pingSlow(dark),
                "failed" to StatusColorDefaults.statusStopped(dark),
            )
            colors.forEach { (name, color) ->
                assertTrue(saturation(color) >= 0.7f, "$name (dark=$dark) is too dull: saturation ${saturation(color)}")
            }
        }
    }

    @Test
    fun aTimeoutIsPlainRedNotPinkOrBrown() {
        for (dark in themes) {
            val failed = StatusColorDefaults.statusStopped(dark)
            assertTrue(failed.red >= 0.85f, "red channel (dark=$dark) = ${failed.red}")
            assertTrue(failed.green <= 0.30f && failed.blue <= 0.30f, "green/blue (dark=$dark) = ${failed.green}/${failed.blue}")
        }
    }

    @Test
    fun theThemeFallbacksAreTheSameColorsTheStatusesUse() {
        val light = AppColors(
            background = Color.White, onBackground = Color.Black, accent = Color.Blue, onAccent = Color.White,
            surface = Color.White, onSurface = Color.Black, surfaceVariant = Color.LightGray,
            onSurfaceVariant = Color.DarkGray, isDark = false,
        )
        assertEquals(StatusColorDefaults.statusRunning(false), light.success)
        assertEquals(StatusColorDefaults.pingMedium(false), light.warning)
        assertEquals(StatusColorDefaults.statusRunning(true), light.copy(isDark = true).run { StatusColorDefaults.statusRunning(isDark) })
    }
}
