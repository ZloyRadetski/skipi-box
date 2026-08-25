// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import androidx.compose.animation.AnimatedVisibility
import ui.text.themedFontWeight
import ui.components.AppOverlayDropdownPreference
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppChromeState
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.modes.BackgroundStyleClassic
import app.modes.BackgroundStyleConnection
import app.modes.BackgroundStylePhoto
import app.modes.ConnectionDisplayModeClassic
import app.modes.LanguageModeChinese
import app.modes.LanguageModeEnglish
import app.modes.LanguageModePersian
import app.modes.LanguageModeRussian
import app.modes.LanguageModeSystem
import app.modes.normalizeBackgroundStyle
import ui.background.clearCustomBackgroundPhoto
import ui.background.customBackgroundPhotoExists
import ui.background.saveCustomBackgroundPhoto
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import features.proxy.server.display.ProtocolColorUtils
import features.settings.sheets.AppIconSelectionBottomSheet
import features.settings.sheets.appIconDescriptorForMode
import ui.AppTheme
import ui.KeyColors
import ui.StatusColorDefaults
import ui.components.BackNavigationIcon
import ui.components.ColorPickerDialog
import ui.isInDarkTheme
import ui.keyColorFor
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import ui.resolveSystemAccentColor

private enum class ColorPickerTarget {
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

@Composable
fun SettingsAppearancePage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val tipNotifier = services.tipNotifier
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val isDark = isInDarkTheme()

    var activeColorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showAppIconBottomSheet by remember { mutableStateOf(false) }

    val contentPadding = pageContentPaddingWithCutout(
        innerPadding = PaddingValues(),
        outerPadding = padding,
        isWideScreen = isWideScreen,
    )
    val listPadding = pageListPadding(contentPadding)

    val colorModeOptions = listOf(
        stringResource(R.string.option_follow_system),
        stringResource(R.string.option_light),
        stringResource(R.string.option_dark),
        stringResource(R.string.option_theme_system),
        stringResource(R.string.option_theme_light),
        stringResource(R.string.option_theme_dark),
        stringResource(R.string.option_amoled),
    )
    val languageOptions = listOf(
        stringResource(R.string.option_follow_system),
        stringResource(R.string.option_english),
        stringResource(R.string.option_russian),
        stringResource(R.string.option_chinese),
        stringResource(R.string.option_persian),
    )
    val languageModes = listOf(
        LanguageModeSystem,
        LanguageModeEnglish,
        LanguageModeRussian,
        LanguageModeChinese,
        LanguageModePersian,
    )
    val languageSelectionIndex = languageModes.indexOf(appState.languageMode).coerceAtLeast(0)

    val keyColorOptions = listOf(
        stringResource(R.string.theme_color_default),
        stringResource(R.string.theme_color_blue),
        stringResource(R.string.theme_color_green),
        stringResource(R.string.theme_color_violet),
        stringResource(R.string.theme_color_yellow),
        stringResource(R.string.theme_color_orange),
        stringResource(R.string.theme_color_rose),
        stringResource(R.string.theme_color_cyan),
        stringResource(R.string.settings_theme_color_custom),
    )

    val customSeedIndex = KeyColors.size + 1
    val currentKeyColor = remember(appState.seedIndex, appState.customMaterialYouSeed, context) {
        if (appState.seedIndex == customSeedIndex && appState.customMaterialYouSeed != null) {
            Color(appState.customMaterialYouSeed!!)
        } else {
            keyColorFor(appState.seedIndex) ?: resolveSystemAccentColor(context)
        }
    }

    val connectionDisplayModeOptions = listOf(
        stringResource(R.string.settings_connection_display_mode_compact),
        stringResource(R.string.settings_connection_display_mode_classic),
    )

    val backgroundStyleOptions = listOf(
        stringResource(R.string.settings_background_style_classic),
        stringResource(R.string.settings_background_style_photo),
        stringResource(R.string.settings_background_style_connection),
        stringResource(R.string.settings_background_style_aurora),
    )
    val backgroundDimOptions = remember { listOf(0, 25, 45, 60, 75) }
    val backgroundDimLabels = remember { listOf("0%", "25%", "45%", "60%", "75%") }

    val bottomBarSizeOptions = listOf(
        stringResource(R.string.settings_bottom_bar_size_small),
        stringResource(R.string.settings_bottom_bar_size_medium),
        stringResource(R.string.settings_bottom_bar_size_large),
    )

    val fontFamilyOptions = listOf(
        stringResource(R.string.settings_font_family_default),
        stringResource(R.string.settings_font_family_inter),
        stringResource(R.string.settings_font_family_golos_text),
        stringResource(R.string.settings_font_family_manrope),
        stringResource(R.string.settings_font_family_jetbrains_mono),
        stringResource(R.string.settings_font_family_climate_crisis),
        stringResource(R.string.settings_font_family_unbounded),
        stringResource(R.string.settings_font_family_onest),
    )

    val fontSizeOptions = listOf(
        stringResource(R.string.settings_font_size_tiny),
        stringResource(R.string.settings_font_size_extra_small),
        stringResource(R.string.settings_font_size_very_small),
        stringResource(R.string.settings_font_size_small),
        stringResource(R.string.settings_font_size_default),
        stringResource(R.string.settings_font_size_medium),
        stringResource(R.string.settings_font_size_large),
        stringResource(R.string.settings_font_size_extra_large),
    )

    val fontWeightOptions = listOf(
        stringResource(R.string.settings_font_weight_default),
        stringResource(R.string.settings_font_weight_light),
        stringResource(R.string.settings_font_weight_normal),
        stringResource(R.string.settings_font_weight_medium),
        stringResource(R.string.settings_font_weight_semibold),
        stringResource(R.string.settings_font_weight_bold),
    )

    val resetCompletedMessage = stringResource(R.string.settings_colors_reset_completed)

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_category_appearance),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        BackNavigationIcon(
                            onClick = { navigator.pop() },
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        val innerContentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val innerListPadding = pageListPadding(innerContentPadding)

        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = innerListPadding,
            ) {
                item(key = "appearance_theme") {
                    SmallTitle(text = stringResource(R.string.settings_theme))
                    SettingsSectionCard {
                        AppOverlayDropdownPreference(
                            title = stringResource(R.string.settings_color_mode),
                            items = colorModeOptions,
                            selectedIndex = appState.colorMode,
                            onSelectedIndexChange = { index -> updateAppState { it.copy(colorMode = index) } },
                        )
                        AppOverlayDropdownPreference(
                            title = stringResource(R.string.settings_font_family),
                            items = fontFamilyOptions,
                            selectedIndex = appState.fontFamilyMode.coerceIn(0, fontFamilyOptions.lastIndex),
                            onSelectedIndexChange = { index -> updateAppState { it.copy(fontFamilyMode = index) } },
                        )
                        AppOverlayDropdownPreference(
                            title = stringResource(R.string.settings_font_size),
                            items = fontSizeOptions,
                            selectedIndex = appState.fontSizeMode.coerceIn(0, fontSizeOptions.lastIndex),
                            onSelectedIndexChange = { index -> updateAppState { it.copy(fontSizeMode = index) } },
                        )
                        AnimatedVisibility(
                            visible = app.modes.isFontWeightSupported(appState.fontFamilyMode),
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            AppOverlayDropdownPreference(
                                title = stringResource(R.string.settings_font_weight),
                                items = fontWeightOptions,
                                selectedIndex = appState.fontWeightMode.coerceIn(0, fontWeightOptions.lastIndex),
                                onSelectedIndexChange = { index -> updateAppState { it.copy(fontWeightMode = index) } },
                            )
                        }
                        ArrowPreference(
                            title = stringResource(R.string.settings_app_icon),
                            summary = stringResource(appIconDescriptorForMode(appState.appIcon).titleRes),
                            onClick = { showAppIconBottomSheet = true },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_material_you),
                            summary = stringResource(R.string.settings_enable_material_you_summary),
                            checked = appState.enableMaterialYou,
                            onCheckedChange = { enabled -> updateAppState { it.copy(enableMaterialYou = enabled) } },
                        )
                        AnimatedVisibility(
                            visible = appState.enableMaterialYou,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AppOverlayDropdownPreference(
                                    title = stringResource(R.string.settings_theme_color),
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
                                            activeColorPickerTarget = ColorPickerTarget.MATERIAL_YOU_SEED
                                        }
                                    },
                                )
                                if (appState.seedIndex == customSeedIndex) {
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_theme_color_custom_seed),
                                        summary = stringResource(R.string.settings_theme_color_custom_seed_summary),
                                        color = currentKeyColor,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.MATERIAL_YOU_SEED },
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = !appState.enableMaterialYou,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SettingsColorItem(
                                title = stringResource(R.string.settings_color_accent),
                                summary = stringResource(R.string.settings_color_accent_summary),
                                color = appState.customAccentColor?.let { Color(it) } ?: AppTheme.colors.accent,
                                onClick = { activeColorPickerTarget = ColorPickerTarget.ACCENT },
                            )
                        }
                        AppOverlayDropdownPreference(
                            title = stringResource(R.string.settings_language),
                            items = languageOptions,
                            selectedIndex = languageSelectionIndex,
                            onSelectedIndexChange = { index ->
                                updateAppState { it.copy(languageMode = languageModes[index]) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_haptics),
                            summary = stringResource(R.string.settings_enable_haptics_summary),
                            checked = appState.enableHaptics,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableHaptics = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_traffic_stats_notification),
                            summary = stringResource(R.string.settings_traffic_stats_notification_summary),
                            checked = appState.enableTrafficStatsNotification,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableTrafficStatsNotification = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_resource_files_notifications),
                            summary = stringResource(R.string.settings_resource_files_notifications_summary),
                            checked = appState.enableResourceFileNotifications,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableResourceFileNotifications = enabled) }
                            },
                        )
                    }
                }

                item(key = "appearance_background") {
                    SmallTitle(text = stringResource(R.string.settings_background_title))
                    SettingsSectionCard {
                        AppOverlayDropdownPreference(
                            title = stringResource(R.string.settings_background_style),
                            items = backgroundStyleOptions,
                            selectedIndex = appState.backgroundStyle.coerceIn(0, backgroundStyleOptions.lastIndex),
                            onSelectedIndexChange = { index ->
                                val newStyle = normalizeBackgroundStyle(index)
                                updateAppState { it.copy(backgroundStyle = newStyle) }
                                if (newStyle == BackgroundStylePhoto && !customBackgroundPhotoExists(context)) {
                                    scope.launch {
                                        val uri = services.photoFilePicker()
                                        if (uri != null) {
                                            val saved = saveCustomBackgroundPhoto(context, uri)
                                            if (saved) {
                                                updateAppState { it.copy(backgroundStyle = BackgroundStylePhoto) }
                                            }
                                        }
                                    }
                                }
                            },
                        )
                        AnimatedVisibility(
                            visible = appState.backgroundStyle == BackgroundStylePhoto,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = stringResource(R.string.settings_background_photo_pick),
                                    onClick = {
                                        scope.launch {
                                            val uri = services.photoFilePicker()
                                            if (uri != null) {
                                                val saved = saveCustomBackgroundPhoto(context, uri)
                                                if (saved) {
                                                    updateAppState { it.copy(backgroundStyle = BackgroundStylePhoto) }
                                                }
                                            }
                                        }
                                    },
                                )
                                AppOverlayDropdownPreference(
                                    title = stringResource(R.string.settings_background_dim),
                                    items = backgroundDimLabels,
                                    selectedIndex = backgroundDimOptions.indexOf(appState.backgroundPhotoDimPercent).coerceIn(0, backgroundDimOptions.lastIndex),
                                    onSelectedIndexChange = { index ->
                                        updateAppState {
                                            it.copy(backgroundPhotoDimPercent = backgroundDimOptions[index])
                                        }
                                    },
                                )
                                if (customBackgroundPhotoExists(context)) {
                                    ArrowPreference(
                                        title = stringResource(R.string.settings_background_photo_remove),
                                        onClick = {
                                            clearCustomBackgroundPhoto(context)
                                            updateAppState { it.copy(backgroundStyle = BackgroundStyleClassic) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "appearance_custom_colors") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SmallTitle(text = stringResource(R.string.settings_custom_colors_title))
                        SettingsSectionCard {
                            SwitchPreference(
                                title = stringResource(R.string.settings_enable_custom_colors),
                                summary = stringResource(R.string.settings_enable_custom_colors_summary),
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
                                        title = stringResource(R.string.settings_color_accent),
                                        summary = stringResource(R.string.settings_color_accent_summary),
                                        color = appState.customAccentColor?.let { Color(it) } ?: AppTheme.colors.accent,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.ACCENT },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_background),
                                        summary = stringResource(R.string.settings_color_background_summary),
                                        color = appState.customBackgroundColor?.let { Color(it) } ?: AppTheme.colors.background,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.BACKGROUND },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_surface),
                                        summary = stringResource(R.string.settings_color_surface_summary),
                                        color = appState.customSurfaceColor?.let { Color(it) } ?: AppTheme.colors.surface,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.SURFACE },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_surface_variant),
                                        summary = stringResource(R.string.settings_color_surface_variant_summary),
                                        color = appState.customSurfaceVariantColor?.let { Color(it) } ?: AppTheme.colors.surfaceVariant,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.SURFACE_VARIANT },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_text),
                                        summary = stringResource(R.string.settings_color_text_summary),
                                        color = appState.customTextColor?.let { Color(it) } ?: AppTheme.colors.onSurface,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.TEXT },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_text_secondary),
                                        summary = stringResource(R.string.settings_color_text_secondary_summary),
                                        color = appState.customTextSecondaryColor?.let { Color(it) } ?: AppTheme.colors.onSurfaceVariant,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.TEXT_SECONDARY },
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
                                SmallTitle(text = stringResource(R.string.settings_custom_colors_status_title))
                                SettingsSectionCard {
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_status_running),
                                        summary = stringResource(R.string.settings_color_status_running_summary),
                                        color = appState.customStatusRunningColor?.let { Color(it) } ?: StatusColorDefaults.statusRunning(isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.STATUS_RUNNING },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_status_stopped),
                                        summary = stringResource(R.string.settings_color_status_stopped_summary),
                                        color = appState.customStatusStoppedColor?.let { Color(it) } ?: MiuixTheme.colorScheme.error,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.STATUS_STOPPED },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_ping_fast),
                                        summary = stringResource(R.string.settings_color_ping_fast_summary),
                                        color = appState.customPingFastColor?.let { Color(it) } ?: StatusColorDefaults.pingFast(isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PING_FAST },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_ping_medium),
                                        summary = stringResource(R.string.settings_color_ping_medium_summary),
                                        color = appState.customPingMediumColor?.let { Color(it) } ?: StatusColorDefaults.pingMedium(isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PING_MEDIUM },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_ping_slow),
                                        summary = stringResource(R.string.settings_color_ping_slow_summary),
                                        color = appState.customPingSlowColor?.let { Color(it) } ?: StatusColorDefaults.pingSlow(isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PING_SLOW },
                                    )
                                }

                                SmallTitle(text = stringResource(R.string.settings_custom_colors_categories_title))
                                SettingsSectionCard {
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_category_icons),
                                        summary = stringResource(R.string.settings_color_category_icons_summary),
                                        color = appState.customCategoryIconColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.CATEGORY_ICONS },
                                    )
                                }

                                SmallTitle(text = stringResource(R.string.settings_custom_colors_protocols_title))
                                SettingsSectionCard {
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_vless),
                                        color = ProtocolColorUtils.resolveProtocolColor("vless", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_VLESS },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_vmess),
                                        color = ProtocolColorUtils.resolveProtocolColor("vmess", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_VMESS },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_hysteria2),
                                        color = ProtocolColorUtils.resolveProtocolColor("hysteria2", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_HYSTERIA2 },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_trojan),
                                        color = ProtocolColorUtils.resolveProtocolColor("trojan", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_TROJAN },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_shadowsocks),
                                        color = ProtocolColorUtils.resolveProtocolColor("shadowsocks", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_SHADOWSOCKS },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_wireguard),
                                        color = ProtocolColorUtils.resolveProtocolColor("wireguard", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_WIREGUARD },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_socks),
                                        color = ProtocolColorUtils.resolveProtocolColor("socks", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_SOCKS },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_http),
                                        color = ProtocolColorUtils.resolveProtocolColor("http", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_HTTP },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_strategy),
                                        color = ProtocolColorUtils.resolveProtocolColor("strategy", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_STRATEGY },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_chain),
                                        color = ProtocolColorUtils.resolveProtocolColor("chain", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_CHAIN },
                                    )
                                    SettingsColorItem(
                                        title = stringResource(R.string.settings_color_protocol_json),
                                        color = ProtocolColorUtils.resolveProtocolColor("json", appState, isDark),
                                        onClick = { activeColorPickerTarget = ColorPickerTarget.PROTOCOL_JSON },
                                    )
                                    ArrowPreference(
                                        title = stringResource(R.string.settings_colors_reset),
                                        summary = stringResource(R.string.settings_colors_reset_summary),
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
                                            scope.launch {
                                                tipNotifier.show(resetCompletedMessage)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "appearance_layout") {
                    SmallTitle(text = stringResource(R.string.settings_connection_display_mode))
                    SettingsSectionCard {
                        AppOverlayDropdownPreference(
                            title = stringResource(R.string.settings_bottom_bar_size),
                            summary = stringResource(R.string.settings_bottom_bar_size_summary),
                            items = bottomBarSizeOptions,
                            selectedIndex = appState.bottomBarSize,
                            onSelectedIndexChange = { size ->
                                updateAppState { it.copy(bottomBarSize = size) }
                            },
                        )
                        AppOverlayDropdownPreference(
                            title = stringResource(R.string.settings_connection_display_mode),
                            summary = stringResource(R.string.settings_connection_display_mode_summary),
                            items = connectionDisplayModeOptions,
                            selectedIndex = appState.connectionDisplayMode,
                            onSelectedIndexChange = { mode ->
                                updateAppState { it.copy(connectionDisplayMode = mode) }
                            },
                        )
                        AnimatedVisibility(visible = appState.connectionDisplayMode == ConnectionDisplayModeClassic) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_classic_show_floating_power_button),
                                summary = stringResource(R.string.settings_classic_show_floating_power_button_summary),
                                checked = appState.classicShowFloatingPowerButton,
                                onCheckedChange = { enabled ->
                                    updateAppState { it.copy(classicShowFloatingPowerButton = enabled) }
                                },
                            )
                        }
                        SwitchPreference(
                            title = stringResource(R.string.settings_pin_connection_panel),
                            summary = stringResource(R.string.settings_pin_connection_panel_summary),
                            checked = appState.pinConnectionPanelOnHome,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(pinConnectionPanelOnHome = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_subscription_swipe),
                            summary = stringResource(R.string.settings_enable_subscription_swipe_summary),
                            checked = appState.enableSubscriptionSwipe,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableSubscriptionSwipe = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_tunnel_memory_show_on_home),
                            summary = stringResource(R.string.settings_tunnel_memory_show_on_home_summary),
                            checked = appState.showTunnelMemoryOnHome,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(showTunnelMemoryOnHome = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_show_server_search),
                            summary = stringResource(R.string.settings_show_server_search_summary),
                            checked = appState.showServerSearch,
                            onCheckedChange = { enabled -> updateAppState { it.copy(showServerSearch = enabled) } },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_all_proxy_group),
                            summary = stringResource(R.string.settings_enable_all_proxy_group_summary),
                            checked = appState.enableAllProxyGroup,
                            onCheckedChange = { enabled -> updateAppState { it.copy(enableAllProxyGroup = enabled) } },
                        )
                    }
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerContentPadding,
            )

            // Color Picker Dialog
            val pickerTarget = activeColorPickerTarget
            if (pickerTarget != null) {
                val dialogTitle = when (pickerTarget) {
                    ColorPickerTarget.MATERIAL_YOU_SEED -> stringResource(R.string.settings_theme_color_custom_seed)
                    ColorPickerTarget.ACCENT -> stringResource(R.string.settings_color_accent)
                    ColorPickerTarget.BACKGROUND -> stringResource(R.string.settings_color_background)
                    ColorPickerTarget.SURFACE -> stringResource(R.string.settings_color_surface)
                    ColorPickerTarget.SURFACE_VARIANT -> stringResource(R.string.settings_color_surface_variant)
                    ColorPickerTarget.TEXT -> stringResource(R.string.settings_color_text)
                    ColorPickerTarget.TEXT_SECONDARY -> stringResource(R.string.settings_color_text_secondary)
                    ColorPickerTarget.STATUS_RUNNING -> stringResource(R.string.settings_color_status_running)
                    ColorPickerTarget.STATUS_STOPPED -> stringResource(R.string.settings_color_status_stopped)
                    ColorPickerTarget.PING_FAST -> stringResource(R.string.settings_color_ping_fast)
                    ColorPickerTarget.PING_MEDIUM -> stringResource(R.string.settings_color_ping_medium)
                    ColorPickerTarget.PING_SLOW -> stringResource(R.string.settings_color_ping_slow)
                    ColorPickerTarget.CATEGORY_ICONS -> stringResource(R.string.settings_color_category_icons)
                    ColorPickerTarget.PROTOCOL_VLESS -> stringResource(R.string.settings_color_protocol_vless)
                    ColorPickerTarget.PROTOCOL_VMESS -> stringResource(R.string.settings_color_protocol_vmess)
                    ColorPickerTarget.PROTOCOL_HYSTERIA2 -> stringResource(R.string.settings_color_protocol_hysteria2)
                    ColorPickerTarget.PROTOCOL_TROJAN -> stringResource(R.string.settings_color_protocol_trojan)
                    ColorPickerTarget.PROTOCOL_SHADOWSOCKS -> stringResource(R.string.settings_color_protocol_shadowsocks)
                    ColorPickerTarget.PROTOCOL_WIREGUARD -> stringResource(R.string.settings_color_protocol_wireguard)
                    ColorPickerTarget.PROTOCOL_SOCKS -> stringResource(R.string.settings_color_protocol_socks)
                    ColorPickerTarget.PROTOCOL_HTTP -> stringResource(R.string.settings_color_protocol_http)
                    ColorPickerTarget.PROTOCOL_STRATEGY -> stringResource(R.string.settings_color_protocol_strategy)
                    ColorPickerTarget.PROTOCOL_CHAIN -> stringResource(R.string.settings_color_protocol_chain)
                    ColorPickerTarget.PROTOCOL_JSON -> stringResource(R.string.settings_color_protocol_json)
                }
                val initialColor = when (pickerTarget) {
                    ColorPickerTarget.MATERIAL_YOU_SEED -> currentKeyColor
                    ColorPickerTarget.ACCENT -> appState.customAccentColor?.let { Color(it) } ?: AppTheme.colors.accent
                    ColorPickerTarget.BACKGROUND -> appState.customBackgroundColor?.let { Color(it) } ?: AppTheme.colors.background
                    ColorPickerTarget.SURFACE -> appState.customSurfaceColor?.let { Color(it) } ?: AppTheme.colors.surface
                    ColorPickerTarget.SURFACE_VARIANT -> appState.customSurfaceVariantColor?.let { Color(it) } ?: AppTheme.colors.surfaceVariant
                    ColorPickerTarget.TEXT -> appState.customTextColor?.let { Color(it) } ?: AppTheme.colors.onSurface
                    ColorPickerTarget.TEXT_SECONDARY -> appState.customTextSecondaryColor?.let { Color(it) } ?: AppTheme.colors.onSurfaceVariant
                    ColorPickerTarget.STATUS_RUNNING -> appState.customStatusRunningColor?.let { Color(it) } ?: StatusColorDefaults.statusRunning(isDark)
                    ColorPickerTarget.STATUS_STOPPED -> appState.customStatusStoppedColor?.let { Color(it) } ?: MiuixTheme.colorScheme.error
                    ColorPickerTarget.PING_FAST -> appState.customPingFastColor?.let { Color(it) } ?: StatusColorDefaults.pingFast(isDark)
                    ColorPickerTarget.PING_MEDIUM -> appState.customPingMediumColor?.let { Color(it) } ?: StatusColorDefaults.pingMedium(isDark)
                    ColorPickerTarget.PING_SLOW -> appState.customPingSlowColor?.let { Color(it) } ?: StatusColorDefaults.pingSlow(isDark)
                    ColorPickerTarget.CATEGORY_ICONS -> appState.customCategoryIconColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary
                    ColorPickerTarget.PROTOCOL_VLESS -> ProtocolColorUtils.resolveProtocolColor("vless", appState, isDark)
                    ColorPickerTarget.PROTOCOL_VMESS -> ProtocolColorUtils.resolveProtocolColor("vmess", appState, isDark)
                    ColorPickerTarget.PROTOCOL_HYSTERIA2 -> ProtocolColorUtils.resolveProtocolColor("hysteria2", appState, isDark)
                    ColorPickerTarget.PROTOCOL_TROJAN -> ProtocolColorUtils.resolveProtocolColor("trojan", appState, isDark)
                    ColorPickerTarget.PROTOCOL_SHADOWSOCKS -> ProtocolColorUtils.resolveProtocolColor("shadowsocks", appState, isDark)
                    ColorPickerTarget.PROTOCOL_WIREGUARD -> ProtocolColorUtils.resolveProtocolColor("wireguard", appState, isDark)
                    ColorPickerTarget.PROTOCOL_SOCKS -> ProtocolColorUtils.resolveProtocolColor("socks", appState, isDark)
                    ColorPickerTarget.PROTOCOL_HTTP -> ProtocolColorUtils.resolveProtocolColor("http", appState, isDark)
                    ColorPickerTarget.PROTOCOL_STRATEGY -> ProtocolColorUtils.resolveProtocolColor("strategy", appState, isDark)
                    ColorPickerTarget.PROTOCOL_CHAIN -> ProtocolColorUtils.resolveProtocolColor("chain", appState, isDark)
                    ColorPickerTarget.PROTOCOL_JSON -> ProtocolColorUtils.resolveProtocolColor("json", appState, isDark)
                }
                ColorPickerDialog(
                    show = true,
                    title = dialogTitle,
                    initialColor = initialColor,
                    onDismissRequest = { activeColorPickerTarget = null },
                    onColorSelected = { selectedColor ->
                        val colorLong = selectedColor.toArgb().toLong() and 0xFFFFFFFFL
                        updateAppState { state ->
                            when (pickerTarget) {
                                ColorPickerTarget.MATERIAL_YOU_SEED -> state.copy(
                                    seedIndex = customSeedIndex,
                                    customMaterialYouSeed = colorLong,
                                    )
                                ColorPickerTarget.ACCENT -> state.copy(customAccentColor = colorLong)
                                ColorPickerTarget.BACKGROUND -> state.copy(customBackgroundColor = colorLong)
                                ColorPickerTarget.SURFACE -> state.copy(customSurfaceColor = colorLong)
                                ColorPickerTarget.SURFACE_VARIANT -> state.copy(customSurfaceVariantColor = colorLong)
                                ColorPickerTarget.TEXT -> state.copy(customTextColor = colorLong)
                                ColorPickerTarget.TEXT_SECONDARY -> state.copy(customTextSecondaryColor = colorLong)
                                ColorPickerTarget.STATUS_RUNNING -> state.copy(customStatusRunningColor = colorLong)
                                ColorPickerTarget.STATUS_STOPPED -> state.copy(customStatusStoppedColor = colorLong)
                                ColorPickerTarget.PING_FAST -> state.copy(customPingFastColor = colorLong)
                                ColorPickerTarget.PING_MEDIUM -> state.copy(customPingMediumColor = colorLong)
                                ColorPickerTarget.PING_SLOW -> state.copy(customPingSlowColor = colorLong)
                                ColorPickerTarget.CATEGORY_ICONS -> state.copy(customCategoryIconColor = colorLong)
                                ColorPickerTarget.PROTOCOL_VLESS -> state.copy(customProtocolVlessColor = colorLong)
                                ColorPickerTarget.PROTOCOL_VMESS -> state.copy(customProtocolVmessColor = colorLong)
                                ColorPickerTarget.PROTOCOL_HYSTERIA2 -> state.copy(customProtocolHysteria2Color = colorLong)
                                ColorPickerTarget.PROTOCOL_TROJAN -> state.copy(customProtocolTrojanColor = colorLong)
                                ColorPickerTarget.PROTOCOL_SHADOWSOCKS -> state.copy(customProtocolShadowsocksColor = colorLong)
                                ColorPickerTarget.PROTOCOL_WIREGUARD -> state.copy(customProtocolWireguardColor = colorLong)
                                ColorPickerTarget.PROTOCOL_SOCKS -> state.copy(customProtocolSocksColor = colorLong)
                                ColorPickerTarget.PROTOCOL_HTTP -> state.copy(customProtocolHttpColor = colorLong)
                                ColorPickerTarget.PROTOCOL_STRATEGY -> state.copy(customProtocolStrategyColor = colorLong)
                                ColorPickerTarget.PROTOCOL_CHAIN -> state.copy(customProtocolChainColor = colorLong)
                                ColorPickerTarget.PROTOCOL_JSON -> state.copy(customProtocolJsonColor = colorLong)
                            }
                        }
                    },
                )
            }

            AppIconSelectionBottomSheet(
                show = showAppIconBottomSheet,
                selectedIconMode = appState.appIcon,
                onSelectIconMode = { mode ->
                    updateAppState { it.copy(appIcon = mode) }
                },
                onDismissRequest = { showAppIconBottomSheet = false },
            )
        }
    }
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
