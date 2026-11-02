// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Loading the 36 MB Core library at launch keeps that cost out of the first Connect. */
class DesktopCorePreloadTest {
    private class FakeNative : DesktopCoreNative {
        override val apiVersion: String = DesktopCoreApiVersion
        override val coreVersion: String = "preload-test"
        private var running = false
        override fun initializeAssets(directory: String) = Unit
        override fun createController(): Long = 7L
        override fun destroyController(handle: Long) { running = false }
        override fun start(handle: Long, configJson: String, tunFd: Long) { running = true }
        override fun stop(handle: Long) { running = false }
        override fun isRunning(handle: Long): Boolean = running
        override fun queryTrafficStats(handle: Long): String = "{}"
        override fun measureDelay(handle: Long, targetUrl: String): Long = 1L
        override fun readMemoryStats(): String = "{}"
        override fun forceFreeMemory() = Unit
        override fun startAmneziaWg(configJson: String, socksPort: Int) = Unit
        override fun stopAmneziaWg() = Unit
        override fun close() = Unit
    }

    private val runtime = DesktopCoreRuntime(
        directory = Path.of("assets"),
        library = Path.of("runtime", "libskipicore.so"),
        geoIp = Path.of("assets", "geoip.dat"),
        geoSite = Path.of("assets", "geosite.dat"),
    )

    @Test
    fun aPreloadedLibraryIsNotLoadedAgainByTheFirstStart() {
        var loads = 0
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(runtime) },
            nativeLoader = { loads += 1; FakeNative() },
        )

        controller.preload().getOrThrow()
        assertEquals(1, loads)

        controller.start("{}").getOrThrow()
        assertEquals(1, loads, "start must reuse the preloaded library")
        assertTrue(controller.state().isRunning)
    }

    @Test
    fun aFailedPreloadIsReportedAndLeavesStartToTryAgain() {
        var loads = 0
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(runtime) },
            nativeLoader = { loads += 1; if (loads == 1) error("library busy") else FakeNative() },
        )

        assertTrue(controller.preload().isFailure)

        controller.start("{}").getOrThrow()
        assertEquals(2, loads)
    }

    @Test
    fun aMissingRuntimeFailsThePreloadWithoutThrowing() {
        val controller = DesktopCoreController(
            runtimeProvider = { Result.failure(IllegalStateException("no runtime")) },
            nativeLoader = { error("must not load") },
        )

        assertTrue(controller.preload().isFailure)
    }
}
