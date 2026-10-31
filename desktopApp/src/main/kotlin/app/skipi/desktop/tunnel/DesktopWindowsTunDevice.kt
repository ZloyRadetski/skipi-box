// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.vpn.TunInterfacePlan

/**
 * TUN device on Windows. The hev process creates the Wintun adapter, so there is no descriptor to
 * open: Core only binds its own sockets to the physical adapter, and this host installs the routes
 * once the adapter exists.
 *
 * A hev process that is terminated cannot close its Wintun adapter, and Wintun then names the next
 * one "skipi0 1". Leftovers of earlier sessions are therefore removed before a session starts.
 */
internal class DesktopWindowsTunDevice(
    private val core: DesktopCoreFfmNative,
    private val routes: DesktopWindowsTunRoutes = DesktopWindowsTunRoutes(),
    private val removeStaleAdapters: (String) -> Unit = ::removeStaleWintunAdapters,
) : DesktopTunDevice {
    private var interfaceName: String? = null

    override fun prepare(interfaceName: String): Result<Unit> = runCatching {
        this.interfaceName = interfaceName
        removeStaleAdapters(interfaceName)
        core.prepareTun(interfaceName)
    }

    /** Windows hands over no descriptor: hev opens the adapter itself. */
    override fun open(planJson: String): Result<Int> = Result.success(NoDescriptor)

    override fun attach(plan: TunInterfacePlan): Result<Unit> = routes.attach(plan)

    override fun close(): Result<Unit> {
        routes.detach()
        interfaceName?.let { name -> runCatching { removeStaleAdapters(name) } }
        interfaceName = null
        return runCatching { core.closeTun() }
    }

    private companion object {
        const val NoDescriptor = -1
    }
}

/**
 * Removes Wintun adapters named like [name] ("skipi0", "skipi0 1", ...). Best effort: a failure
 * only means a stale adapter stays in the network connections list.
 */
internal fun removeStaleWintunAdapters(name: String) {
    require(Regex("[A-Za-z][A-Za-z0-9_-]{0,14}").matches(name)) { "invalid adapter name" }
    val script = "Get-NetAdapter -ErrorAction SilentlyContinue | " +
        "Where-Object { \$_.InterfaceDescription -like 'Wintun*' -and (\$_.Name -eq '$name' -or \$_.Name -like '$name *') } | " +
        "ForEach-Object { pnputil /remove-device \$_.PnPDeviceID | Out-Null }"
    runWindowsCommand(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script))
}
