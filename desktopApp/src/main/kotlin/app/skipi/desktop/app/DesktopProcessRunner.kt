// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A command that ended with a non-zero exit code; the message is its output. */
internal class DesktopProcessFailure(val exitCode: Int, val output: String, command: List<String>) :
    IllegalStateException(output.ifBlank { "${command.take(2).joinToString(" ")} failed ($exitCode)" })

/** A command that did not finish within its time limit and was killed. */
internal class DesktopProcessTimeout(command: List<String>, timeout: Duration) :
    IllegalStateException("${command.first()} did not finish in ${timeout.inWholeSeconds} s")

internal fun startDesktopProcess(command: List<String>): Process =
    ProcessBuilder(command).redirectErrorStream(true).start()

/**
 * Runs [command] and returns its trimmed output (stdout and stderr). The output is drained on a daemon
 * thread, so [timeout] holds even when the command hangs while printing or never closes its pipe; on
 * timeout the process is killed and the pipe closed. A non-zero exit is a failure carrying the output.
 */
internal fun runProcessWithTimeout(
    command: List<String>,
    timeout: Duration = 10.seconds,
    charset: Charset = Charsets.UTF_8,
    start: (List<String>) -> Process = ::startDesktopProcess,
): Result<String> {
    val process = runCatching { start(command) }.getOrElse { return Result.failure(it) }
    val buffer = ByteArrayOutputStream()
    val reader = Thread({
        runCatching { process.inputStream.use { input -> input.copyTo(buffer) } }
    }, "process-output").apply { isDaemon = true }
    reader.start()
    return try {
        if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            runCatching { process.inputStream.close() }
            Result.failure(DesktopProcessTimeout(command, timeout))
        } else {
            reader.join(2_000)
            if (reader.isAlive) runCatching { process.inputStream.close() }
            val output = synchronized(buffer) { buffer.toString(charset) }.trim()
            val exit = process.exitValue()
            if (exit == 0) Result.success(output) else Result.failure(DesktopProcessFailure(exit, output, command))
        }
    } catch (interrupted: InterruptedException) {
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        Result.failure(interrupted)
    }
}
