// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The AmneziaWG runner starts before Core, stops after it, and never outlives a failed start. */
class DesktopCoreAmneziaWgLifecycleTest {
    private val bridge = DesktopAmneziaWgBridge(configJson = """{"awg":true}""", socksPort = 10_809)

    private fun controller(native: RecordingNative) = DesktopCoreController(
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
    fun startsTheRunnerBeforeCoreAndStopsItAfterCore() {
        val native = RecordingNative()
        val controller = controller(native)

        controller.start("{}", amneziaWg = bridge).getOrThrow()
        controller.stop().getOrThrow()

        assertEquals(
            listOf("awg-start:10809", "core-start", "core-stop", "core-destroy", "awg-stop"),
            native.events.filterNot { it == "assets" || it == "core-create" },
        )
        assertEquals("""{"awg":true}""", native.awgConfig)
    }

    @Test
    fun noBridgeMeansNoRunnerCalls() {
        val native = RecordingNative()
        val controller = controller(native)

        controller.start("{}").getOrThrow()
        controller.stop().getOrThrow()

        assertTrue(native.events.none { it.startsWith("awg") })
    }

    @Test
    fun aCoreStartFailureStopsTheRunnerAgain() {
        val native = RecordingNative(failCoreStart = true)
        val controller = controller(native)

        val result = controller.start("{}", amneziaWg = bridge)

        assertTrue(result.isFailure)
        assertEquals("awg-stop", native.events.last())
        assertTrue(native.events.indexOf("awg-start:10809") < native.events.indexOf("awg-stop"))
        assertEquals(false, controller.state().isRunning)
    }

    @Test
    fun aRunnerStartFailureNeverStartsCore() {
        val native = RecordingNative(failAwgStart = true)
        val controller = controller(native)

        val result = controller.start("{}", amneziaWg = bridge)

        assertTrue(result.isFailure)
        assertTrue(native.events.none { it == "core-start" })
        assertEquals(false, controller.state().isRunning)
    }

    @Test
    fun aCoreThatStoppedByItselfReleasesTheRunner() {
        val native = RecordingNative()
        val controller = controller(native)
        controller.start("{}", amneziaWg = bridge).getOrThrow()

        native.running = false
        assertEquals(false, controller.state().isRunning)

        assertEquals("awg-stop", native.events.last())
    }

    private class RecordingNative(
        private val failCoreStart: Boolean = false,
        private val failAwgStart: Boolean = false,
    ) : DesktopCoreNative {
        override val apiVersion: String = DesktopCoreApiVersion
        override val coreVersion: String = "awg-lifecycle-test"
        val events = mutableListOf<String>()
        var awgConfig: String? = null
        var running = false

        override fun initializeAssets(directory: String) { events += "assets" }
        override fun createController(): Long { events += "core-create"; return 7L }
        override fun destroyController(handle: Long) { events += "core-destroy"; running = false }
        override fun start(handle: Long, configJson: String, tunFd: Long) {
            events += "core-start"
            if (failCoreStart) error("core refused to start")
            running = true
        }
        override fun stop(handle: Long) { events += "core-stop"; running = false }
        override fun isRunning(handle: Long): Boolean = running
        override fun queryTrafficStats(handle: Long): String = "{}"
        override fun measureDelay(handle: Long, targetUrl: String): Long = 1L
        override fun readMemoryStats(): String = "{}"
        override fun forceFreeMemory() = Unit
        override fun startAmneziaWg(configJson: String, socksPort: Int) {
            events += "awg-start:$socksPort"
            if (failAwgStart) error("awg refused to start")
            awgConfig = configJson
        }
        override fun stopAmneziaWg() { events += "awg-stop" }
        override fun close() = Unit
    }
}
