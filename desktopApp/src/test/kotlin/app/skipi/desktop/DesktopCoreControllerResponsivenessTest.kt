// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The UI thread polls Core state; a slow native start must never make that poll wait. */
class DesktopCoreControllerResponsivenessTest {
    private class BlockingNative : DesktopCoreNative {
        val startEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        override val apiVersion = DesktopCoreApiVersion
        override val coreVersion = "test"
        override fun initializeAssets(directory: String) = Unit
        override fun createController(): Long = 7L
        override fun destroyController(handle: Long) = Unit
        override fun start(handle: Long, configJson: String, tunFd: Long) {
            startEntered.countDown()
            releaseStart.await(10, TimeUnit.SECONDS)
        }
        override fun stop(handle: Long) = Unit
        override fun isRunning(handle: Long): Boolean = true
        override fun queryTrafficStats(handle: Long): String = "{}"
        override fun measureDelay(handle: Long, targetUrl: String): Long = 0L
        override fun readMemoryStats(): String = ""
        override fun forceFreeMemory() = Unit
        override fun close() = Unit
    }

    @Test
    fun stateReturnsWhileNativeStartIsStillRunning() {
        val directory = Files.createTempDirectory("skipi-core-responsive-")
        val native = BlockingNative()
        val runtime = DesktopCoreRuntime(directory, directory.resolve("lib"), directory.resolve("a"), directory.resolve("b"))
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(runtime) },
            nativeLoader = { _: Path -> native },
        )
        val pool = Executors.newFixedThreadPool(2)
        try {
            val starting = pool.submit<Result<DesktopCoreState>> { controller.start("{}") }
            assertTrue(native.startEntered.await(5, TimeUnit.SECONDS))

            val poll = pool.submit<DesktopCoreState> { controller.state() }
            val state = poll.get(2, TimeUnit.SECONDS)
            assertFalse(state.isRunning)

            native.releaseStart.countDown()
            assertTrue(starting.get(5, TimeUnit.SECONDS).getOrThrow().isRunning)
            assertEquals(7L, controller.state().sessionId)
        } finally {
            native.releaseStart.countDown()
            pool.shutdownNow()
            Files.deleteIfExists(directory)
        }
    }
}
