// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.vpn.TunInterfacePlan
import java.util.concurrent.TimeUnit

private const val RouteMetric = 1

private fun TunInterfacePlan.routePrefixes(): List<Pair<String, String>> = routes.map { route ->
    val family = if (':' in route.address) "ipv6" else "ipv4"
    family to "${route.address}/${route.prefixLength}"
}

/**
 * The routes that send the system's traffic into the tunnel adapter, on link (the adapter has no
 * gateway) and only for this session: `store=active` keeps them out of the persistent route table.
 */
internal fun windowsTunAddRouteCommands(plan: TunInterfacePlan): List<List<String>> =
    plan.routePrefixes().map { (family, prefix) ->
        listOf(
            "netsh", "interface", family, "add", "route",
            "prefix=$prefix", "interface=${plan.name}",
            "nexthop=${if (family == "ipv6") "::" else "0.0.0.0"}",
            "metric=$RouteMetric", "store=active",
        )
    }

private const val MulticastBlockRuleName = "SKIPI TUN multicast block"

/**
 * Windows sends SSDP, LLMNR and mDNS discovery to every adapter, so a new tunnel adapter received
 * about a thousand UDP flows within seconds. hev serves at most 1023 sessions at once and then fails
 * every new socket, TCP included: the tunnel was up and carried no traffic. Multicast and broadcast
 * never belong in the tunnel, so they are blocked on this adapter only; the LAN keeps its discovery.
 */
internal fun windowsTunMulticastBlockCommand(plan: TunInterfacePlan): List<String> {
    require(Regex("[A-Za-z][A-Za-z0-9_-]{0,14}").matches(plan.name)) { "invalid adapter name" }
    return powershellCommand(
        "Remove-NetFirewallRule -DisplayName '$MulticastBlockRuleName' -ErrorAction SilentlyContinue; " +
            "New-NetFirewallRule -DisplayName '$MulticastBlockRuleName' -Direction Outbound -Action Block " +
            "-InterfaceAlias '${plan.name}' -RemoteAddress 224.0.0.0/4,255.255.255.255,ff00::/8 | Out-Null",
    )
}

internal fun windowsTunMulticastUnblockCommand(): List<String> =
    powershellCommand("Remove-NetFirewallRule -DisplayName '$MulticastBlockRuleName' -ErrorAction SilentlyContinue")

private val DnsIpv4 = Regex("""(\d{1,3}\.){3}\d{1,3}""")
private val DnsIpv6 = Regex("""[0-9A-Fa-f:]*:[0-9A-Fa-f:.]*""")

/**
 * Windows keeps the DNS servers of the physical adapter, and its DNS client sends a query out of the
 * adapter that owns the server, so a tunnel adapter without DNS never resolves through the tunnel and
 * Core's DNS, which answers port 53 inside it, is never asked. The adapter gets the plan's DNS servers
 * and the lowest interface metric (the order the DNS client ranks adapters by). Null when the plan has
 * no DNS: the system keeps its own resolvers. Only IP addresses are accepted into the script.
 */
internal fun windowsTunDnsCommand(plan: TunInterfacePlan): List<String>? {
    require(Regex("[A-Za-z][A-Za-z0-9_-]{0,14}").matches(plan.name)) { "invalid adapter name" }
    val servers = plan.dnsServers.map(String::trim).filter { DnsIpv4.matches(it) || DnsIpv6.matches(it) }
    if (servers.isEmpty()) return null
    val addresses = servers.joinToString(",") { "'$it'" }
    return powershellCommand(
        "\$ErrorActionPreference = 'Stop'; " +
            "Set-DnsClientServerAddress -InterfaceAlias '${plan.name}' -ServerAddresses ($addresses); " +
            "Set-NetIPInterface -InterfaceAlias '${plan.name}' -InterfaceMetric $RouteMetric -ErrorAction SilentlyContinue; " +
            "Clear-DnsClientCache",
    )
}

/** Drops the answers cached while the tunnel resolved, so the system's own resolvers are asked again. */
internal fun windowsTunDnsFlushCommand(): List<String> = powershellCommand("Clear-DnsClientCache")

private fun powershellCommand(script: String) =
    listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)

internal fun windowsTunDeleteRouteCommands(plan: TunInterfacePlan): List<List<String>> =
    plan.routePrefixes().map { (family, prefix) ->
        listOf(
            "netsh", "interface", family, "delete", "route",
            "prefix=$prefix", "interface=${plan.name}", "store=active",
        )
    }

/**
 * Installs and removes the tunnel's routes. The adapter is created by the hev process, so it may
 * not exist yet when this is asked to attach; [attempts] polls for it first.
 *
 * Every command is its own process (netsh, PowerShell), and run one after the other they were most of
 * the seconds a connect or a disconnect took. They are independent, so with [parallel] they run at once:
 * the routes, the DNS and the firewall rule on the way up; the route deletions on the way down, with the
 * firewall rule and the DNS cache cleaned behind the disconnect through [background].
 */
internal class DesktopWindowsTunRoutes(
    private val run: (List<String>) -> Result<Unit> = ::runWindowsCommand,
    private val adapterExists: (String) -> Boolean = ::windowsAdapterExists,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val attempts: Int = 200,
    private val pollMillis: Long = 25,
    private val parallel: Boolean = true,
    background: ((() -> Unit) -> Unit)? = null,
) {
    private val background: (() -> Unit) -> Unit = background
        ?: if (parallel) ({ task -> routeExecutor.execute { runCatching(task) } }) else ({ task -> task() })
    private var attached: TunInterfacePlan? = null
    private var dnsApplied = false

    @Synchronized
    fun attach(plan: TunInterfacePlan): Result<Unit> {
        if (!awaitAdapter(plan.name)) {
            return Result.failure(IllegalStateException("The TUN adapter ${plan.name} did not appear"))
        }
        val routeCommands = windowsTunAddRouteCommands(plan)
        val dnsCommand = windowsTunDnsCommand(plan)
        dnsApplied = dnsCommand != null
        // Best effort: without its DNS the tunnel still carries traffic, names just resolve outside it; without
        // the block rule it still works, it only stays open to a discovery storm.
        val extras = listOfNotNull(dnsCommand, windowsTunMulticastBlockCommand(plan))
        val results = runAll(routeCommands.map { command -> { run(command) } })
        val failed = results.firstOrNull { it.isFailure }
        if (failed != null) {
            // Undo what was installed so a half-routed tunnel never carries part of the traffic.
            runAll(windowsTunDeleteRouteCommands(plan).map { command -> { run(command) } })
            run(windowsTunMulticastUnblockCommand())
            return Result.failure(failed.exceptionOrNull() ?: IllegalStateException("netsh failed"))
        }
        // Each of these is a PowerShell start (half a second or more) and none of them decides whether the tunnel
        // carries traffic, so the connect does not wait: a discovery storm needs seconds to fill hev's sessions,
        // and the first lookups of a second only go out of the old resolvers.
        extras.forEach { command -> background { run(command) } }
        attached = plan
        return Result.success(Unit)
    }

    @Synchronized
    fun detach(): Result<Unit> {
        val plan = attached ?: return Result.success(Unit)
        attached = null
        // A route that is already gone (the adapter was removed first) is not an error. Without its routes the
        // adapter no longer takes the system's traffic, so only this is waited for.
        runAll(windowsTunDeleteRouteCommands(plan).map { command -> { run(command) } })
        val flush = dnsApplied
        dnsApplied = false
        background {
            run(windowsTunMulticastUnblockCommand())
            if (flush) run(windowsTunDnsFlushCommand())
        }
        return Result.success(Unit)
    }

    private fun runAll(tasks: List<() -> Result<Unit>>): List<Result<Unit>> {
        if (!parallel || tasks.size < 2) return tasks.map { it() }
        val futures = tasks.map { task -> routeExecutor.submit<Result<Unit>> { task() } }
        return futures.map { future ->
            runCatching { future.get() }.getOrElse { error -> Result.failure(error) }
        }
    }

    private fun awaitAdapter(name: String): Boolean {
        repeat(attempts) { attempt ->
            if (adapterExists(name)) return true
            if (attempt < attempts - 1) sleep(pollMillis)
        }
        return false
    }

    private companion object {
        val routeExecutor: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newCachedThreadPool { task ->
            Thread(task, "skipi-tun-route").apply { isDaemon = true }
        }
    }
}

/** Runs one system command without a console window; a non-zero exit is a failure with its output. */
internal fun runWindowsCommand(command: List<String>): Result<Unit> =
    runProcessWithTimeout(command, kotlin.time.Duration.parse("30s"))
        .map { }
        .recoverCatching { error ->
            if (error is DesktopProcessFailure) {
                throw IllegalStateException("${command.take(4).joinToString(" ")} failed: ${error.output}", error)
            }
            throw error
        }

/** Whether an adapter with this name is known to Windows. */
internal fun windowsAdapterExists(name: String): Boolean =
    runWindowsCommand(listOf("netsh", "interface", "show", "interface", "name=$name")).isSuccess
