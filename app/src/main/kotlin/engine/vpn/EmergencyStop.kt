// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Injectable so tests can assert the kill without ending the test JVM. */
internal fun interface ProcessKiller {
    fun kill()
}

internal object AndroidProcessKiller : ProcessKiller {
    override fun kill() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

/** The side effects of an emergency stop; the service supplies the real ones. */
internal interface EmergencyStopActions {
    /** Marks the tunnel stopped and persists "not connected" so nothing reconnects by itself. */
    fun markStoppedAndPersistIntent()
    fun stopHev()
    fun stopCore()
    fun stopTailers()
    fun closeTun()

    /** stopForeground and stopSelf. */
    fun stopService()

    /** Quick Settings tile and widget. */
    fun notifyStateChanged()

    /** Arranges for the user to get back into the app after the process is killed. */
    fun prepareRelaunch()
}

/**
 * Tears the tunnel down without the operation mutex, with short deadlines per
 * native step. If any native step is stuck (now or earlier) the process is
 * killed, since a hung native runtime cannot be recovered in-process.
 *
 * This is an explicit user action: the tunnel goes away and traffic is no
 * longer protected unless the system Always-on VPN with "Block connections
 * without VPN" is enabled. The kill switch of the app cannot hold a TUN
 * across a process death.
 */
internal class EmergencyStopCoordinator(
    private val health: NativeRuntimeHealth,
    private val steps: BoundedNativeSteps,
    private val actions: EmergencyStopActions,
    private val killer: ProcessKiller,
    private val stepDeadlineMillis: Long = EmergencyStopStepDeadlineMillis,
) {
    fun run() {
        actions.markStoppedAndPersistIntent()
        // An earlier stop is still stuck in hev or Core: a second native stop racing it can crash
        // the process instead of letting it be killed cleanly, so only the kill is left.
        if (!health.isDirty) {
            steps.run("hev", stepDeadlineMillis, actions::stopHev)
            steps.run("core", stepDeadlineMillis, actions::stopCore)
        }
        steps.run("tailers", stepDeadlineMillis, actions::stopTailers)
        // The process is about to die if anything hung, so closing the TUN
        // before a still-running hev is acceptable here.
        runCatching { actions.closeTun() }
        runCatching { actions.stopService() }
        runCatching { actions.notifyStateChanged() }
        if (health.isDirty) {
            runCatching { actions.prepareRelaunch() }
            killer.kill()
        }
    }

    /** Runs on a fresh thread: neither serviceScope nor the main thread can be starved by a hung stop. */
    fun runOnDedicatedThread(): Thread =
        Thread(::run, "skipi-emergency-stop").apply {
            isDaemon = true
            start()
        }
}

internal const val StopWatchdogTimeoutMillis = 8_000L

/**
 * Fires [onHung] once a stop guarded by [guard] has run longer than
 * [timeoutMillis]; [onCleared] runs when such a stop finally completes.
 */
internal class StopWatchdog(
    private val timeoutMillis: Long = StopWatchdogTimeoutMillis,
    private val onHung: () -> Unit,
    private val onCleared: () -> Unit = {},
) {
    fun <T> guard(block: () -> T): T {
        val state = AtomicInteger(Pending)
        val task = scheduler.schedule({
            if (state.compareAndSet(Pending, Fired)) runCatching(onHung)
        }, timeoutMillis, TimeUnit.MILLISECONDS)
        try {
            return block()
        } finally {
            task.cancel(false)
            if (!state.compareAndSet(Pending, Cancelled)) runCatching(onCleared)
        }
    }

    private companion object {
        const val Pending = 0
        const val Fired = 1
        const val Cancelled = 2
        val scheduler = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "skipi-stop-watchdog").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}

internal interface EmergencyStopMarkerStore {
    fun read(): Long
    fun write(epochMillis: Long)
}

/**
 * Remembers that the user ran an emergency stop, because the app process is
 * killed and the in-memory proxyRunning flag is gone. The next app-open
 * auto-connect is skipped once; a system-managed (Always-on / sticky) start
 * is skipped for a short window so the service does not bring the tunnel back
 * the moment it was torn down.
 */
internal class EmergencyStopMarker(
    private val store: EmergencyStopMarkerStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun record() = store.write(now())

    fun consumeSuppressAutoConnect(): Boolean {
        val at = store.read()
        if (at == 0L) return false
        store.write(0L)
        return now() - at <= AppOpenWindowMillis
    }

    fun suppressSystemManagedStart(): Boolean {
        val at = store.read()
        return at != 0L && now() - at <= SystemStartWindowMillis
    }

    companion object {
        const val SystemStartWindowMillis = 60_000L
        const val AppOpenWindowMillis = 10 * 60_000L
    }
}
