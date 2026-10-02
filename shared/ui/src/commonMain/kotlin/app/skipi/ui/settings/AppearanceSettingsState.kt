// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

/** View state consumed by the shared appearance form; persistence stays in each host. */
data class AppearanceSettingsState(
    val appIcon: Int = 0,
    val backgroundPhotoDimPercent: Int = 45,
    val backgroundStyle: Int = 0,
    val bottomBarSize: Int = 1,
    val classicShowFloatingPowerButton: Boolean = true,
    val colorMode: Int = 0,
    val connectionDisplayMode: Int = 1,
    val customAccentColor: Long? = null,
    val customBackgroundColor: Long? = null,
    val customCategoryIconColor: Long? = null,
    val customMaterialYouSeed: Long? = null,
    val customPingFastColor: Long? = null,
    val customPingMediumColor: Long? = null,
    val customPingSlowColor: Long? = null,
    val customProtocolChainColor: Long? = null,
    val customProtocolHysteria2Color: Long? = null,
    val customProtocolHttpColor: Long? = null,
    val customProtocolJsonColor: Long? = null,
    val customProtocolShadowsocksColor: Long? = null,
    val customProtocolSocksColor: Long? = null,
    val customProtocolStrategyColor: Long? = null,
    val customProtocolTrojanColor: Long? = null,
    val customProtocolVlessColor: Long? = null,
    val customProtocolVmessColor: Long? = null,
    val customProtocolWireguardColor: Long? = null,
    val customStatusRunningColor: Long? = null,
    val customStatusStoppedColor: Long? = null,
    val customSurfaceColor: Long? = null,
    val customSurfaceVariantColor: Long? = null,
    val customTextColor: Long? = null,
    val customTextSecondaryColor: Long? = null,
    val enableAllProxyGroup: Boolean = true,
    val enableCustomColors: Boolean = false,
    val enableMaterialYou: Boolean = true,
    val enableSubscriptionSwipe: Boolean = true,
    val fontFamilyMode: Int = 0,
    val fontSizeMode: Int = 100,
    val fontWeightMode: Int = 0,
    val pinConnectionPanelOnHome: Boolean = true,
    val proxyServerListLayout: Int = 1,
    val seedIndex: Int = 0,
    val showServerSearch: Boolean = true,
    val showTunnelMemoryOnHome: Boolean = false,
)

enum class AppearanceColorTarget {
    MATERIAL_YOU_SEED,
    ACCENT,
    BACKGROUND,
    SURFACE,
    SURFACE_VARIANT,
    TEXT,
    TEXT_SECONDARY,
    STATUS_RUNNING,
    STATUS_STOPPED,
    PING_FAST,
    PING_MEDIUM,
    PING_SLOW,
    CATEGORY_ICONS,
    PROTOCOL_VLESS,
    PROTOCOL_VMESS,
    PROTOCOL_HYSTERIA2,
    PROTOCOL_TROJAN,
    PROTOCOL_SHADOWSOCKS,
    PROTOCOL_WIREGUARD,
    PROTOCOL_SOCKS,
    PROTOCOL_HTTP,
    PROTOCOL_STRATEGY,
    PROTOCOL_CHAIN,
    PROTOCOL_JSON,
}

/** Persisted integer values used by the existing Android appearance preferences. */
object AppearanceSettingValues {
    const val ColorModeSystem = 0
    const val ColorModeLight = 1
    const val ColorModeDark = 2
    const val ColorModeAmoled = 6
    const val ColorModeAurora = 7
    const val ColorModeSakura = 8
    const val ColorModeForest = 9
    const val ColorModeSunset = 10

    const val BackgroundStyleClassic = 0
    const val BackgroundStylePhoto = 1
    const val BackgroundStyleConnection = 2
    const val BackgroundStyleAurora = 3

    const val ConnectionDisplayModeCompact = 0
    const val ConnectionDisplayModeClassic = 1

    const val ProxyServerListLayoutSingle = 1
    const val ProxyServerListLayoutDouble = 2
    const val ProxyServerListLayoutMultiple = 3

    const val FontSizeModeTiny = 55
    const val FontSizeModeExtraLarge = 145
    const val FontSizeModeStepPercent = 5
    const val CustomMaterialYouSeedIndex = 8

    fun normalizeColorMode(value: Int): Int = when (value) {
        3 -> ColorModeSystem
        4 -> ColorModeLight
        5 -> ColorModeDark
        ColorModeSystem,
        ColorModeLight,
        ColorModeDark,
        ColorModeAmoled,
        ColorModeAurora,
        ColorModeSakura,
        ColorModeForest,
        ColorModeSunset -> value
        else -> ColorModeSystem
    }

    fun isNamedColorTheme(value: Int): Boolean = when (normalizeColorMode(value)) {
        ColorModeAurora, ColorModeSakura, ColorModeForest, ColorModeSunset -> true
        else -> false
    }

    fun normalizeBackgroundStyle(value: Int): Int = when (value) {
        BackgroundStylePhoto, BackgroundStyleConnection, BackgroundStyleAurora -> value
        else -> BackgroundStyleClassic
    }

    fun normalizeFontSizeMode(value: Int): Int = when {
        value in 0..7 -> listOf(55, 65, 75, 85, 100, 115, 130, 145)[value]
        value in FontSizeModeTiny..FontSizeModeExtraLarge && (value - FontSizeModeTiny) % FontSizeModeStepPercent == 0 -> value
        else -> 100
    }

    fun isFontWeightSupported(fontFamilyMode: Int): Boolean = fontFamilyMode != 5
}
