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

    @Test
    fun theCollapsedSidebarAndItsStyleSurviveARestart() {
        val dir = Files.createTempDirectory("skipi-sidebar")
        val path = dir.resolve("settings.json")
        try {
            DesktopSettingsLibraries.save(
                path,
                DesktopAppSettings(navigationStyle = 1, sidebarCollapsed = true),
            ).getOrThrow()

            val loaded = DesktopSettingsLibraries.load(path).getOrThrow()
            assertEquals(1, loaded.navigationStyle)
            assertEquals(true, loaded.sidebarCollapsed)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun theSidebarIsTheDefaultAndAChosenBottomBarIsKept() {
        assertEquals(1, DesktopAppSettings().navigationStyle)

        val dir = Files.createTempDirectory("skipi-nav-default")
        val path = dir.resolve("settings.json")
        try {
            DesktopSettingsLibraries.save(path, DesktopAppSettings(navigationStyle = 0)).getOrThrow()

            assertEquals(0, DesktopSettingsLibraries.load(path).getOrThrow().navigationStyle)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
