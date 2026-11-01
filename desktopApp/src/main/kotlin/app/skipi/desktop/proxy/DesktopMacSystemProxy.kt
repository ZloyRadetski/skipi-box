// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

/** One entry of `networksetup -listallnetworkservices`. A disabled service is listed with a leading `*`. */
internal data class MacNetworkService(val name: String, val enabled: Boolean)

/** One proxy kind (web, secure web or SOCKS) of one network service, as `networksetup -get…proxy` reports it. */
@Serializable
internal data class MacProxyState(val enabled: Boolean, val host: String, val port: Int)

internal fun parseMacNetworkServices(output: String): List<MacNetworkService> = output.lineSequence()
    .map(String::trimEnd)
    .filter(String::isNotBlank)
    // The first line is a notice, not a service.
    .filterNot { line -> line.startsWith("An asterisk (*)") }
    .map { line ->
        if (line.startsWith("*")) MacNetworkService(line.removePrefix("*"), enabled = false) else MacNetworkService(line, enabled = true)
    }
    .toList()

internal fun parseMacProxyState(output: String): MacProxyState? {
    val fields = output.lineSequence()
        .mapNotNull { line -> line.substringBefore(':', "").trim().takeIf(String::isNotEmpty)?.let { it to line.substringAfter(':').trim() } }
        .toMap()
    val enabled = fields["Enabled"] ?: return null
    val port = fields["Port"]?.toIntOrNull() ?: return null
    return MacProxyState(enabled = enabled.equals("Yes", ignoreCase = true), host = fields["Server"].orEmpty(), port = port)
}

internal fun parseMacBypassDomains(output: String): List<String> = output.lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .filterNot { line -> line.startsWith("There aren't any bypass domains") }
    .toList()

private val MacRequiredBypassDomains = listOf("localhost", "127.0.0.1", "::1")

/** The user's own bypass list plus loopback; nothing is listed twice. */
internal fun mergeMacBypassDomains(existing: List<String>): List<String> =
    (existing + MacRequiredBypassDomains).distinctBy { entry -> entry.lowercase() }

@Serializable
internal data class MacServiceProxySnapshot(
    val service: String,
    val web: MacProxyState,
    val secureWeb: MacProxyState,
    val socks: MacProxyState,
    val bypass: List<String>,
)

@Serializable
internal data class DesktopMacSystemProxyLease(
    val version: Int = LeaseVersion,
    val phase: DesktopSystemProxyLeasePhase,
    val ownerToken: String,
    val endpoints: DesktopSystemProxyEndpoints,
    val services: List<MacServiceProxySnapshot>,
) {
    companion object {
        const val LeaseVersion = 1
    }
}

object DesktopMacSystemProxyLeasePaths {
    fun defaultPath(): Path = Path.of(System.getProperty("user.home"), "SKIPI", "mac-system-proxy-lease.json")
}

/**
 * macOS system proxy through `networksetup`: every enabled network service gets the web, secure web
 * and SOCKS proxy, and the settings each service had are written back on release, after a crash
 * (the lease file) or by [forceClear].
 */
class DesktopMacSystemProxyLeaseManager(
    private val leasePath: Path = DesktopMacSystemProxyLeasePaths.defaultPath(),
    private val commandRunner: DesktopLinuxCommandRunner = ProcessDesktopLinuxCommandRunner,
) : DesktopSystemProxyManager {

    private val supported: Boolean by lazy {
        commandRunner.run(Networksetup, listOf("-listallnetworkservices")).map { it.exitCode == 0 }.getOrDefault(false)
    }

    override fun isSupportedHost(): Boolean = supported

    override fun acquire(endpoints: DesktopSystemProxyEndpoints): Result<DesktopSystemProxyLeaseResult> =
        withExclusiveLeaseLock {
            val existing = DesktopMacSystemProxyLeaseStore.load(leasePath).getOrThrow()
            if (existing != null) {
                if (existing.phase == DesktopSystemProxyLeasePhase.Active && existing.endpoints == endpoints) {
                    return@withExclusiveLeaseLock DesktopSystemProxyLeaseResult(
                        action = DesktopSystemProxyLeaseAction.AlreadyAcquired,
                        message = "macOS system proxy is already owned by SKIPI.",
                    )
                }
                restore(existing.services).getOrThrow()
            }

            val snapshots = readSnapshots().getOrThrow()
            val prepared = DesktopMacSystemProxyLease(
                phase = DesktopSystemProxyLeasePhase.Prepared,
                ownerToken = UUID.randomUUID().toString(),
                endpoints = endpoints,
                services = snapshots,
            )
            DesktopMacSystemProxyLeaseStore.save(leasePath, prepared).getOrThrow()
            snapshots.forEach { snapshot -> apply(snapshot, endpoints).getOrThrow() }
            DesktopMacSystemProxyLeaseStore.save(leasePath, prepared.copy(phase = DesktopSystemProxyLeasePhase.Active)).getOrThrow()

            DesktopSystemProxyLeaseResult(
                action = DesktopSystemProxyLeaseAction.Acquired,
                message = "Системный прокси macOS активирован.",
            ).also {
                DesktopLogger.info("SystemProxy", "Acquired macOS system proxy (HTTP ${endpoints.httpPort}, SOCKS ${endpoints.socksPort})")
            }
        }

    override fun release(): Result<DesktopSystemProxyLeaseResult> = withExclusiveLeaseLock {
        val lease = DesktopMacSystemProxyLeaseStore.load(leasePath).getOrThrow()
            ?: return@withExclusiveLeaseLock DesktopSystemProxyLeaseResult(
                action = DesktopSystemProxyLeaseAction.NothingToRelease,
                message = "Нет активной аренды системного прокси macOS.",
            )
        restore(lease.services).getOrThrow()
        DesktopMacSystemProxyLeaseStore.clear(leasePath).getOrThrow()
        DesktopSystemProxyLeaseResult(
            action = DesktopSystemProxyLeaseAction.Released,
            message = "Системный прокси macOS возвращен в исходное состояние.",
        ).also { DesktopLogger.info("SystemProxy", "Released macOS system proxy") }
    }

    override fun recover(): Result<DesktopSystemProxyLeaseResult> = withExclusiveLeaseLock {
        val lease = DesktopMacSystemProxyLeaseStore.load(leasePath).getOrThrow()
            ?: return@withExclusiveLeaseLock DesktopSystemProxyLeaseResult(
                action = DesktopSystemProxyLeaseAction.NothingToRelease,
                message = "Нет сохраненной аренды для восстановления.",
            )
        restore(lease.services).getOrThrow()
        DesktopMacSystemProxyLeaseStore.clear(leasePath).getOrThrow()
        DesktopSystemProxyLeaseResult(
            action = DesktopSystemProxyLeaseAction.Recovered,
            message = "Восстановлены системные настройки прокси macOS после предыдущей сессии.",
        ).also { DesktopLogger.info("SystemProxy", "Recovered macOS system proxy from previous session") }
    }

    override fun forceClear(): Result<Unit> = withExclusiveLeaseLock {
        listServices().getOrNull().orEmpty().filter(MacNetworkService::enabled).forEach { service ->
            ProxyStateCommands.forEach { command -> run(command, service.name, "off").getOrNull() }
        }
        DesktopMacSystemProxyLeaseStore.clear(leasePath).getOrNull()
    }.map { }

    private fun listServices(): Result<List<MacNetworkService>> = runCatching {
        val result = commandRunner.run(Networksetup, listOf("-listallnetworkservices")).getOrThrow()
        check(result.exitCode == 0) { "networksetup -listallnetworkservices failed: ${result.output.trim()}" }
        parseMacNetworkServices(result.output)
    }

    /** A service whose settings cannot be read (a VPN pseudo-service, one that just vanished) is left out. */
    private fun readSnapshots(): Result<List<MacServiceProxySnapshot>> = runCatching {
        listServices().getOrThrow().filter(MacNetworkService::enabled).mapNotNull { service ->
            runCatching {
                MacServiceProxySnapshot(
                    service = service.name,
                    web = readProxy("-getwebproxy", service.name),
                    secureWeb = readProxy("-getsecurewebproxy", service.name),
                    socks = readProxy("-getsocksfirewallproxy", service.name),
                    bypass = readBypass(service.name),
                )
            }.getOrNull()
        }
    }

    private fun readProxy(command: String, service: String): MacProxyState {
        val result = commandRunner.run(Networksetup, listOf(command, service)).getOrThrow()
        check(result.exitCode == 0) { "networksetup $command $service failed: ${result.output.trim()}" }
        return checkNotNull(parseMacProxyState(result.output)) { "Unreadable proxy report for $service" }
    }

    private fun readBypass(service: String): List<String> {
        val result = commandRunner.run(Networksetup, listOf("-getproxybypassdomains", service)).getOrThrow()
        check(result.exitCode == 0) { "networksetup -getproxybypassdomains $service failed: ${result.output.trim()}" }
        return parseMacBypassDomains(result.output)
    }

    private fun apply(snapshot: MacServiceProxySnapshot, endpoints: DesktopSystemProxyEndpoints): Result<Unit> = runCatching {
        run("-setwebproxy", snapshot.service, endpoints.host, endpoints.httpPort.toString()).getOrThrow()
        run("-setsecurewebproxy", snapshot.service, endpoints.host, endpoints.httpPort.toString()).getOrThrow()
        run("-setsocksfirewallproxy", snapshot.service, endpoints.host, endpoints.socksPort.toString()).getOrThrow()
        setBypass(snapshot.service, mergeMacBypassDomains(snapshot.bypass)).getOrThrow()
    }

    private fun restore(snapshots: List<MacServiceProxySnapshot>): Result<Unit> = runCatching {
        val present = listServices().getOrThrow().map(MacNetworkService::name).toSet()
        snapshots.filter { snapshot -> snapshot.service in present }.forEach { snapshot ->
            // The endpoint goes back even when the proxy was off: the host and port are the user's too.
            restoreKind("web", snapshot.service, snapshot.web)
            restoreKind("secureweb", snapshot.service, snapshot.secureWeb)
            restoreKind("socksfirewall", snapshot.service, snapshot.socks)
            setBypass(snapshot.service, snapshot.bypass).getOrThrow()
        }
    }

    private fun restoreKind(kind: String, service: String, state: MacProxyState) {
        run("-set${kind}proxy", service, state.host, state.port.toString()).getOrThrow()
        run("-set${kind}proxystate", service, if (state.enabled) "on" else "off").getOrThrow()
    }

    private fun setBypass(service: String, domains: List<String>): Result<Unit> =
        run("-setproxybypassdomains", service, *(domains.ifEmpty { listOf("Empty") }).toTypedArray())

    private fun run(command: String, vararg arguments: String): Result<Unit> = runCatching {
        val result = commandRunner.run(Networksetup, listOf(command) + arguments).getOrThrow()
        check(result.exitCode == 0) { "networksetup $command ${arguments.firstOrNull().orEmpty()} failed: ${result.output.trim()}" }
    }

    private fun <T> withExclusiveLeaseLock(block: () -> T): Result<T> = runCatching {
        leasePath.parent?.let(Files::createDirectories)
        val lockPath = leasePath.resolveSibling("${leasePath.fileName}.lock")
        FileChannel.open(lockPath, CREATE, WRITE).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            checkNotNull(lock) { "Another SKIPI process is changing the macOS system proxy" }
            lock.use { block() }
        }
    }

    private companion object {
        const val Networksetup = "networksetup"
        val ProxyStateCommands = listOf("-setwebproxystate", "-setsecurewebproxystate", "-setsocksfirewallproxystate")
    }
}

internal object DesktopMacSystemProxyLeaseStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(path: Path): Result<DesktopMacSystemProxyLease?> = runCatching {
        if (!Files.exists(path)) return@runCatching null
        val content = Files.readString(path, StandardCharsets.UTF_8).trim()
        if (content.isBlank()) return@runCatching null
        val lease = json.decodeFromString<DesktopMacSystemProxyLease>(content)
        require(lease.version == DesktopMacSystemProxyLease.LeaseVersion) { "Unsupported macOS system proxy lease format" }
        lease
    }

    fun save(path: Path, lease: DesktopMacSystemProxyLease): Result<Unit> = runCatching {
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(lease), StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)
        try {
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, REPLACE_EXISTING)
        }
    }

    fun clear(path: Path): Result<Unit> = runCatching { Files.deleteIfExists(path) }.map { }
}
