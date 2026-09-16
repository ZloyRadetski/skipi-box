// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped holder for the running speed test. Keeping the state and
 * the engine job here (instead of inside the composition) lets the test keep
 * running and preserve its result across Activity recreations, such as a
 * screen rotation.
 */
@Stable
internal object SpeedTestSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var state by mutableStateOf(SpeedTestState())
        private set

    private var job: Job? = null

    fun start(context: Context) {
        stop()
        state = SpeedTestState(phase = SpeedTestPhase.Ping)
        val connectionFactory = TunnelSpeedTestConnectionFactory(context.applicationContext)
        job = scope.launch {
            SpeedTestEngine(
                onState = { fresh -> state = fresh },
                connectionFactory = connectionFactory,
            ).run()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        state = SpeedTestState()
    }
}
