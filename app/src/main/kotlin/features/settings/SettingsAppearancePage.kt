// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import androidx.compose.ui.platform.LocalClipboard
import app.skipi.ui.settings.AppearanceSettingsState
import ui.clipboard.setPlainText
import app.skipi.ui.settings.SkipiAppearanceSettingsScreen
import features.settings.sheets.AppIconSelectionBottomSheet
import features.settings.sheets.appIconDescriptorForMode
import kotlinx.coroutines.launch
import app.modes.BackgroundStyleClassic
import app.modes.BackgroundStylePhoto
import ui.background.clearCustomBackgroundPhoto
import ui.background.customBackgroundPhotoExists
import ui.background.saveCustomBackgroundPhoto
import ui.KeyColors
import ui.keyColorFor
import ui.resolveSystemAccentColor

/** Android adapter for appearance persistence and platform-only photo/icon actions. */
@Composable
fun SettingsAppearancePage(padding: PaddingValues) {
    val context = LocalContext.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var showIconPicker by remember { mutableStateOf(false) }

    val appearance = appState.toAppearanceSettingsState()
    SkipiAppearanceSettingsScreen(
        state = appearance,
        padding = padding,
        isWideScreen = isWideScreen,
        // Read the update seed so the page follows a photo that was just saved or removed.
        hasCustomBackgroundPhoto = remember(ui.background.backgroundPhotoUpdateSeed) { customBackgroundPhotoExists(context) },
        appIconTitle = stringResource(appIconDescriptorForMode(appState.appIcon).titleRes),
        onSettingsChange = { transform -> updateAppState { current -> current.withAppearanceSettings(transform(current.toAppearanceSettingsState())) } },
        onBack = { navigator.pop() },
        onChooseBackgroundPhoto = {
            scope.launch {
                val uri = services.photoFilePicker()
                if (uri != null) {
                    if (saveCustomBackgroundPhoto(context, uri)) {
                        updateAppState { it.copy(backgroundStyle = BackgroundStylePhoto) }
                    } else {
                        services.tipNotifier.show(context.getString(R.string.settings_background_photo_failed))
                    }
                }
            }
        },
        onRemoveBackgroundPhoto = {
            clearCustomBackgroundPhoto(context)
            updateAppState { it.copy(backgroundStyle = BackgroundStyleClassic) }
        },
        onRequestAppIconSelection = { showIconPicker = true },
        onColorsReset = { message -> scope.launch { services.tipNotifier.show(message) } },
        onCopyText = { text, confirmation ->
            scope.launch {
                clipboard.setPlainText(text)
                services.tipNotifier.show(confirmation)
            }
        },
    )

    AppIconSelectionBottomSheet(
        show = showIconPicker,
        selectedIconMode = appState.appIcon,
        onSelectIconMode = { mode -> updateAppState { it.copy(appIcon = mode) } },
        onDismissRequest = { showIconPicker = false },
    )
}

private fun app.AppState.toAppearanceSettingsState() = AppearanceSettingsState(
    appIcon = appIcon,
    backgroundPhotoDimPercent = backgroundPhotoDimPercent,
    backgroundStyle = backgroundStyle,
    bottomBarSize = bottomBarSize,
    classicButtonSize = classicButtonSize,
    customThemes = customThemes,
    classicShowFloatingPowerButton = classicShowFloatingPowerButton,
    colorMode = colorMode,
    connectionDisplayMode = connectionDisplayMode,
    customAccentColor = customAccentColor,
    customBackgroundColor = customBackgroundColor,
    customCategoryIconColor = customCategoryIconColor,
    customCategoryIconGlyphColor = customCategoryIconGlyphColor,
    customMaterialYouSeed = customMaterialYouSeed,
    customPingFastColor = customPingFastColor,
    customPingMediumColor = customPingMediumColor,
    customPingSlowColor = customPingSlowColor,
    customProtocolChainColor = customProtocolChainColor,
    customProtocolHysteria2Color = customProtocolHysteria2Color,
    customProtocolHttpColor = customProtocolHttpColor,
    customProtocolJsonColor = customProtocolJsonColor,
    customProtocolShadowsocksColor = customProtocolShadowsocksColor,
    customProtocolSocksColor = customProtocolSocksColor,
    customProtocolStrategyColor = customProtocolStrategyColor,
    customProtocolTrojanColor = customProtocolTrojanColor,
    customProtocolVlessColor = customProtocolVlessColor,
    customProtocolVmessColor = customProtocolVmessColor,
    customProtocolWireguardColor = customProtocolWireguardColor,
    customStatusRunningColor = customStatusRunningColor,
    customStatusStoppedColor = customStatusStoppedColor,
    customSurfaceColor = customSurfaceColor,
    customSurfaceVariantColor = customSurfaceVariantColor,
    customTextColor = customTextColor,
    customTextSecondaryColor = customTextSecondaryColor,
    enableAllProxyGroup = enableAllProxyGroup,
    enableCustomColors = enableCustomColors,
    enableMaterialYou = enableMaterialYou,
    enableSubscriptionSwipe = enableSubscriptionSwipe,
    fontFamilyMode = fontFamilyMode,
    fontSizeMode = fontSizeMode,
    fontWeightMode = fontWeightMode,
    pinConnectionPanelOnHome = pinConnectionPanelOnHome,
    seedIndex = seedIndex,
    showServerSearch = showServerSearch,
    showTunnelMemoryOnHome = showTunnelMemoryOnHome,
    showTunnelIpOnHome = showTunnelIpOnHome,
    maskTunnelIpOnHome = maskTunnelIpOnHome,
)

private fun app.AppState.withAppearanceSettings(settings: AppearanceSettingsState) = copy(
    appIcon = settings.appIcon,
    backgroundPhotoDimPercent = settings.backgroundPhotoDimPercent,
    backgroundStyle = settings.backgroundStyle,
    bottomBarSize = settings.bottomBarSize,
    classicButtonSize = settings.classicButtonSize,
    customThemes = settings.customThemes,
    classicShowFloatingPowerButton = settings.classicShowFloatingPowerButton,
    colorMode = settings.colorMode,
    connectionDisplayMode = settings.connectionDisplayMode,
    customAccentColor = settings.customAccentColor,
    customBackgroundColor = settings.customBackgroundColor,
    customCategoryIconColor = settings.customCategoryIconColor,
    customCategoryIconGlyphColor = settings.customCategoryIconGlyphColor,
    customMaterialYouSeed = settings.customMaterialYouSeed,
    customPingFastColor = settings.customPingFastColor,
    customPingMediumColor = settings.customPingMediumColor,
    customPingSlowColor = settings.customPingSlowColor,
    customProtocolChainColor = settings.customProtocolChainColor,
    customProtocolHysteria2Color = settings.customProtocolHysteria2Color,
    customProtocolHttpColor = settings.customProtocolHttpColor,
    customProtocolJsonColor = settings.customProtocolJsonColor,
    customProtocolShadowsocksColor = settings.customProtocolShadowsocksColor,
    customProtocolSocksColor = settings.customProtocolSocksColor,
    customProtocolStrategyColor = settings.customProtocolStrategyColor,
    customProtocolTrojanColor = settings.customProtocolTrojanColor,
    customProtocolVlessColor = settings.customProtocolVlessColor,
    customProtocolVmessColor = settings.customProtocolVmessColor,
    customProtocolWireguardColor = settings.customProtocolWireguardColor,
    customStatusRunningColor = settings.customStatusRunningColor,
    customStatusStoppedColor = settings.customStatusStoppedColor,
    customSurfaceColor = settings.customSurfaceColor,
    customSurfaceVariantColor = settings.customSurfaceVariantColor,
    customTextColor = settings.customTextColor,
    customTextSecondaryColor = settings.customTextSecondaryColor,
    enableAllProxyGroup = settings.enableAllProxyGroup,
    enableCustomColors = settings.enableCustomColors,
    enableMaterialYou = settings.enableMaterialYou,
    enableSubscriptionSwipe = settings.enableSubscriptionSwipe,
    fontFamilyMode = settings.fontFamilyMode,
    fontSizeMode = settings.fontSizeMode,
    fontWeightMode = settings.fontWeightMode,
    pinConnectionPanelOnHome = settings.pinConnectionPanelOnHome,
    seedIndex = settings.seedIndex,
    showServerSearch = settings.showServerSearch,
    showTunnelMemoryOnHome = settings.showTunnelMemoryOnHome,
    showTunnelIpOnHome = settings.showTunnelIpOnHome,
    maskTunnelIpOnHome = settings.maskTunnelIpOnHome,
)
