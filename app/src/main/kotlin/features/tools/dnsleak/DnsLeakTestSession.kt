// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped holder for the running DNS leak test. Keeping the state
 * and the engine job here (instead of inside the composition) lets the test
 * keep running and preserve its results across Activity recreations, such as
 * a screen rotation.
 */
@Stable
internal object DnsLeakTestSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var running by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<DnsLeakTestOutcome?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var failedReason by mutableStateOf<String?>(null)
        private set
    var failureKind by mutableStateOf<DnsLeakFailureKind?>(null)
        private set

    private var job: Job? = null

    fun start(probe: suspend () -> DnsLeakTestOutcome) {
        stop()
        running = true
        job = scope.launch {
            try {
                outcome = probe()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failed = true
                failedReason = error.message
                failureKind = (error as? DnsLeakTestFailure)?.kind
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        running = false
    }
}
