// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.runBlocking
import platform.TunnelFailure
import platform.TunnelOperationStage
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopTunnelHealthTest {
    private val connected = TunnelSnapshot(phase = TunnelPhase.Connected)

    @Test
    fun aHealthyTunnelIsLeftAlone() = runBlocking {
        var disconnects = 0
        val failure = checkDesktopTunnelHealth(snapshot = { connected }, disconnect = { disconnects++; Result.success(Unit) })

        assertNull(failure)
        assertEquals(0, disconnects)
    }

    @Test
    fun aTunnelThatDiedUnderALiveCoreIsReportedAndCoreIsStopped() = runBlocking {
        // hev (the TUN tunnel) exited while Core kept running: the snapshot fails the session and releases the
        // interface, but Core stays up, so the session must also stop it and say why.
        val dead = TunnelSnapshot(
            phase = TunnelPhase.Failed,
            failure = TunnelFailure(
                message = "The TUN tunnel stopped unexpectedly",
                stage = TunnelOperationStage.ReadStatus,
                code = "desktop_runtime",
                recoverable = true,
            ),
        )
        var disconnects = 0
        val failure = checkDesktopTunnelHealth(snapshot = { dead }, disconnect = { disconnects++; Result.success(Unit) })

        assertEquals("The TUN tunnel stopped unexpectedly", failure)
        assertEquals(1, disconnects)
    }
}
