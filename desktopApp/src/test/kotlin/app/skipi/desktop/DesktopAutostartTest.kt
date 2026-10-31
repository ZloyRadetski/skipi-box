// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAutostartTest {
    private val launcher = "/home/me/apps/SKIPI/bin/SKIPI"

    @Test
    fun theEntryStartsTheApplicationHiddenThroughItsLauncher() {
        val entry = desktopAutostartEntry(launcher)

        assertTrue(entry.startsWith("[Desktop Entry]\n"))
        assertTrue("Type=Application\n" in entry)
        assertTrue("Name=SKIPI\n" in entry)
        assertTrue("Exec=\"/home/me/apps/SKIPI/bin/SKIPI\" --hidden\n" in entry)
        assertTrue("Terminal=false\n" in entry)
    }

    @Test
    fun specialCharactersInThePathAreEscapedAsTheDesktopEntrySpecRequires() {
        val entry = desktopAutostartEntry("/home/me/my apps/\$SKI\"PI`/bin/SKIPI")

        // Quoting adds one backslash and the string-value rule doubles it: two in the file.
        assertTrue("Exec=\"/home/me/my apps/\\\\\$SKI\\\\\"PI\\\\`/bin/SKIPI\" --hidden\n" in entry)
    }

    @Test
    fun onlyTheApplicationLauncherCanBeRegistered() {
        assertTrue(resolveAutostartLauncher("/home/me/apps/SKIPI/bin/SKIPI").isSuccess)
        assertTrue(resolveAutostartLauncher("/usr/lib/jvm/java-26/bin/java").isFailure)
        assertTrue(resolveAutostartLauncher(null).isFailure)
        assertTrue(resolveAutostartLauncher("").isFailure)
    }

    @Test
    fun anAppImageRegistersItselfNotItsTemporaryMount() {
        val appImage = createTempDirectory("skipi-appimage").resolve("SKIPI-x86_64.AppImage")
        Files.writeString(appImage, "x")

        val launcher = resolveAutostartLauncher(
            command = "/tmp/.mount_SKIPIabc/bin/SKIPI",
            appImagePath = appImage.toString(),
        )

        assertEquals(appImage.toString(), launcher.getOrThrow())
    }

    @Test
    fun aMissingAppImageFileFallsBackToTheLauncher() {
        val launcher = resolveAutostartLauncher(
            command = "/opt/skipi/bin/SKIPI",
            appImagePath = "/nonexistent/SKIPI.AppImage",
        )

        assertEquals("/opt/skipi/bin/SKIPI", launcher.getOrThrow())
    }

    @Test
    fun enablingWritesTheEntryAndDisablingRemovesIt() {
        val config = createTempDirectory("skipi-autostart")
        val autostart = DesktopAutostart(configHome = config)

        assertTrue(autostart.apply(enabled = true, launcher = launcher).isSuccess)
        val file = config.resolve("autostart/skipi.desktop")
        assertTrue(Files.isRegularFile(file))
        assertEquals(desktopAutostartEntry(launcher), Files.readString(file))
        assertTrue(autostart.isEnabled())

        assertTrue(autostart.apply(enabled = false, launcher = launcher).isSuccess)
        assertFalse(Files.exists(file))
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun disablingWhenNothingIsRegisteredSucceeds() {
        val autostart = DesktopAutostart(configHome = createTempDirectory("skipi-autostart-empty"))

        assertTrue(autostart.apply(enabled = false, launcher = launcher).isSuccess)
    }

    @Test
    fun rewritingKeepsASingleCurrentEntry() {
        val config = createTempDirectory("skipi-autostart-rewrite")
        val autostart = DesktopAutostart(configHome = config)
        autostart.apply(enabled = true, launcher = "/old/bin/SKIPI")

        autostart.apply(enabled = true, launcher = launcher)

        assertEquals(desktopAutostartEntry(launcher), Files.readString(config.resolve("autostart/skipi.desktop")))
    }
}
