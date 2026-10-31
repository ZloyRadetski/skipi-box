// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopHevProcessTunnelTest {
    private class FakeProcess(
        private var alive: Boolean = true,
        private val exit: Int = 0,
        output: String = "",
    ) : Process() {
        var destroyed = false
        private val stream = ByteArrayInputStream(output.toByteArray())
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = stream
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = exit
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = !alive
        override fun exitValue(): Int = if (alive) throw IllegalThreadStateException() else exit
        override fun isAlive(): Boolean = alive
        override fun destroy() { destroyed = true; alive = false }
        override fun destroyForcibly(): Process { destroyed = true; alive = false; return this }
    }

    private val exe = Path.of("C:/skipi/resources/hev/hev-socks5-tunnel.exe")

    private fun tunnel(process: FakeProcess, started: MutableList<Pair<List<String>, Path>> = mutableListOf()) =
        DesktopHevProcessTunnel(
            executable = exe,
            startProcess = { command, directory -> started += command to directory; process },
            startupGraceMillis = 0,
            stopTimeoutMillis = 0,
        )

    @Test
    fun runsHevOnAConfigFileFromItsOwnDirectory() {
        val started = mutableListOf<Pair<List<String>, Path>>()

        tunnel(FakeProcess(), started).start("tunnel:\n  name: skipi0\n", tunFd = -1).getOrThrow()

        val (command, directory) = started.single()
        assertEquals(exe.toString(), command.first())
        assertEquals(exe.parent, directory, "wintun.dll and msys-2.0.dll are found next to the executable")
        val config = Path.of(command[1])
        assertEquals("tunnel:\n  name: skipi0\n", Files.readString(config))
        Files.deleteIfExists(config)
    }

    @Test
    fun aProcessThatExitsDuringStartIsAFailureWithItsOutputAndExitCode() {
        val result = tunnel(FakeProcess(alive = false, exit = 3, output = "wintun.dll: cannot open")).start("x", -1)

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(result.isFailure)
        assertTrue("code 3" in message, message)
        assertTrue("wintun.dll: cannot open" in message, message)
    }

    @Test
    fun isRunningFollowsTheProcess() {
        val tunnel = tunnel(FakeProcess())
        assertFalse(tunnel.isRunning())

        tunnel.start("x", -1).getOrThrow()
        assertTrue(tunnel.isRunning())

        tunnel.stop().getOrThrow()
        assertFalse(tunnel.isRunning())
    }

    @Test
    fun stopTerminatesTheProcessAndStoppingAStoppedTunnelIsFine() {
        val process = FakeProcess()
        val tunnel = tunnel(process)
        tunnel.start("x", -1).getOrThrow()

        tunnel.stop().getOrThrow()

        assertTrue(process.destroyed)
        assertTrue(tunnel.stop().isSuccess)
    }

    @Test
    fun startingTwiceIsRejected() {
        val tunnel = tunnel(FakeProcess())
        tunnel.start("x", -1).getOrThrow()

        assertTrue(tunnel.start("x", -1).isFailure)
    }
}
