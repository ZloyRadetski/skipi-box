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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Tune
import ui.AppTheme
import ui.KeyColors
import ui.clipboard.getPlainText

@Composable
internal fun OnboardingHeroSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AppTheme.colors.accent.copy(alpha = 0.28f),
                            AppTheme.colors.accent.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.accent.copy(alpha = 0.2f))
                    .border(
                        width = 1.5.dp,
                        color = AppTheme.colors.accent.copy(alpha = 0.5f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            fontSize = 23.sp,
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

@OptIn(ExperimentalLayoutApi::class)
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
        Triple(LanguageModeSystem, stringResource(R.string.option_follow_system), "🌐"),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        OnboardingHeroSection(
            icon = MiuixIcons.Search,
            title = stringResource(R.string.onboarding_welcome_title),
            subtitle = stringResource(R.string.onboarding_welcome_subtitle),
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = AppTheme.colors.surface,
                contentColor = AppTheme.colors.onSurface,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiuixIcons.Tune,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_language_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = AppTheme.colors.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    languages.forEach { (mode, name, flag) ->
                        val isSelected = appState.languageMode == mode
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) AppTheme.colors.accent else AppTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                            label = "lang_bg_$mode",
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) AppTheme.colors.onAccent else AppTheme.colors.onSurface,
                            label = "lang_text_$mode",
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .clickable {
                                    updateAppState { it.copy(languageMode = mode) }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = "$flag  $name",
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 1: System Permissions
// ----------------------------------------------------------------------------

@Composable
internal fun OnboardingPermissionsPage(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
    DisposableEffect(lifecycleOwner) {
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

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        isVpnGranted = VpnService.prepare(context) == null
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        isNotificationGranted = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        OnboardingHeroSection(
            icon = MiuixIcons.Tune,
            title = stringResource(R.string.onboarding_permissions_title),
            subtitle = stringResource(R.string.onboarding_permissions_subtitle),
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 1. VPN Service Permission
        PermissionCard(
            icon = MiuixIcons.Ok,
            title = stringResource(R.string.onboarding_perm_vpn_title),
            description = stringResource(R.string.onboarding_perm_vpn_desc),
            isGranted = isVpnGranted,
            onGrantClick = {
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    vpnLauncher.launch(intent)
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
                title = stringResource(R.string.onboarding_perm_notifications_title),
                description = stringResource(R.string.onboarding_perm_notifications_desc),
                isGranted = isNotificationGranted,
                onGrantClick = {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. Battery Optimization
        PermissionCard(
            icon = MiuixIcons.Refresh,
            title = stringResource(R.string.onboarding_perm_battery_title),
            description = stringResource(R.string.onboarding_perm_battery_desc),
            isGranted = isBatteryOptIgnored,
            onGrantClick = {
                runCatching {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = AppTheme.colors.surface,
            contentColor = AppTheme.colors.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                        else AppTheme.colors.accent.copy(alpha = 0.15f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isGranted) MiuixIcons.Ok else icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else AppTheme.colors.accent,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
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

            Spacer(modifier = Modifier.width(10.dp))

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_perm_granted),
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.accent)
                        .clickable(onClick = onGrantClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_perm_grant),
                        color = AppTheme.colors.onAccent,
                        fontWeight = FontWeight.SemiBold,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        OnboardingHeroSection(
            icon = MiuixIcons.Edit,
            title = stringResource(R.string.onboarding_appearance_title),
            subtitle = stringResource(R.string.onboarding_appearance_subtitle),
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 1. Theme Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = AppTheme.colors.surface,
                contentColor = AppTheme.colors.onSurface,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_theme_mode),
                    fontWeight = FontWeight.SemiBold,
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
                            targetValue = if (isSelected) AppTheme.colors.accent else AppTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                            label = "theme_mode_bg_$mode",
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) AppTheme.colors.onAccent else AppTheme.colors.onSurface,
                            label = "theme_mode_text_$mode",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .clickable {
                                    updateAppState { it.copy(colorMode = mode) }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Color Palette / Material You
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = AppTheme.colors.surface,
                contentColor = AppTheme.colors.onSurface,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_accent_color),
                    fontWeight = FontWeight.SemiBold,
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSystemAccent) AppTheme.colors.accent else AppTheme.colors.surfaceVariant)
                            .border(
                                width = if (isSystemAccent) 2.dp else 1.dp,
                                color = if (isSystemAccent) AppTheme.colors.onSurface else Color.Transparent,
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Key Color Presets
                    KeyColors.forEachIndexed { index, color ->
                        val seedIdx = index + 1
                        val isSelected = appState.seedIndex == seedIdx
                        Box(
                            modifier = Modifier
                                .size(36.dp)
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
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Bottom Bar Size
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = AppTheme.colors.surface,
                contentColor = AppTheme.colors.onSurface,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_bottom_bar_size),
                    fontWeight = FontWeight.SemiBold,
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
                            targetValue = if (isSelected) AppTheme.colors.accent else AppTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                            label = "bar_size_bg_$size",
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) AppTheme.colors.onAccent else AppTheme.colors.onSurface,
                            label = "bar_size_text_$size",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .clickable {
                                    updateAppState { it.copy(bottomBarSize = size) }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
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
// PAGE 3: Quick Import (Clipboard / QR / File)
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

    suspend fun importRawText(text: String) {
        val importResult = importProxyServersFromText(text, ProxyServerImportSource.Clipboard)
        if (importResult.servers.isNotEmpty()) {
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
        }
    }

    LaunchedEffect(Unit) {
        val clip = clipboard.getPlainText()?.trim()
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }
                if (!text.isNullOrBlank()) {
                    importRawText(text)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        OnboardingHeroSection(
            icon = MiuixIcons.Add,
            title = stringResource(R.string.onboarding_import_title),
            subtitle = stringResource(R.string.onboarding_import_subtitle),
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Detected in clipboard banner
        clipboardText?.let { clip ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = AppTheme.colors.accent.copy(alpha = 0.15f),
                    contentColor = AppTheme.colors.onSurface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                            text = clip.take(36) + "...",
                            fontSize = 11.sp,
                            color = AppTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppTheme.colors.accent)
                            .clickable {
                                scope.launch {
                                    importRawText(clip)
                                    clipboardText = null
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_clipboard_import_btn),
                            color = AppTheme.colors.onAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Action 1: Scan QR Code
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        val scanText = runCatching { services.qrScanner() }.getOrNull()
                        if (!scanText.isNullOrBlank()) {
                            importRawText(scanText)
                        }
                    }
                },
            colors = CardDefaults.defaultColors(
                color = AppTheme.colors.surface,
                contentColor = AppTheme.colors.onSurface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_scan_qr_btn),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = AppTheme.colors.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action 2: Import from File
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
            colors = CardDefaults.defaultColors(
                color = AppTheme.colors.surface,
                contentColor = AppTheme.colors.onSurface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_import_file_btn),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = AppTheme.colors.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status badge
        val count = appState.proxyServers.size
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.12f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_servers_added_count, count),
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.SemiBold,
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF10B981).copy(alpha = 0.3f),
                            Color(0xFF10B981).copy(alpha = 0.08f),
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
                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                    .border(
                        width = 2.dp,
                        color = Color(0xFF10B981).copy(alpha = 0.6f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = AppTheme.colors.surface,
                contentColor = AppTheme.colors.onSurface,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_ready_status_ready),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = AppTheme.colors.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val serverCount = appState.proxyServers.size
                Text(
                    text = if (serverCount > 0) {
                        stringResource(R.string.onboarding_servers_added_count, serverCount)
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
