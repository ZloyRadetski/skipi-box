// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps Core's link merging in step with the saved settings and the tunnel, with no UI in it. A change of either
 * restarts the pending apply; when the tunnel is down the apply waits [offDelayMillis] first (a session restart takes
 * a moment, and stopping would throw the measured link speeds away). [onResult] hears every finished apply.
 */
class MultilinkController(
    private val scope: CoroutineScope,
    private val sync: MultilinkSync,
    private val settings: Flow<MultilinkSettings>,
    private val tunnelRunning: Flow<Boolean>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val offDelayMillis: Long = MultilinkOffDelayMillis,
    private val onResult: (MultilinkSettings, Boolean, Result<Unit>) -> Unit = { _, _, _ -> },
) {
    private val lock = Any()
    private var job: Job? = null
    private var stopped = false

    fun start() {
        synchronized(lock) {
            if (job != null || stopped) return
            job = scope.launch {
                combine(settings, tunnelRunning) { current, running -> current to running }
                    .distinctUntilChanged()
                    .collectLatest { (current, running) -> applyOnce(current, running) }
            }
        }
    }

    /**
     * Applies [current] to Core (after the grace period while the tunnel is down). Null when the controller was
     * stopped meanwhile, so a late apply never undoes the stop.
     */
    suspend fun applyOnce(current: MultilinkSettings, running: Boolean): Result<Unit>? {
        if (!running) delay(offDelayMillis)
        val result = withContext(ioDispatcher) {
            synchronized(lock) { if (stopped) null else sync.sync(current, running) }
        } ?: return null
        onResult(current, running, result)
        return result
    }

    /** Stops following the settings and tells Core to stop merging. A failure leaves the next sync to apply afresh. */
    fun stop(): Result<Unit> = synchronized(lock) {
        stopped = true
        job?.cancel()
        job = null
        sync.sync(MultilinkSettings(), tunnelRunning = false)
    }
}
