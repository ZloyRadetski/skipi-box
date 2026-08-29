// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

/** Platform-independent tunnel lifecycle exposed to shared application logic. */
interface TunnelController {
    suspend fun connect(request: TunnelConnectRequest): Result<Unit>

    suspend fun disconnect(): Result<Unit>

    suspend fun snapshot(): TunnelSnapshot

    fun supports(capability: TunnelCapability): Boolean
}

data class TunnelConnectRequest(
    /** Stable ID of the selected profile; profile parsing remains in shared config code. */
    val profileId: String,
)

data class TunnelSnapshot(
    val phase: TunnelPhase = TunnelPhase.Disconnected,
    val traffic: TunnelTraffic = TunnelTraffic(),
    val failure: TunnelFailure? = null,
)

data class TunnelTraffic(
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
)

data class TunnelFailure(
    val code: String,
    val message: String,
    val recoverable: Boolean,
)

enum class TunnelPhase {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
    Failed,
}

enum class TunnelCapability {
    SystemProxy,
    Tun,
    SplitTunneling,
    KillSwitch,
    BackgroundExecution,
}
