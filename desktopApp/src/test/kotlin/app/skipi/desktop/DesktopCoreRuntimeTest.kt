// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import platform.TunnelTraffic

class DesktopCoreRuntimeTest {
    @Test
    fun runtimeRequiresNativeLibraryAndGeoAssets() {
        val directory = Files.createTempDirectory("skipi-core-runtime-")
        try {
            assertTrue(DesktopCoreRuntimes.fromDirectory(directory).isFailure)

            listOf("skipicore.dll", "libskipicore.so", "geoip.dat", "geosite.dat").forEach { name ->
                Files.writeString(directory.resolve(name), name)
            }
            val runtime = DesktopCoreRuntimes.fromDirectory(directory).getOrThrow()

            assertEquals(directory.toAbsolutePath().normalize(), runtime.directory)
            assertTrue(Files.isRegularFile(runtime.library))
            assertTrue(Files.isRegularFile(runtime.geoIp))
            assertTrue(Files.isRegularFile(runtime.geoSite))
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun controllerStartsAndStopsOneInProcessCoreSession() {
        val native = FakeDesktopCoreNative()
        val runtime = fakeRuntime()
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(runtime) },
            nativeLoader = { native },
        )

        val started = controller.start("{}").getOrThrow()
        assertTrue(started.isRunning)
        assertEquals(41L, started.sessionId)
        assertEquals("xray-test", started.coreVersion)
        assertEquals(runtime.directory.toString(), native.initializedAssetsDirectory)
        assertEquals("{}", native.startedConfig)
        assertEquals(0L, native.startedTunFd)

        val stopped = controller.stop().getOrThrow()
        assertFalse(stopped.isRunning)
        assertEquals(listOf(41L), native.destroyedHandles)
    }

    @Test
    fun failedStartReleasesTheAllocatedCoreHandle() {
        val native = FakeDesktopCoreNative(startFailure = IllegalStateException("invalid Xray JSON"))
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(fakeRuntime()) },
            nativeLoader = { native },
        )

        val result = controller.start("{}")

        assertTrue(result.isFailure)
        assertFalse(controller.state().isRunning)
        assertEquals(listOf(41L), native.destroyedHandles)
    }

    @Test
    fun controllerMapsCoreCountersToTheSharedTunnelTrafficModel() {
        val native = FakeDesktopCoreNative(
            trafficStats = """
                {
                  "inbound": {
                    "socks-in": { "uplink": 10, "downlink": 20 },
                    "http-in": { "uplink": 30, "downlink": 40 }
                  },
                  "outbound": {
                    "proxy": { "uplink": 50, "downlink": 60 }
                  }
                }
            """.trimIndent(),
        )
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(fakeRuntime()) },
            nativeLoader = { native },
        )
        controller.start("{}").getOrThrow()

        assertEquals(
            TunnelTraffic(uploadBytes = 40L, downloadBytes = 60L),
            controller.readTunnelTraffic().getOrThrow(),
        )
    }

    @Test
    fun controllerRejectsAnIncompatibleDesktopAbiBeforeStarting() {
        val native = FakeDesktopCoreNative(apiVersionValue = "999")
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(fakeRuntime()) },
            nativeLoader = { native },
        )

        val error = assertFailsWith<IllegalStateException> {
            controller.start("{}").getOrThrow()
        }

        assertTrue(error.message.orEmpty().contains("Unsupported SKIPI Core desktop ABI"))
        assertEquals(emptyList(), native.destroyedHandles)
        assertFalse(native.running)
    }

    @Test
    fun closeStopsTheCoreAndReleasesTheLoadedLibrary() {
        val native = FakeDesktopCoreNative()
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(fakeRuntime()) },
            nativeLoader = { native },
        )
        controller.start("{}").getOrThrow()

        controller.close()

        assertTrue(native.closed)
        assertFalse(native.running)
        assertEquals(listOf(41L), native.destroyedHandles)
    }

    private fun fakeRuntime(): DesktopCoreRuntime = DesktopCoreRuntime(
        directory = Path.of("C:/SKIPI/resources"),
        library = Path.of("C:/SKIPI/resources/skipicore.dll"),
        geoIp = Path.of("C:/SKIPI/resources/geoip.dat"),
        geoSite = Path.of("C:/SKIPI/resources/geosite.dat"),
    )

    private class FakeDesktopCoreNative(
        private val apiVersionValue: String = DesktopCoreApiVersion,
        private val startFailure: Throwable? = null,
        private val trafficStats: String = "{\"inbound\":{},\"outbound\":{}}",
    ) : DesktopCoreNative {
        override val apiVersion: String
            get() = apiVersionValue
        override val coreVersion: String = "xray-test"

        var initializedAssetsDirectory: String? = null
        var startedConfig: String? = null
        var startedTunFd: Long? = null
        var running = false
        var closed = false
        val destroyedHandles = mutableListOf<Long>()

        override fun initializeAssets(directory: String) {
            initializedAssetsDirectory = directory
        }

        override fun createController(): Long = 41L

        override fun destroyController(handle: Long) {
            destroyedHandles += handle
            running = false
        }

        override fun start(handle: Long, configJson: String, tunFd: Long) {
            startFailure?.let { throw it }
            startedConfig = configJson
            startedTunFd = tunFd
            running = true
        }

        override fun stop(handle: Long) {
            running = false
        }

        override fun isRunning(handle: Long): Boolean = running

        override fun queryTrafficStats(handle: Long): String = trafficStats

        override fun measureDelay(handle: Long, targetUrl: String): Long = 1L

        override fun readMemoryStats(): String = "{}"

        override fun forceFreeMemory() = Unit

        override fun close() {
            closed = true
        }
    }
}
