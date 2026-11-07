// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopHomeLeftColumnSettingTest {
    @Test
    fun theDraggedWidthOfTheHomeLeftColumnSurvivesARestart() {
        val dir = Files.createTempDirectory("skipi-left-column")
        val path = dir.resolve("settings.json")
        try {
            DesktopSettingsLibraries.save(path, DesktopAppSettings(homeLeftColumnExtraDp = 137.5f)).getOrThrow()

            assertEquals(137.5f, DesktopSettingsLibraries.load(path).getOrThrow().homeLeftColumnExtraDp)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
