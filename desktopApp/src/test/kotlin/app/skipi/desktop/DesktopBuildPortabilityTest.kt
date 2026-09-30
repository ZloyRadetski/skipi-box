// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopBuildPortabilityTest {

    private val isWindows = System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)
    private val isLinux = System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)

    @Test
    fun hostOperatingSystemRequiresExpectedNativeLibraryName() {
        val directory = Files.createTempDirectory("skipi-portability-")
        try {
            // Write only geo assets
            Files.writeString(directory.resolve("geoip.dat"), "geo-ip-data")
            Files.writeString(directory.resolve("geosite.dat"), "geo-site-data")

            // If only the Windows DLL is present:
            Files.writeString(directory.resolve("skipicore.dll"), "dll-payload")

            if (isLinux) {
                // On Linux, DesktopCoreRuntimes expects libskipicore.so; having only skipicore.dll fails
                val result = DesktopCoreRuntimes.fromDirectory(directory)
                assertTrue(result.isFailure, "Linux runtime must not accept only skipicore.dll without libskipicore.so")
                val message = result.exceptionOrNull()?.message.orEmpty()
                assertTrue(message.contains("libskipicore.so"), "Error must specify missing libskipicore.so: $message")

                // Once libskipicore.so is added, it succeeds
                Files.writeString(directory.resolve("libskipicore.so"), "so-payload")
                val successResult = DesktopCoreRuntimes.fromDirectory(directory)
                assertTrue(successResult.isSuccess, "Linux runtime must succeed when libskipicore.so is present")
                assertEquals("libskipicore.so", successResult.getOrThrow().library.fileName.toString())
            } else if (isWindows) {
                // On Windows, skipicore.dll is sufficient
                val result = DesktopCoreRuntimes.fromDirectory(directory)
                assertTrue(result.isSuccess, "Windows runtime must succeed when skipicore.dll is present")
                assertEquals("skipicore.dll", result.getOrThrow().library.fileName.toString())
            }
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun runtimeFailsWhenGeoAssetsAreMissingRegardlessOfLibrary() {
        val directory = Files.createTempDirectory("skipi-portability-geo-")
        try {
            val libraryName = if (isWindows) "skipicore.dll" else "libskipicore.so"
            Files.writeString(directory.resolve(libraryName), "native-payload")

            val result = DesktopCoreRuntimes.fromDirectory(directory)
            assertTrue(result.isFailure, "Runtime must fail when geo assets are missing")
            val message = result.exceptionOrNull()?.message.orEmpty()
            assertTrue(
                message.contains("geoip.dat") && message.contains("geosite.dat"),
                "Error message must specify missing geo assets: $message",
            )
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
