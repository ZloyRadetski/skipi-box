// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A latency probe measures one outbound without a session, and borrows the AmneziaWG runner only while Core is idle. */
class DesktopCoreOutboundProbeTest {
    private val bridge = DesktopAmneziaWgBridge(configJson = """{"awg":true}""", socksPort = 10_811)

    private fun controller(native: ProbeNative) = DesktopCoreController(
        runtimeProvider = {
            Result.success(
                DesktopCoreRuntime(
                    directory = Path.of("assets"),
                    library = Path.of("runtime", "libskipicore.so"),
                    geoIp = Path.of("assets", "geoip.dat"),
                    geoSite = Path.of("assets", "geosite.dat"),
                ),
            )
        },
        nativeLoader = { native },
    )

    @Test
    fun measuresTheOutboundWithoutCreatingASession() {
        val native = ProbeNative(delay = 42L)
        val controller = controller(native)

        val delay = controller.measureOutboundDelay("{\"probe\":1}", "https://probe.example/204").getOrThrow()

        assertEquals(42L, delay)
        assertEquals(listOf("measure:{\"probe\":1}@https://probe.example/204"), native.events)
        assertEquals(false, controller.state().isRunning)
    }

    @Test
    fun measuresATcpConnectInCoreWithoutASessionSoItCanBypassTheTunnel() {
        val native = ProbeNative(tcpDelay = 23L)
        val controller = controller(native)

        val delay = controller.measureTcpDelay("server.example", 443, 5_000).getOrThrow()

        assertEquals(23L, delay)
        assertEquals(listOf("tcp:server.example:443@5000"), native.events)
        assertEquals(false, controller.state().isRunning)
    }

    @Test
    fun aTcpConnectStillWorksWhileTheTunnelRuns() {
        val native = ProbeNative(tcpDelay = 4L)
        val controller = controller(native)
        controller.start("{}").getOrThrow()

        assertEquals(4L, controller.measureTcpDelay("server.example", 443, 5_000).getOrThrow())
        assertEquals(true, controller.state().isRunning)
    }

    @Test
    fun aMeasurementFailureIsReportedAsAFailureNotAsADelay() {
        val controller = controller(ProbeNative(failMeasure = true))

        assertTrue(controller.measureOutboundDelay("{}", "https://probe.example/204").isFailure)
    }

    @Test
    fun anAmneziaWgProbeRunsTheRunnerOnlyForTheMeasurementAndAlwaysStopsIt() {
        val native = ProbeNative(delay = 7L)
        val controller = controller(native)
        assertEquals(7L, controller.measureOutboundDelay("{}", "https://probe.example/204", bridge).getOrThrow())
        assertEquals(listOf("awg-start:10811", "measure:{}@https://probe.example/204", "awg-stop"), native.events)

        val failing = ProbeNative(failMeasure = true)
        assertTrue(controller(failing).measureOutboundDelay("{}", "u", bridge).isFailure)
        assertEquals("awg-stop", failing.events.last())
    }

    @Test
    fun anAmneziaWgProbeIsRefusedWhileTheTunnelOwnsTheRunner() {
        val native = ProbeNative()
        val controller = controller(native)
        controller.start("{}", amneziaWg = bridge).getOrThrow()
        native.events.clear()

        val probe = controller.measureOutboundDelay("{}", "u", bridge)

        assertTrue(probe.isFailure)
        assertTrue(native.events.none { it.startsWith("awg") }, "the tunnel's runner must not be touched: ${native.events}")
        assertEquals(true, controller.state().isRunning)
    }

    @Test
    fun anOrdinaryProbeStillWorksWhileTheTunnelRuns() {
        val native = ProbeNative(delay = 9L)
        val controller = controller(native)
        controller.start("{}").getOrThrow()

        assertEquals(9L, controller.measureOutboundDelay("{}", "u").getOrThrow())
        assertEquals(true, controller.state().isRunning)
    }

    @Test
    fun stopDoesNotWaitForARunningAmneziaWgProbe() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val native = ProbeNative(onMeasure = { entered.countDown(); release.await(20, TimeUnit.SECONDS) })
        val controller = controller(native)
        controller.start("{}").getOrThrow()
        val probe = thread { controller.measureOutboundDelay("{}", "u", bridge) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val stopped = CountDownLatch(1)
            thread { controller.stop(); stopped.countDown() }

            assertTrue(stopped.await(3, TimeUnit.SECONDS), "Stop waited for the probe that holds the runner")
        } finally {
            release.countDown()
            probe.join(5_000)
        }
    }

    @Test
    fun aSecondAmneziaWgProbeIsRefusedWhileOneHoldsTheRunnerInsteadOfWaiting() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val native = ProbeNative(onMeasure = { entered.countDown(); release.await(20, TimeUnit.SECONDS) })
        val controller = controller(native)
        val first = thread { controller.measureOutboundDelay("{}", "u", bridge) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val startedAt = System.nanoTime()

            val second = controller.measureOutboundDelay("{}", "u", bridge)

            assertTrue(second.isFailure)
            assertTrue((System.nanoTime() - startedAt) < 3_000_000_000L)
        } finally {
            release.countDown()
            first.join(5_000)
        }
    }

    private class ProbeNative(
        private val onMeasure: () -> Unit = {},
        private val delay: Long = 1L,
        private val failMeasure: Boolean = false,
        private val tcpDelay: Long = 1L,
    ) : DesktopCoreNative {
        override val apiVersion: String = DesktopCoreApiVersion
        override val coreVersion: String = "probe-test"
        val events = mutableListOf<String>()
        private var running = false

        override fun initializeAssets(directory: String) = Unit
        override fun createController(): Long = 3L
        override fun destroyController(handle: Long) { running = false }
        override fun start(handle: Long, configJson: String, tunFd: Long) { running = true }
        override fun stop(handle: Long) { running = false }
        override fun isRunning(handle: Long): Boolean = running
        override fun queryTrafficStats(handle: Long): String = "{}"
        override fun measureDelay(handle: Long, targetUrl: String): Long = 1L
        override fun readMemoryStats(): String = "{}"
        override fun forceFreeMemory() = Unit
        override fun measureOutboundDelay(configJson: String, targetUrl: String): Long {
            synchronized(events) { events += "measure:$configJson@$targetUrl" }
            onMeasure()
            if (failMeasure) error("the probe failed")
            return delay
        }
        override fun measureTcpDelay(host: String, port: Int, timeoutMillis: Int): Long {
            events += "tcp:$host:$port@$timeoutMillis"
            return tcpDelay
        }
        override fun startAmneziaWg(configJson: String, socksPort: Int) { events += "awg-start:$socksPort" }
        override fun stopAmneziaWg() { events += "awg-stop" }
        override fun close() = Unit
    }
}
