// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.ipinfo

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Stable
internal object IpInfoSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var state by mutableStateOf<IpInfoState>(IpInfoState.Idle)
        private set

    private var job: Job? = null

    fun refresh(fetcher: suspend () -> IpInfoData) {
        job?.cancel()
        state = IpInfoState.Loading
        job = scope.launch {
            try {
                val data = fetcher()
                state = IpInfoState.Success(data)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state = IpInfoState.Failure(
                    errorMessage = error.message,
                    isTunnelIssue = error.message?.contains("VPN tunnel", ignoreCase = true) == true,
                )
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (state is IpInfoState.Loading) {
            state = IpInfoState.Idle
        }
    }
}
