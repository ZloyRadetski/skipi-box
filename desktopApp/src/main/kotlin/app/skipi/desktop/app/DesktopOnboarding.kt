// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import app.skipi.ui.onboarding.OnboardingAppearance
import app.skipi.ui.onboarding.OnboardingBottomBarSize
import app.skipi.ui.onboarding.OnboardingLanguage
import app.skipi.ui.onboarding.SkipiOnboardingActions
import app.skipi.ui.onboarding.SkipiOnboardingScreen
import app.skipi.ui.onboarding.SkipiOnboardingState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.ic_about_logo
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import org.jetbrains.compose.resources.painterResource

/** Android's first-run flow minus the options Desktop has no equivalent for. */
internal fun DesktopAppSettings.toOnboardingState(serverCount: Int, subscriptionCount: Int) = SkipiOnboardingState(
    language = when (languageTag?.substringBefore('-')?.lowercase()) {
        "en" -> OnboardingLanguage.English
        "zh" -> OnboardingLanguage.Chinese
        "ru" -> OnboardingLanguage.Russian
        "fa" -> OnboardingLanguage.Persian
        else -> OnboardingLanguage.FollowSystem
    },
    appearance = when (themeMode) {
        DesktopThemeMode.Light -> OnboardingAppearance.Light
        DesktopThemeMode.Dark -> OnboardingAppearance.Dark
        DesktopThemeMode.Amoled -> OnboardingAppearance.Amoled
        else -> OnboardingAppearance.Other
    },
    bottomBarSize = when (bottomBarSize ?: 1) {
        0 -> OnboardingBottomBarSize.Small
        1 -> OnboardingBottomBarSize.Medium
        else -> OnboardingBottomBarSize.Large
    },
    proxyServerCount = serverCount,
    subscriptionGroupCount = subscriptionCount,
    showPermissionsStep = false,
    qrScanAvailable = false,
    showSystemAppearance = false,
    showAccentColors = false,
)

internal fun DesktopAppSettings.withOnboardingAppearance(appearance: OnboardingAppearance): DesktopAppSettings =
    copy(
        themeMode = when (appearance) {
            OnboardingAppearance.Light -> DesktopThemeMode.Light
            OnboardingAppearance.Amoled -> DesktopThemeMode.Amoled
            OnboardingAppearance.Dark, OnboardingAppearance.System, OnboardingAppearance.Other -> DesktopThemeMode.Dark
        },
    )

internal fun DesktopAppSettings.withOnboardingLanguage(language: OnboardingLanguage): DesktopAppSettings =
    copy(
        languageTag = when (language) {
            OnboardingLanguage.English -> "en"
            OnboardingLanguage.Chinese -> "zh"
            OnboardingLanguage.Russian -> "ru"
            OnboardingLanguage.Persian -> "fa"
            OnboardingLanguage.FollowSystem -> null
        },
    )

internal fun DesktopAppSettings.withOnboardingBottomBarSize(size: OnboardingBottomBarSize): DesktopAppSettings =
    copy(
        bottomBarSize = when (size) {
            OnboardingBottomBarSize.Small -> 0
            OnboardingBottomBarSize.Medium -> 1
            OnboardingBottomBarSize.Large -> 2
        },
    )

internal fun DesktopAppSettings.withOnboardingCompleted(): DesktopAppSettings = copy(onboardingCompleted = true)

@Composable
internal fun DesktopOnboardingScreen(
    settings: DesktopAppSettings,
    serverCount: Int,
    subscriptionCount: Int,
    onSettingsChange: (DesktopAppSettings) -> Unit,
    onImportText: suspend (String, Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    SkipiOnboardingScreen(
        state = settings.toOnboardingState(serverCount, subscriptionCount),
        actions = SkipiOnboardingActions(
            onFinish = onFinish,
            onLanguageSelected = { onSettingsChange(settings.withOnboardingLanguage(it)) },
            onAppearanceSelected = { onSettingsChange(settings.withOnboardingAppearance(it)) },
            onAccentSelected = {},
            onBottomBarSizeSelected = { onSettingsChange(settings.withOnboardingBottomBarSize(it)) },
            onOpenTelegram = { url -> runCatching { Desktop.getDesktop().browse(java.net.URI(url)) } },
            onCopyTelegram = { url, _ ->
                runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null) }
            },
            onRequestVpnPermission = {},
            onRequestNotificationPermission = {},
            onOpenBatterySettings = {},
            onReadClipboard = {
                runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
                }.getOrNull()
            },
            onImportText = onImportText,
            onScanQr = { null },
        ),
        logoPainter = painterResource(Res.drawable.ic_about_logo),
    )
}
