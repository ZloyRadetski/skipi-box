// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.dnsleak.DnsLeakFailureKind
import features.tools.dnsleak.DnsLeakStage
import features.tools.dnsleak.DnsLeakTestFailure
import features.tools.dnsleak.DnsLeakTestOutcome
import features.tools.ipinfo.IpInfoData
import features.tools.ipinfo.IpInfoState
import features.tools.speedtest.SpeedTestPhase
import features.tools.speedtest.SpeedTestResult
import features.tools.speedtest.SpeedTestState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One run of the speed test. Controllers are created by the host and kept alive for the whole
 * app, so a run and its result survive screen recreation (Android rotation, Desktop navigation).
 */
fun interface SpeedTestJob {
    suspend fun run(): SpeedTestResult?
}

class SpeedTestController(
    private val scope: CoroutineScope,
    private val runnerFactory: ((SpeedTestState) -> Unit) -> SpeedTestJob,
) {
    private val mutableState = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var currentRun = 0

    fun start() {
        stop()
        val run = currentRun
        mutableState.value = SpeedTestState(phase = SpeedTestPhase.Ping)
        // Only the current run may publish: a cancelled run winding down after a restart reports its
        // cleanup state late and must not overwrite the new run.
        val speedTest = runnerFactory { fresh -> if (run == currentRun) mutableState.value = fresh }
        job = scope.launch { speedTest.run() }
    }

    /** Cancels a run in progress and clears it; a finished or failed run keeps its last state on screen. */
    fun stop() {
        job?.cancel()
        job = null
        currentRun++
        if (mutableState.value.phase.isRunning()) mutableState.value = SpeedTestState()
    }
}

private fun SpeedTestPhase.isRunning() =
    this == SpeedTestPhase.Ping || this == SpeedTestPhase.Download || this == SpeedTestPhase.Upload

data class DnsLeakTestUiState(
    val running: Boolean = false,
    val stage: DnsLeakStage? = null,
    val outcome: DnsLeakTestOutcome? = null,
    val failed: Boolean = false,
    val failedReason: String? = null,
    val failureKind: DnsLeakFailureKind? = null,
)

/**
 * One run at a time. A new run drops the previous outcome and failure, so the screen never shows
 * stale results next to a fresh status, and only the run that is current may touch the state: a
 * cancelled run winding down after a restart must not flip the new one back to idle.
 */
class DnsLeakTestController(
    private val scope: CoroutineScope,
    private val probe: suspend (onStage: (DnsLeakStage) -> Unit) -> DnsLeakTestOutcome,
) {
    private val mutableState = MutableStateFlow(DnsLeakTestUiState())
    val state: StateFlow<DnsLeakTestUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var currentRun = 0

    fun start() {
        job?.cancel()
        val run = ++currentRun
        mutableState.value = DnsLeakTestUiState(running = true, stage = DnsLeakStage.Probing)
        fun update(transform: (DnsLeakTestUiState) -> DnsLeakTestUiState) {
            if (run == currentRun) mutableState.update(transform)
        }
        job = scope.launch {
            try {
                val outcome = probe { stage -> update { it.copy(stage = stage) } }
                update { it.copy(outcome = outcome) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                update {
                    it.copy(
                        failed = true,
                        failedReason = error.message,
                        failureKind = (error as? DnsLeakTestFailure)?.kind,
                    )
                }
            } finally {
                update { it.copy(running = false, stage = null) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        currentRun++
        mutableState.update { it.copy(running = false, stage = null) }
    }
}

class IpInfoController(
    private val scope: CoroutineScope,
    private val fetcher: suspend () -> IpInfoData,
) {
    private val mutableState = MutableStateFlow<IpInfoState>(IpInfoState.Idle)
    val state: StateFlow<IpInfoState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        mutableState.value = IpInfoState.Loading
        job = scope.launch {
            try {
                mutableState.value = IpInfoState.Success(fetcher())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.value = IpInfoState.Failure(
                    errorMessage = error.message,
                    isTunnelIssue = (error as? IpInfoFailure)?.isTunnelIssue == true,
                )
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (mutableState.value is IpInfoState.Loading) mutableState.value = IpInfoState.Idle
    }
}
