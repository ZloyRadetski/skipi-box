// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.unit.Density
import app.skipi.ui.resources.Res
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.decodeToImageVector
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared logo vectors must be decodable by Compose Resources, not only by Android's aapt. */
class DesktopLogoResourcesTest {
    private fun decode(path: String) = runBlocking { Res.readBytes(path).decodeToImageVector(Density(1f)) }

    @Test
    fun wideAboutLogoDecodesWithItsTwoToOneViewport() {
        val logo = decode("drawable/ic_about_logo.xml")
        assertEquals(1024f, logo.viewportWidth)
        assertEquals(512f, logo.viewportHeight)
    }

    @Test
    fun squareWindowLogoDecodes() {
        val logo = decode("drawable/ic_skipi_logo.xml")
        assertEquals(512f, logo.viewportWidth)
        assertEquals(512f, logo.viewportHeight)
    }
}
