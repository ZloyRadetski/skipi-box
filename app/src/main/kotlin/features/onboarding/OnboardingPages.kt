// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.AppState
import app.LocalAppServices
import app.LocalAppStateStore
import app.ProxyServerState
import app.R
import app.SubscriptionGroupState
import app.modes.BottomBarSizeLarge
import app.modes.BottomBarSizeMedium
import app.modes.BottomBarSizeSmall
import app.modes.ColorModeDark
import app.modes.ColorModeLight
import app.modes.ColorModeSystem
import app.modes.LanguageModeChinese
import app.modes.LanguageModeEnglish
import app.modes.LanguageModePersian
import app.modes.LanguageModeRussian
import app.modes.LanguageModeSystem
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.importProxyServersFromText
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.SubscriptionInstallConfigUseCase
import features.subscription.toSubscriptionInstallConfigOrNull
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune
import ui.AppTheme
import ui.KeyColors
import ui.clipboard.getPlainText
import ui.clipboard.setPlainText

@Composable
internal fun onboardingCardBackground(): Color {
    return if (AppTheme.colors.isDark) Color(0xFF18191E) else Color(0xFFFFFFFF)
}

@Composable
internal fun onboardingCardBorder(): Color {
    return if (AppTheme.colors.isDark) Color(0xFF282932) else Color(0xFFE5E7EB)
}

@Composable
internal fun onboardingItemInactiveBg(): Color {
    return if (AppTheme.colors.isDark) Color(0xFF22232B) else Color(0xFFF3F4F6)
}

@Composable
internal fun onboardingItemInactiveBorder(): Color {
    return if (AppTheme.colors.isDark) Color(0xFF30313C) else Color(0xFFE5E7EB)
}

@Composable
internal fun OnboardingHeroSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    logoPainter: Painter? = null,
    glowColor: Color = AppTheme.colors.accent,
) {
    val isDark = AppTheme.colors.isDark

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (logoPainter != null) {
            Box(
                modifier = Modifier
                    .size(width = 260.dp, height = 135.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = if (isDark) 0.38f else 0.22f),
                                glowColor.copy(alpha = if (isDark) 0.14f else 0.06f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = logoPainter,
                    contentDescription = "SKIPI Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(105.dp)
                        .aspectRatio(2f)
                        .clip(RoundedCornerShape(22.dp)),
                )
            }
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = if (isDark) 0.35f else 0.22f),
                                glowColor.copy(alpha = if (isDark) 0.12f else 0.06f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = if (isDark) 0.3f else 0.18f),
                                    glowColor.copy(alpha = if (isDark) 0.15f else 0.08f),
                                ),
                            ),
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = if (isDark) 0.8f else 0.6f),
                                    glowColor.copy(alpha = if (isDark) 0.3f else 0.2f),
                                ),
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = glowColor,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = AppTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth(0.88f),
        )
    }
}

// ----------------------------------------------------------------------------
// PAGE 0: Welcome & Language Selection
// ----------------------------------------------------------------------------

@Composable
internal fun OnboardingWelcomePage(
    appState: AppState,
    updateAppState: ((AppState) -> AppState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val languages = listOf(
        Triple(LanguageModeRussian, "Русский", "🇷🇺"),
        Triple(LanguageModeEnglish, "English", "🇬🇧"),
        Triple(LanguageModeChinese, "简体中文", "🇨🇳"),
        Triple(LanguageModePersian, "فارسی", "🇮🇷"),
    )

    val cardBg = onboardingCardBackground()
    val cardBorder = onboardingCardBorder()
    val itemBg = onboardingItemInactiveBg()
    val itemBorder = onboardingItemInactiveBorder()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        OnboardingHeroSection(
            logoPainter = painterResource(R.drawable.ic_about_logo),
            title = stringResource(R.string.onboarding_welcome_title),
            subtitle = stringResource(R.string.onboarding_welcome_subtitle),
            glowColor = AppTheme.colors.accent,
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Language Selector Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppTheme.colors.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Tune,
                            contentDescription = null,
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_language_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppTheme.colors.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2x2 Grid of specific languages
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in languages.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (j in i until (i + 2).coerceAtMost(languages.size)) {
                                val (mode, name, flag) = languages[j]
                                val isSelected = appState.languageMode == mode
                                val bg by animateColorAsState(
                                    targetValue = if (isSelected) AppTheme.colors.accent else itemBg,
                                    label = "lang_bg_$mode",
                                )
                                val textColor by animateColorAsState(
                                    targetValue = if (isSelected) AppTheme.colors.onAccent else AppTheme.colors.onSurface,
                                    label = "lang_text_$mode",
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.Transparent else itemBorder,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable {
                                            updateAppState { it.copy(languageMode = mode) }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$flag  $name",
                                        color = textColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }

                    // Follow System Full Width
                    val isSystemSelected = appState.languageMode == LanguageModeSystem
                    val sysBg by animateColorAsState(
                        targetValue = if (isSystemSelected) AppTheme.colors.accent else itemBg,
                        label = "sys_lang_bg",
                    )
                    val sysTextColor by animateColorAsState(
                        targetValue = if (isSystemSelected) AppTheme.colors.onAccent else AppTheme.colors.onSurface,
                        label = "sys_lang_text",
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(sysBg)
                            .border(
                                width = 1.dp,
                                color = if (isSystemSelected) Color.Transparent else itemBorder,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                updateAppState { it.copy(languageMode = LanguageModeSystem) }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "🌐  " + stringResource(R.string.option_follow_system),
                            color = sysTextColor,
                            fontWeight = if (isSystemSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 1: Official Telegram Channel
// ----------------------------------------------------------------------------

@Composable
internal fun OnboardingTelegramPage(
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.onboarding_telegram_copied_link)
    val tgColor = Color(0xFF2AABEE)
    val cardBg = onboardingCardBackground()
    val cardBorder = onboardingCardBorder()
    val isDark = AppTheme.colors.isDark

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Telegram Logo / Icon Hero
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            tgColor.copy(alpha = if (isDark) 0.35f else 0.20f),
                            tgColor.copy(alpha = if (isDark) 0.10f else 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                tgColor.copy(alpha = if (isDark) 0.30f else 0.18f),
                                tgColor.copy(alpha = if (isDark) 0.15f else 0.08f),
                            ),
                        ),
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                tgColor,
                                tgColor.copy(alpha = 0.5f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✈️",
                    fontSize = 32.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.onboarding_telegram_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_telegram_subtitle),
            fontSize = 14.sp,
            color = AppTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth(0.9f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Features list card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TelegramFeatureItem(
                    emoji = "🚀",
                    title = stringResource(R.string.onboarding_telegram_feature_releases_title),
                    description = stringResource(R.string.onboarding_telegram_feature_releases_desc),
                )
                TelegramFeatureItem(
                    emoji = "💬",
                    title = stringResource(R.string.onboarding_telegram_feature_chat_title),
                    description = stringResource(R.string.onboarding_telegram_feature_chat_desc),
                )
                TelegramFeatureItem(
                    emoji = "📢",
                    title = stringResource(R.string.onboarding_telegram_feature_news_title),
                    description = stringResource(R.string.onboarding_telegram_feature_news_desc),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Action: Join Channel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tgColor)
                .clickable {
                    uriHandler.openUri("https://t.me/skipi_public")
                }
                .padding(vertical = 15.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✈️  " + stringResource(R.string.onboarding_telegram_open_button),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Copy link action
        Text(
            text = "@skipi_public",
            color = tgColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    scope.launch {
                        clipboard.setPlainText("https://t.me/skipi_public")
                        tipNotifier.show(copiedText)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TelegramFeatureItem(
    emoji: String,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2AABEE).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = AppTheme.colors.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 2: System Permissions
// ----------------------------------------------------------------------------

@Composable
internal fun OnboardingPermissionsPage(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    var isVpnGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    var isNotificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true,
        )
    }
    var isBatteryOptIgnored by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true,
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isVpnGranted = VpnService.prepare(context) == null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isNotificationGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                isBatteryOptIgnored = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permGreen = Color(0xFF10B981)
    val permBlue = Color(0xFF0070F3)
    val permAmber = Color(0xFFF59E0B)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        OnboardingHeroSection(
            icon = MiuixIcons.Tune,
            title = stringResource(R.string.onboarding_permissions_title),
            subtitle = stringResource(R.string.onboarding_permissions_subtitle),
            glowColor = permBlue,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. VPN Service Permission
        PermissionCard(
            icon = MiuixIcons.Ok,
            iconColor = permBlue,
            title = stringResource(R.string.onboarding_perm_vpn_title),
            description = stringResource(R.string.onboarding_perm_vpn_desc),
            isGranted = isVpnGranted,
            onGrantClick = {
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    scope.launch {
                        val granted = runCatching { services.requestVpnPermission(intent) }.getOrDefault(false)
                        isVpnGranted = granted || VpnService.prepare(context) == null
                    }
                } else {
                    isVpnGranted = true
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Notifications Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = MiuixIcons.Copy,
                iconColor = permAmber,
                title = stringResource(R.string.onboarding_perm_notifications_title),
                description = stringResource(R.string.onboarding_perm_notifications_desc),
                isGranted = isNotificationGranted,
                onGrantClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                },
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. Battery Optimization
        PermissionCard(
            icon = MiuixIcons.Refresh,
            iconColor = permGreen,
            title = stringResource(R.string.onboarding_perm_battery_title),
            description = stringResource(R.string.onboarding_perm_battery_desc),
            isGranted = isBatteryOptIgnored,
            onGrantClick = {
                val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    context.startActivity(directIntent)
                }.onFailure {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(settingsIntent) }
                    }
                }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
) {
    val cardBg = onboardingCardBackground()
    val cardBorder = onboardingCardBorder()
    val permGreen = Color(0xFF10B981)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) permGreen.copy(alpha = 0.16f)
                        else iconColor.copy(alpha = 0.16f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isGranted) MiuixIcons.Ok else icon,
                    contentDescription = null,
                    tint = if (isGranted) permGreen else iconColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AppTheme.colors.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(permGreen.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "✓ " + stringResource(R.string.onboarding_perm_granted),
                        color = permGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor)
                        .clickable(onClick = onGrantClick)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_perm_grant),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 2: Appearance & Theme
// ----------------------------------------------------------------------------

@Composable
internal fun OnboardingAppearancePage(
    appState: AppState,
    updateAppState: ((AppState) -> AppState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeModes = listOf(
        ColorModeSystem to stringResource(R.string.option_follow_system),
        ColorModeLight to stringResource(R.string.option_light),
        ColorModeDark to stringResource(R.string.option_dark),
    )

    val bottomBarSizes = listOf(
        BottomBarSizeSmall to stringResource(R.string.settings_bottom_bar_size_small),
        BottomBarSizeMedium to stringResource(R.string.settings_bottom_bar_size_medium),
        BottomBarSizeLarge to stringResource(R.string.settings_bottom_bar_size_large),
    )

    val cardBg = onboardingCardBackground()
    val cardBorder = onboardingCardBorder()
    val itemBg = onboardingItemInactiveBg()
    val itemBorder = onboardingItemInactiveBorder()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        OnboardingHeroSection(
            icon = MiuixIcons.Edit,
            title = stringResource(R.string.onboarding_appearance_title),
            subtitle = stringResource(R.string.onboarding_appearance_subtitle),
            glowColor = Color(0xFFA855F7),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. Theme Mode
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.onboarding_theme_mode),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AppTheme.colors.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    themeModes.forEach { (mode, label) ->
                        val isSelected = appState.colorMode == mode
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) AppTheme.colors.accent else itemBg,
                            label = "theme_mode_bg_$mode",
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) AppTheme.colors.onAccent else AppTheme.colors.onSurface,
                            label = "theme_mode_text_$mode",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else itemBorder,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    updateAppState { it.copy(colorMode = mode) }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Color Palette / Material You
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.onboarding_accent_color),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AppTheme.colors.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Seed Index 0 (Default system accent / Material You)
                    val isSystemAccent = appState.seedIndex == 0
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSystemAccent) AppTheme.colors.accent else itemBg)
                            .border(
                                width = if (isSystemAccent) 2.5.dp else 1.dp,
                                color = if (isSystemAccent) AppTheme.colors.onSurface else itemBorder,
                                shape = CircleShape,
                            )
                            .clickable {
                                updateAppState { it.copy(seedIndex = 0, enableMaterialYou = true) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✦",
                            color = if (isSystemAccent) AppTheme.colors.onAccent else AppTheme.colors.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Key Color Presets
                    KeyColors.forEachIndexed { index, color ->
                        val seedIdx = index + 1
                        val isSelected = appState.seedIndex == seedIdx
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) AppTheme.colors.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    updateAppState { it.copy(seedIndex = seedIdx, enableMaterialYou = true) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Bottom Bar Size
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.onboarding_bottom_bar_size),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AppTheme.colors.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    bottomBarSizes.forEach { (size, label) ->
                        val isSelected = appState.bottomBarSize == size
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) AppTheme.colors.accent else itemBg,
                            label = "bar_size_bg_$size",
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) AppTheme.colors.onAccent else AppTheme.colors.onSurface,
                            label = "bar_size_text_$size",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else itemBorder,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    updateAppState { it.copy(bottomBarSize = size) }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 3: Quick Import (Clipboard Paste / QR Scan)
// ----------------------------------------------------------------------------

@Composable
internal fun OnboardingImportPage(
    appState: AppState,
    updateAppState: ((AppState) -> AppState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val services = LocalAppServices.current
    val stateStore = LocalAppStateStore.current
    val tipNotifier = services.tipNotifier
    val scope = rememberCoroutineScope()
    var clipboardText by remember { mutableStateOf<String?>(null) }

    val cardBg = onboardingCardBackground()
    val cardBorder = onboardingCardBorder()
    val permGreen = Color(0xFF10B981)
    val permBlue = Color(0xFF0070F3)

    suspend fun importRawText(text: String, isExplicitPaste: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            if (isExplicitPaste) {
                tipNotifier.show(context.getString(R.string.onboarding_clipboard_empty))
            }
            return
        }

        // Try installing as subscription URL
        val config = trimmed.toSubscriptionInstallConfigOrNull()
        if (config != null) {
            runCatching {
                SubscriptionInstallConfigUseCase(
                    stateStore = stateStore,
                    subscriptionFetcher = services.subscriptionFetcher,
                ).install(config)
            }.onSuccess {
                tipNotifier.show("Подписка успешно добавлена!")
            }.onFailure { error ->
                tipNotifier.showError(error)
            }
            return
        }

        // Otherwise import as proxy servers (vless, vmess, ss, etc.)
        val importResult = runCatching {
            importProxyServersFromText(trimmed, ProxyServerImportSource.Clipboard)
        }.getOrNull()

        if (importResult != null && importResult.servers.isNotEmpty()) {
            stateStore.update { current ->
                val nextId = current.nextProxyServerId
                var currentId = nextId
                val newStates = importResult.servers.map { server ->
                    ProxyServerState(
                        id = currentId++,
                        server = server,
                        groupId = DefaultSubscriptionGroupId,
                    )
                }
                current.copy(
                    proxyServers = current.proxyServers + newStates,
                    nextProxyServerId = currentId,
                )
            }
            tipNotifier.show(context.getString(R.string.onboarding_servers_added_count, importResult.servers.size))
        } else if (isExplicitPaste) {
            tipNotifier.show(context.getString(R.string.onboarding_clipboard_empty))
        }
    }

    LaunchedEffect(Unit) {
        val clip = runCatching { clipboard.getPlainText()?.trim() }.getOrNull()
        if (!clip.isNullOrBlank() && (
            clip.startsWith("vless://") ||
            clip.startsWith("vmess://") ||
            clip.startsWith("ss://") ||
            clip.startsWith("trojan://") ||
            clip.startsWith("hysteria2://") ||
            clip.startsWith("hy2://") ||
            clip.startsWith("http://") ||
            clip.startsWith("https://")
        )) {
            clipboardText = clip
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        OnboardingHeroSection(
            icon = MiuixIcons.Add,
            title = stringResource(R.string.onboarding_import_title),
            subtitle = stringResource(R.string.onboarding_import_subtitle),
            glowColor = AppTheme.colors.accent,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Detected in clipboard banner (if present)
        clipboardText?.let { clip ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppTheme.colors.accent.copy(alpha = 0.14f))
                    .border(1.dp, AppTheme.colors.accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Copy,
                            contentDescription = null,
                            tint = AppTheme.colors.onAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.onboarding_clipboard_detected_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppTheme.colors.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = clip.take(34) + "...",
                            fontSize = 11.sp,
                            color = AppTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.colors.accent)
                            .clickable {
                                scope.launch {
                                    importRawText(clip, isExplicitPaste = false)
                                    clipboardText = null
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_clipboard_import_btn),
                            color = AppTheme.colors.onAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Action 1: Paste from Clipboard Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                .clickable {
                    scope.launch {
                        val text = runCatching { clipboard.getPlainText() }.getOrNull()
                        importRawText(text.orEmpty(), isExplicitPaste = true)
                    }
                }
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(permGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = null,
                        tint = permGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_import_clipboard_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppTheme.colors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.onboarding_import_clipboard_desc),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action 2: Scan QR Code Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                .clickable {
                    scope.launch {
                        val scanText = runCatching { services.qrScanner() }.getOrNull()
                        if (!scanText.isNullOrBlank()) {
                            importRawText(scanText, isExplicitPaste = false)
                        }
                    }
                }
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(permBlue.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = null,
                        tint = permBlue,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_scan_qr_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppTheme.colors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.onboarding_scan_qr_desc),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Status badge
        val count = appState.proxyServers.size + appState.subscriptionGroups.size
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(permGreen.copy(alpha = 0.14f))
                    .border(1.dp, permGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "✓ " + stringResource(R.string.onboarding_servers_added_count, count),
                    color = permGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.onboarding_import_later_hint),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 4: Ready to Go!
// ----------------------------------------------------------------------------

@Composable
internal fun OnboardingCompletePage(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    val cardBg = onboardingCardBackground()
    val cardBorder = onboardingCardBorder()
    val permGreen = Color(0xFF10B981)
    val isDark = AppTheme.colors.isDark

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            permGreen.copy(alpha = if (isDark) 0.4f else 0.22f),
                            permGreen.copy(alpha = if (isDark) 0.12f else 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                permGreen.copy(alpha = if (isDark) 0.35f else 0.2f),
                                permGreen.copy(alpha = if (isDark) 0.18f else 0.1f),
                            ),
                        ),
                    )
                    .border(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                permGreen,
                                permGreen.copy(alpha = 0.5f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    tint = permGreen,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.onboarding_complete_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.onboarding_complete_subtitle),
            fontSize = 14.sp,
            color = AppTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth(0.85f),
        )

        Spacer(modifier = Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(permGreen),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_ready_status_ready),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppTheme.colors.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val totalItems = appState.proxyServers.size + appState.subscriptionGroups.size
                Text(
                    text = if (totalItems > 0) {
                        stringResource(R.string.onboarding_servers_added_count, totalItems)
                    } else {
                        stringResource(R.string.onboarding_import_later_hint)
                    },
                    fontSize = 13.sp,
                    color = AppTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
