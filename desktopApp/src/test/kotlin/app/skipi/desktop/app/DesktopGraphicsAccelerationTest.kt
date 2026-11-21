// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The renderer is chosen when the JVM starts, so the choice is a small file the app reads before anything
// draws. It belongs to this machine (a weak laptop), not to the settings a backup carries to another one.
class DesktopGraphicsAccelerationTest {
    private val env = { values: Map<String, String> -> { key: String -> values[key] } }

    @Test
    fun theSystemIsToldApartByItsName() {
        assertEquals(DesktopOs.Windows, desktopOsOf("Windows 11"))
        assertEquals(DesktopOs.Mac, desktopOsOf("Mac OS X"))
        assertEquals(DesktopOs.Linux, desktopOsOf("Linux"))
        assertEquals(DesktopOs.Linux, desktopOsOf("FreeBSD"))
    }

    @Test
    fun withoutAChoiceEverySystemUsesTheGraphicsCard() {
        for (os in DesktopOs.values()) assertTrue(graphicsAccelerationDefault(os), "$os")
    }

    @Test
    fun acceleratedLeavesTheRendererToSkikoAndJavaOnEverySystem() {
        for (os in DesktopOs.values()) assertEquals(emptyMap(), graphicsRenderProperties(os, accelerated = true))
    }

    @Test
    fun softwareUsesSkikosDirectSoftwareRendererOnEverySystem() {
        for (os in DesktopOs.values()) {
            assertEquals("SOFTWARE_FAST", graphicsRenderProperties(os, accelerated = false)["skiko.renderApi"])
        }
    }

    @Test
    fun softwareOnWindowsAlsoTurnsOffJava2DsDirect3DAndKeepsTheBackgroundErase() {
        assertEquals(
            mapOf(
                "skiko.renderApi" to "SOFTWARE_FAST",
                "skiko.rendering.noerasebackground" to "false",
                "sun.java2d.d3d" to "false",
            ),
            graphicsRenderProperties(DesktopOs.Windows, accelerated = false),
        )
        assertEquals(mapOf("skiko.renderApi" to "SOFTWARE_FAST"), graphicsRenderProperties(DesktopOs.Linux, accelerated = false))
    }

    @Test
    fun theChoiceFileLivesWithTheOtherLocalDataOfEachSystem() {
        assertEquals(
            Path.of("C:\\Users\\a\\AppData\\Local", "SKIPI"),
            graphicsPreferenceDirectory(DesktopOs.Windows, env(mapOf("LOCALAPPDATA" to "C:\\Users\\a\\AppData\\Local")), "C:\\Users\\a"),
        )
        assertEquals(
            Path.of("/Users/a", "Library", "Application Support", "SKIPI"),
            graphicsPreferenceDirectory(DesktopOs.Mac, env(emptyMap()), "/Users/a"),
        )
        assertEquals(
            Path.of("/home/a", ".config", "skipi"),
            graphicsPreferenceDirectory(DesktopOs.Linux, env(emptyMap()), "/home/a"),
        )
        assertEquals(
            Path.of("/cfg", "skipi"),
            graphicsPreferenceDirectory(DesktopOs.Linux, env(mapOf("XDG_CONFIG_HOME" to "/cfg")), "/home/a"),
        )
    }

    @Test
    fun withoutAUsableFolderThereIsNoDirectoryAndTheDefaultApplies() {
        assertNull(graphicsPreferenceDirectory(DesktopOs.Windows, env(emptyMap()), null))
        assertNull(graphicsPreferenceDirectory(DesktopOs.Windows, env(mapOf("LOCALAPPDATA" to " ")), ""))
        assertTrue(readGraphicsAcceleration(null, DesktopOs.Linux))
        assertTrue(readGraphicsAcceleration(null, DesktopOs.Windows))
    }

    @Test
    fun theChoiceSurvivesAWriteAndARead() {
        val directory = Files.createTempDirectory("graphics-choice")
        try {
            val folder = directory.resolve("SKIPI") // not there yet: writing creates it
            assertTrue(writeGraphicsAcceleration(folder, true).isSuccess)
            assertTrue(readGraphicsAcceleration(folder, DesktopOs.Windows))
            assertTrue(writeGraphicsAcceleration(folder, false).isSuccess)
            assertFalse(readGraphicsAcceleration(folder, DesktopOs.Linux))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun aMissingEmptyOrGarbledFileMeansTheSystemsDefault() {
        val directory = Files.createTempDirectory("graphics-choice")
        try {
            assertTrue(readGraphicsAcceleration(directory, DesktopOs.Linux))
            val file = directory.resolve(GRAPHICS_ACCELERATION_FILE)
            for (text in listOf("", "\u0000\u0000", "maybe", "ON ?")) {
                Files.writeString(file, text)
                assertTrue(readGraphicsAcceleration(directory, DesktopOs.Linux), "'$text' on Linux")
                assertTrue(readGraphicsAcceleration(directory, DesktopOs.Windows), "'$text' on Windows")
            }
            Files.writeString(file, " off\r\n")
            assertFalse(readGraphicsAcceleration(directory, DesktopOs.Windows))
            Files.writeString(file, " ON\r\n")
            assertTrue(readGraphicsAcceleration(directory, DesktopOs.Windows))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFolderThatCannotBeWrittenIsAFailureTheStartIsNotBlockedBy() {
        val directory = Files.createTempDirectory("graphics-choice")
        try {
            val blocker = Files.createFile(directory.resolve("blocked"))
            assertTrue(writeGraphicsAcceleration(blocker.resolve("SKIPI"), true).isFailure)
            assertTrue(readGraphicsAcceleration(blocker.resolve("SKIPI"), DesktopOs.Linux))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun appliedPropertiesNeverReplaceOnesTheLauncherOrTheUserAlreadySet() {
        val properties = Properties().apply { setProperty("skiko.renderApi", "OPENGL") }
        applyGraphicsRenderProperties(DesktopOs.Windows, accelerated = false, properties)
        assertEquals("OPENGL", properties.getProperty("skiko.renderApi"))
        assertEquals("false", properties.getProperty("sun.java2d.d3d"))
    }

    @Test
    fun startingReadsTheChoiceAndSetsTheRendererBeforeAnythingDraws() {
        val directory = Files.createTempDirectory("graphics-choice")
        try {
            writeGraphicsAcceleration(directory, false).getOrThrow()
            val properties = Properties()
            applyStoredGraphicsAcceleration(DesktopOs.Linux, directory, properties)
            assertEquals("SOFTWARE_FAST", properties.getProperty("skiko.renderApi"))

            writeGraphicsAcceleration(directory, true).getOrThrow()
            val accelerated = Properties()
            applyStoredGraphicsAcceleration(DesktopOs.Linux, directory, accelerated)
            assertNull(accelerated.getProperty("skiko.renderApi"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
