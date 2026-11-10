// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.DesktopUpdatePackage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/** Runs the real PowerShell helper of a portable update on a throwaway folder: only on Windows. */
class DesktopWindowsPortableUpdateTest {
    private val root: Path = Files.createTempDirectory("skipi portable's test-")
    private val app = root.resolve("SKIPI")
    private val launcher = app.resolve("SKIPI.exe")

    @AfterTest
    fun cleanup() {
        root.toFile().deleteRecursively()
    }

    private fun windowsOnly() = assumeTrue(System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true))

    private fun installedApp() {
        Files.createDirectories(app)
        // Any small executable stands in for the launcher: the helper starts it at the end.
        Files.copy(Path.of(System.getenv("SystemRoot"), "System32", "whoami.exe"), launcher, StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(app.resolve("old.txt"), "old")
    }

    private fun archive(withLauncher: Boolean): Path {
        val zip = root.resolve("update.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            if (withLauncher) {
                out.putNextEntry(ZipEntry("SKIPI/SKIPI.exe"))
                out.write(Files.readAllBytes(Path.of(System.getenv("SystemRoot"), "System32", "whoami.exe")))
                out.closeEntry()
            }
            out.putNextEntry(ZipEntry("SKIPI/new.txt"))
            out.write("new".toByteArray())
            out.closeEntry()
        }
        return zip
    }

    private fun runHelper(zip: Path) {
        val plan = desktopUpdateInstallPlan(
            DesktopUpdatePackage.WindowsPortable,
            zip,
            // A process id that is not running: the helper does not wait.
            DesktopInstallLocation(launcher, portableRoot = app, processId = 2_147_000_000L),
        ) as DesktopUpdateInstallPlan.AfterExit
        val process = ProcessBuilder(plan.command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
    }

    @Test
    fun theNewFilesReplaceTheOldOnesAndNothingStaleIsLeft() {
        windowsOnly()
        installedApp()

        runHelper(archive(withLauncher = true))

        assertEquals("new", Files.readString(app.resolve("new.txt")))
        assertFalse(Files.exists(app.resolve("old.txt")))
        assertFalse(Files.exists(root.resolve("SKIPI.old")))
        assertTrue(Files.exists(launcher))
    }

    @Test
    fun anArchiveWithoutTheLauncherLeavesTheInstalledAppUntouched() {
        windowsOnly()
        installedApp()

        runHelper(archive(withLauncher = false))

        assertEquals("old", Files.readString(app.resolve("old.txt")))
        assertFalse(Files.exists(app.resolve("new.txt")))
        assertTrue(Files.exists(launcher))
    }

    @Test
    fun theMsiHelperIsValidPowerShellEvenForAnAwkwardPath() {
        windowsOnly()
        val awkward = root.resolve("it's a  (x86); `ok`.msi")
        val plan = desktopUpdateInstallPlan(
            DesktopUpdatePackage.WindowsMsi,
            awkward,
            DesktopInstallLocation(Path.of("C:", "Program Files", "SKIPI", "SKIPI.exe"), portableRoot = null, processId = 1),
        ) as DesktopUpdateInstallPlan.AfterExit

        val parse = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "\$errors = \$null; [void][System.Management.Automation.Language.Parser]::ParseInput(\$env:SKIPI_SCRIPT, [ref]\$null, [ref]\$errors); " +
                "if (\$errors.Count -gt 0) { \$errors | ForEach-Object { \$_.Message }; exit 1 }",
        ).apply {
            environment()["SKIPI_SCRIPT"] = plan.command.last()
            redirectErrorStream(true)
        }.start()
        val output = parse.inputStream.bufferedReader().readText()

        assertEquals(0, parse.waitFor(), output)
    }
}
