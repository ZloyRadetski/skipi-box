// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.colorModeOfAppearance
import app.skipi.app.model.customColors
import app.skipi.app.model.themeChoiceOfColorMode
import app.skipi.app.model.withCustomColors

/** The appearance choices a client keeps outside [PersistedSettings]: the Android app icon, the Desktop sidebar and log panel. */
data class AppearanceHostSettings(
    val appIcon: Int = 0,
    val navigationStyle: Int = 0,
    val showConnectionLogOnHome: Boolean = true,
)

fun AppearanceSettingsState.hostSettings() = AppearanceHostSettings(
    appIcon = appIcon,
    navigationStyle = navigationStyle,
    showConnectionLogOnHome = showConnectionLogOnHome,
)

/**
 * The appearance screen's state for stored settings. What they leave open takes the defaults a client without
 * a saved value has always shown: the screen's own, except the small font size.
 */
fun PersistedSettings.toAppearanceSettingsState(host: AppearanceHostSettings = AppearanceHostSettings()): AppearanceSettingsState {
    val defaults = AppearanceSettingsState()
    return AppearanceSettingsState(
        appIcon = host.appIcon,
        backgroundPhotoDimPercent = appearance.backgroundPhotoDimPercent ?: defaults.backgroundPhotoDimPercent,
        backgroundPhotoBlurPercent = appearance.backgroundPhotoBlurPercent ?: defaults.backgroundPhotoBlurPercent,
        backgroundStyle = appearance.backgroundStyle ?: defaults.backgroundStyle,
        bottomBarSize = appearance.bottomBarSize ?: defaults.bottomBarSize,
        classicButtonSize = appearance.classicButtonSize ?: defaults.classicButtonSize,
        classicShowFloatingPowerButton = home.showFloatingPowerButton,
        enableSeasonalEvents = appearance.seasonalEvents,
        colorMode = colorModeOfAppearance(appearance, fallback = AppearanceSettingValues.ColorModeDark),
        connectionDisplayMode = home.connectionDisplayMode ?: defaults.connectionDisplayMode,
        customThemes = appearance.customThemes,
        customMaterialYouSeed = appearance.customMaterialYouSeed,
        enableAllProxyGroup = home.enableAllProxyGroup ?: defaults.enableAllProxyGroup,
        enableCustomColors = appearance.customColorsEnabled,
        enableMaterialYou = appearance.dynamicColors,
        enableSubscriptionSwipe = home.subscriptionSwipeEnabled,
        fontFamilyMode = appearance.fontFamilyMode ?: defaults.fontFamilyMode,
        fontSizeMode = appearance.fontSizeMode ?: app.modes.FontSizeModeSmall,
        fontWeightMode = appearance.fontWeightMode ?: defaults.fontWeightMode,
        pinConnectionPanelOnHome = home.pinConnectionPanel,
        seedIndex = appearance.seedIndex,
        showServerSearch = home.showServerSearch,
        showTunnelMemoryOnHome = home.showTunnelMemoryOnHome ?: defaults.showTunnelMemoryOnHome,
        showTunnelIpOnHome = home.showTunnelIpOnHome ?: defaults.showTunnelIpOnHome,
        maskTunnelIpOnHome = home.maskTunnelIpOnHome ?: defaults.maskTunnelIpOnHome,
        showConnectionLogOnHome = host.showConnectionLogOnHome,
        navigationStyle = host.navigationStyle,
    ).withCustomColors(appearance.customColors())
}

/**
 * The stored settings with the appearance screen's choices written in. Only the appearance and Home sections
 * change; the client keeps [AppearanceSettingsState.hostSettings] itself.
 */
fun PersistedSettings.withAppearanceSettingsState(state: AppearanceSettingsState): PersistedSettings {
    val choice = themeChoiceOfColorMode(AppearanceSettingValues.normalizeColorMode(state.colorMode))
    return copy(
        appearance = appearance.copy(
            themeMode = choice.themeMode,
            amoledBlack = choice.amoledBlack,
            themeVariant = choice.themeVariant,
            dynamicColors = state.enableMaterialYou,
            seedIndex = state.seedIndex,
            customMaterialYouSeed = state.customMaterialYouSeed,
            customColorsEnabled = state.enableCustomColors,
            fontFamilyMode = state.fontFamilyMode,
            fontSizeMode = state.fontSizeMode,
            fontWeightMode = state.fontWeightMode,
            backgroundStyle = state.backgroundStyle,
            backgroundPhotoDimPercent = state.backgroundPhotoDimPercent,
            backgroundPhotoBlurPercent = state.backgroundPhotoBlurPercent,
            bottomBarSize = state.bottomBarSize,
            classicButtonSize = state.classicButtonSize,
            seasonalEvents = state.enableSeasonalEvents,
            customThemes = state.customThemes,
        ).withCustomColors(state.customColors()),
        home = home.copy(
            connectionDisplayMode = state.connectionDisplayMode,
            pinConnectionPanel = state.pinConnectionPanelOnHome,
            subscriptionSwipeEnabled = state.enableSubscriptionSwipe,
            showFloatingPowerButton = state.classicShowFloatingPowerButton,
            showServerSearch = state.showServerSearch,
            enableAllProxyGroup = state.enableAllProxyGroup,
            showTunnelMemoryOnHome = state.showTunnelMemoryOnHome,
            showTunnelIpOnHome = state.showTunnelIpOnHome,
            maskTunnelIpOnHome = state.maskTunnelIpOnHome,
        ),
    )
}
