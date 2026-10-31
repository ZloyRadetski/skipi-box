// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.test.Test
import kotlin.test.assertTrue

/** Flags for the codes users meet most are bundled as pictures and decodable by Compose Resources. */
class CountryFlagResourcesTest {
    @Test
    fun commonCountryFlagsDecode() {
        for (code in listOf("nl", "de", "us", "ru", "fi", "gb", "fr", "jp", "tr", "eu")) {
            val bitmap = runBlocking { Res.readBytes("files/flags/$code.png").decodeToImageBitmap() }
            assertTrue(bitmap.width > 0 && bitmap.height > 0, "flag $code is empty")
        }
    }
}
