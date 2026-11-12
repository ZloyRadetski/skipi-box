// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_autostart_remove_failed
import utils.powerShellLiteral
import java.util.concurrent.TimeUnit

private const val RunKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
private const val RunValueName = "SKIPI"

private const val TaskName = "SKIPI"

/**
 * Start at login on Windows. Without administrator rights it is a value under the current user's
 * `Run` key, which starts the app hidden but never elevated, so TUN mode makes the start ask UAC
 * for rights. An elevated app registers a logon task that runs with the highest rights instead, so
 * the login shows no prompt; the `Run` value is dropped then, or it would start a second copy.
 * Only an elevated process can create such a task, which is why the move happens when the app
 * runs elevated with TUN mode on ([shouldRegisterElevatedAutostart]).
 */
internal class DesktopWindowsAutostart(
    private val elevated: () -> Boolean = ::isWindowsElevated,
    private val run: (List<String>) -> Result<String> = ::runRegistryCommand,
) : DesktopAutostartControl {
    private fun runValueExists(): Boolean = run(listOf("reg", "query", RunKey, "/v", RunValueName)).isSuccess

    private fun taskExists(): Boolean = run(listOf("schtasks", "/query", "/tn", TaskName)).isSuccess

    override fun isEnabled(): Boolean = runValueExists() || taskExists()

    override fun apply(enabled: Boolean, launcher: String): Result<Unit> {
        if (!enabled) {
            // A value or task that is already gone is the state the caller asked for.
            run(listOf("reg", "delete", RunKey, "/v", RunValueName, "/f"))
            run(listOf("schtasks", "/delete", "/tn", TaskName, "/f"))
            return if (isEnabled()) {
                Result.failure(IllegalStateException(desktopText(Res.string.desktop_autostart_remove_failed)))
            } else {
                Result.success(Unit)
            }
        }
        if ('"' in launcher) return Result.failure(IllegalArgumentException("The launcher path contains a quote"))
        if (elevated()) {
            val task = run(registerTaskCommand(launcher))
            if (task.isSuccess) {
                run(listOf("reg", "delete", RunKey, "/v", RunValueName, "/f"))
                return Result.success(Unit)
            }
        } else if (taskExists()) {
            // Only an elevated process can change the task, and it already starts the app at login.
            return Result.success(Unit)
        }
        return run(
            listOf(
                "reg", "add", RunKey, "/v", RunValueName, "/t", "REG_SZ",
                // reg.exe wants the inner quotes escaped; an argument that starts and ends with a quote
                // reaches it untouched, because the JDK does not escape quotes inside an argument.
                "/d", "\"\\\"$launcher\\\" --hidden\"", "/f",
            ),
        ).map { }
    }
}

/**
 * Whether the app should move its autostart to the elevated logon task: only a running elevated
 * copy can create it, and it matters only when TUN mode is why the app needs the rights.
 */
internal fun shouldRegisterElevatedAutostart(launchOnLogin: Boolean, tunModeEnabled: Boolean, elevated: Boolean): Boolean =
    launchOnLogin && tunModeEnabled && elevated


// Battery and time-limit defaults would keep a laptop from starting the app or kill it after 72 hours.
private fun registerTaskCommand(launcher: String): List<String> = listOf(
    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
    "\$u = [Security.Principal.WindowsIdentity]::GetCurrent().Name; " +
        "\$a = New-ScheduledTaskAction -Execute ${launcher.powerShellLiteral()} -Argument '--hidden'; " +
        "\$t = New-ScheduledTaskTrigger -AtLogOn -User \$u; " +
        "\$p = New-ScheduledTaskPrincipal -UserId \$u -LogonType Interactive -RunLevel Highest; " +
        "\$s = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries " +
        "-ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew; " +
        "Register-ScheduledTask -TaskName ${TaskName.powerShellLiteral()} -Action \$a -Trigger \$t -Principal \$p -Settings \$s -Force | Out-Null",
)

/** Runs `reg.exe`, `schtasks` or PowerShell without a console window and returns its output, or its error text on a non-zero exit. */
private fun runRegistryCommand(command: List<String>): Result<String> = runCatching {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    // reg.exe answers in the console's code page, not UTF-8.
    val charset = runCatching { java.nio.charset.Charset.forName(System.getProperty("native.encoding")) }
        .getOrDefault(Charsets.UTF_8)
    val output = process.inputStream.bufferedReader(charset).use { it.readText().trim() }
    check(process.waitFor(15, TimeUnit.SECONDS)) { "${command.first()} did not finish in time" }
    check(process.exitValue() == 0) { output.ifBlank { "${command.take(2).joinToString(" ")} failed" } }
    output
}
