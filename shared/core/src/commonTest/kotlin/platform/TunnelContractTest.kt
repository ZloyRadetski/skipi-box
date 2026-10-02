// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunnelContractTest {
    @Test
    fun disconnectedSnapshotIsSafeBeforeAnyPlatformAdapterStarts() {
        val snapshot = TunnelSnapshot()

        assertEquals(TunnelPhase.Disconnected, snapshot.phase)
        assertEquals(TunnelTraffic(), snapshot.traffic)
        assertFalse(snapshot.failure?.recoverable ?: false)
        assertNull(snapshot.profileId)
    }

    @Test
    fun lifecycle_accepts_retry_after_failure_and_keeps_selected_profile() {
        val failed = TunnelSnapshot(
            phase = TunnelPhase.Failed,
            failure = TunnelFailure("runtime", "temporary failure", true),
        )

        val decision = TunnelLifecyclePolicy.beginConnect(failed, "  profile-7  ")
        val connecting = assertIs<TunnelLifecycleDecision.Accepted>(decision).snapshot

        assertEquals(TunnelPhase.Connecting, connecting.phase)
        assertEquals("profile-7", connecting.profileId)
        assertNull(connecting.failure)
        assertEquals(
            TunnelPhase.Connected,
            TunnelLifecyclePolicy.connected(connecting).phase,
        )
    }

    @Test
    fun lifecycle_rejects_invalid_or_overlapping_connect_and_structures_failure() {
        val empty = TunnelLifecyclePolicy.beginConnect(TunnelSnapshot(), "  ")
        val blankFailure = assertIs<TunnelLifecycleDecision.Rejected>(empty).failure
        assertEquals("profile_required", blankFailure.code)
        assertEquals(TunnelOperationStage.ResolveProfile, blankFailure.stage)
        assertFalse(blankFailure.recoverable)

        val alreadyConnected = TunnelLifecyclePolicy.beginConnect(
            TunnelSnapshot(phase = TunnelPhase.Connected),
            "profile-7",
        )
        assertEquals(
            "already_running",
            assertIs<TunnelLifecycleDecision.Rejected>(alreadyConnected).failure.code,
        )

        val error = IllegalStateException("runtime unavailable")
        val failure = TunnelLifecyclePolicy.failure(
            error,
            stage = TunnelOperationStage.Start,
            code = "runtime_unavailable",
            platformCode = "native_17",
        )
        assertEquals("runtime_unavailable", failure.code)
        assertEquals("runtime unavailable", failure.message)
        assertEquals("native_17", failure.platformCode)
        assertTrue(failure.recoverable)
    }

    @Test
    fun lifecycle_disconnect_clears_traffic_and_preserves_last_profile_reference() {
        val connected = TunnelSnapshot(
            phase = TunnelPhase.Connected,
            traffic = TunnelTraffic(uploadBytes = 10, downloadBytes = 20),
            profileId = "profile-3",
        )

        val disconnected = TunnelLifecyclePolicy.disconnected(
            TunnelLifecyclePolicy.beginDisconnect(connected),
        )

        assertEquals(TunnelPhase.Disconnected, disconnected.phase)
        assertEquals(TunnelTraffic(), disconnected.traffic)
        assertEquals("profile-3", disconnected.profileId)
    }
}
