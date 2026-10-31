// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.util.concurrent.TimeUnit

private const val RunKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
private const val RunValueName = "SKIPI"

/**
 * Start at login on Windows: a value under the current user's `Run` key, so no administrator
 * rights are needed. The login starts the application hidden, like the XDG entry on Linux. A
 * start from the `Run` key is not elevated, so TUN mode, which needs administrator rights, still
 * asks for them.
 */
internal class DesktopWindowsAutostart(
    private val run: (List<String>) -> Result<String> = ::runRegistryCommand,
) : DesktopAutostartControl {
    override fun isEnabled(): Boolean = run(listOf("reg", "query", RunKey, "/v", RunValueName)).isSuccess

    override fun apply(enabled: Boolean, launcher: String): Result<Unit> {
        if (!enabled) {
            // A value that is already gone is the state the caller asked for.
            val result = run(listOf("reg", "delete", RunKey, "/v", RunValueName, "/f"))
            return if (result.isFailure && isEnabled()) Result.failure(result.exceptionOrNull()!!) else Result.success(Unit)
        }
        if ('"' in launcher) return Result.failure(IllegalArgumentException("The launcher path contains a quote"))
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

/** Runs `reg.exe` without a console window and returns its output, or its error text on a non-zero exit. */
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
