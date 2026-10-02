// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

/** Pure lifecycle decisions shared by platform tunnel adapters. */
object TunnelLifecyclePolicy {
    fun beginConnect(snapshot: TunnelSnapshot, profileId: String): TunnelLifecycleDecision {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isEmpty()) {
            return TunnelLifecycleDecision.Rejected(
                failure("profile_required", "A tunnel profile must be selected", TunnelOperationStage.ResolveProfile),
            )
        }
        if (snapshot.phase == TunnelPhase.Connected || snapshot.phase == TunnelPhase.Connecting) {
            return TunnelLifecycleDecision.Rejected(
                failure("already_running", "Tunnel is already running", TunnelOperationStage.Start),
            )
        }
        if (snapshot.phase == TunnelPhase.Disconnecting) {
            return TunnelLifecycleDecision.Rejected(
                failure("transition_in_progress", "Tunnel is currently disconnecting", TunnelOperationStage.Start),
            )
        }
        return TunnelLifecycleDecision.Accepted(
            snapshot.copy(
                phase = TunnelPhase.Connecting,
                traffic = TunnelTraffic(),
                failure = null,
                profileId = normalizedProfileId,
            ),
        )
    }

    fun connected(snapshot: TunnelSnapshot, traffic: TunnelTraffic = snapshot.traffic): TunnelSnapshot =
        snapshot.copy(phase = TunnelPhase.Connected, traffic = traffic, failure = null)

    fun connectFailed(snapshot: TunnelSnapshot, failure: TunnelFailure): TunnelSnapshot =
        snapshot.copy(phase = TunnelPhase.Failed, failure = failure)

    fun beginDisconnect(snapshot: TunnelSnapshot): TunnelSnapshot =
        snapshot.copy(phase = TunnelPhase.Disconnecting, failure = null)

    fun disconnected(snapshot: TunnelSnapshot): TunnelSnapshot = TunnelSnapshot(
        phase = TunnelPhase.Disconnected,
        profileId = snapshot.profileId,
    )

    fun disconnectFailed(snapshot: TunnelSnapshot, failure: TunnelFailure): TunnelSnapshot =
        snapshot.copy(phase = TunnelPhase.Failed, failure = failure)

    fun failure(
        error: Throwable,
        stage: TunnelOperationStage,
        code: String,
        recoverable: Boolean = true,
        platformCode: String? = null,
    ): TunnelFailure = (error as? TunnelOperationException)?.failure ?: TunnelFailure(
        code = code,
        message = error.message ?: error::class.simpleName.orEmpty(),
        recoverable = recoverable,
        stage = stage,
        platformCode = platformCode,
    )

    private fun failure(code: String, message: String, stage: TunnelOperationStage) = TunnelFailure(
        code = code,
        message = message,
        recoverable = false,
        stage = stage,
    )
}

sealed interface TunnelLifecycleDecision {
    data class Accepted(val snapshot: TunnelSnapshot) : TunnelLifecycleDecision
    data class Rejected(val failure: TunnelFailure) : TunnelLifecycleDecision
}
