// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package app.skipi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import app.skipi.ui.components.AppOverlayDropdownPreference
import app.skipi.ui.components.AppSlider
import app.skipi.ui.components.ColorPickerDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.*
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.StatusColorDefaults
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import app.skipi.ui.theme.LocalAppColors
import kotlin.math.roundToInt

@Composable
fun SkipiAppearanceSettingsScreen(
    state: AppearanceSettingsState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    currentKeyColor: Color,
    hasCustomBackgroundPhoto: Boolean,
    appIconTitle: String,
    onSettingsChange: ((AppearanceSettingsState) -> AppearanceSettingsState) -> Unit,
    onBack: () -> Unit,
    onChooseBackgroundPhoto: () -> Unit,
    onRemoveBackgroundPhoto: () -> Unit,
    onRequestAppIconSelection: () -> Unit,
    onColorsReset: (String) -> Unit,
) {
    val appState = state
    val updateAppState = onSettingsChange
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val isDark = LocalAppColors.current.isDark

    var activeAppearanceColorTarget by remember { mutableStateOf<AppearanceColorTarget?>(null) }

    val layoutDirection = LocalLayoutDirection.current

    val colorModeOptions = listOf(
        AppearanceSettingValues.ColorModeSystem to stringResource(Res.string.option_follow_system),
        AppearanceSettingValues.ColorModeLight to stringResource(Res.string.option_light),
        AppearanceSettingValues.ColorModeDark to stringResource(Res.string.option_dark),
        AppearanceSettingValues.ColorModeAmoled to stringResource(Res.string.option_amoled),
        AppearanceSettingValues.ColorModeAurora to stringResource(Res.string.option_theme_aurora),
        AppearanceSettingValues.ColorModeSakura to stringResource(Res.string.option_theme_sakura),
        AppearanceSettingValues.ColorModeForest to stringResource(Res.string.option_theme_forest),
        AppearanceSettingValues.ColorModeSunset to stringResource(Res.string.option_theme_sunset),
    )
    val selectedColorModeIndex = colorModeOptions.indexOfFirst { (mode) ->
        AppearanceSettingValues.normalizeColorMode(mode) == AppearanceSettingValues.normalizeColorMode(appState.colorMode)
    }.coerceAtLeast(0)
    val isNamedThemeSelected = AppearanceSettingValues.isNamedColorTheme(appState.colorMode)
    val keyColorOptions = listOf(
        stringResource(Res.string.theme_color_default),
        stringResource(Res.string.theme_color_blue),
        stringResource(Res.string.theme_color_green),
        stringResource(Res.string.theme_color_violet),
        stringResource(Res.string.theme_color_yellow),
        stringResource(Res.string.theme_color_orange),
        stringResource(Res.string.theme_color_rose),
        stringResource(Res.string.theme_color_cyan),
        stringResource(Res.string.settings_theme_color_custom),
    )

    val customSeedIndex = AppearanceSettingValues.CustomMaterialYouSeedIndex

    val backgroundStyleOptions = listOf(
        stringResource(Res.string.settings_background_style_classic),
        stringResource(Res.string.settings_background_style_photo),
        stringResource(Res.string.settings_background_style_connection),
        stringResource(Res.string.settings_background_style_aurora),
    )
    val backgroundDimOptions = remember { listOf(0, 25, 45, 60, 75) }
    val backgroundDimLabels = remember { listOf("0%", "25%", "45%", "60%", "75%") }

    val bottomBarSizeOptions = listOf(
        stringResource(Res.string.settings_bottom_bar_size_small),
        stringResource(Res.string.settings_bottom_bar_size_medium),
        stringResource(Res.string.settings_bottom_bar_size_large),
    )

    val connectionDisplayModeOptions = listOf(
        stringResource(Res.string.settings_connection_display_mode_compact),
        stringResource(Res.string.settings_connection_display_mode_classic),
    )

    val proxyServerListLayoutOptions = listOf(
        stringResource(Res.string.settings_proxy_server_list_columns_single),
        stringResource(Res.string.settings_proxy_server_list_columns_double),
        stringResource(Res.string.settings_proxy_server_list_columns_triple),
    )

    val proxyServerListLayoutModes = remember {
        listOf(
            AppearanceSettingValues.ProxyServerListLayoutSingle,
            AppearanceSettingValues.ProxyServerListLayoutDouble,
            AppearanceSettingValues.ProxyServerListLayoutMultiple,
        )
    }

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

    val resetCompletedMessage = stringResource(Res.string.settings_colors_reset_completed)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val title = stringResource(Res.string.settings_category_appearance)
            val navigationIcon: @Composable () -> Unit = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
            SmallTopAppBar(
                title = title,
                scrollBehavior = topAppBarScrollBehavior,
                color = Color.Transparent,
                defaultWindowInsetsPadding = !isWideScreen,
                navigationIcon = navigationIcon,
            )
        },
    ) { innerPadding ->
        val innerListPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + if (isWideScreen) padding.calculateTopPadding() else 0.dp,
            start = innerPadding.calculateStartPadding(layoutDirection) + padding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection) + padding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + padding.calculateBottomPadding() + 12.dp,
        )

        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    .fillMaxHeight(),
                contentPadding = innerListPadding,
            ) {
                item(key = "appearance_theme") {
                    SmallTitle(text = stringResource(Res.string.settings_theme))
                    SkipiSettingsSectionCard {
                        AppOverlayDropdownPreference(
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
                        AppOverlayDropdownPreference(
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
                            AppOverlayDropdownPreference(
                                title = stringResource(Res.string.settings_font_weight),
                                items = fontWeightOptions,
                                selectedIndex = appState.fontWeightMode.coerceIn(0, fontWeightOptions.lastIndex),
                                onSelectedIndexChange = { index -> updateAppState { it.copy(fontWeightMode = index) } },
                            )
                        }
                        ArrowPreference(
                            title = stringResource(Res.string.settings_app_icon),
                            summary = appIconTitle,
                            onClick = onRequestAppIconSelection,
                        )
                        AnimatedVisibility(
                            visible = !isNamedThemeSelected,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SwitchPreference(
                                title = stringResource(Res.string.settings_enable_material_you),
                                summary = stringResource(Res.string.settings_enable_material_you_summary),
                                checked = appState.enableMaterialYou,
                                onCheckedChange = { enabled -> updateAppState { it.copy(enableMaterialYou = enabled) } },
                            )
                        }
                        AnimatedVisibility(
                            visible = appState.enableMaterialYou && !isNamedThemeSelected,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AppOverlayDropdownPreference(
                                    title = stringResource(Res.string.settings_theme_color),
                                    items = keyColorOptions,
                                    selectedIndex = appState.seedIndex.coerceIn(0, keyColorOptions.lastIndex),
                                    onSelectedIndexChange = { index ->
                                        updateAppState { state ->
                                            if (index == customSeedIndex && state.customMaterialYouSeed == null) {
                                                state.copy(seedIndex = index, customMaterialYouSeed = currentKeyColor.toArgb().toLong() and 0xFFFFFFFFL)
                                            } else {
                                                state.copy(seedIndex = index)
                                            }
                                        }
                                        if (index == customSeedIndex) {
                                            activeAppearanceColorTarget = AppearanceColorTarget.MATERIAL_YOU_SEED
                                        }
                                    },
                                )
                                if (appState.seedIndex == customSeedIndex) {
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_theme_color_custom_seed),
                                        summary = stringResource(Res.string.settings_theme_color_custom_seed_summary),
                                        color = currentKeyColor,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.MATERIAL_YOU_SEED },
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = !appState.enableMaterialYou && !isNamedThemeSelected,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SettingsColorItem(
                                title = stringResource(Res.string.settings_color_accent),
                                summary = stringResource(Res.string.settings_color_accent_summary),
                                color = appState.customAccentColor?.let { Color(it) } ?: SkipiTheme.colors.accent,
                                onClick = { activeAppearanceColorTarget = AppearanceColorTarget.ACCENT },
                            )
                        }
                    }
                }

                item(key = "appearance_background") {
                    SmallTitle(text = stringResource(Res.string.settings_background_title))
                    SkipiSettingsSectionCard {
                        AppOverlayDropdownPreference(
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
                                AppOverlayDropdownPreference(
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

                item(key = "appearance_custom_colors") {
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
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_accent),
                                        summary = stringResource(Res.string.settings_color_accent_summary),
                                        color = appState.customAccentColor?.let { Color(it) } ?: SkipiTheme.colors.accent,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.ACCENT },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_background),
                                        summary = stringResource(Res.string.settings_color_background_summary),
                                        color = appState.customBackgroundColor?.let { Color(it) } ?: SkipiTheme.colors.background,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.BACKGROUND },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_surface),
                                        summary = stringResource(Res.string.settings_color_surface_summary),
                                        color = appState.customSurfaceColor?.let { Color(it) } ?: SkipiTheme.colors.surface,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.SURFACE },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_surface_variant),
                                        summary = stringResource(Res.string.settings_color_surface_variant_summary),
                                        color = appState.customSurfaceVariantColor?.let { Color(it) } ?: SkipiTheme.colors.surfaceVariant,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.SURFACE_VARIANT },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_text),
                                        summary = stringResource(Res.string.settings_color_text_summary),
                                        color = appState.customTextColor?.let { Color(it) } ?: SkipiTheme.colors.onSurface,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.TEXT },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_text_secondary),
                                        summary = stringResource(Res.string.settings_color_text_secondary_summary),
                                        color = appState.customTextSecondaryColor?.let { Color(it) } ?: SkipiTheme.colors.onSurfaceVariant,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.TEXT_SECONDARY },
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
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.STATUS_RUNNING },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_status_stopped),
                                        summary = stringResource(Res.string.settings_color_status_stopped_summary),
                                        color = appState.customStatusStoppedColor?.let { Color(it) } ?: MiuixTheme.colorScheme.error,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.STATUS_STOPPED },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_ping_fast),
                                        summary = stringResource(Res.string.settings_color_ping_fast_summary),
                                        color = appState.customPingFastColor?.let { Color(it) } ?: StatusColorDefaults.pingFast(isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PING_FAST },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_ping_medium),
                                        summary = stringResource(Res.string.settings_color_ping_medium_summary),
                                        color = appState.customPingMediumColor?.let { Color(it) } ?: StatusColorDefaults.pingMedium(isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PING_MEDIUM },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_ping_slow),
                                        summary = stringResource(Res.string.settings_color_ping_slow_summary),
                                        color = appState.customPingSlowColor?.let { Color(it) } ?: StatusColorDefaults.pingSlow(isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PING_SLOW },
                                    )
                                }

                                SmallTitle(text = stringResource(Res.string.settings_custom_colors_categories_title))
                                SkipiSettingsSectionCard {
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_category_icons),
                                        summary = stringResource(Res.string.settings_color_category_icons_summary),
                                        color = appState.customCategoryIconColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.CATEGORY_ICONS },
                                    )
                                }

                                SmallTitle(text = stringResource(Res.string.settings_custom_colors_protocols_title))
                                SkipiSettingsSectionCard {
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_vless),
                                        color = appearanceProtocolColor("vless", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_VLESS },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_vmess),
                                        color = appearanceProtocolColor("vmess", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_VMESS },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_hysteria2),
                                        color = appearanceProtocolColor("hysteria2", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_HYSTERIA2 },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_trojan),
                                        color = appearanceProtocolColor("trojan", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_TROJAN },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_shadowsocks),
                                        color = appearanceProtocolColor("shadowsocks", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_SHADOWSOCKS },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_wireguard),
                                        color = appearanceProtocolColor("wireguard", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_WIREGUARD },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_socks),
                                        color = appearanceProtocolColor("socks", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_SOCKS },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_http),
                                        color = appearanceProtocolColor("http", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_HTTP },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_strategy),
                                        color = appearanceProtocolColor("strategy", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_STRATEGY },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_chain),
                                        color = appearanceProtocolColor("chain", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_CHAIN },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(Res.string.settings_color_protocol_json),
                                        color = appearanceProtocolColor("json", appState, isDark),
                                        onClick = { activeAppearanceColorTarget = AppearanceColorTarget.PROTOCOL_JSON },
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

                item(key = "appearance_layout") {
                    SmallTitle(text = stringResource(Res.string.settings_header_layout))
                    SkipiSettingsSectionCard {
                        AppOverlayDropdownPreference(
                            title = stringResource(Res.string.settings_bottom_bar_size),
                            summary = stringResource(Res.string.settings_bottom_bar_size_summary),
                            items = bottomBarSizeOptions,
                            selectedIndex = appState.bottomBarSize,
                            onSelectedIndexChange = { size ->
                                updateAppState { it.copy(bottomBarSize = size) }
                            },
                        )
                        AppOverlayDropdownPreference(
                            title = stringResource(Res.string.settings_connection_display_mode),
                            summary = stringResource(Res.string.settings_connection_display_mode_summary),
                            items = connectionDisplayModeOptions,
                            selectedIndex = if (appState.connectionDisplayMode == AppearanceSettingValues.ConnectionDisplayModeCompact) 0 else 1,
                            onSelectedIndexChange = { index ->
                                val mode = if (index == 0) AppearanceSettingValues.ConnectionDisplayModeCompact else AppearanceSettingValues.ConnectionDisplayModeClassic
                                updateAppState { it.copy(connectionDisplayMode = mode) }
                            },
                        )
                        AnimatedVisibility(
                            visible = appState.connectionDisplayMode == AppearanceSettingValues.ConnectionDisplayModeClassic,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SwitchPreference(
                                title = stringResource(Res.string.settings_classic_show_floating_power_button),
                                summary = stringResource(Res.string.settings_classic_show_floating_power_button_summary),
                                checked = appState.classicShowFloatingPowerButton,
                                onCheckedChange = { enabled ->
                                    updateAppState { it.copy(classicShowFloatingPowerButton = enabled) }
                                },
                            )
                        }
                        SwitchPreference(
                            title = stringResource(Res.string.settings_pin_connection_panel),
                            summary = stringResource(Res.string.settings_pin_connection_panel_summary),
                            checked = appState.pinConnectionPanelOnHome,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(pinConnectionPanelOnHome = enabled) }
                            },
                        )
                        AppOverlayDropdownPreference(
                            title = stringResource(Res.string.settings_proxy_server_list_columns),
                            summary = stringResource(Res.string.settings_proxy_server_list_columns_summary),
                            items = proxyServerListLayoutOptions,
                            selectedIndex = proxyServerListLayoutModes.indexOf(appState.proxyServerListLayout).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                val layout = proxyServerListLayoutModes.getOrElse(index) { AppearanceSettingValues.ProxyServerListLayoutSingle }
                                updateAppState { it.copy(proxyServerListLayout = layout) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.settings_enable_subscription_swipe),
                            summary = stringResource(Res.string.settings_enable_subscription_swipe_summary),
                            checked = appState.enableSubscriptionSwipe,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableSubscriptionSwipe = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.settings_tunnel_memory_show_on_home),
                            summary = stringResource(Res.string.settings_tunnel_memory_show_on_home_summary),
                            checked = appState.showTunnelMemoryOnHome,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(showTunnelMemoryOnHome = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.settings_show_server_search),
                            summary = stringResource(Res.string.settings_show_server_search_summary),
                            checked = appState.showServerSearch,
                            onCheckedChange = { enabled -> updateAppState { it.copy(showServerSearch = enabled) } },
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.settings_enable_all_proxy_group),
                            summary = stringResource(Res.string.settings_enable_all_proxy_group_summary),
                            checked = appState.enableAllProxyGroup,
                            onCheckedChange = { enabled -> updateAppState { it.copy(enableAllProxyGroup = enabled) } },
                        )
                    }
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerListPadding,
            )

            // Color Picker Dialog
            val pickerTarget = activeAppearanceColorTarget
            if (pickerTarget != null) {
                val dialogTitle = when (pickerTarget) {
                    AppearanceColorTarget.MATERIAL_YOU_SEED -> stringResource(Res.string.settings_theme_color_custom_seed)
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
                    AppearanceColorTarget.MATERIAL_YOU_SEED -> currentKeyColor
                    AppearanceColorTarget.ACCENT -> appState.customAccentColor?.let { Color(it) } ?: SkipiTheme.colors.accent
                    AppearanceColorTarget.BACKGROUND -> appState.customBackgroundColor?.let { Color(it) } ?: SkipiTheme.colors.background
                    AppearanceColorTarget.SURFACE -> appState.customSurfaceColor?.let { Color(it) } ?: SkipiTheme.colors.surface
                    AppearanceColorTarget.SURFACE_VARIANT -> appState.customSurfaceVariantColor?.let { Color(it) } ?: SkipiTheme.colors.surfaceVariant
                    AppearanceColorTarget.TEXT -> appState.customTextColor?.let { Color(it) } ?: SkipiTheme.colors.onSurface
                    AppearanceColorTarget.TEXT_SECONDARY -> appState.customTextSecondaryColor?.let { Color(it) } ?: SkipiTheme.colors.onSurfaceVariant
                    AppearanceColorTarget.STATUS_RUNNING -> appState.customStatusRunningColor?.let { Color(it) } ?: StatusColorDefaults.statusRunning(isDark)
                    AppearanceColorTarget.STATUS_STOPPED -> appState.customStatusStoppedColor?.let { Color(it) } ?: MiuixTheme.colorScheme.error
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
                    onDismissRequest = { activeAppearanceColorTarget = null },
                    onColorSelected = { selectedColor ->
                        val colorLong = selectedColor.toArgb().toLong() and 0xFFFFFFFFL
                        updateAppState { state ->
                            when (pickerTarget) {
                                AppearanceColorTarget.MATERIAL_YOU_SEED -> state.copy(
                                    seedIndex = customSeedIndex,
                                    customMaterialYouSeed = colorLong,
                                    )
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

        }
    }
}

@Composable
private fun FontSizeSliderPreference(
    fontSizeMode: Int,
    onFontSizeModeChange: (Int) -> Unit,
) {
    val selectedMode = AppearanceSettingValues.normalizeFontSizeMode(fontSizeMode)
    var sliderValue by remember(selectedMode) { mutableFloatStateOf(selectedMode.toFloat()) }
    val selectedPercent = fontSizePercent(sliderValue.roundToInt())

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_font_size),
                fontSize = 14.sp,
                fontWeight = themedFontWeight(FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$selectedPercent%",
                    fontSize = 14.sp,
                    fontWeight = themedFontWeight(FontWeight.Bold),
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AppSlider(
            value = sliderValue,
            onValueChange = { value -> sliderValue = value },
            onValueChangeFinished = {
                onFontSizeModeChange(
                    AppearanceSettingValues.normalizeFontSizeMode(sliderValue.roundToInt()),
                )
            },
            valueRange = AppearanceSettingValues.FontSizeModeTiny.toFloat()..AppearanceSettingValues.FontSizeModeExtraLarge.toFloat(),
            steps = (AppearanceSettingValues.FontSizeModeExtraLarge - AppearanceSettingValues.FontSizeModeTiny) / AppearanceSettingValues.FontSizeModeStepPercent - 1,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${fontSizePercent(AppearanceSettingValues.FontSizeModeTiny)}%",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(
                text = "${fontSizePercent(AppearanceSettingValues.FontSizeModeExtraLarge)}%",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

private fun fontSizePercent(fontSizeMode: Int): Int = AppearanceSettingValues.normalizeFontSizeMode(fontSizeMode)

private fun appearanceProtocolColor(protocol: String, state: AppearanceSettingsState, isDark: Boolean): Color {
    val (custom, light, dark) = when (protocol) {
        "vless" -> Triple(state.customProtocolVlessColor, 0xFF3F51B5, 0xFF7986CB)
        "vmess" -> Triple(state.customProtocolVmessColor, 0xFF00897B, 0xFF4DB6AC)
        "hysteria2" -> Triple(state.customProtocolHysteria2Color, 0xFFE64A19, 0xFFFF7043)
        "trojan" -> Triple(state.customProtocolTrojanColor, 0xFFD87A00, 0xFFFFB74D)
        "shadowsocks" -> Triple(state.customProtocolShadowsocksColor, 0xFF7B1FA2, 0xFFBA68C8)
        "wireguard" -> Triple(state.customProtocolWireguardColor, 0xFFC2185B, 0xFFF06292)
        "socks" -> Triple(state.customProtocolSocksColor, 0xFF455A64, 0xFF90A4AE)
        "http" -> Triple(state.customProtocolHttpColor, 0xFF37474F, 0xFFB0BEC5)
        "strategy" -> Triple(state.customProtocolStrategyColor, 0xFF2E7D32, 0xFF66BB6A)
        "chain" -> Triple(state.customProtocolChainColor, 0xFF00838F, 0xFF4DD0E1)
        "json" -> Triple(state.customProtocolJsonColor, 0xFF8E24AA, 0xFFCE93D8)
        else -> Triple(null, 0xFF546E7A, 0xFFB0BEC5)
    }
    return custom?.let(::Color) ?: Color(if (isDark) dark else light)
}

@Composable
private fun SettingsColorItem(
    title: String,
    summary: String? = null,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())
        Text(
            text = hex,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
