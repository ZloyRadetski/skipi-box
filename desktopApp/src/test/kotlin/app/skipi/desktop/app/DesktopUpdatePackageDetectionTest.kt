// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.DesktopUpdatePackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopUpdatePackageDetectionTest {
    private fun windows(executable: String?) = DesktopRuntimeFacts(
        osName = "Windows 11", osArch = "amd64", executable = executable, appImage = null,
        programFilesDirs = listOf("C:\\Program Files", "C:\\Program Files (x86)"), hasDpkg = false, hasRpm = false,
    )

    private fun linux(executable: String?, appImage: String? = null, dpkg: Boolean = false, rpm: Boolean = false) =
        DesktopRuntimeFacts(
            osName = "Linux", osArch = "amd64", executable = executable, appImage = appImage,
            programFilesDirs = emptyList(), hasDpkg = dpkg, hasRpm = rpm,
        )

    @Test
    fun aWindowsBuildUnderProgramFilesIsTheMsiInstall() {
        assertEquals(
            DesktopUpdatePackage.WindowsMsi,
            detectDesktopUpdatePackage(windows("C:\\Program Files\\SKIPI\\SKIPI.exe")),
        )
        assertEquals(
            DesktopUpdatePackage.WindowsMsi,
            detectDesktopUpdatePackage(windows("c:\\program files\\skipi\\SKIPI.exe")),
        )
    }

    @Test
    fun aWindowsBuildAnywhereElseIsThePortableOne() {
        assertEquals(
            DesktopUpdatePackage.WindowsPortable,
            detectDesktopUpdatePackage(windows("D:\\Tools\\SKIPI\\SKIPI.exe")),
        )
        // A folder that only starts like Program Files is not Program Files.
        assertEquals(
            DesktopUpdatePackage.WindowsPortable,
            detectDesktopUpdatePackage(windows("C:\\Program Files Portable\\SKIPI\\SKIPI.exe")),
        )
    }

    @Test
    fun anUnknownWindowsLauncherUpdatesNothingBlindly() {
        assertNull(detectDesktopUpdatePackage(windows(null)))
    }

    @Test
    fun onlyTheWindowsBuildForX64HasAnInstaller() {
        assertNull(detectDesktopUpdatePackage(windows("C:\\Program Files\\SKIPI\\SKIPI.exe").copy(osArch = "aarch64")))
    }

    @Test
    fun anAppImageUpdatesItself() {
        assertEquals(
            DesktopUpdatePackage.LinuxAppImage,
            detectDesktopUpdatePackage(linux("/tmp/.mount_SKIPI/usr/bin/SKIPI", appImage = "/home/u/SKIPI.AppImage")),
        )
    }

    @Test
    fun aLinuxPackageInstalledUnderOptFollowsItsPackageManager() {
        assertEquals(DesktopUpdatePackage.LinuxDeb, detectDesktopUpdatePackage(linux("/opt/skipi/bin/SKIPI", dpkg = true)))
        assertEquals(DesktopUpdatePackage.LinuxRpm, detectDesktopUpdatePackage(linux("/opt/skipi/bin/SKIPI", rpm = true)))
    }

    @Test
    fun aLinuxFolderOutsideTheSystemIsThePortableArchive() {
        assertEquals(
            DesktopUpdatePackage.LinuxPortable,
            detectDesktopUpdatePackage(linux("/home/u/SKIPI/bin/SKIPI", dpkg = true)),
        )
    }

    @Test
    fun appleSiliconGetsTheDmgAndNothingElseOnAMac() {
        val mac = DesktopRuntimeFacts(
            osName = "Mac OS X", osArch = "aarch64", executable = "/Applications/SKIPI.app/Contents/MacOS/SKIPI",
            appImage = null, programFilesDirs = emptyList(), hasDpkg = false, hasRpm = false,
        )

        assertEquals(DesktopUpdatePackage.MacDmg, detectDesktopUpdatePackage(mac))
        assertNull(detectDesktopUpdatePackage(mac.copy(osArch = "x86_64")))
    }
}
