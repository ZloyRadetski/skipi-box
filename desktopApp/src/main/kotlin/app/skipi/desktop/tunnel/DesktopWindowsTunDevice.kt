// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.vpn.TunInterfacePlan

/** What the TUN devices ask of SKIPI Core (the native library behind [DesktopCoreFfmNative]). */
internal interface DesktopTunCore {
    fun prepareTun(interfaceName: String)

    /** Finishes the setup once the session runs on the interface (the routes on Windows). */
    fun attachTun(planJson: String)

    fun closeTun()
}

/**
 * TUN device on Windows. The hev process creates the Wintun adapter, so there is no descriptor to open. SKIPI Core
 * owns everything around it: it binds its own sockets to the physical adapter and removes the adapters an earlier
 * session left behind ([prepare]), installs the routes, the DNS servers and the multicast block once the adapter
 * exists ([attach]), and takes the routes out again and cleans up behind the disconnect ([close]).
 */
internal class DesktopWindowsTunDevice(
    private val core: DesktopTunCore,
) : DesktopTunDevice {
    private var planJson: String? = null

    override fun prepare(interfaceName: String): Result<Unit> = runCatching { core.prepareTun(interfaceName) }

    /** Windows hands over no descriptor: hev opens the adapter itself. The plan is kept for [attach]. */
    override fun open(planJson: String): Result<Int> {
        this.planJson = planJson
        return Result.success(NoDescriptor)
    }

    override fun attach(plan: TunInterfacePlan): Result<Unit> = runCatching {
        core.attachTun(checkNotNull(planJson) { "The TUN device was not opened" })
    }

    override fun close(): Result<Unit> {
        planJson = null
        return runCatching { core.closeTun() }
    }

    private companion object {
        const val NoDescriptor = -1
    }
}
