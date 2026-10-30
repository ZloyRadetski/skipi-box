// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.networkautomation.model.NetworkAutomationRule
import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxUdp443Mode
import engine.xray.DefaultMuxXudpConcurrency
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.toAndroidValue
import app.modes.SubscriptionPingModeHttp
import engine.network.NetworkDefaults
import features.subscription.SubscriptionExpiryReminder
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
    /** Raw text retained for settings UI round-trip; null denotes a legacy numeric-only value. */
    val localProxyPortRaw: String? = null,
    val localHttpProxyPort: Int = DefaultLocalHttpProxyPort,
    val useWindowsSystemProxy: Boolean = isDesktopSystemProxySupported(),
    /** Enables OS system proxy (gsettings/KDE on Linux, WinINet on Windows). */
    val useSystemProxy: Boolean = useWindowsSystemProxy,
    val localProxyListenAddress: String = "127.0.0.1",
    val enableDynamicLocalProxyPort: Boolean = false,
    /** The legacy Desktop listener did not authenticate; keep that default for old settings files. */
    val enableLocalProxyAuth: Boolean = false,
    val localProxyUsername: String = "",
    val localProxyPassword: String = "",
    val coreLogLevel: String = "error",
    val subscriptionUserAgent: String = DefaultDesktopSubscriptionUserAgent,
    val subscriptionFetchTimeoutSeconds: Int = 30,
    val subscriptionPingMode: Int = SubscriptionPingModeHttp,
    val subscriptionPingUrl: String = NetworkDefaults.CONNECTIVITY_CHECK_URL,
    val subscriptionPingTimeoutMillis: String = "5000",
    val subscriptionPingConcurrency: Int = 8,
    /** Separate from the single Desktop fetch fallback in [subscriptionUserAgent]. */
    val subscriptionUserAgents: List<String> = emptyList(),
    val enableSubscriptionExpiryNotifications: Boolean = false,
    val subscriptionExpiryReminders: List<SubscriptionExpiryReminder> = emptyList(),
    val themeMode: DesktopThemeMode = DesktopThemeMode.Dark,
    val compactHome: Boolean = false,
    val showTunnelMemory: Boolean = true,
    val showTunnelIp: Boolean = true,
    val maskTunnelIp: Boolean = false,
    val pinConnectionPanelOnHome: Boolean = false,
    val classicShowFloatingPowerButton: Boolean = false,
    val enableAllProxyGroup: Boolean = false,
    val showServerSearch: Boolean = false,
    val enableSubscriptionSwipe: Boolean = true,
    val proxyServerListColumns: Int = 1,
    val proxyServerListSort: Int = ProxyHomeSortMode.Default.toAndroidValue(),
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
    /** Persisted routing fields exposed through the shared repository; UI/runtime wiring remains separate. */
    val routeDomainStrategy: Int = 0,
    val defaultRouteOutboundTag: String = DefaultRouteOutboundTag,
    val routeRules: List<RouteRule> = emptyList(),
    val nextRouteRuleId: Int = 10,
    val enableMux: Boolean = false,
    val muxConcurrency: String = DefaultMuxConcurrency,
    val muxXudpConcurrency: String = DefaultMuxXudpConcurrency,
    val muxXudpProxyUdp443: Int = DefaultMuxUdp443Mode,
    val enableNetworkAutomation: Boolean = false,
    val enableOnDemandVpn: Boolean = false,
    val networkAutomationRules: List<NetworkAutomationRule> = emptyList(),
    val enableAccessLog: Boolean = false,
    val logRetentionDays: Int = DefaultLogRetentionDays,
    val enableSniffing: Boolean = true,
    val enableSniffingRouteOnly: Boolean = true,
    val enableFragment: Boolean = false,
    val fragmentPackets: String = DefaultFragmentPackets,
    val fragmentLength: String = DefaultFragmentLength,
    val fragmentInterval: String = DefaultFragmentInterval,

    val languageTag: String? = null,
    val hapticsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val autoCheckAppUpdates: Boolean = true,
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

/** Android's retention choices in days; 0 keeps logs indefinitely. */
val DesktopLogRetentionDays = listOf(1, 3, 7, 14, 30, 0)
const val DefaultLogRetentionDays = 7

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
    val normalizedSocksPort = localProxyPortRaw
        ?.toIntOrNull()
        ?.takeIf { it in 1..65_535 }
        ?: localProxyPort.takeIf { it in 1..65_535 }
        ?: DefaultLocalSocksPort
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
        coreLogLevel = coreLogLevel.trim().lowercase().takeIf { it in DesktopCoreLogLevels } ?: "error",
        subscriptionUserAgent = subscriptionUserAgent.trim().ifBlank { DefaultDesktopSubscriptionUserAgent },
        subscriptionFetchTimeoutSeconds = subscriptionFetchTimeoutSeconds.coerceIn(10, 120),
        proxyServerListColumns = proxyServerListColumns.coerceIn(1, 3),
        logRetentionDays = logRetentionDays.takeIf { it in DesktopLogRetentionDays } ?: DefaultLogRetentionDays,
        sendDeviceHeaders = sendDeviceHeaders,
        installationUuid = installationUuid.trim(),
    )
}
