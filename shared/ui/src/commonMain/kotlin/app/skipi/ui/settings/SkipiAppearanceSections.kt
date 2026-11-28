// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import app.skipi.ui.settings.search.SettingsSearchAnchor
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.isSpecified
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

    val customColorModeWord = stringResource(Res.string.option_custom_colors)
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
        SettingsSearchAnchor("settings_color_mode") {
            SkipiDropdownPreference(
                title = stringResource(Res.string.settings_color_mode),
                startAction = settingIcon(Icons.Rounded.DarkMode),
                summary = if (isNamedThemeSelected) {
                    stringResource(Res.string.settings_theme_preset_summary)
                } else {
                    stringResource(Res.string.settings_color_mode_summary)
                },
                items = colorModeOptions.mapIndexed { index, (_, label) ->
                    if (index == selectedColorModeIndex) colorModeLabel(label, appState.enableCustomColors, customColorModeWord) else label
                },
                selectedIndex = selectedColorModeIndex,
                onSelectedIndexChange = { index ->
                    colorModeOptions.getOrNull(index)?.first?.let { mode ->
                        updateAppState { it.withColorMode(mode) }
                    }
                },
            )
        }
        if (capabilities.fonts) {
            SettingsSearchAnchor("settings_font_family") {
                SkipiDropdownPreference(
                    title = stringResource(Res.string.settings_font_family),
                    startAction = settingIcon(Icons.Rounded.TextFields),
                    items = fontFamilyOptions,
                    selectedIndex = appState.fontFamilyMode.coerceIn(0, fontFamilyOptions.lastIndex),
                    onSelectedIndexChange = { index -> updateAppState { it.copy(fontFamilyMode = index) } },
                )
            }
            SettingsSearchAnchor("settings_font_size") {
                FontSizeSliderPreference(
                    fontSizeMode = appState.fontSizeMode,
                    onFontSizeModeChange = { mode ->
                        updateAppState { it.copy(fontSizeMode = mode) }
                    },
                )
            }
            SkipiSettingsReveal(visible = AppearanceSettingValues.isFontWeightSupported(appState.fontFamilyMode)) {
                SettingsSearchAnchor("settings_font_weight") {
                    SkipiDropdownPreference(
                        title = stringResource(Res.string.settings_font_weight),
                        startAction = settingIcon(Icons.Rounded.FormatBold),
                        items = fontWeightOptions,
                        selectedIndex = appState.fontWeightMode.coerceIn(0, fontWeightOptions.lastIndex),
                        onSelectedIndexChange = { index -> updateAppState { it.copy(fontWeightMode = index) } },
                    )
                }
            }
        }
        if (capabilities.appIcon) {
            SettingsSearchAnchor("settings_app_icon") {
                ArrowPreference(
                    title = stringResource(Res.string.settings_app_icon),
                    startAction = settingIcon(Icons.Rounded.Apps),
                    summary = appIconTitle,
                    onClick = onRequestAppIconSelection,
                )
            }
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
    val backgroundBlurOptions = remember { listOf(0, 25, 50, 75, 100) }
    val backgroundBlurLabels = remember { listOf("0%", "25%", "50%", "75%", "100%") }

    SmallTitle(text = stringResource(Res.string.settings_background_title))
    SkipiSettingsSectionCard {
        SettingsSearchAnchor("settings_background_style") {
            SkipiDropdownPreference(
                title = stringResource(Res.string.settings_background_style),
                startAction = settingIcon(Icons.Rounded.Wallpaper),
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
        }
        SkipiSettingsReveal(visible = appState.backgroundStyle == AppearanceSettingValues.BackgroundStylePhoto) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsSearchAnchor("settings_background_photo_pick") {
                    ArrowPreference(
                        title = stringResource(Res.string.settings_background_photo_pick),
                        startAction = settingIcon(Icons.Rounded.Image),
                        onClick = onChooseBackgroundPhoto,
                    )
                }
                SettingsSearchAnchor("settings_background_dim") {
                    SkipiDropdownPreference(
                        title = stringResource(Res.string.settings_background_dim),
                        startAction = settingIcon(Icons.Rounded.Contrast),
                        items = backgroundDimLabels,
                        selectedIndex = backgroundDimOptions.indexOf(appState.backgroundPhotoDimPercent).coerceIn(0, backgroundDimOptions.lastIndex),
                        onSelectedIndexChange = { index ->
                            updateAppState {
                                it.copy(backgroundPhotoDimPercent = backgroundDimOptions[index])
                            }
                        },
                    )
                }
                SettingsSearchAnchor("settings_background_photo_blur") {
                    SkipiDropdownPreference(
                        title = stringResource(Res.string.settings_background_photo_blur),
                        startAction = settingIcon(Icons.Rounded.BlurOn),
                        items = backgroundBlurLabels,
                        selectedIndex = backgroundBlurOptions.indexOf(appState.backgroundPhotoBlurPercent).coerceIn(0, backgroundBlurOptions.lastIndex),
                        onSelectedIndexChange = { index ->
                            updateAppState {
                                it.copy(backgroundPhotoBlurPercent = backgroundBlurOptions[index])
                            }
                        },
                    )
                }
                SkipiSettingsReveal(visible = hasCustomBackgroundPhoto) {
                    SettingsSearchAnchor("settings_background_photo_remove") {
                        ArrowPreference(
                            title = stringResource(Res.string.settings_background_photo_remove),
                            startAction = settingIcon(Icons.Rounded.HideImage),
                            onClick = {
                                onRemoveBackgroundPhoto()
                                updateAppState { it.copy(backgroundStyle = AppearanceSettingValues.BackgroundStyleClassic) }
                            },
                        )
                    }
                }
            }
        }
        if (capabilities.seasonalEvents) {
            SettingsSearchAnchor("settings_seasonal_events") {
                SwitchPreference(
                    title = stringResource(Res.string.settings_seasonal_events),
                    startAction = settingIcon(Icons.Rounded.AcUnit),
                    summary = stringResource(Res.string.settings_seasonal_events_summary),
                    checked = appState.enableSeasonalEvents,
                    onCheckedChange = { enabled ->
                        updateAppState { it.copy(enableSeasonalEvents = enabled) }
                    },
                )
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
    val isNamedThemeSelected = AppearanceSettingValues.isNamedColorTheme(appState.colorMode)

    val resetCompletedMessage = stringResource(Res.string.settings_colors_reset_completed)

    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = stringResource(Res.string.settings_custom_colors_title))
        SkipiSettingsSectionCard {
            SkipiSettingsReveal(visible = capabilities.dynamicColors && !isNamedThemeSelected) {
                // Only the system accent: no seed colour to choose. The seed index goes back to "system", which also
                // clears a colour that onboarding picked.
                SettingsSearchAnchor("settings_enable_material_you") {
                    SwitchPreference(
                        title = stringResource(Res.string.settings_enable_material_you),
                        startAction = settingIcon(Icons.Rounded.ColorLens),
                        summary = stringResource(Res.string.settings_enable_material_you_summary),
                        checked = appState.enableMaterialYou,
                        onCheckedChange = { enabled ->
                            updateAppState { it.copy(enableMaterialYou = enabled, seedIndex = if (enabled) 0 else it.seedIndex) }
                        },
                    )
                }
            }
            SkipiSettingsReveal(visible = appState.showsAccentPicker()) {
                SettingsSearchAnchor("settings_color_accent") {
                    SettingsColorItem(
                        title = stringResource(Res.string.settings_color_accent),
                        summary = stringResource(Res.string.settings_color_accent_summary),
                        color = appState.customAccentColor?.let { Color(it) } ?: SkipiTheme.colors.accent,
                        onClick = { onPickColor(AppearanceColorTarget.ACCENT) },
                    )
                }
            }
            SettingsSearchAnchor("settings_enable_custom_colors") {
                SwitchPreference(
                    title = stringResource(Res.string.settings_enable_custom_colors),
                    startAction = settingIcon(Icons.Rounded.Colorize),
                    summary = stringResource(Res.string.settings_enable_custom_colors_summary),
                    checked = appState.enableCustomColors,
                    onCheckedChange = { enabled ->
                        updateAppState { it.copy(enableCustomColors = enabled) }
                    },
                )
            }
            SkipiSettingsReveal(visible = appState.enableCustomColors) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSearchAnchor("settings_color_background") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_background),
                            summary = stringResource(Res.string.settings_color_background_summary),
                            color = appState.customBackgroundColor?.let { Color(it) } ?: SkipiTheme.colors.background,
                            onClick = { onPickColor(AppearanceColorTarget.BACKGROUND) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_surface") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_surface),
                            summary = stringResource(Res.string.settings_color_surface_summary),
                            color = appState.customSurfaceColor?.let { Color(it) } ?: SkipiTheme.colors.surface,
                            onClick = { onPickColor(AppearanceColorTarget.SURFACE) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_surface_variant") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_surface_variant),
                            summary = stringResource(Res.string.settings_color_surface_variant_summary),
                            color = appState.customSurfaceVariantColor?.let { Color(it) } ?: SkipiTheme.colors.surfaceVariant,
                            onClick = { onPickColor(AppearanceColorTarget.SURFACE_VARIANT) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_text") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_text),
                            summary = stringResource(Res.string.settings_color_text_summary),
                            color = appState.customTextColor?.let { Color(it) } ?: SkipiTheme.colors.onSurface,
                            onClick = { onPickColor(AppearanceColorTarget.TEXT) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_text_secondary") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_text_secondary),
                            summary = stringResource(Res.string.settings_color_text_secondary_summary),
                            color = appState.customTextSecondaryColor?.let { Color(it) } ?: SkipiTheme.colors.onSurfaceVariant,
                            onClick = { onPickColor(AppearanceColorTarget.TEXT_SECONDARY) },
                        )
                    }
                }
            }
        }

        SkipiSettingsReveal(visible = appState.enableCustomColors) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SmallTitle(text = stringResource(Res.string.settings_custom_colors_status_title))
                SkipiSettingsSectionCard {
                    SettingsSearchAnchor("settings_color_status_running") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_status_running),
                            summary = stringResource(Res.string.settings_color_status_running_summary),
                            color = appState.customStatusRunningColor?.let { Color(it) } ?: StatusColorDefaults.statusRunning(isDark),
                            onClick = { onPickColor(AppearanceColorTarget.STATUS_RUNNING) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_status_stopped") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_status_stopped),
                            summary = stringResource(Res.string.settings_color_status_stopped_summary),
                            color = appState.customStatusStoppedColor?.let { Color(it) } ?: StatusColorDefaults.statusStopped(isDark),
                            onClick = { onPickColor(AppearanceColorTarget.STATUS_STOPPED) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_ping_fast") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_ping_fast),
                            summary = stringResource(Res.string.settings_color_ping_fast_summary),
                            color = appState.customPingFastColor?.let { Color(it) } ?: StatusColorDefaults.pingFast(isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PING_FAST) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_ping_medium") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_ping_medium),
                            summary = stringResource(Res.string.settings_color_ping_medium_summary),
                            color = appState.customPingMediumColor?.let { Color(it) } ?: StatusColorDefaults.pingMedium(isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PING_MEDIUM) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_ping_slow") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_ping_slow),
                            summary = stringResource(Res.string.settings_color_ping_slow_summary),
                            color = appState.customPingSlowColor?.let { Color(it) } ?: StatusColorDefaults.pingSlow(isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PING_SLOW) },
                        )
                    }
                }

                SmallTitle(text = stringResource(Res.string.settings_custom_colors_categories_title))
                SkipiSettingsSectionCard {
                    SettingsSearchAnchor("settings_color_icons") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_icons),
                            summary = stringResource(Res.string.settings_color_icons_summary),
                            color = shownIconColor(appState.customCategoryIconGlyphColor),
                            onClick = { onPickColor(AppearanceColorTarget.ICONS) },
                        )
                    }
                }

                SmallTitle(text = stringResource(Res.string.settings_custom_colors_protocols_title))
                SkipiSettingsSectionCard {
                    SettingsSearchAnchor("settings_color_protocol_vless") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_vless),
                            color = appearanceProtocolColor("vless", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_VLESS) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_vmess") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_vmess),
                            color = appearanceProtocolColor("vmess", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_VMESS) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_hysteria2") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_hysteria2),
                            color = appearanceProtocolColor("hysteria2", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_HYSTERIA2) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_trojan") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_trojan),
                            color = appearanceProtocolColor("trojan", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_TROJAN) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_shadowsocks") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_shadowsocks),
                            color = appearanceProtocolColor("shadowsocks", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_SHADOWSOCKS) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_wireguard") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_wireguard),
                            color = appearanceProtocolColor("wireguard", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_WIREGUARD) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_socks") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_socks),
                            color = appearanceProtocolColor("socks", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_SOCKS) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_http") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_http),
                            color = appearanceProtocolColor("http", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_HTTP) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_strategy") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_strategy),
                            color = appearanceProtocolColor("strategy", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_STRATEGY) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_chain") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_chain),
                            color = appearanceProtocolColor("chain", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_CHAIN) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_json") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_json),
                            color = appearanceProtocolColor("json", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_JSON) },
                        )
                    }
                    SettingsSearchAnchor("settings_color_protocol_masque") {
                        SettingsColorItem(
                            title = stringResource(Res.string.settings_color_protocol_masque),
                            color = appearanceProtocolColor("masque", appState, isDark),
                            onClick = { onPickColor(AppearanceColorTarget.PROTOCOL_MASQUE) },
                        )
                    }
                    SettingsSearchAnchor("settings_colors_reset") {
                        ArrowPreference(
                            title = stringResource(Res.string.settings_colors_reset),
                            startAction = settingIcon(Icons.Rounded.RestartAlt),
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
                                        customCategoryIconGlyphColor = null,
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
                                        customProtocolMasqueColor = null,
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
            SettingsSearchAnchor("settings_navigation_style") {
                SkipiDropdownPreference(
                    title = stringResource(Res.string.settings_navigation_style),
                    startAction = settingIcon(Icons.Rounded.Dock),
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
        }
        if (capabilities.bottomBarSize && appState.navigationStyle == 0) {
            SettingsSearchAnchor("settings_bottom_bar_size") {
                SkipiDropdownPreference(
                    title = stringResource(Res.string.settings_bottom_bar_size),
                    startAction = settingIcon(Icons.Rounded.Height),
                    summary = stringResource(Res.string.settings_bottom_bar_size_summary),
                    items = bottomBarSizeOptions,
                    selectedIndex = appState.bottomBarSize,
                    onSelectedIndexChange = { size ->
                        updateAppState { it.copy(bottomBarSize = size) }
                    },
                )
            }
        }
        SettingsSearchAnchor("settings_connection_display_mode") {
            SkipiDropdownPreference(
                title = stringResource(Res.string.settings_connection_display_mode),
                startAction = settingIcon(Icons.Rounded.Dashboard),
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
        }
        SkipiSettingsReveal(visible = appState.connectionDisplayMode != AppearanceSettingValues.ConnectionDisplayModeCompact) {
            Column {
                // The size applies to the classic widget only; the legacy card has a fixed layout.
                SkipiSettingsReveal(visible = appState.connectionDisplayMode == AppearanceSettingValues.ConnectionDisplayModeClassic) {
                    SettingsSearchAnchor("settings_classic_button_size") {
                        SkipiDropdownPreference(
                            title = stringResource(Res.string.settings_classic_button_size),
                            startAction = settingIcon(Icons.Rounded.SmartButton),
                            summary = stringResource(Res.string.settings_classic_button_size_summary),
                            items = classicButtonSizeOptions,
                            selectedIndex = appState.classicButtonSize.coerceIn(0, classicButtonSizeOptions.lastIndex),
                            onSelectedIndexChange = { size ->
                                updateAppState { it.copy(classicButtonSize = size) }
                            },
                        )
                    }
                }
                if (capabilities.floatingPowerButton) {
                    SettingsSearchAnchor("settings_classic_show_floating_power_button") {
                        SwitchPreference(
                            title = stringResource(Res.string.settings_classic_show_floating_power_button),
                            startAction = settingIcon(Icons.Rounded.PowerSettingsNew),
                            summary = stringResource(Res.string.settings_classic_show_floating_power_button_summary),
                            checked = appState.classicShowFloatingPowerButton,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(classicShowFloatingPowerButton = enabled) }
                            },
                        )
                    }
                }
            }
        }
        SettingsSearchAnchor("settings_pin_connection_panel") {
            SwitchPreference(
                title = stringResource(Res.string.settings_pin_connection_panel),
                startAction = settingIcon(Icons.Rounded.PushPin),
                summary = stringResource(Res.string.settings_pin_connection_panel_summary),
                checked = appState.pinConnectionPanelOnHome,
                onCheckedChange = { enabled ->
                    updateAppState { it.copy(pinConnectionPanelOnHome = enabled) }
                },
            )
        }
        if (capabilities.subscriptionSwipe) {
            SettingsSearchAnchor("settings_enable_subscription_swipe") {
                SwitchPreference(
                    title = stringResource(Res.string.settings_enable_subscription_swipe),
                    startAction = settingIcon(Icons.Rounded.Swipe),
                    summary = stringResource(Res.string.settings_enable_subscription_swipe_summary),
                    checked = appState.enableSubscriptionSwipe,
                    onCheckedChange = { enabled ->
                        updateAppState { it.copy(enableSubscriptionSwipe = enabled) }
                    },
                )
            }
        }
        SettingsSearchAnchor("settings_tunnel_memory_show_on_home") {
            SwitchPreference(
                title = stringResource(Res.string.settings_tunnel_memory_show_on_home),
                startAction = settingIcon(Icons.Rounded.Memory),
                summary = stringResource(Res.string.settings_tunnel_memory_show_on_home_summary),
                checked = appState.showTunnelMemoryOnHome,
                onCheckedChange = { enabled ->
                    updateAppState { it.copy(showTunnelMemoryOnHome = enabled) }
                },
            )
        }
        SettingsSearchAnchor("settings_tunnel_ip_show_on_home") {
            SwitchPreference(
                title = stringResource(Res.string.settings_tunnel_ip_show_on_home),
                startAction = settingIcon(Icons.Rounded.Language),
                summary = stringResource(Res.string.settings_tunnel_ip_show_on_home_summary),
                checked = appState.showTunnelIpOnHome,
                onCheckedChange = { enabled ->
                    updateAppState { it.copy(showTunnelIpOnHome = enabled) }
                },
            )
        }
        SkipiSettingsReveal(visible = appState.showTunnelIpOnHome) {
            SettingsSearchAnchor("settings_tunnel_ip_mask_on_home") {
                SwitchPreference(
                    title = stringResource(Res.string.settings_tunnel_ip_mask_on_home),
                    startAction = settingIcon(Icons.Rounded.VisibilityOff),
                    summary = stringResource(Res.string.settings_tunnel_ip_mask_on_home_summary),
                    checked = appState.maskTunnelIpOnHome,
                    onCheckedChange = { enabled ->
                        updateAppState { it.copy(maskTunnelIpOnHome = enabled) }
                    },
                )
            }
        }
        if (capabilities.connectionLog) {
            SettingsSearchAnchor("settings_connection_log_show_on_home") {
                SwitchPreference(
                    title = stringResource(Res.string.settings_connection_log_show_on_home),
                    startAction = settingIcon(Icons.Rounded.Article),
                    summary = stringResource(Res.string.settings_connection_log_show_on_home_summary),
                    checked = appState.showConnectionLogOnHome,
                    onCheckedChange = { enabled ->
                        updateAppState { it.copy(showConnectionLogOnHome = enabled) }
                    },
                )
            }
        }
        SettingsSearchAnchor("settings_enable_all_proxy_group") {
            SwitchPreference(
                title = stringResource(Res.string.settings_enable_all_proxy_group),
                startAction = settingIcon(Icons.Rounded.AllInclusive),
                summary = stringResource(Res.string.settings_enable_all_proxy_group_summary),
                checked = appState.enableAllProxyGroup,
                onCheckedChange = { enabled -> updateAppState { it.copy(enableAllProxyGroup = enabled) } },
            )
        }
        SettingsSearchAnchor("settings_show_server_search") {
            SwitchPreference(
                title = stringResource(Res.string.settings_show_server_search),
                startAction = settingIcon(Icons.Rounded.Search),
                summary = stringResource(Res.string.settings_show_server_search_summary),
                checked = appState.showServerSearch,
                onCheckedChange = { enabled -> updateAppState { it.copy(showServerSearch = enabled) } },
            )
        }
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
            AppearanceColorTarget.ICONS -> stringResource(Res.string.settings_color_icons)
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
            AppearanceColorTarget.PROTOCOL_MASQUE -> stringResource(Res.string.settings_color_protocol_masque)
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
            AppearanceColorTarget.ICONS -> shownIconColor(appState.customCategoryIconGlyphColor)
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
            AppearanceColorTarget.PROTOCOL_MASQUE -> appearanceProtocolColor("masque", appState, isDark)
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
                        AppearanceColorTarget.ICONS -> state.copy(customCategoryIconGlyphColor = colorLong)
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
                        AppearanceColorTarget.PROTOCOL_MASQUE -> state.copy(customProtocolMasqueColor = colorLong)
                    }
                }
            },
        )
}

/** The icon color a row or the picker shows: the picked one, else the theme's, else the text color icons fall back to. */
@Composable
private fun shownIconColor(picked: Long?): Color =
    picked?.let { Color(it) } ?: SkipiTheme.colors.icon.takeIf { it.isSpecified } ?: SkipiTheme.colors.onSurface

/** The color mode as the row shows it: with "Custom colors" on it says so and keeps the mode the colors sit on. */
internal fun colorModeLabel(mode: String, customColorsOn: Boolean, customWord: String): String =
    if (customColorsOn) "$customWord · $mode" else mode
