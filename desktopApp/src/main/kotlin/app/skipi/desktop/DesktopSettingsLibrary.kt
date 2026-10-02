// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import features.routing.model.DefaultRouteOutboundTag
import features.routing.model.RouteRule
import platform.DefaultLocalHttpProxyPort
import platform.DefaultLocalSocksPort
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@Serializable
/** Enum names are persisted in settings.json; keep existing values stable. */
enum class DesktopThemeMode { Dark, Amoled, Light, Aurora, Sakura, Forest, Sunset }

/** Portable desktop equivalents of the Android settings that affect SKIPI's desktop adapters. */
@Serializable
data class DesktopAppSettings(
    val localProxyPort: Int = DefaultLocalSocksPort,
    val localHttpProxyPort: Int = DefaultLocalHttpProxyPort,
    val useWindowsSystemProxy: Boolean = isDesktopSystemProxySupported(),
    /** Enables OS system proxy (gsettings/KDE on Linux, WinINet on Windows). */
    val useSystemProxy: Boolean = useWindowsSystemProxy,
    val localProxyListenAddress: String = "127.0.0.1",
    val coreLogLevel: String = "warning",
    val subscriptionUserAgent: String = DefaultDesktopSubscriptionUserAgent,
    val subscriptionFetchTimeoutSeconds: Int = 30,
    val themeMode: DesktopThemeMode = DesktopThemeMode.Dark,
    val compactHome: Boolean = false,
    val showTunnelMemory: Boolean = true,
    val pinConnectionPanelOnHome: Boolean = false,
    val classicShowFloatingPowerButton: Boolean = false,
    val enableAllProxyGroup: Boolean = false,
    val showServerSearch: Boolean = false,
    val enableSubscriptionSwipe: Boolean = true,
    val proxyServerListColumns: Int = 1,
    val confirmDeletion: Boolean = true,
    val sendDeviceHeaders: Boolean = true,
    val installationUuid: String = "",
    // Shared appearance fields are stored as additive JSON keys so older settings.json files remain readable.
    val seedIndex: Int = 0,
    val customMaterialYouSeed: Long? = null,
    val customColorsEnabled: Boolean = false,
    val customAccentColor: Long? = null,
    val customBackgroundColor: Long? = null,
    val customSurfaceColor: Long? = null,
    val customSurfaceVariantColor: Long? = null,
    val customTextColor: Long? = null,
    val customTextSecondaryColor: Long? = null,
    val customStatusRunningColor: Long? = null,
    val customStatusStoppedColor: Long? = null,
    val customPingFastColor: Long? = null,
    val customPingMediumColor: Long? = null,
    val customPingSlowColor: Long? = null,
    val customCategoryIconColor: Long? = null,
    val customProtocolVlessColor: Long? = null,
    val customProtocolVmessColor: Long? = null,
    val customProtocolHysteria2Color: Long? = null,
    val customProtocolTrojanColor: Long? = null,
    val customProtocolShadowsocksColor: Long? = null,
    val customProtocolWireguardColor: Long? = null,
    val customProtocolSocksColor: Long? = null,
    val customProtocolHttpColor: Long? = null,
    val customProtocolStrategyColor: Long? = null,
    val customProtocolChainColor: Long? = null,
    val customProtocolJsonColor: Long? = null,
    val fontFamilyMode: Int? = null,
    val fontSizeMode: Int? = null,
    val fontWeightMode: Int? = null,
    val backgroundStyle: Int? = null,
    val backgroundPhotoDimPercent: Int? = null,
    val bottomBarSize: Int? = null,
    val connectionDisplayMode: Int? = null,
    /** Inactive legacy routing values retained so saving other settings preserves existing settings.json data. */
    val routeDomainStrategy: Int = 0,
    val defaultRouteOutboundTag: String = DefaultRouteOutboundTag,
    val routeRules: List<RouteRule> = emptyList(),
    val nextRouteRuleId: Int = 10,
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
    supportsSystemProxy: Boolean = isDesktopSystemProxySupported(),
    supportsWindowsSystemProxy: Boolean = supportsSystemProxy,
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
        require(proxyServerListColumns in 1..3) { "Proxy server list columns must be in 1..3" }
    }
    val normalizedSocksPort = localProxyPort.takeIf { it in 1..65_535 } ?: DefaultLocalSocksPort
    val fallbackHttpProxyPort = DefaultLocalHttpProxyPort
        .takeIf { it != normalizedSocksPort }
        ?: DefaultLocalHttpProxyPort + 1
    val normalizedHttpProxyPort = localHttpProxyPort
        .takeIf { it in 1..65_535 && it != normalizedSocksPort }
        ?: fallbackHttpProxyPort
    val normalizedListenAddress = localProxyListenAddress.trim().ifBlank { "127.0.0.1" }
    val effectiveSupported = supportsWindowsSystemProxy && supportsSystemProxy
    val effectiveSystemProxy = (useWindowsSystemProxy || useSystemProxy) && effectiveSupported
    return copy(
        localProxyPort = normalizedSocksPort,
        localHttpProxyPort = normalizedHttpProxyPort,
        localProxyListenAddress = normalizedListenAddress,
        useSystemProxy = effectiveSystemProxy,
        useWindowsSystemProxy = effectiveSystemProxy,
        coreLogLevel = coreLogLevel.trim().lowercase().takeIf { it in DesktopCoreLogLevels } ?: "warning",
        subscriptionUserAgent = subscriptionUserAgent.trim().ifBlank { DefaultDesktopSubscriptionUserAgent },
        subscriptionFetchTimeoutSeconds = subscriptionFetchTimeoutSeconds.coerceIn(10, 120),
        proxyServerListColumns = proxyServerListColumns.coerceIn(1, 3),
        sendDeviceHeaders = sendDeviceHeaders,
        installationUuid = installationUuid.trim(),
    )
}
