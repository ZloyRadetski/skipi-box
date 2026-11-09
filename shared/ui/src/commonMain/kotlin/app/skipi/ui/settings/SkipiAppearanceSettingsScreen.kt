// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package app.skipi.ui.settings

import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.layout.fadeUnderTopBar
import app.skipi.ui.components.AppSlider
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.*
import app.skipi.ui.text.themedFontWeight
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.roundToInt

data class AppearanceSettingsCapabilities(
    val systemTheme: Boolean = true,
    val appIcon: Boolean = true,
    val wallpaper: Boolean = true,
    val dynamicColors: Boolean = true,
    val customColors: Boolean = true,
    val fonts: Boolean = true,
    val bottomBarSize: Boolean = true,
    /** Only a host that can list the tunnel's connections on Home offers the switch. */
    val connectionLog: Boolean = false,
    /** The floating power button belongs to the phone layout; a desktop host hides its switch. */
    val floatingPowerButton: Boolean = true,
    /** Swiping between subscriptions is a touch gesture; a desktop host hides its switch. */
    val subscriptionSwipe: Boolean = true,
    /** Only a desktop host can place the navigation in a sidebar. */
    val navigationStyle: Boolean = false,
)

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
    /** Puts text on the host's clipboard and tells the user [confirmation]; used to copy a theme link. */
    onCopyText: (text: String, confirmation: String) -> Unit = { _, _ -> },
    capabilities: AppearanceSettingsCapabilities = AppearanceSettingsCapabilities(),
    platformContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val appState = state
    val updateAppState = onSettingsChange
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    var activeAppearanceColorTarget by remember { mutableStateOf<AppearanceColorTarget?>(null) }

    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val title = stringResource(Res.string.settings_category_appearance)
            val navigationIcon: @Composable () -> Unit = {
                BackNavigationIcon(onClick = onBack)
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
                    .fadeUnderTopBar(innerListPadding.calculateTopPadding())
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    .fillMaxHeight(),
                contentPadding = innerListPadding.withTopBarGap(),
            ) {
                item(key = "appearance_theme") {
                    AppearanceThemeSection(
                        state = appState,
                        onSettingsChange = updateAppState,
                        capabilities = capabilities,
                        appIconTitle = appIconTitle,
                        currentKeyColor = currentKeyColor,
                        onRequestAppIconSelection = onRequestAppIconSelection,
                        onPickColor = { activeAppearanceColorTarget = it },
                    )
                }

                if (capabilities.wallpaper) item(key = "appearance_background") {
                    AppearanceBackgroundSection(
                        state = appState,
                        onSettingsChange = updateAppState,
                        capabilities = capabilities,
                        hasCustomBackgroundPhoto = hasCustomBackgroundPhoto,
                        onChooseBackgroundPhoto = onChooseBackgroundPhoto,
                        onRemoveBackgroundPhoto = onRemoveBackgroundPhoto,
                    )
                }

                if (capabilities.customColors) item(key = "appearance_custom_themes") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CustomThemesSection(
                            state = appState,
                            onSettingsChange = updateAppState,
                            onCopyText = onCopyText,
                        )
                    }
                }

                if (capabilities.customColors) item(key = "appearance_custom_colors") {
                    AppearanceCustomColorsSection(
                        state = appState,
                        onSettingsChange = updateAppState,
                        capabilities = capabilities,
                        onColorsReset = onColorsReset,
                        onPickColor = { activeAppearanceColorTarget = it },
                    )
                }

                platformContent?.let { content -> item(key = "appearance_platform_extension") { Column(content = content) } }

                item(key = "appearance_layout") {
                    AppearanceLayoutSection(
                        state = appState,
                        onSettingsChange = updateAppState,
                        capabilities = capabilities,
                    )
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerListPadding,
            )

            AppearanceColorPickerDialog(
                target = activeAppearanceColorTarget,
                state = appState,
                onSettingsChange = updateAppState,
                currentKeyColor = currentKeyColor,
                onDismiss = { activeAppearanceColorTarget = null },
            )

        }
    }
}

@Composable
internal fun FontSizeSliderPreference(
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

internal fun fontSizePercent(fontSizeMode: Int): Int = AppearanceSettingValues.normalizeFontSizeMode(fontSizeMode)

internal fun appearanceProtocolColor(protocol: String, state: AppearanceSettingsState, isDark: Boolean): Color {
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
internal fun SettingsColorItem(
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
