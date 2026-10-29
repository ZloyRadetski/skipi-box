// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.dnsleak.DnsLeakFailureKind
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

    fun start() {
        stop()
        mutableState.value = SpeedTestState(phase = SpeedTestPhase.Ping)
        val speedTest = runnerFactory { fresh -> mutableState.value = fresh }
        job = scope.launch { speedTest.run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        mutableState.value = SpeedTestState()
    }
}

data class DnsLeakTestUiState(
    val running: Boolean = false,
    val outcome: DnsLeakTestOutcome? = null,
    val failed: Boolean = false,
    val failedReason: String? = null,
    val failureKind: DnsLeakFailureKind? = null,
)

class DnsLeakTestController(
    private val scope: CoroutineScope,
    private val probe: suspend () -> DnsLeakTestOutcome,
) {
    private val mutableState = MutableStateFlow(DnsLeakTestUiState())
    val state: StateFlow<DnsLeakTestUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun start() {
        stop()
        // A new run clears the previous failure; the last outcome stays visible until replaced.
        mutableState.value = mutableState.value.copy(
            running = true,
            failed = false,
            failedReason = null,
            failureKind = null,
        )
        job = scope.launch {
            try {
                val outcome = probe()
                mutableState.value = mutableState.value.copy(outcome = outcome)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(
                    failed = true,
                    failedReason = error.message,
                    failureKind = (error as? DnsLeakTestFailure)?.kind,
                )
            } finally {
                mutableState.value = mutableState.value.copy(running = false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        mutableState.value = mutableState.value.copy(running = false)
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
