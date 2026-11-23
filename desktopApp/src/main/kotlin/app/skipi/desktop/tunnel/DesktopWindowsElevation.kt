// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_settings_launcher_unknown
import utils.powerShellLiteral
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
        " -ArgumentList " + arguments.joinToString(",") { argument -> argument.powerShellLiteral() }
    }
    val delay = if (exitDelaySeconds > 0) "Start-Sleep -Seconds $exitDelaySeconds; " else ""
    return listOf(
        "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
        "${delay}Start-Process -FilePath ${launcher.powerShellLiteral()}$argumentList -Verb RunAs",
    )
}


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

/**
 * Starts this very application again elevated; the copy waits for this process to exit before it takes the
 * single-instance channel. Shared by the TUN switch in Settings and the one in the tray and the menus.
 */
internal fun relaunchCurrentProcessElevated(
    run: (List<String>) -> Result<Unit> = ::runWindowsCommand,
): Result<Unit> {
    val info = ProcessHandle.current().info()
    val launcher = info.command().orElse(null)
        ?: return Result.failure(IllegalStateException(desktopText(Res.string.desktop_settings_launcher_unknown)))
    val currentArguments = info.arguments().orElse(emptyArray()).toList()
    val arguments = currentArguments.withoutWaitForPid() + listOf(WaitForPidOption, ProcessHandle.current().pid().toString())
    return relaunchElevated(launcher, arguments, exitDelaySeconds = 0, run = run)
}

/**
 * The TUN switch of a non-elevated Windows process: the elevated copy reads `enableTunMode` from the settings file as it
 * starts, so the file must hold it before the copy exists. A save that failed starts nothing; a declined UAC prompt
 * puts the setting back through [revert].
 */
internal suspend fun relaunchElevatedWithSavedSetting(
    save: suspend () -> Result<Unit>,
    relaunch: suspend () -> Result<Unit>,
    revert: suspend () -> Unit,
): Result<Unit> {
    save().onFailure { return Result.failure(it) }
    return relaunch().onFailure { revert() }
}

/**
 * Whether a start should hand over to an elevated copy: TUN mode is saved as on, this process lacks
 * administrator rights, and it is not itself the copy that was started for that. The relaunched copy
 * never asks again, so a wrong elevation check cannot loop on UAC prompts.
 */
internal fun shouldRelaunchElevatedAtStart(
    tunModeEnabled: Boolean,
    needsElevation: Boolean,
    alreadyRelaunched: Boolean,
): Boolean = tunModeEnabled && needsElevation && !alreadyRelaunched

/**
 * Whether this process runs with an elevated administrator token. The token of a running process does not change, so
 * the FFM lookup is made once: the settings page asks on every recomposition.
 */
internal fun isWindowsElevated(): Boolean = windowsElevated

private val windowsElevated: Boolean by lazy { readWindowsElevated() }

private fun readWindowsElevated(): Boolean = runCatching {
    val linker = Linker.nativeLinker()
    val shell32 = SymbolLookup.libraryLookup("shell32", java.lang.foreign.Arena.global())
    val isUserAnAdmin = linker.downcallHandle(
        shell32.findOrThrow("IsUserAnAdmin"),
        FunctionDescriptor.of(ValueLayout.JAVA_INT),
    )
    (isUserAnAdmin.invokeWithArguments() as Int) != 0
}.getOrDefault(false)

/** Runs one system command without a console window; a non-zero exit is a failure with its output. */
internal fun runWindowsCommand(command: List<String>): Result<Unit> =
    runProcessWithTimeout(command, kotlin.time.Duration.parse("30s"))
        .map { }
        .recoverCatching { error ->
            if (error is DesktopProcessFailure) {
                throw IllegalStateException("${command.take(4).joinToString(" ")} failed: ${error.output}", error)
            }
            throw error
        }
