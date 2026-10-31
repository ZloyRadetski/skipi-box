// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests log, save settings and libraries through the same default paths as the app. They must land in
 * the build directory: a test once wrote "HTTP ping timed out ... probe.example" into the user's own
 * ~/SKIPI/logs/skipi.log, which then misled the diagnosis of a real ping problem.
 */
class DesktopTestIsolationTest {
    private val buildDirectory: Path = Path.of(System.getProperty("user.dir")).resolve("build").toAbsolutePath().normalize()

    @Test
    fun theDefaultLogAndDataPathsOfATestRunAreInsideTheBuildDirectory() {
        for (path in listOf(DesktopLogger.logFile(), DesktopSettingsLibraries.defaultPath(), DesktopServerLibraries.defaultPath())) {
            assertTrue(path.toAbsolutePath().normalize().startsWith(buildDirectory), "$path is outside $buildDirectory")
        }
    }
}
