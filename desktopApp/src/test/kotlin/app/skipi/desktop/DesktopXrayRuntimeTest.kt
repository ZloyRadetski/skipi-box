// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.util.Comparator

class DesktopXrayRuntimeTest {
    @Test
    fun runtimeRequiresExecutableAndGeoAssets() {
        val directory = Files.createTempDirectory("skipi-xray-runtime-")
        try {
            assertTrue(DesktopXrayRuntimes.fromDirectory(directory).isFailure)

            listOf("xray.exe", "geoip.dat", "geosite.dat").forEach { name ->
                Files.writeString(directory.resolve(name), name)
            }
            val runtime = DesktopXrayRuntimes.fromDirectory(directory).getOrThrow()

            assertEquals(directory.toAbsolutePath().normalize(), runtime.directory)
            assertEquals("xray.exe", runtime.executable.fileName.toString())
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun stoppedControllerHasNoRunningProcess() {
        val controller = DesktopXrayProcessController()
        assertFalse(controller.state().isRunning)
    }

    @Test
    fun validationCommandUsesXrayRunTestAndExplicitConfigPath() {
        val runtime = DesktopXrayRuntime(
            directory = java.nio.file.Path.of("C:/SKIPI/resources"),
            executable = java.nio.file.Path.of("C:/SKIPI/resources/xray.exe"),
            geoIp = java.nio.file.Path.of("C:/SKIPI/resources/geoip.dat"),
            geoSite = java.nio.file.Path.of("C:/SKIPI/resources/geosite.dat"),
        )

        val configPath = java.nio.file.Path.of("C:/SKIPI/runtime/active-config.json")
        assertEquals(
            listOf(runtime.executable.toString(), "run", "-test", "-c", configPath.toString()),
            xrayCommand(runtime, configPath, testOnly = true),
        )
    }
}
