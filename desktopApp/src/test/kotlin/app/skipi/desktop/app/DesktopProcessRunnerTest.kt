// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DesktopProcessRunnerTest {
    private val windows = System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)
    private val java = System.getProperty("java.home") + "/bin/java"

    private val hangingWhilePrinting: List<String> =
        if (windows) listOf("ping", "-n", "30", "127.0.0.1") else listOf("sh", "-c", "echo started; sleep 30")

    @Test
    fun outputOfAFinishedCommandComesBackTrimmed() {
        val result = runProcessWithTimeout(listOf(java, "-version"), 30.seconds)

        assertTrue(result.isSuccess, result.toString())
        assertTrue(result.getOrThrow().contains("version"))
    }

    @Test
    fun aCommandThatKeepsPrintingIsKilledAtTheTimeout() {
        val startedAt = System.nanoTime()

        val result = runProcessWithTimeout(hangingWhilePrinting, 700.milliseconds)

        val tookMillis = (System.nanoTime() - startedAt) / 1_000_000
        assertIs<DesktopProcessTimeout>(result.exceptionOrNull())
        assertTrue(tookMillis < 10_000, "the timeout did not fire: $tookMillis ms")
    }

    @Test
    fun aNonZeroExitIsAFailureWithTheOutput() {
        val result = runProcessWithTimeout(listOf(java, "--no-such-option"), 30.seconds)

        val failure = assertIs<DesktopProcessFailure>(result.exceptionOrNull())
        assertTrue(failure.exitCode != 0)
    }

    @Test
    fun aCommandThatCannotStartIsAFailure() {
        assertTrue(runProcessWithTimeout(listOf("definitely-not-a-command-skipi"), 1.seconds).isFailure)
    }

    @Test
    fun theStartInjectionIsUsed() {
        var started: List<String>? = null
        runProcessWithTimeout(listOf("x"), 1.seconds, start = { started = it; error("no") })
        assertEquals(listOf("x"), started)
    }
}
