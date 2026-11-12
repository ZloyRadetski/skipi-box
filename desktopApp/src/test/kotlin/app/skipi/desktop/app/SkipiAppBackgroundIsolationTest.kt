// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import app.skipi.ui.background.SkipiAppBackground
import app.skipi.ui.settings.AppearanceSettingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An animated page background must not make the whole UI on top of it redraw every frame. */
@OptIn(ExperimentalComposeUiApi::class)
class SkipiAppBackgroundIsolationTest {
    private fun contentDrawsWhileAnimating(style: Int): Int {
        var contentDraws = 0
        val scene = ImageComposeScene(width = 320, height = 240)
        try {
            scene.setContent {
                SkipiAppBackground(
                    style = style,
                    isDark = true,
                    solidColor = Color.Black,
                    isConnected = true,
                    dimPercent = 0,
                    photo = null,
                    reduceMotion = false,
                ) {
                    Box(Modifier.fillMaxSize().drawBehind { contentDraws++ })
                }
            }
            // Real time: the aurora paces itself with a wall-clock delay.
            repeat(75) {
                scene.render(System.nanoTime())
                Thread.sleep(16)
            }
        } finally {
            scene.close()
        }
        return contentDraws
    }

    @Test
    fun connectionPulseDoesNotRedrawContent() {
        val draws = contentDrawsWhileAnimating(AppearanceSettingValues.BackgroundStyleConnection)
        assertTrue(draws <= 5, "content was drawn $draws times in 75 animated frames")
    }

    @Test
    fun auroraDriftDoesNotRedrawContent() {
        val draws = contentDrawsWhileAnimating(AppearanceSettingValues.BackgroundStyleAurora)
        assertTrue(draws <= 5, "content was drawn $draws times in 75 animated frames")
    }

    @Test
    fun changingTheStyleKeepsTheContentAliveSoThePageIsNotRebuilt() {
        var style by mutableStateOf(AppearanceSettingValues.BackgroundStyleClassic)
        var created = 0
        val scene = ImageComposeScene(width = 320, height = 240)
        try {
            scene.setContent {
                SkipiAppBackground(
                    style = style,
                    isDark = true,
                    solidColor = Color.Black,
                    isConnected = false,
                    dimPercent = 0,
                    photo = null,
                    reduceMotion = true,
                ) {
                    remember { created++ }
                    Box(Modifier.fillMaxSize())
                }
            }
            scene.render(System.nanoTime())
            listOf(
                AppearanceSettingValues.BackgroundStylePhoto,
                AppearanceSettingValues.BackgroundStyleConnection,
                AppearanceSettingValues.BackgroundStyleAurora,
                AppearanceSettingValues.BackgroundStyleClassic,
            ).forEach { next ->
                style = next
                scene.render(System.nanoTime())
            }
        } finally {
            scene.close()
        }
        assertEquals(1, created, "the content was rebuilt when the background style changed")
    }
}
