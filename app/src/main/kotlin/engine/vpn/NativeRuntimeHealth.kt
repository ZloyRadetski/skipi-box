// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide record of whether a native runtime (hev-socks5-tunnel, SKIPI
 * Core) failed to stop in time. Such a runtime can still hold sockets or
 * threads, so nothing may start a new one in this process; only a process
 * restart clears the state.
 */
internal class NativeRuntimeHealth {
    private val dirtyReason = AtomicReference<String?>(null)

    val isDirty: Boolean get() = dirtyReason.get() != null

    fun markDirty(reason: String) {
        dirtyReason.compareAndSet(null, reason)
    }

    companion object {
        val Process = NativeRuntimeHealth()
    }
}

internal enum class VpnStartDecision { Allowed, RefusedNativeRuntimeDirty }

internal fun decideVpnStart(health: NativeRuntimeHealth): VpnStartDecision =
    if (health.isDirty) VpnStartDecision.RefusedNativeRuntimeDirty else VpnStartDecision.Allowed

internal sealed interface StepOutcome {
    data object Completed : StepOutcome
    data object TimedOut : StepOutcome
    data class Failed(val error: Throwable) : StepOutcome
}

internal const val NativeStopStepDeadlineMillis = 3_000L
internal const val EmergencyStopStepDeadlineMillis = 1_000L

/**
 * Runs a native stop call on its own daemon thread and waits at most the
 * deadline, so a call stuck in native code cannot block the caller. A step
 * that misses its deadline is abandoned (the thread may stay blocked) and
 * marks the runtime dirty.
 */
internal class BoundedNativeSteps(
    private val health: NativeRuntimeHealth,
    private val warn: (message: String, error: Throwable?) -> Unit,
) {
    fun run(name: String, deadlineMillis: Long, block: () -> Unit): StepOutcome {
        var failure: Throwable? = null
        val thread = Thread({
            try {
                block()
            } catch (error: Throwable) {
                failure = error
            }
        }, "skipi-native-stop-$name").apply { isDaemon = true }
        thread.start()
        try {
            thread.join(deadlineMillis)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            warn("Waiting for native stop step $name was interrupted", interrupted)
            return StepOutcome.Failed(interrupted)
        }
        if (thread.isAlive) {
            health.markDirty("$name did not stop within ${deadlineMillis}ms")
            warn("Native stop step $name did not finish within ${deadlineMillis}ms; the runtime is now dirty", null)
            return StepOutcome.TimedOut
        }
        failure?.let { error ->
            warn("Native stop step $name failed", error)
            return StepOutcome.Failed(error)
        }
        return StepOutcome.Completed
    }
}
