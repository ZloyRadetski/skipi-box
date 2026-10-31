// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `ProcessBuilder` on Windows does not escape embedded double quotes, so PowerShell strips them from a
 * `-Command` argument and `DllImport("wininet.dll")` reaches the compiler as `DllImport(wininet.dll)`.
 */
class PowerShellWinInetSettingsRefresherTest {
    private class RecordingRunner : DesktopWindowsCommandRunner {
        var executable: String? = null
        var arguments: List<String> = emptyList()

        override fun run(executable: String, arguments: List<String>): Result<DesktopWindowsCommandResult> {
            this.executable = executable
            this.arguments = arguments
            return Result.success(DesktopWindowsCommandResult(exitCode = 0))
        }
    }

    @Test
    fun noArgumentCarriesADoubleQuoteThatWindowsWouldStrip() {
        val runner = RecordingRunner()

        PowerShellWinInetSettingsRefresher(runner).refresh().getOrThrow()

        assertEquals("powershell.exe", runner.executable)
        assertTrue(runner.arguments.none { '"' in it }, "arguments: ${runner.arguments}")
    }

    @Test
    fun scriptIsPassedEncodedAndKeepsTheQuotedLibraryName() {
        val runner = RecordingRunner()

        PowerShellWinInetSettingsRefresher(runner).refresh().getOrThrow()

        val flagIndex = runner.arguments.indexOf("-EncodedCommand")
        assertTrue(flagIndex >= 0 && flagIndex + 1 < runner.arguments.size, "arguments: ${runner.arguments}")
        val script = String(Base64.getDecoder().decode(runner.arguments[flagIndex + 1]), Charsets.UTF_16LE)
        assertTrue("[DllImport(\"wininet.dll\"" in script, script)
        assertFalse("-Command" in runner.arguments)
    }
}
