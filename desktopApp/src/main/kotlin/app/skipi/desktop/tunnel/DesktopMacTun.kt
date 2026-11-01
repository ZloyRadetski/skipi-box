// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.concurrent.TimeUnit

/*
 * TUN on macOS.
 *
 * hev-socks5-tunnel creates the utun interface itself, and both that and the routes need root. The
 * application is never elevated: when TUN is switched on, the user is asked for the administrator
 * password once (the standard system prompt of `osascript ... with administrator privileges`), and a
 * small supervisor shell script runs as root. The supervisor starts hev, installs the routes and
 * removes them again when the app asks for a stop through a file, when hev ends, or when the app
 * itself is gone, so a crash never leaves the machine routed into a dead tunnel.
 *
 * Core's own sockets stay off the tunnel by IP_BOUND_IF (see skipi-core's netbind), so the
 * supervisor needs no host routes for the proxy servers.
 */

private val MacUtunName = Regex("utun[0-9]{1,4}")
private val MacIpv4Cidr = Regex("((?:[0-9]{1,3}\\.){3}[0-9]{1,3})/([0-9]{1,2})")
private val MacIpv6Cidr = Regex("([0-9A-Fa-f:]{2,39})/([0-9]{1,3})")
private val MacIpv4Address = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")

/** Everything the root supervisor needs; the values end up in a script that runs with full rights. */
internal data class MacTunSupervisorSpec(
    val interfaceName: String,
    val mtu: Int,
    val ipv4Address: String,
    val routes: List<String>,
    /** POSIX paths as text: the script runs on macOS whatever system built it. */
    val hevExecutable: String,
    val hevConfig: String,
    val stopFile: String,
    val readyFile: String,
    val errorFile: String,
    val appPid: Long,
    val userUid: Int,
)

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun validOctets(address: String) = address.split('.').all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }

private fun validateMacRoute(route: String): Boolean {
    MacIpv4Cidr.matchEntire(route)?.let { match ->
        return validOctets(match.groupValues[1]) && match.groupValues[2].toInt() in 0..32
    }
    MacIpv6Cidr.matchEntire(route)?.let { match ->
        return ':' in match.groupValues[1] && match.groupValues[2].toInt() in 0..128
    }
    return false
}

private fun routeCommand(verb: String, route: String): String {
    val family = if (':' in route) "-inet6 " else ""
    return "route -n $verb $family-net ${shellQuote(route)} -interface \"\$IFACE\""
}

/**
 * The supervisor that runs as root. It is POSIX sh and is handed to the shell as base64 text, so
 * nothing in it is subject to a second round of quoting.
 */
internal fun macTunSupervisorScript(spec: MacTunSupervisorSpec): String {
    require(MacUtunName.matches(spec.interfaceName)) { "The macOS tunnel interface must be named utunN" }
    require(spec.routes.isNotEmpty() && spec.routes.all(::validateMacRoute)) { "Invalid tunnel route" }
    require(MacIpv4Address.matches(spec.ipv4Address) && validOctets(spec.ipv4Address)) { "Invalid tunnel address" }
    require(spec.mtu in 576..65_535) { "Invalid tunnel MTU" }
    require(spec.appPid > 0 && spec.userUid >= 0) { "Invalid owner" }

    return buildString {
        appendLine("IFACE=${shellQuote(spec.interfaceName)}")
        appendLine("STOP=${shellQuote(spec.stopFile)}")
        appendLine("READY=${shellQuote(spec.readyFile)}")
        appendLine("ERR=${shellQuote(spec.errorFile)}")
        appendLine("APP_PID=${spec.appPid}")
        appendLine("HEV_PID=")
        appendLine("cleanup() {")
        appendLine("  [ -n \"\$HEV_PID\" ] && kill \"\$HEV_PID\" 2>/dev/null")
        spec.routes.forEach { route -> appendLine("  ${routeCommand("delete", route)} >/dev/null 2>&1") }
        appendLine("  rm -f \"\$READY\"")
        appendLine("}")
        appendLine("fail() {")
        appendLine("  printf '%s' \"\$1\" > \"\$ERR\"")
        appendLine("  chown ${spec.userUid} \"\$ERR\"")
        appendLine("  cleanup")
        appendLine("  exit 1")
        appendLine("}")
        appendLine("trap 'cleanup; rm -f \"\$STOP\"; exit 0' TERM INT HUP")
        appendLine("${shellQuote(spec.hevExecutable)} ${shellQuote(spec.hevConfig)} </dev/null >/dev/null 2>&1 &")
        appendLine("HEV_PID=\$!")
        appendLine("i=0")
        appendLine("until ifconfig \"\$IFACE\" >/dev/null 2>&1; do")
        appendLine("  i=\$((i + 1))")
        appendLine("  [ \"\$i\" -gt 100 ] && fail \"The tunnel interface did not appear\"")
        appendLine("  kill -0 \"\$HEV_PID\" 2>/dev/null || fail \"The tunnel process stopped before the interface appeared\"")
        appendLine("  sleep 0.1")
        appendLine("done")
        appendLine("ifconfig \"\$IFACE\" inet ${shellQuote(spec.ipv4Address)} ${shellQuote(spec.ipv4Address)} mtu ${spec.mtu} up || fail \"Could not configure the tunnel interface\"")
        spec.routes.forEach { route ->
            appendLine("${routeCommand("add", route)} >/dev/null 2>&1 || fail \"Could not add the route $route\"")
        }
        appendLine("echo \$\$ > \"\$READY\"")
        appendLine("chown ${spec.userUid} \"\$READY\"")
        appendLine("while kill -0 \"\$HEV_PID\" 2>/dev/null && kill -0 \"\$APP_PID\" 2>/dev/null && [ ! -e \"\$STOP\" ]; do sleep 1; done")
        appendLine("cleanup")
        appendLine("rm -f \"\$STOP\"")
    }
}

/** The shell command that runs [script] in the background; the script travels as base64. */
internal fun macPrivilegedShellCommand(script: String): String {
    val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_8))
    return "echo $encoded | /usr/bin/base64 -D | /bin/sh >/dev/null 2>&1 &"
}

/** AppleScript that runs [shellCommand] after the system's administrator password prompt. */
internal fun macAdminAppleScript(shellCommand: String): String {
    val escaped = shellCommand.replace("\\", "\\\\").replace("\"", "\\\"")
    return "do shell script \"$escaped\" with administrator privileges"
}

internal fun macPrivilegedLaunchCommand(shellCommand: String): List<String> =
    listOf("/usr/bin/osascript", "-e", macAdminAppleScript(shellCommand))

/** The first free utunN from 9 up, clear of the system's own low numbers and of other VPNs. */
internal fun pickMacTunInterfaceName(existing: Collection<String>, first: Int = 9): String {
    val taken = existing.toSet()
    var number = first
    while ("utun$number" in taken) number += 1
    return "utun$number"
}

internal fun parseMacInterfaceList(output: String): List<String> = output.trim().split(Regex("\\s+")).filter(String::isNotEmpty)

/** The names of all network interfaces, from `ifconfig -l`. */
internal fun macInterfaceNames(): List<String> = runCatching {
    val process = ProcessBuilder("/sbin/ifconfig", "-l").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor(5, TimeUnit.SECONDS)
    parseMacInterfaceList(output)
}.getOrDefault(emptyList())

@Serializable
private data class MacTunPlan(
    val name: String,
    val mtu: Int,
    val addresses: List<String>,
    val routes: List<String>,
)

/** Carries the interface plan from the device (which receives it) to the tunnel (which needs it). */
internal class MacTunPlanHolder {
    @Volatile
    var planJson: String? = null
}

/** Runs one system command line; a non-zero exit is a failure carrying the command's output. */
internal fun runMacPrivilegedLauncher(command: List<String>): Result<Unit> = runCatching {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    check(process.waitFor(120, TimeUnit.SECONDS)) { "The administrator prompt did not finish in time" }
    check(process.exitValue() == 0) { output.ifBlank { "${command.first()} failed" } }
}

/**
 * hev-socks5-tunnel on macOS, run by a root supervisor (see the file comment). [launcher] shows the
 * administrator prompt and starts the supervisor; the app then follows it through marker files that
 * only the supervisor and the app can touch.
 */
internal class DesktopMacHevTunnel(
    private val hevExecutable: Path,
    private val directory: Path,
    private val planJson: () -> String,
    private val appPid: Long = ProcessHandle.current().pid(),
    private val userUid: Int = currentUserUid(),
    private val launcher: (List<String>) -> Result<Unit> = ::runMacPrivilegedLauncher,
    private val isAlive: (Long) -> Boolean = { pid -> ProcessHandle.of(pid).map { it.isAlive }.orElse(false) },
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val readyTimeoutMillis: Long = 30_000,
    private val stopTimeoutMillis: Long = 8_000,
) : DesktopHevTunnel {
    private val readyFile get() = directory.resolve("tun-ready")
    private val errorFile get() = directory.resolve("tun-error")
    private val stopFile get() = directory.resolve("tun-stop")
    private val configFile get() = directory.resolve("tun-hev.yaml")

    @Synchronized
    override fun start(configYaml: String, tunFd: Int): Result<Unit> = runCatching {
        prepareDirectory()
        listOf(readyFile, errorFile, stopFile).forEach(Files::deleteIfExists)
        Files.writeString(configFile, configYaml)
        restrict(configFile)

        val plan = Json.decodeFromString<MacTunPlan>(planJson())
        val spec = MacTunSupervisorSpec(
            interfaceName = plan.name,
            mtu = plan.mtu,
            ipv4Address = plan.addresses.first { ':' !in it }.substringBefore('/'),
            routes = plan.routes,
            hevExecutable = hevExecutable.toString(),
            hevConfig = configFile.toString(),
            stopFile = stopFile.toString(),
            readyFile = readyFile.toString(),
            errorFile = errorFile.toString(),
            appPid = appPid,
            userUid = userUid,
        )
        val command = macPrivilegedLaunchCommand(macPrivilegedShellCommand(macTunSupervisorScript(spec)))
        launcher(command).getOrElse { error -> throw IllegalStateException(describeLaunchFailure(error), error) }
        awaitReady()
    }

    @Synchronized
    override fun stop(): Result<Unit> = runCatching {
        if (!Files.exists(readyFile)) return@runCatching
        Files.writeString(stopFile, "")
        val deadline = now() + stopTimeoutMillis
        while (Files.exists(readyFile)) {
            check(now() < deadline) { "The tunnel did not stop in time; its routes may still be installed" }
            sleep(PollMillis)
        }
    }

    override fun isRunning(): Boolean {
        if (Files.exists(stopFile)) return false
        val pid = runCatching { Files.readString(readyFile).trim().toLongOrNull() }.getOrNull() ?: return false
        return isAlive(pid)
    }

    private fun awaitReady() {
        val deadline = now() + readyTimeoutMillis
        while (true) {
            if (Files.exists(errorFile)) {
                val reason = Files.readString(errorFile).trim().ifBlank { "The tunnel could not be started" }
                throw IllegalStateException(reason)
            }
            if (Files.exists(readyFile)) return
            if (now() >= deadline) {
                // Ask the supervisor to clean up whatever it did get done.
                runCatching { Files.writeString(stopFile, "") }
                throw IllegalStateException("The tunnel did not become ready in time")
            }
            sleep(PollMillis)
        }
    }

    private fun describeLaunchFailure(error: Throwable): String {
        val message = error.message.orEmpty()
        return if ("User canceled" in message || "-128" in message) {
            "Administrator permission was not granted, so TUN mode cannot start"
        } else {
            "Could not start the privileged tunnel: ${message.ifBlank { "unknown error" }}"
        }
    }

    private fun prepareDirectory() {
        Files.createDirectories(directory)
        restrict(directory, directoryMode = true)
    }

    private fun restrict(path: Path, directoryMode: Boolean = false) {
        runCatching {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(if (directoryMode) "rwx------" else "rw-------"))
        }
    }

    private companion object {
        const val PollMillis = 100L
    }
}

/** The TUN device on macOS: Core binds its own sockets; the interface and routes belong to the supervisor. */
internal class DesktopMacTunDevice(
    private val core: DesktopCoreFfmNative,
    private val plan: MacTunPlanHolder,
) : DesktopTunDevice {
    override fun prepare(interfaceName: String): Result<Unit> = runCatching { core.prepareTun(interfaceName) }

    /** macOS hands over no descriptor: hev opens the utun interface itself. */
    override fun open(planJson: String): Result<Int> {
        plan.planJson = planJson
        return Result.success(NoDescriptor)
    }

    override fun close(): Result<Unit> = runCatching {
        plan.planJson = null
        core.closeTun()
    }

    private companion object {
        const val NoDescriptor = -1
    }
}

private fun currentUserUid(): Int = runCatching {
    (Files.getAttribute(Path.of(System.getProperty("user.home")), "unix:uid") as Int)
}.getOrDefault(0)

/** Where the supervisor's markers and the generated hev configuration live. */
internal fun macTunRunDirectory(): Path = Path.of(System.getProperty("user.home"), "SKIPI", "run")
