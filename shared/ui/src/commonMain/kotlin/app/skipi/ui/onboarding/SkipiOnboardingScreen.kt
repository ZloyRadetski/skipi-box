// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.*
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune

private const val OnboardingPageCount = 6
private val TelegramBlue = Color(0xFF2AABEE)
private val PermissionGreen = Color(0xFF10B981)
private val PermissionBlue = Color(0xFF0070F3)
private val PermissionAmber = Color(0xFFF59E0B)
private val AccentPresets = listOf(
    Color(0xFF3482FF), Color(0xFF36D167), Color(0xFF7C4DFF), Color(0xFFFFB21D),
    Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF00BCD4),
)

enum class OnboardingLanguage { FollowSystem, English, Chinese, Russian, Persian }
enum class OnboardingAppearance { System, Light, Dark, Amoled, Other }
enum class OnboardingBottomBarSize { Small, Medium, Large }

data class OnboardingPermissionState(
    val vpnGranted: Boolean = false,
    val showNotificationPermission: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
)

data class SkipiOnboardingState(
    val language: OnboardingLanguage = OnboardingLanguage.FollowSystem,
    val appearance: OnboardingAppearance = OnboardingAppearance.System,
    val accentSeedIndex: Int = 0,
    val bottomBarSize: OnboardingBottomBarSize = OnboardingBottomBarSize.Large,
    val proxyServerCount: Int = 0,
    val subscriptionGroupCount: Int = 0,
    val permissions: OnboardingPermissionState = OnboardingPermissionState(),
)

data class SkipiOnboardingActions(
    val onFinish: () -> Unit,
    val onLanguageSelected: (OnboardingLanguage) -> Unit,
    val onAppearanceSelected: (OnboardingAppearance) -> Unit,
    val onAccentSelected: (Int) -> Unit,
    val onBottomBarSizeSelected: (OnboardingBottomBarSize) -> Unit,
    val onOpenTelegram: (String) -> Unit,
    val onCopyTelegram: (String, String) -> Unit,
    val onRequestVpnPermission: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onOpenBatterySettings: () -> Unit,
    val onReadClipboard: suspend () -> String?,
    val onImportText: suspend (String, Boolean) -> Unit,
    val onScanQr: suspend () -> String?,
)

/** Shared onboarding flow. OS interactions and persistence are supplied by platform callbacks. */
@Composable
fun SkipiOnboardingScreen(
    state: SkipiOnboardingState,
    actions: SkipiOnboardingActions,
    logoPainter: Painter? = null,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingPageCount })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val colors = SkipiTheme.colors
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == OnboardingPageCount - 1
    val headerChipBg = if (colors.isDark) Color(0xFF1C1D24) else Color.White
    val headerChipBorder = if (colors.isDark) Color(0xFF2C2D38) else Color(0xFFE5E7EB)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderChip(
                    text = "${currentPage + 1} / $OnboardingPageCount",
                    background = headerChipBg,
                    border = headerChipBorder,
                )
                if (!isLastPage) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(headerChipBg)
                            .border(1.dp, headerChipBorder, RoundedCornerShape(20.dp))
                            .clickable {
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                actions.onFinish()
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.onboarding_skip),
                            fontSize = 13.sp,
                            fontWeight = themedFontWeight(FontWeight.Medium),
                            color = colors.onSurfaceVariant,
                        )
                    }
                } else Spacer(Modifier.width(1.dp))
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> WelcomeStep(state, actions, logoPainter)
                    1 -> SkipiOnboardingTelegramScreen(actions.onOpenTelegram, actions.onCopyTelegram)
                    2 -> PermissionsStep(state.permissions, actions)
                    3 -> AppearanceStep(state, actions)
                    4 -> ImportStep(state, actions)
                    5 -> CompleteStep(state)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentPage > 0) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(headerChipBg)
                            .border(1.dp, headerChipBorder, CircleShape)
                            .clickable {
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(Res.string.onboarding_back),
                            tint = colors.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else Spacer(Modifier.size(50.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(OnboardingPageCount) { index ->
                        val selected = currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (selected) 28.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
                            label = "onboarding_dot_width_$index",
                        )
                        val color by animateColorAsState(
                            targetValue = if (selected) colors.accent else if (colors.isDark) Color(0xFF2C2D38) else Color(0xFFD1D5DB),
                            label = "onboarding_dot_color_$index",
                        )
                        Box(Modifier.size(width, 8.dp).clip(CircleShape).background(color))
                    }
                }

                val buttonBg by animateColorAsState(colors.accent, label = "onboarding_next_bg")
                val buttonText by animateColorAsState(colors.onAccent, label = "onboarding_next_text")
                Box(
                    modifier = Modifier
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(buttonBg)
                        .clickable {
                            runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            if (isLastPage) actions.onFinish()
                            else scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        }
                        .padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(if (isLastPage) Res.string.onboarding_get_started else Res.string.onboarding_next),
                        color = buttonText,
                        fontWeight = themedFontWeight(FontWeight.Bold),
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(text: String, background: Color, border: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = themedFontWeight(FontWeight.Bold), color = SkipiTheme.colors.onSurface)
    }
}

@Composable
private fun WelcomeStep(state: SkipiOnboardingState, actions: SkipiOnboardingActions, logo: Painter?) {
    val languages = listOf(
        OnboardingLanguage.Russian to "🇷🇺  Русский",
        OnboardingLanguage.English to "🇬🇧  English",
        OnboardingLanguage.Chinese to "🇨🇳  简体中文",
        OnboardingLanguage.Persian to "🇮🇷  فارسی",
        OnboardingLanguage.FollowSystem to "🌐  ${stringResource(Res.string.option_follow_system)}",
    )
    StepColumn {
        Spacer(Modifier.height(8.dp))
        Hero(
            title = stringResource(Res.string.onboarding_welcome_title),
            subtitle = stringResource(Res.string.onboarding_welcome_subtitle),
            icon = null,
            logoPainter = logo,
            glow = SkipiTheme.colors.accent,
        )
        Spacer(Modifier.height(28.dp))
        SurfaceCard {
            Column {
                Text(
                    stringResource(Res.string.onboarding_language_title),
                    fontWeight = themedFontWeight(FontWeight.Bold),
                    fontSize = 16.sp,
                    color = SkipiTheme.colors.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.dropLast(1).chunked(2).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { (language, label) ->
                                ChoiceChip(
                                    label = label,
                                    selected = state.language == language,
                                    modifier = Modifier.weight(1f),
                                    onClick = { actions.onLanguageSelected(language) },
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    val systemLanguage = languages.last()
                    ChoiceChip(
                        label = systemLanguage.second,
                        selected = state.language == systemLanguage.first,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { actions.onLanguageSelected(systemLanguage.first) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsStep(state: OnboardingPermissionState, actions: SkipiOnboardingActions) {
    StepColumn {
        Spacer(Modifier.height(8.dp))
        Hero(
            title = stringResource(Res.string.onboarding_permissions_title),
            subtitle = stringResource(Res.string.onboarding_permissions_subtitle),
            icon = MiuixIcons.Tune,
            glow = PermissionBlue,
        )
        Spacer(Modifier.height(24.dp))
        PermissionCard(
            icon = MiuixIcons.Ok,
            iconColor = PermissionBlue,
            title = stringResource(Res.string.onboarding_perm_vpn_title),
            description = stringResource(Res.string.onboarding_perm_vpn_desc),
            granted = state.vpnGranted,
            onGrant = actions.onRequestVpnPermission,
        )
        if (state.showNotificationPermission) {
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = MiuixIcons.Copy,
                iconColor = PermissionAmber,
                title = stringResource(Res.string.onboarding_perm_notifications_title),
                description = stringResource(Res.string.onboarding_perm_notifications_desc),
                granted = state.notificationsGranted,
                onGrant = actions.onRequestNotificationPermission,
            )
        }
        Spacer(Modifier.height(12.dp))
        var batteryOpened by rememberSaveable { mutableStateOf(false) }
        PermissionCard(
            icon = MiuixIcons.Refresh,
            iconColor = PermissionGreen,
            title = stringResource(Res.string.onboarding_perm_battery_title),
            description = stringResource(Res.string.onboarding_perm_battery_desc),
            granted = state.batteryOptimizationIgnored || batteryOpened,
            onGrant = {
                batteryOpened = true
                actions.onOpenBatterySettings()
            },
        )
    }
}

@Composable
private fun AppearanceStep(state: SkipiOnboardingState, actions: SkipiOnboardingActions) {
    StepColumn {
        Spacer(Modifier.height(8.dp))
        Hero(
            title = stringResource(Res.string.onboarding_appearance_title),
            subtitle = stringResource(Res.string.onboarding_appearance_subtitle),
            icon = MiuixIcons.Edit,
            glow = Color(0xFFA855F7),
        )
        Spacer(Modifier.height(24.dp))
        SurfaceCard {
            Column {
                Text(stringResource(Res.string.onboarding_theme_mode), fontWeight = themedFontWeight(FontWeight.Bold), color = SkipiTheme.colors.onSurface)
                Spacer(Modifier.height(12.dp))
                ChoiceChip(
                    label = stringResource(Res.string.option_follow_system),
                    selected = state.appearance == OnboardingAppearance.System,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { actions.onAppearanceSelected(OnboardingAppearance.System) },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        OnboardingAppearance.Light to stringResource(Res.string.option_light),
                        OnboardingAppearance.Dark to stringResource(Res.string.option_dark),
                        OnboardingAppearance.Amoled to stringResource(Res.string.option_amoled),
                    ).forEach { (mode, label) ->
                        ChoiceChip(label, state.appearance == mode, Modifier.weight(1f)) { actions.onAppearanceSelected(mode) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard {
            Column {
                Text(stringResource(Res.string.onboarding_accent_color), fontWeight = themedFontWeight(FontWeight.Bold), color = SkipiTheme.colors.onSurface)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccentDot(Color(0xFF9AA0A6), state.accentSeedIndex == 0) { actions.onAccentSelected(0) }
                    AccentPresets.forEachIndexed { index, color ->
                        AccentDot(color, state.accentSeedIndex == index + 1) { actions.onAccentSelected(index + 1) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard {
            Column {
                Text(stringResource(Res.string.onboarding_bottom_bar_size), fontWeight = themedFontWeight(FontWeight.Bold), color = SkipiTheme.colors.onSurface)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        OnboardingBottomBarSize.Small to Res.string.settings_bottom_bar_size_small,
                        OnboardingBottomBarSize.Medium to Res.string.settings_bottom_bar_size_medium,
                        OnboardingBottomBarSize.Large to Res.string.settings_bottom_bar_size_large,
                    ).forEach { (size, label) ->
                        ChoiceChip(
                            label = stringResource(label),
                            selected = state.bottomBarSize == size,
                            modifier = Modifier.weight(1f),
                            onClick = { actions.onBottomBarSizeSelected(size) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportStep(state: SkipiOnboardingState, actions: SkipiOnboardingActions) {
    val scope = rememberCoroutineScope()
    var detectedClipboard by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        detectedClipboard = actions.onReadClipboard()?.trim()?.takeIf(::isOnboardingImportCandidate)
    }
    StepColumn {
        Spacer(Modifier.height(8.dp))
        Hero(
            title = stringResource(Res.string.onboarding_import_title),
            subtitle = stringResource(Res.string.onboarding_import_subtitle),
            icon = MiuixIcons.Add,
            glow = SkipiTheme.colors.accent,
        )
        Spacer(Modifier.height(24.dp))
        detectedClipboard?.let { clipboardText ->
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(MiuixIcons.Copy, null, tint = SkipiTheme.colors.accent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.onboarding_clipboard_detected_title), fontWeight = themedFontWeight(FontWeight.Bold), color = SkipiTheme.colors.onSurface)
                        Text(clipboardText.take(34) + "...", fontSize = 11.sp, color = SkipiTheme.colors.onSurfaceVariant, maxLines = 1)
                    }
                    ChoiceChip(
                        label = stringResource(Res.string.onboarding_clipboard_import_btn),
                        selected = true,
                        modifier = Modifier,
                        onClick = {
                            scope.launch {
                                actions.onImportText(clipboardText, false)
                                detectedClipboard = null
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        ImportActionCard(
            icon = MiuixIcons.Copy,
            iconColor = PermissionGreen,
            title = stringResource(Res.string.onboarding_import_clipboard_title),
            description = stringResource(Res.string.onboarding_import_clipboard_desc),
        ) {
            scope.launch { actions.onImportText(actions.onReadClipboard().orEmpty(), true) }
        }
        Spacer(Modifier.height(12.dp))
        ImportActionCard(
            icon = MiuixIcons.Add,
            iconColor = PermissionBlue,
            title = stringResource(Res.string.onboarding_scan_qr_title),
            description = stringResource(Res.string.onboarding_scan_qr_desc),
        ) {
            scope.launch { actions.onScanQr()?.takeIf(String::isNotBlank)?.let { actions.onImportText(it, false) } }
        }
        Spacer(Modifier.height(18.dp))
        val total = state.proxyServerCount + state.subscriptionGroupCount
        if (total > 0) {
            Text(
                text = "✓ ${stringResource(Res.string.onboarding_servers_added_count, total)}",
                color = PermissionGreen,
                fontWeight = themedFontWeight(FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(PermissionGreen.copy(alpha = 0.14f))
                    .border(1.dp, PermissionGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Text(stringResource(Res.string.onboarding_import_later_hint), fontSize = 12.sp, color = SkipiTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private fun isOnboardingImportCandidate(value: String): Boolean {
    val text = value.trim().lowercase()
    return listOf("vless://", "vmess://", "ss://", "trojan://", "hysteria2://", "hy2://", "http://", "https://")
        .any(text::startsWith)
}

@Composable
private fun CompleteStep(state: SkipiOnboardingState) {
    val colors = SkipiTheme.colors
    val total = state.proxyServerCount + state.subscriptionGroupCount
    StepColumn {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(PermissionGreen.copy(alpha = if (colors.isDark) 0.4f else 0.22f), PermissionGreen.copy(alpha = 0.08f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MiuixIcons.Ok, null, tint = PermissionGreen, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(stringResource(Res.string.onboarding_complete_title), fontSize = 24.sp, fontWeight = themedFontWeight(FontWeight.Bold), color = colors.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(Res.string.onboarding_complete_subtitle), fontSize = 14.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth(0.85f))
        Spacer(Modifier.height(28.dp))
        SurfaceCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(PermissionGreen))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(Res.string.onboarding_ready_status_ready), fontWeight = themedFontWeight(FontWeight.Bold), fontSize = 15.sp, color = colors.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (total > 0) stringResource(Res.string.onboarding_servers_added_count, total) else stringResource(Res.string.onboarding_import_later_hint),
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun Hero(title: String, subtitle: String, icon: ImageVector?, logoPainter: Painter? = null, glow: Color) {
    val colors = SkipiTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            logoPainter != null -> androidx.compose.foundation.Image(
                painter = logoPainter,
                contentDescription = stringResource(Res.string.onboarding_logo_content_description),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(width = 260.dp, height = 135.dp).aspectRatio(2f),
            )
            icon != null -> Box(
                Modifier.size(104.dp).clip(CircleShape).background(Brush.radialGradient(listOf(glow.copy(alpha = 0.3f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = glow, modifier = Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 24.sp, fontWeight = themedFontWeight(FontWeight.Bold), color = colors.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.92f))
        Spacer(Modifier.height(10.dp))
        Text(subtitle, fontSize = 14.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth(0.88f))
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = SkipiTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (colors.isDark) Color(0xFF18191E) else Color.White)
            .border(1.dp, if (colors.isDark) Color(0xFF282932) else Color(0xFFE5E7EB), shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = SkipiTheme.colors
    val background by animateColorAsState(if (selected) colors.accent else if (colors.isDark) Color(0xFF22232B) else Color(0xFFF3F4F6), label = "choice_bg_$label")
    val textColor by animateColorAsState(if (selected) colors.onAccent else colors.onSurface, label = "choice_text_$label")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, if (selected) Color.Transparent else if (colors.isDark) Color(0xFF30313C) else Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontWeight = themedFontWeight(if (selected) FontWeight.Bold else FontWeight.Medium), fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 0.dp, if (selected) SkipiTheme.colors.onSurface else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(MiuixIcons.Ok, null, tint = if (color == Color(0xFF9AA0A6)) Color.White else Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, iconColor: Color, title: String, description: String, granted: Boolean, onGrant: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (granted) PermissionGreen.copy(alpha = 0.16f) else iconColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(if (granted) MiuixIcons.Ok else icon, null, tint = if (granted) PermissionGreen else iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = themedFontWeight(FontWeight.Bold), fontSize = 15.sp, color = SkipiTheme.colors.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = SkipiTheme.colors.onSurfaceVariant, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text("✓ ${stringResource(Res.string.onboarding_perm_granted)}", color = PermissionGreen, fontWeight = themedFontWeight(FontWeight.Bold), fontSize = 12.sp)
            } else {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(iconColor).clickable(onClick = onGrant).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(stringResource(Res.string.onboarding_perm_grant), color = Color.White, fontWeight = themedFontWeight(FontWeight.Bold), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ImportActionCard(icon: ImageVector, iconColor: Color, title: String, description: String, onClick: () -> Unit) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = themedFontWeight(FontWeight.Bold), fontSize = 15.sp, color = SkipiTheme.colors.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = SkipiTheme.colors.onSurfaceVariant)
            }
        }
    }
}
