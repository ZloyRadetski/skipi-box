// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.serialization.Serializable

@Serializable
data class DesktopSystemProxyEndpoints(
    val host: String = LoopbackHost,
    val httpPort: Int,
    val socksPort: Int,
) {
    init {
        require(host == LoopbackHost) { "System proxy must use $LoopbackHost" }
        require(httpPort in 1..65_535) { "HTTP proxy port must be in 1..65535" }
        require(socksPort in 1..65_535) { "SOCKS proxy port must be in 1..65535" }
    }

    companion object {
        const val LoopbackHost = "127.0.0.1"
    }
}

enum class DesktopSystemProxyLeaseAction {
    Acquired,
    AlreadyAcquired,
    Released,
    NothingToRelease,
    Recovered,
    SkippedNotOwner,
}

data class DesktopSystemProxyLeaseResult(
    val action: DesktopSystemProxyLeaseAction,
    val refreshApplied: Boolean = true,
    val message: String = "",
)

@Serializable
enum class DesktopSystemProxyLeasePhase {
    Prepared,
    Active,
}

interface DesktopSystemProxyManager {
    fun isSupportedHost(): Boolean
    fun acquire(endpoints: DesktopSystemProxyEndpoints): Result<DesktopSystemProxyLeaseResult>
    fun release(): Result<DesktopSystemProxyLeaseResult>
    fun recover(): Result<DesktopSystemProxyLeaseResult>
    fun forceClear(): Result<Unit>
}

internal fun isDesktopSystemProxySupported(
    osName: String = System.getProperty("os.name"),
): Boolean = osName.startsWith("Windows", ignoreCase = true) || osName.startsWith("Linux", ignoreCase = true)

object DesktopSystemProxyManagers {
    fun create(
        osName: String = System.getProperty("os.name"),
    ): DesktopSystemProxyManager = when {
        osName.startsWith("Windows", ignoreCase = true) -> DesktopWindowsSystemProxyLeaseManager()
        osName.startsWith("Linux", ignoreCase = true) -> DesktopLinuxSystemProxyLeaseManager()
        else -> NoOpDesktopSystemProxyManager
    }
}

object NoOpDesktopSystemProxyManager : DesktopSystemProxyManager {
    override fun isSupportedHost(): Boolean = false
    override fun acquire(endpoints: DesktopSystemProxyEndpoints): Result<DesktopSystemProxyLeaseResult> =
        Result.success(DesktopSystemProxyLeaseResult(DesktopSystemProxyLeaseAction.Acquired))

    override fun release(): Result<DesktopSystemProxyLeaseResult> =
        Result.success(DesktopSystemProxyLeaseResult(DesktopSystemProxyLeaseAction.Released))

    override fun recover(): Result<DesktopSystemProxyLeaseResult> =
        Result.success(DesktopSystemProxyLeaseResult(DesktopSystemProxyLeaseAction.NothingToRelease))

    override fun forceClear(): Result<Unit> = Result.success(Unit)
}
