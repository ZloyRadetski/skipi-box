// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.DesktopUpdatePackage
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopUpdateInstallerTest {
    private val file = Path.of("downloads", "update-file")
    private val location = DesktopInstallLocation(
        launcher = Path.of("app", "SKIPI"),
        portableRoot = Path.of("portable", "SKIPI"),
        processId = 4242,
    )

    private fun afterExit(plan: DesktopUpdateInstallPlan?): List<String> =
        (assertNotNull(plan) as DesktopUpdateInstallPlan.AfterExit).command

    private fun script(plan: DesktopUpdateInstallPlan?): String = afterExit(plan).last()

    @Test
    fun theMsiIsInstalledByAnElevatedMsiexecAfterTheAppHasExited() {
        val plan = desktopUpdateInstallPlan(DesktopUpdatePackage.WindowsMsi, file, location)

        val command = afterExit(plan)
        val text = script(plan)
        assertEquals("powershell.exe", command.first())
        assertTrue("Hidden" in command)
        assertTrue(text.startsWith("Wait-Process -Id 4242"), text)
        assertTrue("msiexec.exe" in text && "-Verb RunAs" in text && "/passive" in text, text)
        assertTrue(file.toString() in text, text)
        // The app is started again in every case, after the installer is done with it.
        assertTrue(text.indexOf("msiexec") < text.lastIndexOf("Start-Process -FilePath '${location.launcher}'"), text)
    }

    @Test
    fun aDeclinedPromptStillBringsTheAppBack() {
        val text = script(desktopUpdateInstallPlan(DesktopUpdatePackage.WindowsMsi, file, location))

        assertTrue("catch { }" in text, text)
        assertTrue(text.trimEnd().endsWith("Start-Process -FilePath '${location.launcher}'"), text)
    }

    @Test
    fun anApostropheInAPathCannotBreakOutOfTheScript() {
        val tricky = Path.of("o'brien", "update.msi")

        val text = script(desktopUpdateInstallPlan(DesktopUpdatePackage.WindowsMsi, tricky, location))

        assertTrue("o''brien" in text, text)
        assertFalse("o'brien" in text.replace("o''brien", ""), text)
    }

    @Test
    fun aPortableWindowsBuildIsSwappedInItsFolderAndRestoredOnFailure() {
        val text = script(desktopUpdateInstallPlan(DesktopUpdatePackage.WindowsPortable, file, location))

        assertTrue(text.startsWith("Wait-Process -Id 4242"), text)
        assertTrue("Expand-Archive" in text, text)
        assertTrue("Move-Item -LiteralPath \$root -Destination \$old" in text, text)
        assertTrue("Move-Item -LiteralPath \$old -Destination \$root; throw" in text, text)
        assertTrue(text.trimEnd().endsWith("Start-Process -FilePath '${location.launcher}'"), text)
    }

    @Test
    fun aPortableBuildWithoutAKnownFolderIsNotTouched() {
        val unknown = location.copy(portableRoot = null)

        assertNull(desktopUpdateInstallPlan(DesktopUpdatePackage.WindowsPortable, file, unknown))
        assertNull(desktopUpdateInstallPlan(DesktopUpdatePackage.LinuxPortable, file, unknown))
    }

    @Test
    fun anAppImageIsReplacedInOneRenameAfterTheAppHasExited() {
        val text = script(desktopUpdateInstallPlan(DesktopUpdatePackage.LinuxAppImage, file, location, appImage = "/home/u/SKIPI.AppImage"))

        assertTrue(text.startsWith("while kill -0 4242"), text)
        assertTrue("chmod +x '/home/u/SKIPI.AppImage.new'" in text, text)
        assertTrue("mv -f '/home/u/SKIPI.AppImage.new' '/home/u/SKIPI.AppImage'" in text, text)
        assertTrue(text.trimEnd().endsWith("'/home/u/SKIPI.AppImage' >/dev/null 2>&1 &)"), text)
    }

    @Test
    fun anAppImageWithoutAKnownFileIsNotTouched() {
        assertNull(desktopUpdateInstallPlan(DesktopUpdatePackage.LinuxAppImage, file, location, appImage = null))
    }

    @Test
    fun aQuoteInAShellPathCannotBreakOutEither() {
        val text = script(desktopUpdateInstallPlan(DesktopUpdatePackage.LinuxAppImage, file, location, appImage = "/home/o'brien/SKIPI.AppImage"))

        assertTrue("'/home/o'\\''brien/SKIPI.AppImage'" in text, text)
    }

    @Test
    fun aLinuxPortableBuildIsUnpackedAsideAndSwapped() {
        val text = script(desktopUpdateInstallPlan(DesktopUpdatePackage.LinuxPortable, file, location))

        assertTrue("tar -xzf" in text && "mktemp -d" in text, text)
        assertTrue(".old" in text, text)
    }

    @Test
    fun packagesTheSystemInstallsAreHandedOverAndTheAppKeepsRunning() {
        fun handOver(pkg: DesktopUpdatePackage) =
            (assertNotNull(desktopUpdateInstallPlan(pkg, file, location)) as DesktopUpdateInstallPlan.HandOver).command

        assertEquals(listOf("xdg-open", file.toString()), handOver(DesktopUpdatePackage.LinuxDeb))
        assertEquals(listOf("xdg-open", file.toString()), handOver(DesktopUpdatePackage.LinuxRpm))
        assertEquals(listOf("open", file.toString()), handOver(DesktopUpdatePackage.MacDmg))
    }

    @Test
    fun theAppExitsOnlyWhenAHelperIsWaitingForIt() {
        val started = mutableListOf<List<String>>()
        val installer = DesktopUpdateInstaller { command -> started += command; Result.success(Unit) }

        assertEquals(true, installer.install(DesktopUpdateInstallPlan.AfterExit(listOf("helper"))).getOrThrow())
        assertEquals(false, installer.install(DesktopUpdateInstallPlan.HandOver(listOf("open", "x"))).getOrThrow())
        assertEquals(listOf(listOf("helper"), listOf("open", "x")), started)
    }

    @Test
    fun aHelperThatCannotStartKeepsTheAppRunning() {
        val installer = DesktopUpdateInstaller { Result.failure(java.io.IOException("no powershell")) }

        assertTrue(installer.install(DesktopUpdateInstallPlan.AfterExit(listOf("helper"))).isFailure)
    }
}
