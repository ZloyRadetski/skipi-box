// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.DefaultLocalHttpProxyPort
import platform.DefaultLocalSocksPort
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@Serializable
enum class DesktopThemeMode { Dark, Amoled }

/** Portable desktop equivalents of the Android settings that affect SKIPI's desktop adapters. */
@Serializable
data class DesktopAppSettings(
    val localProxyPort: Int = DefaultLocalSocksPort,
    val localHttpProxyPort: Int = DefaultLocalHttpProxyPort,
    /** Mirrors Android's one-tap connection by temporarily applying a Windows HTTP proxy. */
    val useWindowsSystemProxy: Boolean = isDesktopWindowsSystemProxySupported(),
    val localProxyListenAddress: String = "127.0.0.1",
    val coreLogLevel: String = "warning",
    val subscriptionUserAgent: String = DefaultDesktopSubscriptionUserAgent,
    val subscriptionFetchTimeoutSeconds: Int = 30,
    val themeMode: DesktopThemeMode = DesktopThemeMode.Dark,
    val compactHome: Boolean = false,
    val showTunnelMemory: Boolean = true,
    val confirmDeletion: Boolean = true,
)

object DesktopSettingsLibraries {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun defaultPath(): Path = Path.of(
        System.getenv("APPDATA")?.takeIf(String::isNotBlank) ?: System.getProperty("user.home"),
        "SKIPI",
        "settings.json",
    )

    fun loadDefault(): Result<DesktopAppSettings> = load(defaultPath())
    fun saveDefault(settings: DesktopAppSettings): Result<Unit> = save(defaultPath(), settings)

    fun load(path: Path): Result<DesktopAppSettings> = runCatching {
        if (!Files.exists(path)) DesktopAppSettings()
        else Files.readString(path, StandardCharsets.UTF_8)
            .takeIf(String::isNotBlank)
            ?.let { content -> json.decodeFromString<DesktopAppSettings>(content) }
            ?.normalized()
            ?: DesktopAppSettings()
    }

    fun save(path: Path, settings: DesktopAppSettings): Result<Unit> = runCatching {
        val normalized = settings.normalized(validate = true)
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(normalized), StandardCharsets.UTF_8)
        try {
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, REPLACE_EXISTING)
        }
    }

}

val DesktopCoreLogLevels = listOf("debug", "info", "warning", "error", "none")

/**
 * Normalizes persisted settings without letting a Windows-only preference leak
 * into macOS/Linux. The SOCKS listener remains independent: its address may be
 * exposed to a LAN, while the Windows HTTP inbound is always loopback-only.
 */
internal fun DesktopAppSettings.normalized(
    validate: Boolean = false,
    supportsWindowsSystemProxy: Boolean = isDesktopWindowsSystemProxySupported(),
): DesktopAppSettings {
    if (validate) {
        require(localProxyPort in 1..65_535) { "Local proxy port must be in 1..65535" }
        require(localHttpProxyPort in 1..65_535) { "Local HTTP proxy port must be in 1..65535" }
        require(localHttpProxyPort != localProxyPort) {
            "Local HTTP proxy port must differ from the SOCKS port"
        }
        require(localProxyListenAddress.isNotBlank()) { "Local proxy listen address must not be blank" }
        require(subscriptionFetchTimeoutSeconds in 10..120) { "Subscription timeout must be in 10..120 seconds" }
        require(coreLogLevel.trim().lowercase() in DesktopCoreLogLevels) {
            "Xray log level must be one of: ${DesktopCoreLogLevels.joinToString()}"
        }
    }
    val normalizedSocksPort = localProxyPort.takeIf { it in 1..65_535 } ?: DefaultLocalSocksPort
    val fallbackHttpProxyPort = DefaultLocalHttpProxyPort
        .takeIf { it != normalizedSocksPort }
        ?: DefaultLocalHttpProxyPort + 1
    val normalizedHttpProxyPort = localHttpProxyPort
        .takeIf { it in 1..65_535 && it != normalizedSocksPort }
        ?: fallbackHttpProxyPort
    val normalizedListenAddress = localProxyListenAddress.trim().ifBlank { "127.0.0.1" }
    return copy(
        localProxyPort = normalizedSocksPort,
        localHttpProxyPort = normalizedHttpProxyPort,
        localProxyListenAddress = normalizedListenAddress,
        // Do not start a useless HTTP inbound on non-Windows hosts, even for legacy settings.json files.
        useWindowsSystemProxy = useWindowsSystemProxy && supportsWindowsSystemProxy,
        coreLogLevel = coreLogLevel.trim().lowercase().takeIf { it in DesktopCoreLogLevels } ?: "warning",
        subscriptionUserAgent = subscriptionUserAgent.trim().ifBlank { DefaultDesktopSubscriptionUserAgent },
        subscriptionFetchTimeoutSeconds = subscriptionFetchTimeoutSeconds.coerceIn(10, 120),
    )
}
