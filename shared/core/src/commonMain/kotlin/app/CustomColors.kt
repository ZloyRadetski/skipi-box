// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

/**
 * The colors a user may pick in the appearance settings, as ARGB longs; null keeps the theme's own color.
 *
 * Every settings model (the app state, the persisted records, the backup, the settings screen and the Desktop
 * file) stores these flat under their `custom...Color` names. This one value is what they are converted
 * through, so a color is copied between two models in one line and a new color is added in one place per model.
 */
data class CustomColors(
    val accent: Long? = null,
    val background: Long? = null,
    val surface: Long? = null,
    val surfaceVariant: Long? = null,
    val text: Long? = null,
    val textSecondary: Long? = null,
    val statusRunning: Long? = null,
    val statusStopped: Long? = null,
    val pingFast: Long? = null,
    val pingMedium: Long? = null,
    val pingSlow: Long? = null,
    val categoryIcon: Long? = null,
    val categoryIconGlyph: Long? = null,
    val protocolVless: Long? = null,
    val protocolVmess: Long? = null,
    val protocolHysteria2: Long? = null,
    val protocolTrojan: Long? = null,
    val protocolShadowsocks: Long? = null,
    val protocolWireguard: Long? = null,
    val protocolSocks: Long? = null,
    val protocolHttp: Long? = null,
    val protocolStrategy: Long? = null,
    val protocolChain: Long? = null,
    val protocolJson: Long? = null,
)

/** The colors of [AppState]. */
fun AppState.customColors(): CustomColors = CustomColors(
    accent = customAccentColor,
    background = customBackgroundColor,
    surface = customSurfaceColor,
    surfaceVariant = customSurfaceVariantColor,
    text = customTextColor,
    textSecondary = customTextSecondaryColor,
    statusRunning = customStatusRunningColor,
    statusStopped = customStatusStoppedColor,
    pingFast = customPingFastColor,
    pingMedium = customPingMediumColor,
    pingSlow = customPingSlowColor,
    categoryIcon = customCategoryIconColor,
    categoryIconGlyph = customCategoryIconGlyphColor,
    protocolVless = customProtocolVlessColor,
    protocolVmess = customProtocolVmessColor,
    protocolHysteria2 = customProtocolHysteria2Color,
    protocolTrojan = customProtocolTrojanColor,
    protocolShadowsocks = customProtocolShadowsocksColor,
    protocolWireguard = customProtocolWireguardColor,
    protocolSocks = customProtocolSocksColor,
    protocolHttp = customProtocolHttpColor,
    protocolStrategy = customProtocolStrategyColor,
    protocolChain = customProtocolChainColor,
    protocolJson = customProtocolJsonColor,
)

fun AppState.withCustomColors(colors: CustomColors): AppState = copy(
    customAccentColor = colors.accent,
    customBackgroundColor = colors.background,
    customSurfaceColor = colors.surface,
    customSurfaceVariantColor = colors.surfaceVariant,
    customTextColor = colors.text,
    customTextSecondaryColor = colors.textSecondary,
    customStatusRunningColor = colors.statusRunning,
    customStatusStoppedColor = colors.statusStopped,
    customPingFastColor = colors.pingFast,
    customPingMediumColor = colors.pingMedium,
    customPingSlowColor = colors.pingSlow,
    customCategoryIconColor = colors.categoryIcon,
    customCategoryIconGlyphColor = colors.categoryIconGlyph,
    customProtocolVlessColor = colors.protocolVless,
    customProtocolVmessColor = colors.protocolVmess,
    customProtocolHysteria2Color = colors.protocolHysteria2,
    customProtocolTrojanColor = colors.protocolTrojan,
    customProtocolShadowsocksColor = colors.protocolShadowsocks,
    customProtocolWireguardColor = colors.protocolWireguard,
    customProtocolSocksColor = colors.protocolSocks,
    customProtocolHttpColor = colors.protocolHttp,
    customProtocolStrategyColor = colors.protocolStrategy,
    customProtocolChainColor = colors.protocolChain,
    customProtocolJsonColor = colors.protocolJson,
)
