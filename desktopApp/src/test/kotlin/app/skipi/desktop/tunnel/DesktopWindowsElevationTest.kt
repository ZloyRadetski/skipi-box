// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowsElevationTest {
    @Test
    fun relaunchStartsTheSameApplicationElevatedAfterTheCurrentOneHadTimeToExit() {
        val command = windowsElevatedRelaunchCommand(
            launcher = "C:\\Program Files\\SKIPI\\SKIPI.exe",
            arguments = emptyList(),
            exitDelaySeconds = 3,
        )

        assertEquals(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command"), command.take(4))
        assertEquals(
            "Start-Sleep -Seconds 3; Start-Process -FilePath 'C:\\Program Files\\SKIPI\\SKIPI.exe' -Verb RunAs",
            command[4],
        )
    }

    @Test
    fun argumentsAreForwardedAndSingleQuotesAreEscapedForPowerShell() {
        val command = windowsElevatedRelaunchCommand(
            launcher = "C:\\Users\\O'Neil\\SKIPI\\SKIPI.exe",
            arguments = listOf("skipi://toggle", "--name=it's"),
            exitDelaySeconds = 0,
        )

        assertEquals(
            "Start-Process -FilePath 'C:\\Users\\O''Neil\\SKIPI\\SKIPI.exe' " +
                "-ArgumentList 'skipi://toggle','--name=it''s' -Verb RunAs",
            command[4],
        )
    }

    @Test
    fun aDeclinedUacPromptIsAFailureTheCallerCanReport() {
        val result = relaunchElevated(
            launcher = "C:\\SKIPI\\SKIPI.exe",
            arguments = emptyList(),
            run = { Result.failure(IllegalStateException("The operation was canceled by the user")) },
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun anAcceptedPromptIsASuccessSoTheCurrentInstanceCanExit() {
        var ran: List<String>? = null

        val result = relaunchElevated(
            launcher = "C:\\SKIPI\\SKIPI.exe",
            arguments = emptyList(),
            run = { command -> ran = command; Result.success(Unit) },
        )

        assertTrue(result.isSuccess)
        assertTrue(ran.orEmpty().last().contains("-Verb RunAs"))
    }

    @Test
    fun theElevatedCopyIsOnlyStartedOnceTheSettingIsOnDisk() = runBlocking<Unit> {
        val events = mutableListOf<String>()

        val result = relaunchElevatedWithSavedSetting(
            save = { events += "saved"; Result.success(Unit) },
            relaunch = { events += "relaunch"; Result.success(Unit) },
            revert = { events += "reverted" },
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("saved", "relaunch"), events)
    }

    @Test
    fun aSaveThatFailedNeverStartsTheElevatedCopy() = runBlocking<Unit> {
        val events = mutableListOf<String>()

        val result = relaunchElevatedWithSavedSetting(
            save = { events += "save"; Result.failure(java.io.IOException("disk full")) },
            relaunch = { events += "relaunch"; Result.success(Unit) },
            revert = { events += "reverted" },
        )

        assertTrue(result.isFailure)
        assertFalse("relaunch" in events)
    }

    @Test
    fun aDeclinedPromptPutsTheSettingBack() = runBlocking<Unit> {
        val events = mutableListOf<String>()

        val result = relaunchElevatedWithSavedSetting(
            save = { events += "saved"; Result.success(Unit) },
            relaunch = { events += "relaunch"; Result.failure(IllegalStateException("declined")) },
            revert = { events += "reverted" },
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("saved", "relaunch", "reverted"), events)
    }
}
