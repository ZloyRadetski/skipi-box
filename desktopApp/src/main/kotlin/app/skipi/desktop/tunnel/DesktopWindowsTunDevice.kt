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
    private val removeStaleAdapters: (String) -> Unit = { name -> removeStaleWintunAdapters(name) },
) : DesktopTunDevice {
    private var interfaceName: String? = null
    private var cleanup: java.util.concurrent.Future<*>? = null

    /**
     * The clean-up of stale adapters starts here and is awaited in [open], before Core's session starts
     * hev: hev must not create its adapter while a leftover holds the name. It is only a quick listing
     * when nothing is left (see [removeStaleWintunAdapters]).
     */
    override fun prepare(interfaceName: String): Result<Unit> = runCatching {
        this.interfaceName = interfaceName
        cleanup = cleanupExecutor.submit { runCatching { removeStaleAdapters(interfaceName) } }
        core.prepareTun(interfaceName)
    }

    /** Windows hands over no descriptor: hev opens the adapter itself. */
    override fun open(planJson: String): Result<Int> {
        runCatching { cleanup?.get(30, java.util.concurrent.TimeUnit.SECONDS) }
        cleanup = null
        return Result.success(NoDescriptor)
    }

    override fun attach(plan: TunInterfacePlan): Result<Unit> = routes.attach(plan)

    override fun close(): Result<Unit> {
        routes.detach()
        // Removing the dead adapter takes a PowerShell start and a pnputil call (seconds). Nothing waits for it:
        // its routes are already gone, and the next start queues its own clean-up behind this one and awaits it.
        interfaceName?.let { name -> cleanupExecutor.execute { runCatching { removeStaleAdapters(name) } } }
        interfaceName = null
        return runCatching { core.closeTun() }
    }

    private companion object {
        const val NoDescriptor = -1
        val cleanupExecutor: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "skipi-tun-cleanup").apply { isDaemon = true }
        }
    }
}

/**
 * Whether the output of `netsh interface show interface` lists an adapter named like [name] ("skipi0",
 * "skipi0 1", ...). The interface name is the last column, whatever the language of the headings; columns
 * are separated by runs of spaces, a name itself may hold single ones.
 */
internal fun windowsStaleAdapterPresent(netshOutput: String, name: String): Boolean {
    val numbered = Regex(Regex.escape(name) + """ \d+""")
    return netshOutput.lineSequence().any { line ->
        val columns = line.trim().split(Regex("""\s{2,}"""))
        val interfaceName = columns.takeIf { it.size >= 2 }?.last()
        interfaceName != null && (interfaceName == name || numbered.matches(interfaceName))
    }
}

/**
 * Removes Wintun adapters named like [name] ("skipi0", "skipi0 1", ...). Best effort: a failure
 * only means a stale adapter stays in the network connections list.
 *
 * The removal is a PowerShell start and a pnputil call, most of a second, and it holds Core's start
 * back (hev must not create its adapter before the leftovers are gone). A quick `netsh` listing
 * (about a tenth of that) says whether there is anything to remove, so the usual connect, after the
 * previous disconnect cleaned up behind itself, skips it. An unreadable listing runs the cleanup.
 */
internal fun removeStaleWintunAdapters(
    name: String,
    listInterfaces: () -> Result<String> = ::listWindowsInterfaces,
    remove: (String) -> Unit = ::removeWintunAdaptersWithPowerShell,
) {
    require(Regex("[A-Za-z][A-Za-z0-9_-]{0,14}").matches(name)) { "invalid adapter name" }
    val listing = listInterfaces().getOrNull()
    if (listing != null && !windowsStaleAdapterPresent(listing, name)) return
    remove(name)
}

private fun listWindowsInterfaces(): Result<String> =
    runProcessWithTimeout(listOf("netsh", "interface", "show", "interface"), kotlin.time.Duration.parse("10s"))

private fun removeWintunAdaptersWithPowerShell(name: String) {
    val script = "Get-NetAdapter -ErrorAction SilentlyContinue | " +
        "Where-Object { \$_.InterfaceDescription -like 'Wintun*' -and (\$_.Name -eq '$name' -or \$_.Name -like '$name *') } | " +
        "ForEach-Object { pnputil /remove-device \$_.PnPDeviceID | Out-Null }"
    runWindowsCommand(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script))
}
