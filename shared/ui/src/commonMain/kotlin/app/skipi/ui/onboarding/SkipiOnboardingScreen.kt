// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.luminance
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import app.skipi.ui.components.SkipiIcon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune

private val TelegramBlue = Color(0xFF2AABEE)
private val PermissionGreen = Color(0xFF10B981)
private val PermissionBlue = Color(0xFF0070F3)
private val PermissionAmber = Color(0xFFF59E0B)
private val AccentPresets = listOf(
    Color(0xFF3482FF), Color(0xFF36D167), Color(0xFF7C4DFF), Color(0xFFFFB21D),
    Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF00BCD4),
)

enum class OnboardingStep { Welcome, Telegram, Permissions, Appearance, Import, Complete }

/** Steps shown for [state]; hosts without OS runtime permissions skip the permissions page. */
fun onboardingSteps(state: SkipiOnboardingState): List<OnboardingStep> =
    OnboardingStep.entries.filter { it != OnboardingStep.Permissions || state.showPermissionsStep }

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
    val showPermissionsStep: Boolean = true,
    val qrScanAvailable: Boolean = true,
    val showSystemAppearance: Boolean = true,
    val showAccentColors: Boolean = true,
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
    modifier: Modifier = Modifier,
) {
    val steps = onboardingSteps(state)
    val pageCount = steps.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val colors = SkipiTheme.colors
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == pageCount - 1

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
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepProgress(count = pageCount, current = currentPage)
                if (!isLastPage) {
                    Text(
                        text = stringResource(Res.string.onboarding_skip),
                        fontSize = 16.sp,
                        fontWeight = themedFontWeight(FontWeight.Medium),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                actions.onFinish()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                } else Spacer(Modifier.width(1.dp))
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (steps[page]) {
                    OnboardingStep.Welcome -> WelcomeStep(state, actions)
                    OnboardingStep.Telegram -> SkipiOnboardingTelegramScreen(actions.onOpenTelegram, actions.onCopyTelegram)
                    OnboardingStep.Permissions -> PermissionsStep(state.permissions, actions)
                    OnboardingStep.Appearance -> AppearanceStep(state, actions)
                    OnboardingStep.Import -> ImportStep(state, actions)
                    OnboardingStep.Complete -> CompleteStep(state)
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
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .clickable {
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        SkipiIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(Res.string.onboarding_back),
                            fallbackTint = colors.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else Spacer(Modifier.size(56.dp))

                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .clickable {
                            runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            if (isLastPage) actions.onFinish()
                            else scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        }
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(if (isLastPage) Res.string.onboarding_get_started else Res.string.onboarding_next),
                        color = inkOn(colors.accent),
                        fontWeight = themedFontWeight(FontWeight.Bold),
                        fontSize = 18.sp,
                    )
                }
            }
        }
    }
}

/** Progress as pills: the current step is a long accent pill, the rest are small dots. */
@Composable
private fun StepProgress(count: Int, current: Int) {
    val colors = SkipiTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = current == index
            val width by animateDpAsState(
                targetValue = if (selected) 40.dp else 12.dp,
                animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
                label = "onboarding_progress_width_$index",
            )
            val color by animateColorAsState(
                targetValue = if (selected) colors.accent else colors.surfaceVariant,
                label = "onboarding_progress_color_$index",
            )
            Box(Modifier.size(width, 12.dp).clip(CircleShape).background(color))
        }
    }
}

private val OnboardingLanguageNames = listOf(
    OnboardingLanguage.English to "English",
    OnboardingLanguage.Persian to "فارسی",
    OnboardingLanguage.Russian to "Русский",
    OnboardingLanguage.Chinese to "中文",
)

@Composable
private fun WelcomeStep(state: SkipiOnboardingState, actions: SkipiOnboardingActions) {
    val colors = SkipiTheme.colors
    StepColumn {
        Hero(
            title = stringResource(Res.string.onboarding_welcome_title),
            subtitle = stringResource(Res.string.onboarding_welcome_subtitle),
            icon = null,
            glow = colors.accent,
            large = true,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(Res.string.onboarding_language_title),
            fontWeight = themedFontWeight(FontWeight.SemiBold),
            fontSize = 15.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                label = stringResource(Res.string.option_follow_system),
                selected = state.language == OnboardingLanguage.FollowSystem,
                modifier = Modifier.fillMaxWidth(),
                onClick = { actions.onLanguageSelected(OnboardingLanguage.FollowSystem) },
            )
            OnboardingLanguageNames.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { (language, label) ->
                        ChoiceChip(
                            label = label,
                            selected = state.language == language,
                            modifier = Modifier.weight(1f),
                            onClick = { actions.onLanguageSelected(language) },
                        )
                    }
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
                if (state.showSystemAppearance) {
                ChoiceChip(
                    label = stringResource(Res.string.option_follow_system),
                    selected = state.appearance == OnboardingAppearance.System,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { actions.onAppearanceSelected(OnboardingAppearance.System) },
                )
                Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        OnboardingAppearance.Light to stringResource(Res.string.option_light),
                        OnboardingAppearance.Dark to stringResource(Res.string.option_dark),
                        OnboardingAppearance.Amoled to stringResource(Res.string.option_amoled),
                    ).forEach { (mode, label) ->
                        ChoiceChip(label, state.appearance == mode, Modifier.weight(1f), compact = true) { actions.onAppearanceSelected(mode) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.showAccentColors) {
        SurfaceCard {
            Column {
                Text(stringResource(Res.string.onboarding_accent_color), fontWeight = themedFontWeight(FontWeight.Bold), color = SkipiTheme.colors.onSurface)
                Spacer(Modifier.height(14.dp))
                // Index 0 is the system accent, the presets follow. Two rows of equal circles: a single row
                // squeezed the last colour to a sliver.
                val swatches = listOf(Color(0xFF9AA0A6)) + AccentPresets
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    swatches.withIndex().chunked(4).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { (index, color) ->
                                AccentDot(color, state.accentSeedIndex == index, Modifier.weight(1f)) { actions.onAccentSelected(index) }
                            }
                            repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        }
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
                            compact = true,
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
                // The action sits under the text: beside it the title was squeezed to ~100dp on a phone.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkipiIcon(MiuixIcons.Copy, null, fallbackTint = SkipiTheme.colors.accent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.onboarding_clipboard_detected_title), fontWeight = themedFontWeight(FontWeight.Bold), color = SkipiTheme.colors.onSurface, maxLines = 2)
                            Text(clipboardText.take(34) + "...", fontSize = 11.sp, color = SkipiTheme.colors.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    ChoiceChip(
                        label = stringResource(Res.string.onboarding_clipboard_import_btn),
                        selected = true,
                        modifier = Modifier.fillMaxWidth(),
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
        if (state.qrScanAvailable) {
        ImportActionCard(
            icon = MiuixIcons.Add,
            iconColor = PermissionBlue,
            title = stringResource(Res.string.onboarding_scan_qr_title),
            description = stringResource(Res.string.onboarding_scan_qr_desc),
        ) {
            scope.launch { actions.onScanQr()?.takeIf(String::isNotBlank)?.let { actions.onImportText(it, false) } }
        }
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
        Hero(
            title = stringResource(Res.string.onboarding_complete_title),
            subtitle = stringResource(Res.string.onboarding_complete_subtitle),
            icon = MiuixIcons.Ok,
            glow = PermissionGreen,
        )
        Spacer(Modifier.height(24.dp))
        SurfaceCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(PermissionGreen))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.onboarding_ready_status_ready), fontWeight = themedFontWeight(FontWeight.Bold), fontSize = 18.sp, color = colors.onSurface)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (total > 0) stringResource(Res.string.onboarding_servers_added_count, total) else stringResource(Res.string.onboarding_import_later_hint),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
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
        content = {
            content()
            Spacer(Modifier.height(16.dp))
        },
    )
}

/**
 * The tonal card every step opens with: [lead] on top, a big title and a line of text. Its tone comes from [tint].
 * Shared with the Telegram step so every page of the flow reads as one family.
 */
@Composable
internal fun OnboardingHeroCard(
    title: String,
    subtitle: String,
    tint: Color,
    large: Boolean = false,
    lead: @Composable () -> Unit,
) {
    val colors = SkipiTheme.colors
    val cardColor = lerp(colors.background, tint, if (colors.isDark) 0.30f else 0.18f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(cardColor)
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        lead()
        Spacer(Modifier.height(if (large) 28.dp else 20.dp))
        Text(
            text = title,
            fontSize = if (large) 32.sp else 28.sp,
            lineHeight = if (large) 36.sp else 32.sp,
            fontWeight = themedFontWeight(FontWeight.ExtraBold),
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = subtitle,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            color = colors.onSurface.copy(alpha = 0.78f),
        )
    }
}

/** A rounded-square tile holding [content], the icon slot of a step card. */
@Composable
internal fun OnboardingIconTile(tint: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(tint),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Hero(title: String, subtitle: String, icon: ImageVector?, glow: Color, large: Boolean = false) {
    OnboardingHeroCard(title = title, subtitle = subtitle, tint = glow, large = large) {
        if (icon != null) {
            OnboardingIconTile(glow) { SkipiIcon(icon, null, tint = inkOn(glow), modifier = Modifier.size(28.dp)) }
        } else {
            // The wide logo plate, as big as the card allows.
            Image(
                painter = painterResource(Res.drawable.ic_about_logo),
                contentDescription = stringResource(Res.string.onboarding_logo_content_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f),
            )
        }
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = SkipiTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .padding(20.dp),
        content = content,
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val colors = SkipiTheme.colors
    val background by animateColorAsState(if (selected) colors.accent else colors.surfaceVariant, label = "choice_bg_$label")
    val textColor by animateColorAsState(if (selected) inkOn(colors.accent) else colors.onSurface, label = "choice_text_$label")
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 48.dp else 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 22.dp, vertical = 12.dp),
        contentAlignment = if (compact) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            label,
            color = textColor,
            fontWeight = themedFontWeight(if (selected) FontWeight.Bold else FontWeight.Medium),
            fontSize = if (compact) 15.sp else 17.sp,
            textAlign = if (compact) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
        )
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) SkipiIcon(MiuixIcons.Ok, null, tint = inkOn(color), modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, iconColor: Color, title: String, description: String, granted: Boolean, onGrant: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    SurfaceCard {
        // Icon + text on top, the action on its own full-width row: beside the text it left ~120dp.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (granted) PermissionGreen.copy(alpha = 0.16f) else iconColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    SkipiIcon(if (granted) MiuixIcons.Ok else icon, null, tint = if (granted) PermissionGreen else iconColor, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = themedFontWeight(FontWeight.Bold), fontSize = 15.sp, color = SkipiTheme.colors.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(description, fontSize = 12.sp, color = SkipiTheme.colors.onSurfaceVariant, lineHeight = 16.sp)
                }
            }
            if (granted) {
                Text("✓ ${stringResource(Res.string.onboarding_perm_granted)}", color = PermissionGreen, fontWeight = themedFontWeight(FontWeight.Bold), fontSize = 12.sp)
            } else {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(iconColor).clickable(onClick = onGrant).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
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
                SkipiIcon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
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

/** Black or white, whichever reads on [background]: the host's on-accent can be dark on a mid-tone accent. */
private fun inkOn(background: Color): Color = if (background.luminance() > 0.5f) Color(0xFF1B1C1E) else Color.White
