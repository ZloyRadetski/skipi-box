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
        // No logon task exists, so the Run value is the autostart.
        val autostart = DesktopWindowsAutostart(elevated = { false }, run = { command ->
            ran += command
            if (command.first() == "schtasks") Result.failure(IllegalStateException("not found")) else Result.success("")
        })

        autostart.apply(enabled = true, launcher = launcher).getOrThrow()

        assertEquals(
            listOf(
                listOf("schtasks", "/query", "/tn", "SKIPI"),
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
        // Deleting works, and afterwards neither the value nor the task can be queried.
        val autostart = DesktopWindowsAutostart(elevated = { false }, run = { command ->
            ran += command
            if (command.any { it.endsWith("query") }) Result.failure(IllegalStateException("not found")) else Result.success("")
        })

        autostart.apply(enabled = false, launcher = launcher).getOrThrow()

        ran.removeAll { command -> command.any { it.endsWith("query") } }
        // The scheduled task of an elevated start is removed with it.
        assertEquals(
            listOf(
                listOf("reg", "delete", runKey, "/v", "SKIPI", "/f"),
                listOf("schtasks", "/delete", "/tn", "SKIPI", "/f"),
            ),
            ran,
        )
    }

    @Test
    fun disablingWhenNothingIsRegisteredIsNotAnError() {
        val autostart = DesktopWindowsAutostart(
            elevated = { false },
            run = { Result.failure(IllegalStateException("ERROR: The system was unable to find the specified registry key or value.")) },
        )

        assertTrue(autostart.apply(enabled = false, launcher = launcher).isSuccess)
    }

    @Test
    fun aFailedWriteIsReportedSoTheSettingIsNotSavedAsOn() {
        val autostart = DesktopWindowsAutostart(elevated = { false }, run = { Result.failure(IllegalStateException("Access is denied")) })

        val result = autostart.apply(enabled = true, launcher = launcher)

        assertTrue(result.isFailure)
        assertTrue("Access is denied" in result.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun isEnabledFollowsTheRegistryValue() {
        assertTrue(DesktopWindowsAutostart(elevated = { false }, run = { Result.success("    SKIPI    REG_SZ    \"x\" --hidden") }).isEnabled())
        assertFalse(DesktopWindowsAutostart(elevated = { false }, run = { Result.failure(IllegalStateException("not found")) }).isEnabled())
    }

    @Test
    fun theLauncherMustNotContainAQuoteThatWouldBreakTheCommandLine() {
        val autostart = DesktopWindowsAutostart(elevated = { false }, run = { Result.success("") })

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

    @Test
    fun anElevatedAppRegistersALogonTaskWithHighestRightsSoTheLoginShowsNoUacPrompt() {
        val ran = mutableListOf<List<String>>()
        val autostart = DesktopWindowsAutostart(elevated = { true }, run = { command -> ran += command; Result.success("") })

        autostart.apply(enabled = true, launcher = launcher).getOrThrow()

        val script = ran.first().last()
        assertEquals(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command"), ran.first().take(4))
        assertTrue("Register-ScheduledTask" in script && "-TaskName 'SKIPI'" in script)
        assertTrue("-AtLogOn" in script && "-RunLevel Highest" in script)
        assertTrue("-Execute 'C:\\Program Files\\SKIPI\\SKIPI.exe'" in script && "-Argument '--hidden'" in script)
        // A laptop on battery still starts it, and the default 72 hour limit would kill the app.
        assertTrue("-AllowStartIfOnBatteries" in script && "-DontStopIfGoingOnBatteries" in script)
        assertTrue("-ExecutionTimeLimit ([TimeSpan]::Zero)" in script)
        // The Run value would start a second, unelevated copy.
        assertEquals(listOf("reg", "delete", runKey, "/v", "SKIPI", "/f"), ran.last())
    }

    @Test
    fun anElevatedAppThatCannotRegisterTheTaskFallsBackToTheRunValue() {
        val ran = mutableListOf<List<String>>()
        val autostart = DesktopWindowsAutostart(elevated = { true }, run = { command ->
            ran += command
            if (command.first() == "powershell.exe") Result.failure(IllegalStateException("denied")) else Result.success("")
        })

        autostart.apply(enabled = true, launcher = launcher).getOrThrow()

        assertEquals("reg", ran.last().first())
        assertTrue("add" in ran.last())
    }

    @Test
    fun withoutAdministratorRightsAnExistingTaskStaysTheOnlyAutostart() {
        val ran = mutableListOf<List<String>>()
        val autostart = DesktopWindowsAutostart(elevated = { false }, run = { command -> ran += command; Result.success("") })

        autostart.apply(enabled = true, launcher = launcher).getOrThrow()

        assertEquals(listOf(listOf("schtasks", "/query", "/tn", "SKIPI")), ran)
    }

    @Test
    fun isEnabledAlsoCountsTheTask() {
        val onlyTheTask = DesktopWindowsAutostart(elevated = { false }, run = { command ->
            if (command.first() == "schtasks") Result.success("") else Result.failure(IllegalStateException("not found"))
        })
        assertTrue(onlyTheTask.isEnabled())
    }

    @Test
    fun aTaskThatCannotBeDeletedIsReportedSoTheSettingIsNotSavedAsOff() {
        val autostart = DesktopWindowsAutostart(elevated = { false }, run = { command ->
            if (command.first() == "reg") Result.failure(IllegalStateException("not found")) else Result.failure(IllegalStateException("Access is denied"))
        })

        // The query still finds the task only when run succeeds, so model "exists" as query success.
        val stuck = DesktopWindowsAutostart(elevated = { false }, run = { command ->
            when {
                command.take(2) == listOf("schtasks", "/query") -> Result.success("")
                command.first() == "schtasks" -> Result.failure(IllegalStateException("Access is denied"))
                else -> Result.failure(IllegalStateException("not found"))
            }
        })
        assertTrue(stuck.apply(enabled = false, launcher = launcher).isFailure)
        assertTrue(autostart.apply(enabled = false, launcher = launcher).isSuccess)
    }

    @Test
    fun theAutostartMovesToTheTaskOnlyWhenTunIsOnTheAppIsElevatedAndStartOnLoginIsWanted() {
        assertTrue(shouldRegisterElevatedAutostart(launchOnLogin = true, tunModeEnabled = true, elevated = true))
        assertFalse(shouldRegisterElevatedAutostart(launchOnLogin = false, tunModeEnabled = true, elevated = true))
        assertFalse(shouldRegisterElevatedAutostart(launchOnLogin = true, tunModeEnabled = false, elevated = true))
        assertFalse(shouldRegisterElevatedAutostart(launchOnLogin = true, tunModeEnabled = true, elevated = false))
    }
}
