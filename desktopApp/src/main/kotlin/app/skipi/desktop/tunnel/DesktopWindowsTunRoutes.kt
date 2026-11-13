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
 */
internal class DesktopWindowsTunRoutes(
    private val run: (List<String>) -> Result<Unit> = ::runWindowsCommand,
    private val adapterExists: (String) -> Boolean = ::windowsAdapterExists,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val attempts: Int = 200,
    private val pollMillis: Long = 25,
) {
    private var attached: TunInterfacePlan? = null

    @Synchronized
    fun attach(plan: TunInterfacePlan): Result<Unit> {
        if (!awaitAdapter(plan.name)) {
            return Result.failure(IllegalStateException("The TUN adapter ${plan.name} did not appear"))
        }
        for (command in windowsTunAddRouteCommands(plan)) {
            val result = run(command)
            if (result.isFailure) {
                // Undo what was installed so a half-routed tunnel never carries part of the traffic.
                windowsTunDeleteRouteCommands(plan).forEach { run(it) }
                return Result.failure(result.exceptionOrNull() ?: IllegalStateException("netsh failed"))
            }
        }
        // Best effort: without the rule the tunnel still works, it only stays open to a discovery storm.
        run(windowsTunMulticastBlockCommand(plan))
        attached = plan
        return Result.success(Unit)
    }

    @Synchronized
    fun detach(): Result<Unit> {
        val plan = attached ?: return Result.success(Unit)
        attached = null
        // A route that is already gone (the adapter was removed first) is not an error.
        windowsTunDeleteRouteCommands(plan).forEach { run(it) }
        run(windowsTunMulticastUnblockCommand())
        return Result.success(Unit)
    }

    private fun awaitAdapter(name: String): Boolean {
        repeat(attempts) { attempt ->
            if (adapterExists(name)) return true
            if (attempt < attempts - 1) sleep(pollMillis)
        }
        return false
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
