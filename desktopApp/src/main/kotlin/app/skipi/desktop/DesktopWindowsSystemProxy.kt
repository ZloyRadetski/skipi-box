// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
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

/**
 * A loopback HTTP endpoint that can be exposed through Windows Internet Settings.
 *
 * Keeping this endpoint loopback-only is intentional: enabling a system proxy must
 * never publish a user-provided remote proxy address by accident. Xray may still
 * expose a separate SOCKS listener for manual application configuration.
 */
@Serializable
data class DesktopWindowsHttpProxyEndpoint(
    val host: String = "127.0.0.1",
    val port: Int,
) {
    init {
        require(host == LoopbackHost) { "Windows system proxy must use $LoopbackHost" }
        require(port in 1..65_535) { "Windows HTTP proxy port must be in 1..65535" }
    }

    /** Windows accepts per-scheme endpoints in the ProxyServer value. */
    fun proxyServerValue(): String = "http=$host:$port;https=$host:$port"

    private companion object {
        const val LoopbackHost = "127.0.0.1"
    }
}

/** The temporary WinINet integration exists only in the packaged Windows client. */
internal fun isDesktopWindowsSystemProxySupported(
    osName: String = System.getProperty("os.name"),
): Boolean = osName.startsWith("Windows", ignoreCase = true)

/** Result action lets UI distinguish a successful recovery from an unsafe no-op. */
enum class DesktopWindowsSystemProxyLeaseAction {
    Acquired,
    AlreadyAcquired,
    Released,
    NothingToRelease,
    Recovered,
    SkippedNotOwner,
}

data class DesktopWindowsSystemProxyLeaseResult(
    val action: DesktopWindowsSystemProxyLeaseAction,
    /** Successful release/recovery always includes a successful WinINet refresh. */
    val refreshApplied: Boolean = true,
    val message: String = "",
)

/** Small process boundary that keeps registry and WinINet calls unit-testable. */
fun interface DesktopWindowsCommandRunner {
    fun run(executable: String, arguments: List<String>): Result<DesktopWindowsCommandResult>
}

data class DesktopWindowsCommandResult(
    val exitCode: Int,
    val output: String = "",
)

/** The production runner never invokes a shell: all executable arguments are passed separately. */
object ProcessDesktopWindowsCommandRunner : DesktopWindowsCommandRunner {
    override fun run(executable: String, arguments: List<String>): Result<DesktopWindowsCommandResult> = runCatching {
        val process = ProcessBuilder(listOf(executable) + arguments)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(CommandTimeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(CommandTimeoutSeconds, TimeUnit.SECONDS)
            error("$executable did not finish within $CommandTimeoutSeconds seconds")
        }
        DesktopWindowsCommandResult(
            exitCode = process.exitValue(),
            output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
        )
    }

    private const val CommandTimeoutSeconds = 10L
}

@Serializable
enum class DesktopWindowsRegistryValueType(val regArgument: String) {
    String("REG_SZ"),
    Dword("REG_DWORD");
}

@Serializable
data class DesktopWindowsRegistryValue(
    val type: DesktopWindowsRegistryValueType,
    val data: String,
) {
    companion object {
        fun string(value: String): DesktopWindowsRegistryValue =
            DesktopWindowsRegistryValue(DesktopWindowsRegistryValueType.String, value)

        fun dword(value: Int): DesktopWindowsRegistryValue =
            DesktopWindowsRegistryValue(DesktopWindowsRegistryValueType.Dword, value.toString())
    }
}

/** A deliberately small registry surface; it cannot mutate arbitrary keys from UI data. */
interface DesktopWindowsRegistry {
    fun read(key: String, valueName: String): Result<DesktopWindowsRegistryValue?>
    fun write(key: String, valueName: String, value: DesktopWindowsRegistryValue): Result<Unit>
    fun delete(key: String, valueName: String): Result<Unit>
}

/**
 * `reg.exe` implementation used in the packaged Windows app. HKCU mutations do
 * not require elevation. It is kept behind [DesktopWindowsCommandRunner] so tests
 * never touch the real registry.
 */
class RegExeDesktopWindowsRegistry(
    private val commandRunner: DesktopWindowsCommandRunner,
) : DesktopWindowsRegistry {
    override fun read(key: String, valueName: String): Result<DesktopWindowsRegistryValue?> =
        commandRunner.run("reg.exe", listOf("query", key, "/v", valueName)).mapCatching { result ->
            when (result.exitCode) {
                0 -> parseValue(valueName, result.output)
                1 -> null
                else -> error("reg.exe could not read $key\\$valueName: ${result.output.trim()}")
            }
        }

    override fun write(key: String, valueName: String, value: DesktopWindowsRegistryValue): Result<Unit> =
        commandRunner.run(
            "reg.exe",
            listOf(
                "add",
                key,
                "/v",
                valueName,
                "/t",
                value.type.regArgument,
                "/d",
                value.data,
                "/f",
            ),
        ).mapCatching { result ->
            check(result.exitCode == 0) {
                "reg.exe could not write $key\\$valueName: ${result.output.trim()}"
            }
        }

    override fun delete(key: String, valueName: String): Result<Unit> = runCatching {
        // Query first so a missing value is idempotent without treating a denied delete as success.
        if (read(key, valueName).getOrThrow() == null) return@runCatching
        val result = commandRunner.run("reg.exe", listOf("delete", key, "/v", valueName, "/f")).getOrThrow()
        check(result.exitCode == 0) {
            "reg.exe could not delete $key\\$valueName: ${result.output.trim()}"
        }
    }

    private fun parseValue(valueName: String, output: String): DesktopWindowsRegistryValue {
        val type = DesktopWindowsRegistryValueType.entries.firstOrNull { candidate ->
            output.contains(candidate.regArgument, ignoreCase = true)
        } ?: error("reg.exe returned an unsupported type for $valueName")
        val line = output.lineSequence().firstOrNull { line ->
            line.contains(type.regArgument, ignoreCase = true)
        } ?: error("reg.exe did not return a value for $valueName")
        val typeIndex = line.indexOf(type.regArgument, ignoreCase = true)
        val rawData = line.substring(typeIndex + type.regArgument.length).trim()
        return DesktopWindowsRegistryValue(type, rawData)
    }
}

/** Allows a test or a future native implementation to replace only the refresh step. */
fun interface DesktopWindowsInternetSettingsRefresher {
    fun refresh(): Result<Unit>
}

/**
 * Refreshes WinINet after the registry is changed. No third-party native bridge is
 * required: the fixed PowerShell script invokes InternetSetOption directly. The
 * script has no user-controlled interpolation and runs without a PowerShell profile.
 */
class PowerShellWinInetSettingsRefresher(
    private val commandRunner: DesktopWindowsCommandRunner,
) : DesktopWindowsInternetSettingsRefresher {
    override fun refresh(): Result<Unit> = commandRunner.run(
        "powershell.exe",
        listOf("-NoLogo", "-NoProfile", "-NonInteractive", "-Command", RefreshScript),
    ).mapCatching { result ->
        check(result.exitCode == 0) {
            "Windows did not refresh its Internet proxy settings: ${result.output.trim()}"
        }
    }

    private companion object {
        // INTERNET_OPTION_SETTINGS_CHANGED = 39, INTERNET_OPTION_REFRESH = 37.
        val RefreshScript = """
            Add-Type @'
            using System;
            using System.Runtime.InteropServices;
            public static class SkipiWinInet {
                [DllImport("wininet.dll", SetLastError = true)]
                public static extern bool InternetSetOption(IntPtr handle, int option, IntPtr buffer, int length);
            }
'@
            ${'$'}changed = [SkipiWinInet]::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0)
            ${'$'}refresh = [SkipiWinInet]::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0)
            if (-not ${'$'}changed -or -not ${'$'}refresh) { exit 1 }
        """.trimIndent()
    }
}

@Serializable
private enum class DesktopWindowsSystemProxyLeasePhase {
    Prepared,
    Active,
}

@Serializable
private data class DesktopWindowsSystemProxySnapshot(
    val proxyEnable: DesktopWindowsRegistryValue?,
    val proxyServer: DesktopWindowsRegistryValue?,
    val proxyOverride: DesktopWindowsRegistryValue?,
)

@Serializable
private data class DesktopWindowsSystemProxyLease(
    val version: Int = LeaseVersion,
    val phase: DesktopWindowsSystemProxyLeasePhase,
    val ownerToken: String,
    val endpoint: DesktopWindowsHttpProxyEndpoint,
    val appliedProxyOverride: String,
    val snapshot: DesktopWindowsSystemProxySnapshot,
)

/** Default crash-recovery location; callers can inject a separate path for tests. */
object DesktopWindowsSystemProxyLeasePaths {
    fun defaultPath(): Path {
        val base = System.getenv("APPDATA")?.takeIf(String::isNotBlank) ?: System.getProperty("user.home")
        return Path.of(base, "SKIPI", "system-proxy-lease.json")
    }
}

/**
 * Owns a temporary user-level Windows system HTTP proxy setting.
 *
 * It intentionally handles only the three WinINet registry values that SKIPI
 * modifies. PAC/WPAD and WinHTTP are left untouched. A unique marker under the
 * SKIPI registry key prevents a stale client from restoring settings changed by
 * the user or another client. The JSON lease is written before the marker, so an
 * application crash can recover the exact prior values on its next launch.
 */
class DesktopWindowsSystemProxyLeaseManager(
    private val leasePath: Path = DesktopWindowsSystemProxyLeasePaths.defaultPath(),
    commandRunner: DesktopWindowsCommandRunner = ProcessDesktopWindowsCommandRunner,
    private val registry: DesktopWindowsRegistry = RegExeDesktopWindowsRegistry(commandRunner),
    private val refresher: DesktopWindowsInternetSettingsRefresher = PowerShellWinInetSettingsRefresher(commandRunner),
) {
    /**
     * Snapshots the current per-user proxy values, starts a lease, and makes the
     * local HTTP Xray endpoint the Windows HTTP/HTTPS proxy. Call this only after
     * Xray has successfully bound the endpoint.
     */
    fun acquire(endpoint: DesktopWindowsHttpProxyEndpoint): Result<DesktopWindowsSystemProxyLeaseResult> =
        withExclusiveLeaseLock {
            val existing = DesktopWindowsSystemProxyLeaseStore.load(leasePath).getOrThrow()
            if (existing != null) {
                val ownership = ownershipOf(existing).getOrThrow()
                if (ownership == DesktopWindowsSystemProxyOwnership.Active) {
                    check(existing.endpoint == endpoint) {
                        "A SKIPI system proxy lease is already active for ${existing.endpoint.host}:${existing.endpoint.port}"
                    }
                    return@withExclusiveLeaseLock DesktopWindowsSystemProxyLeaseResult(
                        action = DesktopWindowsSystemProxyLeaseAction.AlreadyAcquired,
                        message = "Windows system proxy is already owned by SKIPI.",
                    )
                }
                if (ownership == DesktopWindowsSystemProxyOwnership.Restorable) {
                    restoreOwnedLease(existing).getOrThrow()
                } else {
                    discardUnownedLease(existing).getOrThrow()
                }
            }

            check(registry.read(MarkerRegistryKey, MarkerRegistryValue).getOrThrow() == null) {
                "Another SKIPI system proxy lease marker is present; restart SKIPI or restore the proxy settings first"
            }

            val snapshot = readSnapshot().getOrThrow()
            val lease = DesktopWindowsSystemProxyLease(
                phase = DesktopWindowsSystemProxyLeasePhase.Prepared,
                ownerToken = UUID.randomUUID().toString(),
                endpoint = endpoint,
                appliedProxyOverride = snapshot.proxyOverride?.data.mergeRequiredBypassRules(),
                snapshot = snapshot,
            )
            DesktopWindowsSystemProxyLeaseStore.save(leasePath, lease).getOrThrow()

            val applied = runCatching {
                registry.write(MarkerRegistryKey, MarkerRegistryValue, DesktopWindowsRegistryValue.string(lease.ownerToken)).getOrThrow()
                // Keep the old proxy disabled until the complete endpoint is in place.
                registry.write(InternetSettingsKey, ProxyServerValue, DesktopWindowsRegistryValue.string(endpoint.proxyServerValue())).getOrThrow()
                registry.write(InternetSettingsKey, ProxyOverrideValue, DesktopWindowsRegistryValue.string(lease.appliedProxyOverride)).getOrThrow()
                registry.write(InternetSettingsKey, ProxyEnableValue, DesktopWindowsRegistryValue.dword(1)).getOrThrow()
                refresher.refresh().getOrThrow()
                DesktopWindowsSystemProxyLeaseStore.save(
                    leasePath,
                    lease.copy(phase = DesktopWindowsSystemProxyLeasePhase.Active),
                ).getOrThrow()
            }
            if (applied.isFailure) {
                val originalFailure = applied.exceptionOrNull()!!
                val rollback = restoreOwnedLease(lease)
                if (rollback.isFailure) {
                    error(
                        "Could not enable the Windows system proxy: ${originalFailure.message.orEmpty()}. " +
                            "Automatic restoration also failed: ${rollback.exceptionOrNull()?.message.orEmpty()}",
                    )
                }
                throw originalFailure
            }
            DesktopWindowsSystemProxyLeaseResult(
                action = DesktopWindowsSystemProxyLeaseAction.Acquired,
                message = "Windows HTTP/HTTPS proxy points to ${endpoint.host}:${endpoint.port}.",
            )
        }

    /** Restores the exact values captured by [acquire] only while the lease still owns them. */
    fun release(): Result<DesktopWindowsSystemProxyLeaseResult> = withExclusiveLeaseLock {
        val lease = DesktopWindowsSystemProxyLeaseStore.load(leasePath).getOrThrow()
            ?: return@withExclusiveLeaseLock DesktopWindowsSystemProxyLeaseResult(
                action = DesktopWindowsSystemProxyLeaseAction.NothingToRelease,
                message = "No SKIPI system proxy lease is stored.",
            )
        when (ownershipOf(lease).getOrThrow()) {
            DesktopWindowsSystemProxyOwnership.Active,
            DesktopWindowsSystemProxyOwnership.Restorable,
            -> {
                restoreOwnedLease(lease).getOrThrow()
                DesktopWindowsSystemProxyLeaseResult(
                    action = DesktopWindowsSystemProxyLeaseAction.Released,
                    message = "Windows proxy settings were restored.",
                )
            }

            DesktopWindowsSystemProxyOwnership.NotOwned -> {
                discardUnownedLease(lease).getOrThrow()
                DesktopWindowsSystemProxyLeaseResult(
                    action = DesktopWindowsSystemProxyLeaseAction.SkippedNotOwner,
                    message = "Windows proxy changed outside SKIPI; the current settings were not overwritten.",
                )
            }
        }
    }

    /** Invoke at startup before offering a new connection to recover from a crash. */
    fun recover(): Result<DesktopWindowsSystemProxyLeaseResult> = withExclusiveLeaseLock {
        val lease = DesktopWindowsSystemProxyLeaseStore.load(leasePath).getOrThrow()
            ?: return@withExclusiveLeaseLock DesktopWindowsSystemProxyLeaseResult(
                action = DesktopWindowsSystemProxyLeaseAction.NothingToRelease,
                message = "No interrupted system proxy lease was found.",
            )
        when (ownershipOf(lease).getOrThrow()) {
            DesktopWindowsSystemProxyOwnership.Active,
            DesktopWindowsSystemProxyOwnership.Restorable,
            -> {
                restoreOwnedLease(lease).getOrThrow()
                DesktopWindowsSystemProxyLeaseResult(
                    action = DesktopWindowsSystemProxyLeaseAction.Recovered,
                    message = "Recovered the Windows proxy settings left by an interrupted SKIPI session.",
                )
            }

            DesktopWindowsSystemProxyOwnership.NotOwned -> {
                discardUnownedLease(lease).getOrThrow()
                DesktopWindowsSystemProxyLeaseResult(
                    action = DesktopWindowsSystemProxyLeaseAction.SkippedNotOwner,
                    message = "A stale SKIPI lease was discarded without changing Windows proxy settings.",
                )
            }
        }
    }

    /** A small capability probe for callers that want to hide the Windows-only preference. */
    fun isSupportedHost(): Boolean = isDesktopWindowsSystemProxySupported()

    private fun readSnapshot(): Result<DesktopWindowsSystemProxySnapshot> = runCatching {
        DesktopWindowsSystemProxySnapshot(
            proxyEnable = registry.read(InternetSettingsKey, ProxyEnableValue).getOrThrow(),
            proxyServer = registry.read(InternetSettingsKey, ProxyServerValue).getOrThrow(),
            proxyOverride = registry.read(InternetSettingsKey, ProxyOverrideValue).getOrThrow(),
        )
    }

    private fun ownershipOf(lease: DesktopWindowsSystemProxyLease): Result<DesktopWindowsSystemProxyOwnership> = runCatching {
        val marker = registry.read(MarkerRegistryKey, MarkerRegistryValue).getOrThrow().stringDataOrNull()
        if (marker != lease.ownerToken) return@runCatching DesktopWindowsSystemProxyOwnership.NotOwned

        val currentEnable = registry.read(InternetSettingsKey, ProxyEnableValue).getOrThrow()
        val currentServer = registry.read(InternetSettingsKey, ProxyServerValue).getOrThrow()
        val currentOverride = registry.read(InternetSettingsKey, ProxyOverrideValue).getOrThrow()
        if (
            currentEnable.matchesDword(1) &&
            currentServer.matchesString(lease.endpoint.proxyServerValue()) &&
            currentOverride.matchesString(lease.appliedProxyOverride)
        ) {
            DesktopWindowsSystemProxyOwnership.Active
        } else {
            // The marker is ours, but a prior acquisition/release may have been interrupted.
            // Restoring is safe only if all values are either the target or the captured original values.
            val knownValues = listOf(
                currentEnable.isEither(DesktopWindowsRegistryValue.dword(1), lease.snapshot.proxyEnable),
                currentServer.isEither(DesktopWindowsRegistryValue.string(lease.endpoint.proxyServerValue()), lease.snapshot.proxyServer),
                currentOverride.isEither(DesktopWindowsRegistryValue.string(lease.appliedProxyOverride), lease.snapshot.proxyOverride),
            )
            if (knownValues.all { it }) DesktopWindowsSystemProxyOwnership.Restorable
            else DesktopWindowsSystemProxyOwnership.NotOwned
        }
    }

    /**
     * Restores values with ProxyEnable disabled first, so a transient state never
     * sends traffic to the old SKIPI endpoint.  The marker and lease remain until
     * WinINet acknowledges the refresh: a failed refresh is retryable and callers
     * must keep Xray alive rather than leave applications targeting a dead port.
     */
    private fun restoreOwnedLease(lease: DesktopWindowsSystemProxyLease): Result<Boolean> = runCatching {
        val marker = registry.read(MarkerRegistryKey, MarkerRegistryValue).getOrThrow().stringDataOrNull()
        check(marker == lease.ownerToken) { "SKIPI no longer owns this Windows system proxy lease" }

        registry.write(InternetSettingsKey, ProxyEnableValue, DesktopWindowsRegistryValue.dword(0)).getOrThrow()
        restoreRegistryValue(ProxyServerValue, lease.snapshot.proxyServer).getOrThrow()
        restoreRegistryValue(ProxyOverrideValue, lease.snapshot.proxyOverride).getOrThrow()
        restoreRegistryValue(ProxyEnableValue, lease.snapshot.proxyEnable).getOrThrow()
        refresher.refresh().getOrThrow()
        registry.delete(MarkerRegistryKey, MarkerRegistryValue).getOrThrow()
        DesktopWindowsSystemProxyLeaseStore.clear(leasePath).getOrThrow()
        true
    }

    private fun restoreRegistryValue(
        valueName: String,
        original: DesktopWindowsRegistryValue?,
    ): Result<Unit> = if (original == null) {
        registry.delete(InternetSettingsKey, valueName)
    } else {
        registry.write(InternetSettingsKey, valueName, original)
    }

    /** Removes only our marker; third-party proxy settings are deliberately left intact. */
    private fun discardUnownedLease(lease: DesktopWindowsSystemProxyLease): Result<Unit> = runCatching {
        val marker = registry.read(MarkerRegistryKey, MarkerRegistryValue).getOrThrow().stringDataOrNull()
        if (marker == lease.ownerToken) {
            registry.delete(MarkerRegistryKey, MarkerRegistryValue).getOrThrow()
        }
        DesktopWindowsSystemProxyLeaseStore.clear(leasePath).getOrThrow()
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
            checkNotNull(lock) { "Another SKIPI process is changing the Windows system proxy" }
            lock.use { block() }
        }
    }

    private enum class DesktopWindowsSystemProxyOwnership {
        Active,
        Restorable,
        NotOwned,
    }

    private companion object {
        const val InternetSettingsKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        const val ProxyEnableValue = "ProxyEnable"
        const val ProxyServerValue = "ProxyServer"
        const val ProxyOverrideValue = "ProxyOverride"
        const val MarkerRegistryKey = "HKCU\\Software\\SKIPI\\Desktop"
        const val MarkerRegistryValue = "SystemProxyLease"
    }
}

private fun DesktopWindowsRegistryValue?.stringDataOrNull(): String? =
    if (this?.type == DesktopWindowsRegistryValueType.String) data else null

private fun DesktopWindowsRegistryValue?.matchesString(expected: String): Boolean =
    this?.type == DesktopWindowsRegistryValueType.String && data == expected

private fun DesktopWindowsRegistryValue?.matchesDword(expected: Int): Boolean =
    this?.type == DesktopWindowsRegistryValueType.Dword && data.toDwordOrNull() == expected

private fun DesktopWindowsRegistryValue?.isEither(
    expected: DesktopWindowsRegistryValue,
    original: DesktopWindowsRegistryValue?,
): Boolean = this == expected || this == original || when {
    this?.type == DesktopWindowsRegistryValueType.Dword && expected.type == DesktopWindowsRegistryValueType.Dword ->
        data.toDwordOrNull() == expected.data.toDwordOrNull()
    this?.type == DesktopWindowsRegistryValueType.Dword && original?.type == DesktopWindowsRegistryValueType.Dword ->
        data.toDwordOrNull() == original.data.toDwordOrNull()
    else -> false
}

private fun String.toDwordOrNull(): Int? = runCatching {
    val cleaned = trim().substringBefore(' ').removePrefix("0x").removePrefix("0X")
    val radix = if (trim().startsWith("0x", ignoreCase = true)) 16 else 10
    cleaned.toLong(radix).toInt()
}.getOrNull()

private fun String?.mergeRequiredBypassRules(): String {
    val existing = this.orEmpty()
        .split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)
    val required = listOf("<local>", "localhost", "127.*", "[::1]")
    return (existing + required)
        .distinctBy { value -> value.lowercase() }
        .joinToString(";")
}

private object DesktopWindowsSystemProxyLeaseStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(path: Path): Result<DesktopWindowsSystemProxyLease?> = runCatching {
        if (!Files.exists(path)) return@runCatching null
        val content = Files.readString(path, StandardCharsets.UTF_8).trim()
        if (content.isBlank()) return@runCatching null
        val lease = json.decodeFromString<DesktopWindowsSystemProxyLease>(content)
        require(lease.version == LeaseVersion) { "Unsupported Windows system proxy lease format" }
        lease
    }

    fun save(path: Path, lease: DesktopWindowsSystemProxyLease): Result<Unit> = runCatching {
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
    }
}

private const val LeaseVersion = 1
