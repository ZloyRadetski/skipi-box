// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopTunnelSessionTest {
    private class FakeNative(private val onIsRunning: () -> Unit = {}) : DesktopCoreNative {
        override val apiVersion: String = DesktopCoreApiVersion
        override val coreVersion: String = "session-test"
        private var running = false
        override fun initializeAssets(directory: String) = Unit
        override fun createController(): Long = 7L
        override fun destroyController(handle: Long) { running = false }
        override fun start(handle: Long, configJson: String, tunFd: Long) { running = true }
        override fun stop(handle: Long) { running = false }
        override fun isRunning(handle: Long): Boolean { onIsRunning(); return running }
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

    private fun controller(native: DesktopCoreNative = FakeNative()) =
        DesktopCoreController(runtimeProvider = { Result.success(runtime) }, nativeLoader = { native })

    private class FakeProxyManager(
        private val supported: () -> Boolean = { true },
        private val onRecover: () -> Unit = {},
    ) : DesktopSystemProxyManager {
        val supportedCalls = CopyOnWriteArrayList<String>()
        override fun isSupportedHost(): Boolean { supportedCalls += Thread.currentThread().name; return supported() }
        override fun acquire(endpoints: DesktopSystemProxyEndpoints) =
            Result.success(DesktopSystemProxyLeaseResult(DesktopSystemProxyLeaseAction.Acquired))
        override fun release() = Result.success(DesktopSystemProxyLeaseResult(DesktopSystemProxyLeaseAction.Released))
        override fun recover(): Result<DesktopSystemProxyLeaseResult> {
            onRecover()
            return Result.success(DesktopSystemProxyLeaseResult(DesktopSystemProxyLeaseAction.NothingToRelease))
        }
        override fun forceClear() = Result.success(Unit)
    }

    @Test
    fun creatingTheSessionDoesNotAskTheHostForProxySupport() {
        // On Linux and macOS that question loads the 36 MB Core library, and the session is built on the UI thread.
        val manager = FakeProxyManager()

        val session = DesktopTunnelSession(controller(), manager) { false }

        assertTrue(manager.supportedCalls.isEmpty(), "asked on ${manager.supportedCalls}")
        assertEquals(isDesktopSystemProxySupported(), session.systemProxyRecoveryInProgress)
    }

    @Test
    fun theRecoveryAsksTheHostOffTheCallingThread() = runBlocking<Unit> {
        val manager = FakeProxyManager()
        val session = DesktopTunnelSession(controller(), manager) { false }
        val caller = Thread.currentThread().name

        session.recoverSystemProxy()

        assertEquals(1, manager.supportedCalls.size)
        assertTrue(manager.supportedCalls.single() != caller, "asked on the caller thread $caller")
        assertFalse(session.systemProxyRecoveryInProgress)
    }

    @Test
    fun aConnectWaitsForTheStartupProxyRecoveryToFinish() = runBlocking<Unit> {
        val events = CopyOnWriteArrayList<String>()
        val recoverStarted = CountDownLatch(1)
        val releaseRecover = CountDownLatch(1)
        val manager = FakeProxyManager(onRecover = {
            events += "recover begins"
            recoverStarted.countDown()
            releaseRecover.await(10, TimeUnit.SECONDS)
            events += "recover ends"
        })
        val session = DesktopTunnelSession(controller(), manager) { false }
        session.attach(
            DesktopTunnelController(
                configForProfile = { Result.success("{}") },
                startCore = { events += "connect"; Result.success(DesktopCoreState(isRunning = true)) },
                stopCore = { Result.success(DesktopCoreState(isRunning = false)) },
                coreState = { DesktopCoreState(isRunning = false) },
            ),
        )
        val scope = CoroutineScope(Dispatchers.Default)
        val recovery = scope.launch { session.recoverSystemProxy() }
        assertTrue(recoverStarted.await(10, TimeUnit.SECONDS))

        session.toggle(scope, "profile") { false }
        delay(300)
        assertFalse("connect" in events, "the connect ran during the recovery: $events")
        releaseRecover.countDown()
        withTimeout(10_000) { while ("connect" !in events) delay(20) }
        recovery.join()

        assertEquals(listOf("recover begins", "recover ends", "connect"), events.toList())
    }

    @Test
    fun theCoreStateIsPolledOffTheUiThread() = runBlocking<Unit> {
        val uiThread = Executors.newSingleThreadExecutor { task -> Thread(task, "fake-ui-thread") }
        val polledOn = CopyOnWriteArrayList<String>()
        val core = controller(FakeNative(onIsRunning = { polledOn += Thread.currentThread().name }))
        core.start("{}").getOrThrow()
        val session = DesktopTunnelSession(core, FakeProxyManager(supported = { false })) { false }
        polledOn.clear()

        val observing = CoroutineScope(uiThread.asCoroutineDispatcher()).launch { session.observeCore() }
        delay(1_100)
        observing.cancel()
        uiThread.shutdownNow()

        assertTrue(polledOn.isNotEmpty(), "the poll never ran")
        assertFalse("fake-ui-thread" in polledOn, "Core was polled on the UI thread: $polledOn")
    }
}
