// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import app.skipi.ui.resources.*
import androidx.compose.animation.AnimatedVisibility
import app.skipi.ui.components.SkipiDropdownPreference
import app.skipi.ui.components.ColorPickerDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.StatusColorDefaults
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.skipi.ui.theme.LocalAppColors

/** Colour mode, fonts, app icon and the Material You switches. */
@Composable
internal fun AppearanceThemeSection(
    state: AppearanceSettingsState,
    onSettingsChange: ((AppearanceSettingsState) -> AppearanceSettingsState) -> Unit,
    capabilities: AppearanceSettingsCapabilities,
    appIconTitle: String,
    onRequestAppIconSelection: () -> Unit,
    onPickColor: (AppearanceColorTarget) -> Unit,
) {
    val appState = state
    val updateAppState = onSettingsChange
    val colorModeOptions = listOf(
        AppearanceSettingValues.ColorModeSystem to stringResource(Res.string.option_follow_system),
        AppearanceSettingValues.ColorModeLight to stringResource(Res.string.option_light),
        AppearanceSettingValues.ColorModeDark to stringResource(Res.string.option_dark),
        AppearanceSettingValues.ColorModeAmoled to stringResource(Res.string.option_amoled),
        AppearanceSettingValues.ColorModeAurora to stringResource(Res.string.option_theme_aurora),
        AppearanceSettingValues.ColorModeSakura to stringResource(Res.string.option_theme_sakura),
        AppearanceSettingValues.ColorModeForest to stringResource(Res.string.option_theme_forest),
        AppearanceSettingValues.ColorModeSunset to stringResource(Res.string.option_theme_sunset),
    ).filter { capabilities.systemTheme || it.first != AppearanceSettingValues.ColorModeSystem }

    val selectedColorModeIndex = colorModeOptions.indexOfFirst { (mode) ->
        AppearanceSettingValues.normalizeColorMode(mode) == AppearanceSettingValues.normalizeColorMode(appState.colorMode)
    }.coerceAtLeast(0)

    val isNamedThemeSelected = AppearanceSettingValues.isNamedColorTheme(appState.colorMode)

    val fontFamilyOptions = listOf(
        stringResource(Res.string.settings_font_family_default),
        stringResource(Res.string.settings_font_family_inter),
        stringResource(Res.string.settings_font_family_golos_text),
        stringResource(Res.string.settings_font_family_manrope),
        stringResource(Res.string.settings_font_family_jetbrains_mono),
        stringResource(Res.string.settings_font_family_climate_crisis),
        stringResource(Res.string.settings_font_family_unbounded),
        stringResource(Res.string.settings_font_family_onest),
    )

    val fontWeightOptions = listOf(
        stringResource(Res.string.settings_font_weight_default),
        stringResource(Res.string.settings_font_weight_light),
        stringResource(Res.string.settings_font_weight_normal),
        stringResource(Res.string.settings_font_weight_medium),
        stringResource(Res.string.settings_font_weight_semibold),
        stringResource(Res.string.settings_font_weight_bold),
    )

    SmallTitle(text = stringResource(Res.string.settings_theme))
    SkipiSettingsSectionCard {
        SkipiDropdownPreference(
            title = stringResource(Res.string.settings_color_mode),
            summary = if (isNamedThemeSelected) {
                stringResource(Res.string.settings_theme_preset_summary)
            } else {
                stringResource(Res.string.settings_color_mode_summary)
            },
            items = colorModeOptions.map { (_, label) -> label },
            selectedIndex = selectedColorModeIndex,
            onSelectedIndexChange = { index ->
                colorModeOptions.getOrNull(index)?.first?.let { mode ->
                    updateAppState { it.copy(colorMode = mode) }
                }
            },
        )
        if (capabilities.fonts) {
            SkipiDropdownPreference(
                title = stringResource(Res.string.settings_font_family),
                items = fontFamilyOptions,
                selectedIndex = appState.fontFamilyMode.coerceIn(0, fontFamilyOptions.lastIndex),
                onSelectedIndexChange = { index -> updateAppState { it.copy(fontFamilyMode = index) } },
            )
            FontSizeSliderPreference(
                fontSizeMode = appState.fontSizeMode,
                onFontSizeModeChange = { mode ->
                    updateAppState { it.copy(fontSizeMode = mode) }
                },
            )
            AnimatedVisibility(
                visible = AppearanceSettingValues.isFontWeightSupported(appState.fontFamilyMode),
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SkipiDropdownPreference(
                    title = stringResource(Res.string.settings_font_weight),
                    items = fontWeightOptions,
                    selectedIndex = appState.fontWeightMode.coerceIn(0, fontWeightOptions.lastIndex),
                    onSelectedIndexChange = { index -> updateAppState { it.copy(fontWeightMode = index) } },
                )
            }
        }
        if (capabilities.appIcon) {
            ArrowPreference(
                title = stringResource(Res.string.settings_app_icon),
                summary = appIconTitle,
                onClick = onRequestAppIconSelection,
            )
        }
        AnimatedVisibility(
            visible = capabilities.dynamicColors && !isNamedThemeSelected,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // Only the system accent: no seed colour to choose. The seed index goes back to "system", which also
            // clears a colour that onboarding picked.
            SwitchPreference(
                title = stringResource(Res.string.settings_enable_material_you),
                summary = stringResource(Res.string.settings_enable_material_you_summary),
                checked = appState.enableMaterialYou,
                onCheckedChange = { enabled ->
                    updateAppState { it.copy(enableMaterialYou = enabled, seedIndex = if (enabled) 0 else it.seedIndex) }
                },
            )
        }
        AnimatedVisibility(
            visible = capabilities.customColors && !appState.enableMaterialYou && !isNamedThemeSelected,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SettingsColorItem(
                title = stringResource(Res.string.settings_color_accent),
                summary = stringResource(Res.string.settings_color_accent_summary),
                color = appState.customAccentColor?.let { Color(it) } ?: SkipiTheme.colors.accent,
                onClick = { onPickColor(AppearanceColorTarget.ACCENT) },
            )
        }
    }
}

/** Background style, photo and dimming. */
@Composable
internal fun AppearanceBackgroundSection(
    state: AppearanceSettingsState,
    onSettingsChange: ((AppearanceSettingsState) -> AppearanceSettingsState) -> Unit,
    capabilities: AppearanceSettingsCapabilities,
    hasCustomBackgroundPhoto: Boolean,
    onChooseBackgroundPhoto: () -> Unit,
    onRemoveBackgroundPhoto: () -> Unit,
) {
    val appState = state
    val updateAppState = onSettingsChange

    val backgroundStyleOptions = listOf(
        stringResource(Res.string.settings_background_style_classic),
        stringResource(Res.string.settings_background_style_photo),
        stringResource(Res.string.settings_background_style_connection),
        stringResource(Res.string.settings_background_style_aurora),
    )

    val backgroundDimOptions = remember { listOf(0, 25, 45, 60, 75) }
    val backgroundDimLabels = remember { listOf("0%", "25%", "45%", "60%", "75%") }

    SmallTitle(text = stringResource(Res.string.settings_background_title))
    SkipiSettingsSectionCard {
        SkipiDropdownPreference(
            title = stringResource(Res.string.settings_background_style),
            items = backgroundStyleOptions,
            selectedIndex = appState.backgroundStyle.coerceIn(0, backgroundStyleOptions.lastIndex),
            onSelectedIndexChange = { index ->
                val newStyle = AppearanceSettingValues.normalizeBackgroundStyle(index)
                updateAppState { it.copy(backgroundStyle = newStyle) }
                if (newStyle == AppearanceSettingValues.BackgroundStylePhoto && !hasCustomBackgroundPhoto) {
                    onChooseBackgroundPhoto()
                }
            },
        )
        AnimatedVisibility(
            visible = appState.backgroundStyle == AppearanceSettingValues.BackgroundStylePhoto,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(Res.string.settings_background_photo_pick),
                    onClick = onChooseBackgroundPhoto,
                )
                SkipiDropdownPreference(
                    title = stringResource(Res.string.settings_background_dim),
                    items = backgroundDimLabels,
                    selectedIndex = backgroundDimOptions.indexOf(appState.backgroundPhotoDimPercent).coerceIn(0, backgroundDimOptions.lastIndex),
                    onSelectedIndexChange = { index ->
                        updateAppState {
                            it.copy(backgroundPhotoDimPercent = backgroundDimOptions[index])
                        }
                    },
                )
                if (hasCustomBackgroundPhoto) {
                    ArrowPreference(
                        title = stringResource(Res.string.settings_background_photo_remove),
                        onClick = {
                            onRemoveBackgroundPhoto()
                            updateAppState { it.copy(backgroundStyle = AppearanceSettingValues.BackgroundStyleClassic) }
                        },
                    )
                }
            }
        }
    }
}

/** The custom colours, status colours, category icons and per-protocol colours. */
@Composable
internal fun AppearanceCustomColorsSection(
    state: AppearanceSettingsState,
    onSettingsChange: ((AppearanceSettingsState) -> AppearanceSettingsState) -> Unit,
    capabilities: AppearanceSettingsCapabilities,
    onColorsReset: (String) -> Unit,
    onPickColor: (AppearanceColorTarget) -> Unit,
) {
    val appState = state
    val updateAppState = onSettingsChange
    val isDark = LocalAppColors.current.isDark

    val resetCompletedMessage = stringResource(Res.string.settings_colors_reset_completed)

    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = stringResource(Res.string.settings_custom_colors_title))
        SkipiSettingsSectionCard {
            SwitchPreference(
                title = stringResource(Res.string.settings_enable_custom_colors),
                summary = stringResource(Res.string.settings_enable_custom_colors_summary),
                checked = appState.enableCustomColors,
                onCheckedChange = { enabled ->
                    updateAppState { it.copy(enableCustomColors = enabled) }
                },
            )
            AnimatedVisibility(
                visible = appState.enableCustomColors,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // With the system accent on, an accent chosen here would be ignored, so it is not offered.
                    if (!(capabilities.dynamicColors && appState.enableMaterialYou)) {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_accent),
                            summary = stringResource(Res.string.settings_color_accent_summary),
                            color = appState.customAccentColor?.let { Color(it) } ?: SkipiTheme.colors.accent,
                            onClick = { onPickColor(AppearanceColorTarget.ACCENT) },
                        )
                    }
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_background),
                        summary = stringResource(Res.string.settings_color_background_summary),
                        color = appState.customBackgroundColor?.let { Color(it) } ?: SkipiTheme.colors.background,
                        onClick = { onPickColor(AppearanceColorTarget.BACKGROUND) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_surface),
                        summary = stringResource(Res.string.settings_color_surface_summary),
                        color = appState.customSurfaceColor?.let { Color(it) } ?: SkipiTheme.colors.surface,
                        onClick = { onPickColor(AppearanceColorTarget.SURFACE) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_surface_variant),
                        summary = stringResource(Res.string.settings_color_surface_variant_summary),
                        color = appState.customSurfaceVariantColor?.let { Color(it) } ?: SkipiTheme.colors.surfaceVariant,
                        onClick = { onPickColor(AppearanceColorTarget.SURFACE_VARIANT) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_text),
                        summary = stringResource(Res.string.settings_color_text_summary),
                        color = appState.customTextColor?.let { Color(it) } ?: SkipiTheme.colors.onSurface,
                        onClick = { onPickColor(AppearanceColorTarget.TEXT) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_text_secondary),
                        summary = stringResource(Res.string.settings_color_text_secondary_summary),
                        color = appState.customTextSecondaryColor?.let { Color(it) } ?: SkipiTheme.colors.onSurfaceVariant,
                        onClick = { onPickColor(AppearanceColorTarget.TEXT_SECONDARY) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = appState.enableCustomColors,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SmallTitle(text = stringResource(Res.string.settings_custom_colors_status_title))
                SkipiSettingsSectionCard {
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_status_running),
                        summary = stringResource(Res.string.settings_color_status_running_summary),
                        color = appState.customStatusRunningColor?.let { Color(it) } ?: StatusColorDefaults.statusRunning(isDark),
                        onClick = { onPickColor(AppearanceColorTarget.STATUS_RUNNING) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_status_stopped),
                        summary = stringResource(Res.string.settings_color_status_stopped_summary),
                        color = appState.customStatusStoppedColor?.let { Color(it) } ?: StatusColorDefaults.statusStopped(isDark),
                        onClick = { onPickColor(AppearanceColorTarget.STATUS_STOPPED) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_ping_fast),
                        summary = stringResource(Res.string.settings_color_ping_fast_summary),
                        color = appState.customPingFastColor?.let { Color(it) } ?: StatusColorDefaults.pingFast(isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PING_FAST) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_ping_medium),
                        summary = stringResource(Res.string.settings_color_ping_medium_summary),
                        color = appState.customPingMediumColor?.let { Color(it) } ?: StatusColorDefaults.pingMedium(isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PING_MEDIUM) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_ping_slow),
                        summary = stringResource(Res.string.settings_color_ping_slow_summary),
                        color = appState.customPingSlowColor?.let { Color(it) } ?: StatusColorDefaults.pingSlow(isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PING_SLOW) },
                    )
                }

                SmallTitle(text = stringResource(Res.string.settings_custom_colors_categories_title))
                SkipiSettingsSectionCard {
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_category_icons),
                        summary = stringResource(Res.string.settings_color_category_icons_summary),
                        color = appState.customCategoryIconColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        onClick = { onPickColor(AppearanceColorTarget.CATEGORY_ICONS) },
                    )
                }

                SmallTitle(text = stringResource(Res.string.settings_custom_colors_protocols_title))
                SkipiSettingsSectionCard {
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_vless),
                        color = appearanceProtocolColor("vless", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_VLESS) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_vmess),
                        color = appearanceProtocolColor("vmess", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_VMESS) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_hysteria2),
                        color = appearanceProtocolColor("hysteria2", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_HYSTERIA2) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_trojan),
                        color = appearanceProtocolColor("trojan", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_TROJAN) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_shadowsocks),
                        color = appearanceProtocolColor("shadowsocks", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_SHADOWSOCKS) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_wireguard),
                        color = appearanceProtocolColor("wireguard", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_WIREGUARD) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_socks),
                        color = appearanceProtocolColor("socks", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_SOCKS) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_http),
                        color = appearanceProtocolColor("http", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_HTTP) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_strategy),
                        color = appearanceProtocolColor("strategy", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_STRATEGY) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_chain),
                        color = appearanceProtocolColor("chain", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_CHAIN) },
                    )
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_protocol_json),
                        color = appearanceProtocolColor("json", appState, isDark),
                        onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_JSON) },
                    )
                    ArrowPreference(
                        title = stringResource(Res.string.settings_colors_reset),
                        summary = stringResource(Res.string.settings_colors_reset_summary),
                        onClick = {
                            updateAppState { state ->
                                state.copy(
                                    customAccentColor = null,
                                    customBackgroundColor = null,
                                    customSurfaceColor = null,
                                    customSurfaceVariantColor = null,
                                    customTextColor = null,
                                    customTextSecondaryColor = null,
                                    customStatusRunningColor = null,
                                    customStatusStoppedColor = null,
                                    customPingFastColor = null,
                                    customPingMediumColor = null,
                                    customPingSlowColor = null,
                                    customCategoryIconColor = null,
                                    customProtocolVlessColor = null,
                                    customProtocolVmessColor = null,
                                    customProtocolHysteria2Color = null,
                                    customProtocolTrojanColor = null,
                                    customProtocolShadowsocksColor = null,
                                    customProtocolWireguardColor = null,
                                    customProtocolSocksColor = null,
                                    customProtocolHttpColor = null,
                                    customProtocolStrategyColor = null,
                                    customProtocolChainColor = null,
                                    customProtocolJsonColor = null,
                                )
                            }
                            onColorsReset(resetCompletedMessage)
                        },
                    )
                }
            }
        }
    }
}

/** Navigation, Home layout and list options. */
@Composable
internal fun AppearanceLayoutSection(
    state: AppearanceSettingsState,
    onSettingsChange: ((AppearanceSettingsState) -> AppearanceSettingsState) -> Unit,
    capabilities: AppearanceSettingsCapabilities,
) {
    val appState = state
    val updateAppState = onSettingsChange

    val bottomBarSizeOptions = listOf(
        stringResource(Res.string.settings_bottom_bar_size_small),
        stringResource(Res.string.settings_bottom_bar_size_medium),
        stringResource(Res.string.settings_bottom_bar_size_large),
    )

    val classicButtonSizeOptions = listOf(
        stringResource(Res.string.settings_classic_button_size_small),
        stringResource(Res.string.settings_classic_button_size_medium),
        stringResource(Res.string.settings_classic_button_size_large),
        stringResource(Res.string.settings_classic_button_size_giant),
    )

    val connectionDisplayModeOptions = listOf(
        stringResource(Res.string.settings_connection_display_mode_compact),
        stringResource(Res.string.settings_connection_display_mode_classic),
        stringResource(Res.string.settings_connection_display_mode_legacy),
    )

    SmallTitle(text = stringResource(Res.string.settings_header_layout))
    SkipiSettingsSectionCard {
        if (capabilities.navigationStyle) {
            SkipiDropdownPreference(
                title = stringResource(Res.string.settings_navigation_style),
                summary = stringResource(Res.string.settings_navigation_style_summary),
                items = listOf(
                    stringResource(Res.string.settings_navigation_style_bottom),
                    stringResource(Res.string.settings_navigation_style_sidebar),
                ),
                selectedIndex = appState.navigationStyle.coerceIn(0, 1),
                onSelectedIndexChange = { style ->
                    updateAppState { it.copy(navigationStyle = style) }
                },
            )
        }
        if (capabilities.bottomBarSize && appState.navigationStyle == 0) {
            SkipiDropdownPreference(
                title = stringResource(Res.string.settings_bottom_bar_size),
                summary = stringResource(Res.string.settings_bottom_bar_size_summary),
                items = bottomBarSizeOptions,
                selectedIndex = appState.bottomBarSize,
                onSelectedIndexChange = { size ->
                    updateAppState { it.copy(bottomBarSize = size) }
                },
            )
        }
        SkipiDropdownPreference(
            title = stringResource(Res.string.settings_connection_display_mode),
            summary = stringResource(Res.string.settings_connection_display_mode_summary),
            items = connectionDisplayModeOptions,
            selectedIndex = appState.connectionDisplayMode.coerceIn(0, connectionDisplayModeOptions.lastIndex),
            onSelectedIndexChange = { index ->
                val mode = when (index) {
                    0 -> AppearanceSettingValues.ConnectionDisplayModeCompact
                    2 -> AppearanceSettingValues.ConnectionDisplayModeLegacy
                    else -> AppearanceSettingValues.ConnectionDisplayModeClassic
                }
                updateAppState { it.copy(connectionDisplayMode = mode) }
            },
        )
        AnimatedVisibility(
            visible = appState.connectionDisplayMode != AppearanceSettingValues.ConnectionDisplayModeCompact,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                // The size applies to the classic widget only; the legacy card has a fixed layout.
                AnimatedVisibility(
                    visible = appState.connectionDisplayMode == AppearanceSettingValues.ConnectionDisplayModeClassic,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SkipiDropdownPreference(
                        title = stringResource(Res.string.settings_classic_button_size),
                        summary = stringResource(Res.string.settings_classic_button_size_summary),
                        items = classicButtonSizeOptions,
                        selectedIndex = appState.classicButtonSize.coerceIn(0, classicButtonSizeOptions.lastIndex),
                        onSelectedIndexChange = { size ->
                            updateAppState { it.copy(classicButtonSize = size) }
                        },
                    )
                }
                if (capabilities.floatingPowerButton) {
                    SwitchPreference(
                        title = stringResource(Res.string.settings_classic_show_floating_power_button),
                        summary = stringResource(Res.string.settings_classic_show_floating_power_button_summary),
                        checked = appState.classicShowFloatingPowerButton,
                        onCheckedChange = { enabled ->
                            updateAppState { it.copy(classicShowFloatingPowerButton = enabled) }
                        },
                    )
                }
            }
        }
        SwitchPreference(
            title = stringResource(Res.string.settings_pin_connection_panel),
            summary = stringResource(Res.string.settings_pin_connection_panel_summary),
            checked = appState.pinConnectionPanelOnHome,
            onCheckedChange = { enabled ->
                updateAppState { it.copy(pinConnectionPanelOnHome = enabled) }
            },
        )
        if (capabilities.subscriptionSwipe) {
            SwitchPreference(
                title = stringResource(Res.string.settings_enable_subscription_swipe),
                summary = stringResource(Res.string.settings_enable_subscription_swipe_summary),
                checked = appState.enableSubscriptionSwipe,
                onCheckedChange = { enabled ->
                    updateAppState { it.copy(enableSubscriptionSwipe = enabled) }
                },
            )
        }
        SwitchPreference(
            title = stringResource(Res.string.settings_tunnel_memory_show_on_home),
            summary = stringResource(Res.string.settings_tunnel_memory_show_on_home_summary),
            checked = appState.showTunnelMemoryOnHome,
            onCheckedChange = { enabled ->
                updateAppState { it.copy(showTunnelMemoryOnHome = enabled) }
            },
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_tunnel_ip_show_on_home),
            summary = stringResource(Res.string.settings_tunnel_ip_show_on_home_summary),
            checked = appState.showTunnelIpOnHome,
            onCheckedChange = { enabled ->
                updateAppState { it.copy(showTunnelIpOnHome = enabled) }
            },
        )
        if (appState.showTunnelIpOnHome) {
            SwitchPreference(
                title = stringResource(Res.string.settings_tunnel_ip_mask_on_home),
                summary = stringResource(Res.string.settings_tunnel_ip_mask_on_home_summary),
                checked = appState.maskTunnelIpOnHome,
                onCheckedChange = { enabled ->
                    updateAppState { it.copy(maskTunnelIpOnHome = enabled) }
                },
            )
        }
        if (capabilities.connectionLog) {
            SwitchPreference(
                title = stringResource(Res.string.settings_connection_log_show_on_home),
                summary = stringResource(Res.string.settings_connection_log_show_on_home_summary),
                checked = appState.showConnectionLogOnHome,
                onCheckedChange = { enabled ->
                    updateAppState { it.copy(showConnectionLogOnHome = enabled) }
                },
            )
        }
        SwitchPreference(
            title = stringResource(Res.string.settings_enable_all_proxy_group),
            summary = stringResource(Res.string.settings_enable_all_proxy_group_summary),
            checked = appState.enableAllProxyGroup,
            onCheckedChange = { enabled -> updateAppState { it.copy(enableAllProxyGroup = enabled) } },
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_show_server_search),
            summary = stringResource(Res.string.settings_show_server_search_summary),
            checked = appState.showServerSearch,
            onCheckedChange = { enabled -> updateAppState { it.copy(showServerSearch = enabled) } },
        )
    }
}

/** The colour picker for the colour the user chose to edit; nothing is shown while [target] is null. */
@Composable
internal fun AppearanceColorPickerDialog(
    target: AppearanceColorTarget?,
    state: AppearanceSettingsState,
    onSettingsChange: ((AppearanceSettingsState) -> AppearanceSettingsState) -> Unit,
    onDismiss: () -> Unit,
) {
    val appState = state
    val updateAppState = onSettingsChange
    val isDark = LocalAppColors.current.isDark
    val pickerTarget = target ?: return

        val dialogTitle = when (pickerTarget) {
            AppearanceColorTarget.ACCENT -> stringResource(Res.string.settings_color_accent)
            AppearanceColorTarget.BACKGROUND -> stringResource(Res.string.settings_color_background)
            AppearanceColorTarget.SURFACE -> stringResource(Res.string.settings_color_surface)
            AppearanceColorTarget.SURFACE_VARIANT -> stringResource(Res.string.settings_color_surface_variant)
            AppearanceColorTarget.TEXT -> stringResource(Res.string.settings_color_text)
            AppearanceColorTarget.TEXT_SECONDARY -> stringResource(Res.string.settings_color_text_secondary)
            AppearanceColorTarget.STATUS_RUNNING -> stringResource(Res.string.settings_color_status_running)
            AppearanceColorTarget.STATUS_STOPPED -> stringResource(Res.string.settings_color_status_stopped)
            AppearanceColorTarget.PING_FAST -> stringResource(Res.string.settings_color_ping_fast)
            AppearanceColorTarget.PING_MEDIUM -> stringResource(Res.string.settings_color_ping_medium)
            AppearanceColorTarget.PING_SLOW -> stringResource(Res.string.settings_color_ping_slow)
            AppearanceColorTarget.CATEGORY_ICONS -> stringResource(Res.string.settings_color_category_icons)
            AppearanceColorTarget.PROTOCOL_VLESS -> stringResource(Res.string.settings_color_protocol_vless)
            AppearanceColorTarget.PROTOCOL_VMESS -> stringResource(Res.string.settings_color_protocol_vmess)
            AppearanceColorTarget.PROTOCOL_HYSTERIA2 -> stringResource(Res.string.settings_color_protocol_hysteria2)
            AppearanceColorTarget.PROTOCOL_TROJAN -> stringResource(Res.string.settings_color_protocol_trojan)
            AppearanceColorTarget.PROTOCOL_SHADOWSOCKS -> stringResource(Res.string.settings_color_protocol_shadowsocks)
            AppearanceColorTarget.PROTOCOL_WIREGUARD -> stringResource(Res.string.settings_color_protocol_wireguard)
            AppearanceColorTarget.PROTOCOL_SOCKS -> stringResource(Res.string.settings_color_protocol_socks)
            AppearanceColorTarget.PROTOCOL_HTTP -> stringResource(Res.string.settings_color_protocol_http)
            AppearanceColorTarget.PROTOCOL_STRATEGY -> stringResource(Res.string.settings_color_protocol_strategy)
            AppearanceColorTarget.PROTOCOL_CHAIN -> stringResource(Res.string.settings_color_protocol_chain)
            AppearanceColorTarget.PROTOCOL_JSON -> stringResource(Res.string.settings_color_protocol_json)
        }
        val initialColor = when (pickerTarget) {
            AppearanceColorTarget.ACCENT -> appState.customAccentColor?.let { Color(it) } ?: SkipiTheme.colors.accent
            AppearanceColorTarget.BACKGROUND -> appState.customBackgroundColor?.let { Color(it) } ?: SkipiTheme.colors.background
            AppearanceColorTarget.SURFACE -> appState.customSurfaceColor?.let { Color(it) } ?: SkipiTheme.colors.surface
            AppearanceColorTarget.SURFACE_VARIANT -> appState.customSurfaceVariantColor?.let { Color(it) } ?: SkipiTheme.colors.surfaceVariant
            AppearanceColorTarget.TEXT -> appState.customTextColor?.let { Color(it) } ?: SkipiTheme.colors.onSurface
            AppearanceColorTarget.TEXT_SECONDARY -> appState.customTextSecondaryColor?.let { Color(it) } ?: SkipiTheme.colors.onSurfaceVariant
            AppearanceColorTarget.STATUS_RUNNING -> appState.customStatusRunningColor?.let { Color(it) } ?: StatusColorDefaults.statusRunning(isDark)
            AppearanceColorTarget.STATUS_STOPPED -> appState.customStatusStoppedColor?.let { Color(it) } ?: StatusColorDefaults.statusStopped(isDark)
            AppearanceColorTarget.PING_FAST -> appState.customPingFastColor?.let { Color(it) } ?: StatusColorDefaults.pingFast(isDark)
            AppearanceColorTarget.PING_MEDIUM -> appState.customPingMediumColor?.let { Color(it) } ?: StatusColorDefaults.pingMedium(isDark)
            AppearanceColorTarget.PING_SLOW -> appState.customPingSlowColor?.let { Color(it) } ?: StatusColorDefaults.pingSlow(isDark)
            AppearanceColorTarget.CATEGORY_ICONS -> appState.customCategoryIconColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary
            AppearanceColorTarget.PROTOCOL_VLESS -> appearanceProtocolColor("vless", appState, isDark)
            AppearanceColorTarget.PROTOCOL_VMESS -> appearanceProtocolColor("vmess", appState, isDark)
            AppearanceColorTarget.PROTOCOL_HYSTERIA2 -> appearanceProtocolColor("hysteria2", appState, isDark)
            AppearanceColorTarget.PROTOCOL_TROJAN -> appearanceProtocolColor("trojan", appState, isDark)
            AppearanceColorTarget.PROTOCOL_SHADOWSOCKS -> appearanceProtocolColor("shadowsocks", appState, isDark)
            AppearanceColorTarget.PROTOCOL_WIREGUARD -> appearanceProtocolColor("wireguard", appState, isDark)
            AppearanceColorTarget.PROTOCOL_SOCKS -> appearanceProtocolColor("socks", appState, isDark)
            AppearanceColorTarget.PROTOCOL_HTTP -> appearanceProtocolColor("http", appState, isDark)
            AppearanceColorTarget.PROTOCOL_STRATEGY -> appearanceProtocolColor("strategy", appState, isDark)
            AppearanceColorTarget.PROTOCOL_CHAIN -> appearanceProtocolColor("chain", appState, isDark)
            AppearanceColorTarget.PROTOCOL_JSON -> appearanceProtocolColor("json", appState, isDark)
        }
        ColorPickerDialog(
            show = true,
            title = dialogTitle,
            initialColor = initialColor,
            onDismissRequest = onDismiss,
            onColorSelected = { selectedColor ->
                val colorLong = selectedColor.toArgb().toLong() and 0xFFFFFFFFL
                updateAppState { state ->
                    when (pickerTarget) {
                        AppearanceColorTarget.ACCENT -> state.copy(customAccentColor = colorLong)
                        AppearanceColorTarget.BACKGROUND -> state.copy(customBackgroundColor = colorLong)
                        AppearanceColorTarget.SURFACE -> state.copy(customSurfaceColor = colorLong)
                        AppearanceColorTarget.SURFACE_VARIANT -> state.copy(customSurfaceVariantColor = colorLong)
                        AppearanceColorTarget.TEXT -> state.copy(customTextColor = colorLong)
                        AppearanceColorTarget.TEXT_SECONDARY -> state.copy(customTextSecondaryColor = colorLong)
                        AppearanceColorTarget.STATUS_RUNNING -> state.copy(customStatusRunningColor = colorLong)
                        AppearanceColorTarget.STATUS_STOPPED -> state.copy(customStatusStoppedColor = colorLong)
                        AppearanceColorTarget.PING_FAST -> state.copy(customPingFastColor = colorLong)
                        AppearanceColorTarget.PING_MEDIUM -> state.copy(customPingMediumColor = colorLong)
                        AppearanceColorTarget.PING_SLOW -> state.copy(customPingSlowColor = colorLong)
                        AppearanceColorTarget.CATEGORY_ICONS -> state.copy(customCategoryIconColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_VLESS -> state.copy(customProtocolVlessColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_VMESS -> state.copy(customProtocolVmessColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_HYSTERIA2 -> state.copy(customProtocolHysteria2Color = colorLong)
                        AppearanceColorTarget.PROTOCOL_TROJAN -> state.copy(customProtocolTrojanColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_SHADOWSOCKS -> state.copy(customProtocolShadowsocksColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_WIREGUARD -> state.copy(customProtocolWireguardColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_SOCKS -> state.copy(customProtocolSocksColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_HTTP -> state.copy(customProtocolHttpColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_STRATEGY -> state.copy(customProtocolStrategyColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_CHAIN -> state.copy(customProtocolChainColor = colorLong)
                        AppearanceColorTarget.PROTOCOL_JSON -> state.copy(customProtocolJsonColor = colorLong)
                    }
                }
            },
        )
}
