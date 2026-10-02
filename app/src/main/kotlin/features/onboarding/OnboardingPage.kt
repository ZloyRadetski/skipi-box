// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalUpdateAppState
import app.ProxyServerState
import app.R
import app.collectAppState
import app.modes.BottomBarSizeLarge
import app.modes.BottomBarSizeMedium
import app.modes.BottomBarSizeSmall
import app.modes.ColorModeAmoled
import app.modes.ColorModeDark
import app.modes.ColorModeLight
import app.modes.ColorModeSystem
import app.modes.LanguageModeChinese
import app.modes.LanguageModeEnglish
import app.modes.LanguageModePersian
import app.modes.LanguageModeRussian
import app.modes.LanguageModeSystem
import app.skipi.ui.onboarding.OnboardingAppearance
import app.skipi.ui.onboarding.OnboardingBottomBarSize
import app.skipi.ui.onboarding.OnboardingLanguage
import app.skipi.ui.onboarding.OnboardingPermissionState
import app.skipi.ui.onboarding.SkipiOnboardingActions
import app.skipi.ui.onboarding.SkipiOnboardingScreen
import app.skipi.ui.onboarding.SkipiOnboardingState
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.importProxyServersFromText
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.SubscriptionInstallConfigUseCase
import features.subscription.toSubscriptionInstallConfigOrNull
import kotlinx.coroutines.launch
import system.isIgnoringBatteryOptimizations
import system.openBatteryOptimizationSettings
import ui.clipboard.getPlainText
import ui.clipboard.setPlainText
import java.util.Locale

@Composable
fun OnboardingPage(
    padding: PaddingValues,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val stateStore = LocalAppStateStore.current
    val services = LocalAppServices.current
    val tipNotifier = services.tipNotifier
    val updateAppState = LocalUpdateAppState.current
    val appState by stateStore.collectAppState()
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var vpnGranted by remember(context) { mutableStateOf(VpnService.prepare(context) == null) }
    var notificationsGranted by remember(context) {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var batteryOptimizationIgnored by remember(context) { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vpnGranted = VpnService.prepare(context) == null
                notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val clipboardEmptyTip = androidx.compose.ui.res.stringResource(R.string.onboarding_clipboard_empty)
    val serversAddedFormat = androidx.compose.ui.res.stringResource(R.string.onboarding_servers_added_count)
    val subscriptionAddedTip = androidx.compose.ui.res.stringResource(R.string.onboarding_subscription_added)

    suspend fun importRawText(text: String, isExplicitPaste: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            if (isExplicitPaste) tipNotifier.show(clipboardEmptyTip)
            return
        }

        val installConfig = trimmed.toSubscriptionInstallConfigOrNull()
        if (installConfig != null) {
            runCatching {
                SubscriptionInstallConfigUseCase(
                    stateStore = stateStore,
                    subscriptionFetcher = services.subscriptionFetcher,
                ).install(installConfig)
            }.onSuccess {
                tipNotifier.show(subscriptionAddedTip)
            }.onFailure { error -> tipNotifier.showError(error) }
            return
        }

        val imported = runCatching {
            importProxyServersFromText(trimmed, ProxyServerImportSource.Clipboard)
        }.getOrNull()
        if (imported != null && imported.servers.isNotEmpty()) {
            stateStore.proxyServerRepository.updateCatalog { current ->
                var nextId = current.nextServerId
                val importedRecords = imported.servers.map { server ->
                    app.skipi.app.model.ProxyServerRecord(
                        id = nextId++,
                        server = server,
                        sourceSubscriptionId = null,
                    )
                }
                current.copy(
                    servers = current.servers + importedRecords,
                    nextServerId = nextId,
                )
            }
            tipNotifier.show(String.format(Locale.getDefault(), serversAddedFormat, imported.servers.size))
        } else if (isExplicitPaste) {
            tipNotifier.show(clipboardEmptyTip)
        }
    }

    val onboardingState = SkipiOnboardingState(
        language = appState.languageMode.toOnboardingLanguage(),
        appearance = appState.colorMode.toOnboardingAppearance(),
        accentSeedIndex = appState.seedIndex,
        bottomBarSize = appState.bottomBarSize.toOnboardingBottomBarSize(),
        proxyServerCount = appState.proxyServers.size,
        subscriptionGroupCount = appState.subscriptionGroups.size,
        permissions = OnboardingPermissionState(
            vpnGranted = vpnGranted,
            showNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            notificationsGranted = notificationsGranted,
            batteryOptimizationIgnored = batteryOptimizationIgnored,
        ),
    )
    val actions = SkipiOnboardingActions(
        onFinish = onFinish,
        onLanguageSelected = { language -> updateAppState { it.copy(languageMode = language.toLanguageMode()) } },
        onAppearanceSelected = { appearance -> updateAppState { it.copy(colorMode = appearance.toColorMode()) } },
        onAccentSelected = { seedIndex -> updateAppState { it.copy(seedIndex = seedIndex, enableMaterialYou = true) } },
        onBottomBarSizeSelected = { size -> updateAppState { it.copy(bottomBarSize = size.toLegacyValue()) } },
        onOpenTelegram = { url -> runCatching { uriHandler.openUri(url) } },
        onCopyTelegram = { url, message ->
            scope.launch {
                clipboard.setPlainText(url)
                tipNotifier.show(message)
            }
        },
        onRequestVpnPermission = {
            scope.launch {
                val intent = VpnService.prepare(context)
                if (intent == null) vpnGranted = true
                else {
                    runCatching { services.requestVpnPermission(intent) }
                    vpnGranted = VpnService.prepare(context) == null
                }
            }
        },
        onRequestNotificationPermission = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        },
        onOpenBatterySettings = { openBatteryOptimizationSettings(context) },
        onReadClipboard = { runCatching { clipboard.getPlainText() }.getOrNull() },
        onImportText = ::importRawText,
        onScanQr = { runCatching { services.qrScanner() }.getOrNull() },
    )

    SkipiOnboardingScreen(
        state = onboardingState,
        actions = actions,
        logoPainter = painterResource(R.drawable.ic_about_logo),
        modifier = modifier,
    )
}

private fun Int.toOnboardingLanguage(): OnboardingLanguage = when (this) {
    LanguageModeEnglish -> OnboardingLanguage.English
    LanguageModeChinese -> OnboardingLanguage.Chinese
    LanguageModeRussian -> OnboardingLanguage.Russian
    LanguageModePersian -> OnboardingLanguage.Persian
    else -> OnboardingLanguage.FollowSystem
}

private fun OnboardingLanguage.toLanguageMode(): Int = when (this) {
    OnboardingLanguage.English -> LanguageModeEnglish
    OnboardingLanguage.Chinese -> LanguageModeChinese
    OnboardingLanguage.Russian -> LanguageModeRussian
    OnboardingLanguage.Persian -> LanguageModePersian
    OnboardingLanguage.FollowSystem -> LanguageModeSystem
}

private fun Int.toOnboardingAppearance(): OnboardingAppearance = when (this) {
    ColorModeLight -> OnboardingAppearance.Light
    ColorModeDark -> OnboardingAppearance.Dark
    ColorModeAmoled -> OnboardingAppearance.Amoled
    ColorModeSystem -> OnboardingAppearance.System
    else -> OnboardingAppearance.Other
}

private fun OnboardingAppearance.toColorMode(): Int = when (this) {
    OnboardingAppearance.Light -> ColorModeLight
    OnboardingAppearance.Dark -> ColorModeDark
    OnboardingAppearance.Amoled -> ColorModeAmoled
    OnboardingAppearance.System, OnboardingAppearance.Other -> ColorModeSystem
}

private fun Int.toOnboardingBottomBarSize(): OnboardingBottomBarSize = when (this) {
    BottomBarSizeSmall -> OnboardingBottomBarSize.Small
    BottomBarSizeMedium -> OnboardingBottomBarSize.Medium
    else -> OnboardingBottomBarSize.Large
}

private fun OnboardingBottomBarSize.toLegacyValue(): Int = when (this) {
    OnboardingBottomBarSize.Small -> BottomBarSizeSmall
    OnboardingBottomBarSize.Medium -> BottomBarSizeMedium
    OnboardingBottomBarSize.Large -> BottomBarSizeLarge
}
