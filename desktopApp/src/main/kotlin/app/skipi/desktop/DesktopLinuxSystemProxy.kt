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
import java.util.concurrent.TimeUnit

fun interface DesktopLinuxCommandRunner {
    fun run(executable: String, arguments: List<String>): Result<DesktopLinuxCommandResult>
}

data class DesktopLinuxCommandResult(
    val exitCode: Int,
    val output: String = "",
)

object ProcessDesktopLinuxCommandRunner : DesktopLinuxCommandRunner {
    override fun run(executable: String, arguments: List<String>): Result<DesktopLinuxCommandResult> = runCatching {
        val process = ProcessBuilder(listOf(executable) + arguments)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(CommandTimeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(CommandTimeoutSeconds, TimeUnit.SECONDS)
            error("$executable did not finish within $CommandTimeoutSeconds seconds")
        }
        DesktopLinuxCommandResult(
            exitCode = process.exitValue(),
            output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
        )
    }

    private const val CommandTimeoutSeconds = 10L
}

@Serializable
data class DesktopLinuxSystemProxySnapshot(
    val gnomeMode: String = "none",
    val gnomeHttpHost: String = "",
    val gnomeHttpPort: Int = 0,
    val gnomeHttpEnabled: Boolean = false,
    val gnomeHttpsHost: String = "",
    val gnomeHttpsPort: Int = 0,
    val gnomeSocksHost: String = "",
    val gnomeSocksPort: Int = 0,
    val gnomeIgnoreHosts: String = "['localhost', '127.0.0.0/8', '::1']",
    val kdeProxyType: String? = null,
    val kdeHttpProxy: String? = null,
    val kdeHttpsProxy: String? = null,
    val kdeSocksProxy: String? = null,
    val kdeNoProxyFor: String? = null,
)

@Serializable
data class DesktopLinuxSystemProxyLease(
    val version: Int = LeaseVersion,
    val phase: DesktopSystemProxyLeasePhase,
    val ownerToken: String,
    val endpoints: DesktopSystemProxyEndpoints,
    val snapshot: DesktopLinuxSystemProxySnapshot,
) {
    companion object {
        const val LeaseVersion = 1
    }
}

object DesktopLinuxSystemProxyLeasePaths {
    fun defaultPath(): Path {
        val base = System.getProperty("user.home")
        return Path.of(base, "SKIPI", "linux-system-proxy-lease.json")
    }
}

class DesktopLinuxSystemProxyLeaseManager(
    private val leasePath: Path = DesktopLinuxSystemProxyLeasePaths.defaultPath(),
    private val commandRunner: DesktopLinuxCommandRunner = ProcessDesktopLinuxCommandRunner,
) : DesktopSystemProxyManager {

    private val hasGsettings: Boolean by lazy {
        commandRunner.run("which", listOf("gsettings")).map { it.exitCode == 0 }.getOrDefault(false)
    }

    private val kdeWriteConfigExecutable: String? by lazy {
        when {
            commandRunner.run("which", listOf("kwriteconfig6")).map { it.exitCode == 0 }.getOrDefault(false) -> "kwriteconfig6"
            commandRunner.run("which", listOf("kwriteconfig5")).map { it.exitCode == 0 }.getOrDefault(false) -> "kwriteconfig5"
            else -> null
        }
    }

    private val kdeReadConfigExecutable: String? by lazy {
        when {
            commandRunner.run("which", listOf("kreadconfig6")).map { it.exitCode == 0 }.getOrDefault(false) -> "kreadconfig6"
            commandRunner.run("which", listOf("kreadconfig5")).map { it.exitCode == 0 }.getOrDefault(false) -> "kreadconfig5"
            else -> null
        }
    }

    override fun isSupportedHost(): Boolean = hasGsettings || kdeWriteConfigExecutable != null

    override fun acquire(endpoints: DesktopSystemProxyEndpoints): Result<DesktopSystemProxyLeaseResult> =
        withExclusiveLeaseLock {
            val existing = DesktopLinuxSystemProxyLeaseStore.load(leasePath).getOrThrow()
            if (existing != null) {
                if (existing.phase == DesktopSystemProxyLeasePhase.Active && existing.endpoints == endpoints) {
                    return@withExclusiveLeaseLock DesktopSystemProxyLeaseResult(
                        action = DesktopSystemProxyLeaseAction.AlreadyAcquired,
                        message = "Linux system proxy is already owned by SKIPI.",
                    )
                }
                restoreSnapshot(existing.snapshot).getOrThrow()
            }

            val snapshot = readSnapshot().getOrThrow()
            val ownerToken = UUID.randomUUID().toString()
            val preparedLease = DesktopLinuxSystemProxyLease(
                phase = DesktopSystemProxyLeasePhase.Prepared,
                ownerToken = ownerToken,
                endpoints = endpoints,
                snapshot = snapshot,
            )
            DesktopLinuxSystemProxyLeaseStore.save(leasePath, preparedLease).getOrThrow()

            applyProxyEndpoints(endpoints).getOrThrow()

            DesktopLinuxSystemProxyLeaseStore.save(
                leasePath,
                preparedLease.copy(phase = DesktopSystemProxyLeasePhase.Active),
            ).getOrThrow()

            DesktopSystemProxyLeaseResult(
                action = DesktopSystemProxyLeaseAction.Acquired,
                message = "Системный прокси Linux активирован.",
            ).also {
                DesktopLogger.info("SystemProxy", "Acquired Linux system proxy (HTTP ${endpoints.httpPort}, SOCKS ${endpoints.socksPort})")
            }
        }

    override fun release(): Result<DesktopSystemProxyLeaseResult> = withExclusiveLeaseLock {
        val lease = DesktopLinuxSystemProxyLeaseStore.load(leasePath).getOrThrow()
            ?: return@withExclusiveLeaseLock DesktopSystemProxyLeaseResult(
                action = DesktopSystemProxyLeaseAction.NothingToRelease,
                message = "Нет активной аренды системного прокси Linux.",
            )

        restoreSnapshot(lease.snapshot).getOrThrow()
        DesktopLinuxSystemProxyLeaseStore.clear(leasePath).getOrThrow()

        DesktopSystemProxyLeaseResult(
            action = DesktopSystemProxyLeaseAction.Released,
            message = "Системный прокси Linux возвращен в исходное состояние.",
        ).also {
            DesktopLogger.info("SystemProxy", "Released Linux system proxy")
        }
    }

    override fun recover(): Result<DesktopSystemProxyLeaseResult> = withExclusiveLeaseLock {
        val lease = DesktopLinuxSystemProxyLeaseStore.load(leasePath).getOrThrow()
            ?: return@withExclusiveLeaseLock DesktopSystemProxyLeaseResult(
                action = DesktopSystemProxyLeaseAction.NothingToRelease,
                message = "Нет сохраненной аренды для восстановления.",
            )

        restoreSnapshot(lease.snapshot).getOrThrow()
        DesktopLinuxSystemProxyLeaseStore.clear(leasePath).getOrThrow()

        DesktopSystemProxyLeaseResult(
            action = DesktopSystemProxyLeaseAction.Recovered,
            message = "Восстановлены системные настройки прокси Linux после предыдущей сессии.",
        ).also {
            DesktopLogger.info("SystemProxy", "Recovered Linux system proxy from previous session")
        }
    }

    override fun forceClear(): Result<Unit> = withExclusiveLeaseLock {
        if (hasGsettings) {
            commandRunner.run("gsettings", listOf("set", "org.gnome.system.proxy", "mode", "none")).getOrNull()
        }
        kdeWriteConfigExecutable?.let { kdeWrite ->
            commandRunner.run(kdeWrite, listOf("--file", "kioslaurc", "--group", "Proxy Settings", "--key", "ProxyType", "0")).getOrNull()
        }
        DesktopLinuxSystemProxyLeaseStore.clear(leasePath).getOrNull()
    }.map { }

    private fun readSnapshot(): Result<DesktopLinuxSystemProxySnapshot> = runCatching {
        var gnomeMode = "none"
        var gnomeHttpHost = ""
        var gnomeHttpPort = 0
        var gnomeHttpEnabled = false
        var gnomeHttpsHost = ""
        var gnomeHttpsPort = 0
        var gnomeSocksHost = ""
        var gnomeSocksPort = 0
        var gnomeIgnoreHosts = "['localhost', '127.0.0.0/8', '::1']"

        if (hasGsettings) {
            gnomeMode = readGsettingsString("mode") ?: "none"
            gnomeHttpHost = readGsettingsString("http", "host").orEmpty()
            gnomeHttpPort = readGsettingsInt("http", "port") ?: 0
            gnomeHttpEnabled = readGsettingsBool("http", "enabled") ?: false
            gnomeHttpsHost = readGsettingsString("https", "host").orEmpty()
            gnomeHttpsPort = readGsettingsInt("https", "port") ?: 0
            gnomeSocksHost = readGsettingsString("socks", "host").orEmpty()
            gnomeSocksPort = readGsettingsInt("socks", "port") ?: 0
            gnomeIgnoreHosts = readGsettingsRaw("ignore-hosts") ?: "['localhost', '127.0.0.0/8', '::1']"
        }

        var kdeProxyType: String? = null
        var kdeHttpProxy: String? = null
        var kdeHttpsProxy: String? = null
        var kdeSocksProxy: String? = null
        var kdeNoProxyFor: String? = null

        kdeReadConfigExecutable?.let { kdeRead ->
            kdeProxyType = readKdeConfig(kdeRead, "ProxyType")
            kdeHttpProxy = readKdeConfig(kdeRead, "httpProxy")
            kdeHttpsProxy = readKdeConfig(kdeRead, "httpsProxy")
            kdeSocksProxy = readKdeConfig(kdeRead, "socksProxy")
            kdeNoProxyFor = readKdeConfig(kdeRead, "NoProxyFor")
        }

        DesktopLinuxSystemProxySnapshot(
            gnomeMode = gnomeMode,
            gnomeHttpHost = gnomeHttpHost,
            gnomeHttpPort = gnomeHttpPort,
            gnomeHttpEnabled = gnomeHttpEnabled,
            gnomeHttpsHost = gnomeHttpsHost,
            gnomeHttpsPort = gnomeHttpsPort,
            gnomeSocksHost = gnomeSocksHost,
            gnomeSocksPort = gnomeSocksPort,
            gnomeIgnoreHosts = gnomeIgnoreHosts,
            kdeProxyType = kdeProxyType,
            kdeHttpProxy = kdeHttpProxy,
            kdeHttpsProxy = kdeHttpsProxy,
            kdeSocksProxy = kdeSocksProxy,
            kdeNoProxyFor = kdeNoProxyFor,
        )
    }

    private fun applyProxyEndpoints(endpoints: DesktopSystemProxyEndpoints): Result<Unit> = runCatching {
        if (hasGsettings) {
            setGsettings("http", "host", "'${endpoints.host}'")
            setGsettings("http", "port", endpoints.httpPort.toString())
            setGsettings("http", "enabled", "true")
            setGsettings("https", "host", "'${endpoints.host}'")
            setGsettings("https", "port", endpoints.httpPort.toString())
            setGsettings("socks", "host", "'${endpoints.host}'")
            setGsettings("socks", "port", endpoints.socksPort.toString())
            setGsettings("ignore-hosts", "['localhost', '127.0.0.0/8', '::1']")
            setGsettings("mode", "manual")
        }

        kdeWriteConfigExecutable?.let { kdeWrite ->
            writeKdeConfig(kdeWrite, "httpProxy", "http://${endpoints.host}:${endpoints.httpPort}")
            writeKdeConfig(kdeWrite, "httpsProxy", "http://${endpoints.host}:${endpoints.httpPort}")
            writeKdeConfig(kdeWrite, "socksProxy", "socks5://${endpoints.host}:${endpoints.socksPort}")
            writeKdeConfig(kdeWrite, "NoProxyFor", "localhost,127.0.0.1,::1")
            writeKdeConfig(kdeWrite, "ProxyType", "1")
        }
    }

    private fun restoreSnapshot(snapshot: DesktopLinuxSystemProxySnapshot): Result<Unit> = runCatching {
        if (hasGsettings) {
            if (snapshot.gnomeMode == "none" || snapshot.gnomeMode.isBlank()) {
                setGsettings("mode", "none")
            } else {
                setGsettings("http", "host", "'${snapshot.gnomeHttpHost}'")
                setGsettings("http", "port", snapshot.gnomeHttpPort.toString())
                setGsettings("http", "enabled", snapshot.gnomeHttpEnabled.toString())
                setGsettings("https", "host", "'${snapshot.gnomeHttpsHost}'")
                setGsettings("https", "port", snapshot.gnomeHttpsPort.toString())
                setGsettings("socks", "host", "'${snapshot.gnomeSocksHost}'")
                setGsettings("socks", "port", snapshot.gnomeSocksPort.toString())
                setGsettings("ignore-hosts", snapshot.gnomeIgnoreHosts)
                setGsettings("mode", snapshot.gnomeMode)
            }
        }

        kdeWriteConfigExecutable?.let { kdeWrite ->
            val proxyType = snapshot.kdeProxyType?.takeIf(String::isNotBlank) ?: "0"
            writeKdeConfig(kdeWrite, "ProxyType", proxyType)
            snapshot.kdeHttpProxy?.let { writeKdeConfig(kdeWrite, "httpProxy", it) }
            snapshot.kdeHttpsProxy?.let { writeKdeConfig(kdeWrite, "httpsProxy", it) }
            snapshot.kdeSocksProxy?.let { writeKdeConfig(kdeWrite, "socksProxy", it) }
            snapshot.kdeNoProxyFor?.let { writeKdeConfig(kdeWrite, "NoProxyFor", it) }
        }
    }

    private fun readGsettingsString(vararg path: String): String? {
        val schema = if (path.size > 1) "org.gnome.system.proxy.${path[0]}" else "org.gnome.system.proxy"
        val key = path.last()
        val result = commandRunner.run("gsettings", listOf("get", schema, key)).getOrNull() ?: return null
        if (result.exitCode != 0) return null
        return result.output.trim().removeSurrounding("'")
    }

    private fun readGsettingsRaw(key: String): String? {
        val result = commandRunner.run("gsettings", listOf("get", "org.gnome.system.proxy", key)).getOrNull() ?: return null
        if (result.exitCode != 0) return null
        return result.output.trim()
    }

    private fun readGsettingsInt(schemaSuffix: String, key: String): Int? =
        readGsettingsString(schemaSuffix, key)?.toIntOrNull()

    private fun readGsettingsBool(schemaSuffix: String, key: String): Boolean? =
        readGsettingsString(schemaSuffix, key)?.toBooleanStrictOrNull()

    private fun setGsettings(vararg args: String) {
        val (schema, key, value) = when (args.size) {
            2 -> Triple("org.gnome.system.proxy", args[0], args[1])
            3 -> Triple("org.gnome.system.proxy.${args[0]}", args[1], args[2])
            else -> error("Invalid gsettings arguments")
        }
        val result = commandRunner.run("gsettings", listOf("set", schema, key, value)).getOrThrow()
        check(result.exitCode == 0) { "gsettings set $schema $key $value failed: ${result.output.trim()}" }
    }

    private fun readKdeConfig(executable: String, key: String): String? {
        val result = commandRunner.run(executable, listOf("--file", "kioslaurc", "--group", "Proxy Settings", "--key", key)).getOrNull()
            ?: return null
        return result.output.trim().takeIf(String::isNotEmpty)
    }

    private fun writeKdeConfig(executable: String, key: String, value: String) {
        val result = commandRunner.run(executable, listOf("--file", "kioslaurc", "--group", "Proxy Settings", "--key", key, value)).getOrThrow()
        check(result.exitCode == 0) { "$executable failed to write $key: ${result.output.trim()}" }
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
            checkNotNull(lock) { "Another SKIPI process is changing the Linux system proxy" }
            lock.use { block() }
        }
    }
}

object DesktopLinuxSystemProxyLeaseStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(path: Path): Result<DesktopLinuxSystemProxyLease?> = runCatching {
        if (!Files.exists(path)) return@runCatching null
        val content = Files.readString(path, StandardCharsets.UTF_8).trim()
        if (content.isBlank()) return@runCatching null
        val lease = json.decodeFromString<DesktopLinuxSystemProxyLease>(content)
        require(lease.version == DesktopLinuxSystemProxyLease.LeaseVersion) { "Unsupported Linux system proxy lease format" }
        lease
    }

    fun save(path: Path, lease: DesktopLinuxSystemProxyLease): Result<Unit> = runCatching {
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(
            temporary,
            json.encodeToString(lease),
            StandardCharsets.UTF_8,
            CREATE,
            TRUNCATE_EXISTING,
            WRITE,
        )
        try {
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, REPLACE_EXISTING)
        }
    }

    fun clear(path: Path): Result<Unit> = runCatching {
        Files.deleteIfExists(path)
    }.map { }
}
