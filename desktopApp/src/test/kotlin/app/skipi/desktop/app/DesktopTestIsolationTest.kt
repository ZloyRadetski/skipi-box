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
    // Gradle points user.home at <build directory>/test-home. The build directory itself may live elsewhere
    // (a user-level init script can move it), so the isolated home is the reference, not "./build".
    private val testHome: Path = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()

    @Test
    fun theDefaultLogAndDataPathsOfATestRunAreInsideTheBuildDirectory() {
        assertTrue(testHome.endsWith("test-home"), "user.home is not the isolated test home: $testHome")
        for (path in listOf(DesktopLogger.logFile(), DesktopSettingsLibraries.defaultPath(), DesktopServerLibraries.defaultPath())) {
            assertTrue(path.toAbsolutePath().normalize().startsWith(testHome), "$path is outside $testHome")
        }
    }
}
