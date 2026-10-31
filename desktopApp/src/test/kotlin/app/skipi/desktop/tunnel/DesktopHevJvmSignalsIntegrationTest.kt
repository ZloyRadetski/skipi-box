// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression for the Desktop crash on connect: hev must not take over the JVM's own SIGSEGV
 * handling. hev runs on one end of a socketpair (no interface, no routes) while the JVM raises
 * SIGSEGV itself through implicit null checks in compiled code. Opt-in with
 * SKIPI_CORE_INTEGRATION_DIR pointing at a runtime that contains libhev-socks5-tunnel.so.
 */
class DesktopHevJvmSignalsIntegrationTest {
    @Test
    fun theJvmKeepsHandlingItsOwnSignalsWhileHevRuns() {
        val hevLibrary = System.getenv("SKIPI_CORE_INTEGRATION_DIR")
            ?.takeIf(String::isNotBlank)
            ?.let { Path.of(it).resolve("libhev-socks5-tunnel.so") }
            ?: return
        val (tunnelEnd, _) = socketPair()
        val tunnel = DesktopHevFfmTunnel(hevLibrary)
        val yaml = """
            tunnel:
              mtu: 1400
              ipv4: '172.19.0.1'
            socks5:
              port: 9
              address: '127.0.0.1'
              udp: 'udp'
            misc:
              log-level: error
        """.trimIndent() + "\n"

        assertTrue(tunnel.start(yaml, tunnelEnd).isSuccess, "hev did not start on the socketpair")
        try {
            var caught = 0
            repeat(200) { round ->
                repeat(20_000) { index -> caught += dereference(if (index % 64 == 0) MissingProperty else "java.version") }
                // Safepoints make the JVM fault on its polling page in every running thread.
                if (round % 10 == 0) System.gc()
            }
            assertTrue(caught > 0)
            assertTrue(tunnel.isRunning(), "hev stopped while the JVM handled its own signals")
        } finally {
            tunnel.stop()
        }
    }

    // System.getProperty is a Java platform type, so Kotlin adds no null check: the JIT compiles
    // `.length` to a plain load that faults on null, and the JVM turns that SIGSEGV into an NPE.
    private fun dereference(property: String): Int = try {
        System.getProperty(property).length
        0
    } catch (_: NullPointerException) {
        1
    }

    private companion object {
        const val MissingProperty = "skipi.test.property.that.is.never.set"
    }

    private fun socketPair(): Pair<Int, Int> {
        val linker = Linker.nativeLinker()
        val socketpair = linker.downcallHandle(
            linker.defaultLookup().findOrThrow("socketpair"),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
        Arena.ofConfined().use { arena ->
            val fds = arena.allocate(ValueLayout.JAVA_INT, 2)
            // AF_UNIX, SOCK_DGRAM: packet boundaries like a tun device.
            val result = socketpair.invokeWithArguments(1, 2, 0, fds) as Int
            check(result == 0) { "socketpair failed" }
            return fds.getAtIndex(ValueLayout.JAVA_INT, 0) to fds.getAtIndex(ValueLayout.JAVA_INT, 1)
        }
    }
}
