// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyServerSelectionRuntimeDecisionTest {
    @Test
    fun unchangedSelectionDoesNotRequestRuntimeRestart() {
        assertEquals(
            ProxyServerSelectionRuntimeAction.None,
            decision(
                previousSelectedServerId = 7,
                selectedServerId = 7,
                requestedServerId = 7,
                serverExists = true,
                runtimeRunning = true,
                activeServerId = 3,
            ),
        )
    }

    @Test
    fun changedSelectionRequestsRestartOnlyWhileRuntimeIsRunning() {
        val runningDecision = decision(
            previousSelectedServerId = 3,
            selectedServerId = 7,
            requestedServerId = 7,
            serverExists = true,
            runtimeRunning = true,
            activeServerId = 3,
        )
        val stoppedDecision = decision(
            previousSelectedServerId = 3,
            selectedServerId = 7,
            requestedServerId = 7,
            serverExists = true,
            runtimeRunning = false,
            activeServerId = 3,
        )

        assertEquals(ProxyServerSelectionRuntimeAction.Restart, runningDecision)
        assertEquals(ProxyServerSelectionRuntimeAction.None, stoppedDecision)
    }

    @Test
    fun staleSelectionRequestDoesNotRestartRuntime() {
        assertEquals(
            ProxyServerSelectionRuntimeAction.None,
            decision(
                previousSelectedServerId = 3,
                selectedServerId = 9,
                requestedServerId = 7,
                serverExists = true,
                runtimeRunning = true,
                activeServerId = 3,
            ),
        )
    }

    @Test
    fun selectedServerAlreadyActiveDoesNotRestartRuntime() {
        assertEquals(
            ProxyServerSelectionRuntimeAction.None,
            decision(
                previousSelectedServerId = 3,
                selectedServerId = 7,
                requestedServerId = 7,
                serverExists = true,
                runtimeRunning = true,
                activeServerId = 7,
            ),
        )
    }

    @Test
    fun missingSelectedServerDoesNotRestartRuntime() {
        assertEquals(
            ProxyServerSelectionRuntimeAction.None,
            decision(
                previousSelectedServerId = 3,
                selectedServerId = 7,
                requestedServerId = 7,
                serverExists = false,
                runtimeRunning = true,
                activeServerId = 3,
            ),
        )
    }

    private fun decision(
        previousSelectedServerId: Int?,
        selectedServerId: Int?,
        requestedServerId: Int,
        serverExists: Boolean,
        runtimeRunning: Boolean,
        activeServerId: Int?,
    ): ProxyServerSelectionRuntimeAction = decideProxyServerSelectionRuntimeAction(
        previousSelectedServerId = previousSelectedServerId,
        selectedServerId = selectedServerId,
        requestedServerId = requestedServerId,
        serverExists = serverExists,
        runtimeRunning = runtimeRunning,
        activeServerId = activeServerId,
    )
}
