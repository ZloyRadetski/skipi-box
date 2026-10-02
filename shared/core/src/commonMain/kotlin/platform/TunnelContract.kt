// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

/** Platform-independent tunnel lifecycle exposed to shared application logic. */
interface TunnelController {
    suspend fun connect(request: TunnelConnectRequest): Result<Unit>

    suspend fun disconnect(): Result<Unit>

    suspend fun snapshot(): TunnelSnapshot

    fun capabilities(): Set<TunnelCapability>

    fun supports(capability: TunnelCapability): Boolean = capability in capabilities()
}

data class TunnelConnectRequest(
    /** Stable ID selected by the user; platform adapters resolve it against their profile store. */
    val profileId: String,
    /** Optional already-built configuration for runtimes that accept a shared prepared document. */
    val configuration: TunnelConfiguration? = null,
)

data class TunnelConfiguration(
    val content: String,
    val format: TunnelConfigurationFormat = TunnelConfigurationFormat.Json,
)

enum class TunnelConfigurationFormat {
    Json,
}

data class TunnelSnapshot(
    val phase: TunnelPhase = TunnelPhase.Disconnected,
    val traffic: TunnelTraffic = TunnelTraffic(),
    val failure: TunnelFailure? = null,
    val profileId: String? = null,
)

data class TunnelTraffic(
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
)

data class TunnelFailure(
    /** Stable shared reason code, suitable for UI and telemetry branching. */
    val code: String,
    val message: String,
    val recoverable: Boolean,
    val stage: TunnelOperationStage? = null,
    /** Optional platform/native code retained for diagnostics, never used for shared branching. */
    val platformCode: String? = null,
)

enum class TunnelOperationStage {
    ResolveProfile,
    PrepareConfiguration,
    Start,
    Stop,
    ReadStatus,
    ReadTraffic,
    AcquireSystemProxy,
}

/** Keeps the Result-based API while making failures machine-readable across platforms. */
class TunnelOperationException(
    val failure: TunnelFailure,
) : IllegalStateException(failure.message)

enum class TunnelPhase {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
    Failed,
}

enum class TunnelCapability {
    PreparedConfiguration,
    SystemProxy,
    Tun,
    SplitTunneling,
    KillSwitch,
    BackgroundExecution,
}
