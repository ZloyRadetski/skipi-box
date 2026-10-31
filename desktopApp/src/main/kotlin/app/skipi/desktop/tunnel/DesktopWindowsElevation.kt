// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

/**
 * The PowerShell command that starts the application again with administrator rights. It waits
 * [exitDelaySeconds] first so the running instance has left and released its single-instance
 * channel; `Start-Process -Verb RunAs` then shows the UAC prompt and fails if the user declines.
 */
internal fun windowsElevatedRelaunchCommand(
    launcher: String,
    arguments: List<String>,
    exitDelaySeconds: Int,
): List<String> {
    val argumentList = if (arguments.isEmpty()) {
        ""
    } else {
        " -ArgumentList " + arguments.joinToString(",") { argument -> powerShellLiteral(argument) }
    }
    val delay = if (exitDelaySeconds > 0) "Start-Sleep -Seconds $exitDelaySeconds; " else ""
    return listOf(
        "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
        "${delay}Start-Process -FilePath ${powerShellLiteral(launcher)}$argumentList -Verb RunAs",
    )
}

private fun powerShellLiteral(value: String): String = "'" + value.replace("'", "''") + "'"

/**
 * Asks Windows to start this application elevated. Success means the prompt was accepted and the
 * elevated instance is on its way, so the caller exits; a declined prompt is a failure and the
 * caller keeps running.
 */
internal fun relaunchElevated(
    launcher: String,
    arguments: List<String>,
    exitDelaySeconds: Int = 2,
    run: (List<String>) -> Result<Unit> = ::runWindowsCommand,
): Result<Unit> = run(windowsElevatedRelaunchCommand(launcher, arguments, exitDelaySeconds))

/** Whether this process runs with an elevated administrator token. */
internal fun isWindowsElevated(): Boolean = runCatching {
    val linker = Linker.nativeLinker()
    val shell32 = SymbolLookup.libraryLookup("shell32", java.lang.foreign.Arena.global())
    val isUserAnAdmin = linker.downcallHandle(
        shell32.findOrThrow("IsUserAnAdmin"),
        FunctionDescriptor.of(ValueLayout.JAVA_INT),
    )
    (isUserAnAdmin.invokeWithArguments() as Int) != 0
}.getOrDefault(false)
