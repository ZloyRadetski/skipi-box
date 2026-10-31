// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowsAutostartTest {
    private val launcher = "C:\\Program Files\\SKIPI\\SKIPI.exe"
    private val runKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    @Test
    fun enablingWritesTheCurrentUsersRunValueThatStartsTheAppHidden() {
        val ran = mutableListOf<List<String>>()
        val autostart = DesktopWindowsAutostart(run = { command -> ran += command; Result.success("") })

        autostart.apply(enabled = true, launcher = launcher).getOrThrow()

        assertEquals(
            listOf(
                listOf(
                    "reg", "add", runKey, "/v", "SKIPI", "/t", "REG_SZ",
                    // Already quoted for reg.exe, which wants the inner quotes escaped; the JDK passes an
                    // argument that starts and ends with a quote through untouched.
                    "/d", "\"\\\"C:\\Program Files\\SKIPI\\SKIPI.exe\\\" --hidden\"", "/f",
                ),
            ),
            ran,
        )
    }

    @Test
    fun disablingDeletesTheValue() {
        val ran = mutableListOf<List<String>>()
        val autostart = DesktopWindowsAutostart(run = { command -> ran += command; Result.success("") })

        autostart.apply(enabled = false, launcher = launcher).getOrThrow()

        assertEquals(listOf(listOf("reg", "delete", runKey, "/v", "SKIPI", "/f")), ran)
    }

    @Test
    fun disablingWhenNothingIsRegisteredIsNotAnError() {
        val autostart = DesktopWindowsAutostart(
            run = { Result.failure(IllegalStateException("ERROR: The system was unable to find the specified registry key or value.")) },
        )

        assertTrue(autostart.apply(enabled = false, launcher = launcher).isSuccess)
    }

    @Test
    fun aFailedWriteIsReportedSoTheSettingIsNotSavedAsOn() {
        val autostart = DesktopWindowsAutostart(run = { Result.failure(IllegalStateException("Access is denied")) })

        val result = autostart.apply(enabled = true, launcher = launcher)

        assertTrue(result.isFailure)
        assertTrue("Access is denied" in result.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun isEnabledFollowsTheRegistryValue() {
        assertTrue(DesktopWindowsAutostart(run = { Result.success("    SKIPI    REG_SZ    \"x\" --hidden") }).isEnabled())
        assertFalse(DesktopWindowsAutostart(run = { Result.failure(IllegalStateException("not found")) }).isEnabled())
    }

    @Test
    fun theLauncherMustNotContainAQuoteThatWouldBreakTheCommandLine() {
        val autostart = DesktopWindowsAutostart(run = { Result.success("") })

        assertTrue(autostart.apply(enabled = true, launcher = "C:\\bad\"name\\SKIPI.exe").isFailure)
    }

    @Test
    fun windowsPicksTheRegistryAndOtherSystemsTheXdgEntry() {
        assertTrue(desktopAutostartFor(windows = true) is DesktopWindowsAutostart)
        assertTrue(desktopAutostartFor(windows = false) is DesktopAutostart)
    }

    @Test
    fun aBareJavaLauncherIsRefusedOnWindowsToo() {
        assertTrue(resolveAutostartLauncher("C:\\Program Files\\Java\\jdk-26\\bin\\java.exe").isFailure)
        assertTrue(resolveAutostartLauncher("C:\\Program Files\\Java\\jdk-26\\bin\\javaw.exe").isFailure)
        assertTrue(resolveAutostartLauncher("C:\\Program Files\\SKIPI\\SKIPI.exe").isSuccess)
    }
}
